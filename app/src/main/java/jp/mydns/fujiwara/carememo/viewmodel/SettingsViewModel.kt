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

    val isBackupPasswordEnabled: StateFlow<Boolean> = userSettingsRepository.isBackupPasswordEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val backupPassword: StateFlow<String> = userSettingsRepository.backupPassword
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    // 復元処理用の一時保持
    private var pendingImportFile: File? = null
    private var pendingImportUri: Uri? = null

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

    fun setBackupPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.setBackupPasswordEnabled(enabled)
        }
    }

    fun setBackupPassword(password: String) {
        viewModelScope.launch {
            userSettingsRepository.setBackupPassword(password)
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
                val password = if (isBackupPasswordEnabled.value) {
                    backupPassword.value
                } else null
                ZipUtils.zip(filesToZip, tempZipFile, password)
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

    fun importData(context: Context, uri: Uri, passwordOverride: String? = null) {
        viewModelScope.launch {
            try {
                // 初回呼び出し（passwordOverrideがnull）の場合、ファイルを一時保存してチェック
                if (passwordOverride == null) {
                    val tempDir = File(context.cacheDir, "import_check_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    val tempZipFile = File(tempDir, "temp_import.zip")
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempZipFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // Zipかどうかチェック
                    val isZip = tempZipFile.inputStream().use { input ->
                        val header = ByteArray(4)
                        val read = input.read(header)
                        read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                    }

                    if (isZip) {
                        if (ZipUtils.isEncrypted(tempZipFile)) {
                            // デフォルトパスワードで試行
                            val defaultPw = backupPassword.value
                            if (defaultPw.isNotEmpty() && ZipUtils.isValidPassword(tempZipFile, defaultPw)) {
                                proceedImportZip(context, tempZipFile, defaultPw)
                            } else {
                                // パスワード入力が必要
                                pendingImportFile = tempZipFile
                                pendingImportUri = uri
                                sendUiEvent(UiEvent.RequestPassword)
                                return@launch
                            }
                        } else {
                            proceedImportZip(context, tempZipFile, null)
                        }
                    } else {
                        // JSONファイルとして処理
                        val jsonString = tempZipFile.readText()
                        val backup = json.decodeFromString<CareMemoBackup>(jsonString)
                        repository.replaceAllData(backup)
                        tempDir.deleteRecursively()
                        sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データの復元が完了しました。"))
                    }
                } else {
                    // パスワード入力後の再開
                    val file = pendingImportFile ?: throw Exception("一時ファイルが見つかりません。")
                    if (ZipUtils.isValidPassword(file, passwordOverride)) {
                        proceedImportZip(context, file, passwordOverride)
                        pendingImportFile = null
                        pendingImportUri = null
                    } else {
                        showError("エラー", "パスワードが違います。")
                    }
                }
            } catch (e: Exception) {
                showError("エラー", "復元に失敗しました: ${e.localizedMessage}")
                pendingImportFile?.parentFile?.deleteRecursively()
                pendingImportFile = null
            }
        }
    }

    private suspend fun proceedImportZip(context: Context, zipFile: File, password: String?) {
        val tempDir = zipFile.parentFile ?: File(context.cacheDir, "import_exec")
        ZipUtils.unzip(zipFile, tempDir, password)
        val jsonFile = File(tempDir, "backup.json")
        if (!jsonFile.exists()) throw Exception("バックアップファイル(backup.json)が見つかりません。")
        val jsonString = jsonFile.readText()
        val backup = json.decodeFromString<CareMemoBackup>(jsonString)
        repository.replaceAllData(backup)
        
        ImageUtils.clearPhotosDir(context)
        val extractedPhotosDir = File(tempDir, "photos")
        if (extractedPhotosDir.exists() && extractedPhotosDir.isDirectory) {
            val appPhotosDir = ImageUtils.getPhotosDir(context)
            extractedPhotosDir.listFiles()?.forEach { file ->
                file.copyTo(File(appPhotosDir, file.name), overwrite = true)
            }
        }
        tempDir.deleteRecursively()
        sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データと写真の復元が完了しました。"))
    }

    fun clearAllData(context: Context) {
        viewModelScope.launch {
            try {
                // データベースの全消去
                repository.clearAllData()
                // 写真ファイルの全消去
                ImageUtils.clearPhotosDir(context)
                
                sendUiEvent(UiEvent.ShowInfoDialog("完了", "全てのデータと写真を削除しました。"))
            } catch (e: Exception) {
                showError("エラー", "データの削除に失敗しました: ${e.localizedMessage}")
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
