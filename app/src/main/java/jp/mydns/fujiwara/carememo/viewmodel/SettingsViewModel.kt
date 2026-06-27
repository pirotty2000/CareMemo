package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoBackup
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.provider.DocumentsContract
import jp.mydns.fujiwara.carememo.data.InitialDataLoader
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 設定画面・バックアップ管理用の ViewModel
 */
class SettingsViewModel(
    private val repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    private val json = Json { prettyPrint = true }

    val isBiometricEnabled: StateFlow<Boolean> = userSettingsRepository.isBiometricEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val lockTimeoutMinutes: StateFlow<Int> = userSettingsRepository.lockTimeoutMinutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val userList: StateFlow<List<Person>> = repository.getAllPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val deletedUserList: StateFlow<List<Person>> = repository.getDeletedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setNameMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.setNameMaskingEnabled(enabled)
        }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    userSettingsRepository.setBiometricEnabled(true)
                } else {
                    showError(
                        "設定できません",
                        "このデバイスは生体認証または画面ロック設定に対応していないか、認証情報が登録されていません。"
                    )
                }
            } else {
                userSettingsRepository.setBiometricEnabled(false)
            }
        }
    }

    fun setLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            userSettingsRepository.setLockTimeoutMinutes(minutes)
        }
    }

    fun setDefaultRecorderName(name: String) {
        viewModelScope.launch {
            userSettingsRepository.setDefaultRecorderName(name)
        }
    }

    fun deleteEndedPersons() {
        viewModelScope.launch {
            try {
                repository.deleteEndedPersons()
                sendUiEvent(UiEvent.ShowInfoDialog("完了", "利用終了者のデータを完全に抹消しました。"))
            } catch (e: Exception) {
                showError("エラー", "データの抹消に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val backup = repository.getBackupData()
                val jsonString = json.encodeToString(backup)
                val tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                val jsonFile = File(tempDir, "backup.json")
                jsonFile.writeText(jsonString)
                val photosDir = ImageUtils.getPhotosDir(context)
                val filesToZip = mutableListOf<File>()
                filesToZip.add(jsonFile)
                if (photosDir.exists() && photosDir.list()?.isNotEmpty() == true) {
                    filesToZip.add(photosDir)
                }
                val tempZipFile = File(context.cacheDir, "temp_backup.zip")
                ZipUtils.zip(filesToZip, tempZipFile)
                context.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    tempZipFile.inputStream().use { input: InputStream ->
                        input.copyTo(output)
                    }
                }
                jsonFile.delete()
                tempDir.delete()
                tempZipFile.delete()
                sendUiEvent(UiEvent.ShowInfoDialog("エクスポート完了", "データと写真のエクスポートが完了しました。"))
            } catch (e: Exception) {
                showError("エラー", "エクスポートに失敗しました: ${e.localizedMessage}")
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val isZip = context.contentResolver.openInputStream(uri)?.use { input ->
                    val header = ByteArray(4)
                    val read = input.read(header)
                    read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                } ?: false

                if (isZip) {
                    val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    val tempZipFile = File(tempDir, "temp_import.zip")
                    context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                        tempZipFile.outputStream().use { output: OutputStream ->
                            input.copyTo(output)
                        }
                    }
                    ZipUtils.unzip(tempZipFile, tempDir)
                    val jsonFile = File(tempDir, "backup.json")
                    if (!jsonFile.exists()) throw Exception("バックアップファイルが見つかりません。")
                    val jsonString = jsonFile.readText()
                    val backup = json.decodeFromString<CareMemoBackup>(jsonString)
                    repository.replaceAllData(backup)
                    ImageUtils.clearPhotosDir(context)
                    val extractedPhotosDir = File(tempDir, "photos")
                    if (extractedPhotosDir.exists() && extractedPhotosDir.isDirectory) {
                        val appPhotosDir = ImageUtils.getPhotosDir(context)
                        extractedPhotosDir.listFiles()?.forEach { file: File ->
                            file.copyTo(File(appPhotosDir, file.name), overwrite = true)
                        }
                    }
                    tempDir.deleteRecursively()
                    sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データと写真の復元が完了しました。"))
                } else {
                    val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (jsonString != null) {
                        val backup = json.decodeFromString<CareMemoBackup>(jsonString)
                        repository.replaceAllData(backup)
                        sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データの復元が完了しました。"))
                    }
                }
            } catch (e: Exception) {
                showError("エラー", "復元に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                sendUiEvent(UiEvent.ShowInfoDialog("完了", "全てのデータを削除しました。"))
            } catch (e: Exception) {
                showError("エラー", "データの削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 旧アプリ（JSONベース）からのデータ引き継ぎ。
     */
    fun importLegacyDataFromFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            try {
                val loader = InitialDataLoader(context, repository)
                loader.clearAllData()
                val resolver = context.contentResolver
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )
                val fileMap = mutableMapOf<String, Uri>()
                resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx)
                        val id = cursor.getString(idIdx)
                        fileMap[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    }
                }
                fileMap["person_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadPersons(s) } }
                fileMap["height_and_weight_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadHeightAndWeight(s) } }
                fileMap["bp_and_pulse_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadBpAndPulse(s) } }
                fileMap["glucose_and_hba1c_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadGlucoseAndHbA1c(s) } }
                fileMap["condition_at_visit_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadConditionAtVisit(s) } }
                sendUiEvent(UiEvent.ShowInfoDialog("完了", "旧アプリデータの引き継ぎが完了しました。"))
            } catch (e: Exception) {
                showError("エラー", "引き継ぎに失敗しました: ${e.localizedMessage}")
            }
        }
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
