package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoBackup
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.DatabaseKeyManager
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.data.DatabaseInconsistency
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 設定画面・バックアップ管理用の ViewModel
 */
class SettingsViewModel(
    private val maintenanceRepository: AppMaintenanceRepository,
    private val archivedPersonRepository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseViewModel(userSettingsRepository) {

    private val json = Json { prettyPrint = true }

    val isBiometricEnabled: StateFlow<Boolean> = userSettingsRepository.isBiometricEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val lockTimeoutMinutes: StateFlow<Int> = userSettingsRepository.lockTimeoutMinutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    val isBackupPasswordEnabled: StateFlow<Boolean> = userSettingsRepository.isBackupPasswordEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val backupPassword: StateFlow<String> = userSettingsRepository.backupPassword
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "",
        )

    val themeSetting: StateFlow<ThemeSetting> = userSettingsRepository.themeSetting
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeSetting.SYSTEM,
        )

    // 復元処理用の一時保持
    private var pendingImportFile: File? = null
    private var pendingImportUri: Uri? = null

    val deletedUserList: StateFlow<List<Person>> = archivedPersonRepository.getArchivedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0)
    val processingProgress = _processingProgress.asStateFlow()

    // データベース不整合チェックの結果
    private val _inconsistencies = MutableStateFlow<List<DatabaseInconsistency>>(emptyList())
    val inconsistencies = _inconsistencies.asStateFlow()

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
                    userSettingsRepository.setBiometricEnabled(enabled = true)
                } else {
                    showError(
                        "設定できません",
                        "このデバイスは生体認証または画面ロック設定に対応していないか、認証情報が登録されていません。",
                    )
                }
            } else {
                userSettingsRepository.setBiometricEnabled(enabled = false)
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

    fun setThemeSetting(theme: ThemeSetting) {
        viewModelScope.launch {
            userSettingsRepository.setThemeSetting(theme)
        }
    }

    fun deleteEndedPersons() {
        viewModelScope.launch {
            try {
                archivedPersonRepository.deleteAllEndedPersons()
                sendUiEvent(UiEvent.ShowInfoDialog("完了", "利用終了者のデータを一括で完全に抹消しました。"))
            } catch (e: Exception) {
                showError("エラー", "データの抹消に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            var tempDir: File? = null
            var tempZipFile: File? = null
            try {
                // 容量チェック
                if (!hasAvailableSpace(context.cacheDir, 50 * 1024 * 1024)) { // 最低 50MB 
                    showError("エラー", "空き容量が不足しているためエクスポートできません。")
                    return@launch
                }

                val backup = maintenanceRepository.getBackupData()
                val jsonString = json.encodeToString(CareMemoBackup.serializer(), backup)
                tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                val jsonFile = File(tempDir, "backup.json")
                jsonFile.writeText(jsonString)

                val photosDir = ImageUtils.getPhotosDirPublic(context)
                val filesToZip = mutableListOf<File>()
                filesToZip.add(jsonFile)
                if (photosDir.exists() && (photosDir.list()?.isNotEmpty() == true)) {
                    filesToZip.add(photosDir)
                }
                
                tempZipFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")

                // 暗号化パスワードの決定
                val password = if (isBackupPasswordEnabled.value) {
                    backupPassword.value
                } else {
                    val dbKey = DatabaseKeyManager(context).getOrCreatePassphrase()
                    Base64.encodeToString(dbKey, Base64.NO_WRAP)
                }

                _isProcessing.value = true
                _processingProgress.value = 0
                
                ZipUtils.zip(
                    files = filesToZip,
                    zipFile = tempZipFile,
                    password = password,
                    onProgress = { _processingProgress.value = it }
                ).getOrThrow()

                context.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    tempZipFile.inputStream().use { input: InputStream ->
                        input.copyTo(output)
                    }
                }
                sendUiEvent(UiEvent.ShowInfoDialog("エクスポート完了", "データと写真のエクスポートが完了しました。"))
            } catch (e: Exception) {
                showError("エラー", "エクスポートに失敗しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
                // 一時ファイルの確実な削除
                tempDir?.deleteRecursively()
                tempZipFile?.delete()
            }
        }
    }

    fun importData(context: Context, uri: Uri, passwordOverride: String? = null) {
        viewModelScope.launch {
            try {
                if (passwordOverride == null) {
                    // 初回試行：一時ディレクトリの作成とファイルのコピー
                    clearPendingImport()
                    
                    // 容量チェック
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    val fileSize = pfd?.statSize ?: 0L
                    pfd?.close()
                    
                    if (!hasAvailableSpace(context.cacheDir, (fileSize * 2.5).toLong())) {
                        showError("エラー", "空き容量が不足しているため復元できません。")
                        return@launch
                    }

                    val tempDir = File(context.cacheDir, "import_check_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    val tempZipFile = File(tempDir, "temp_import.zip")
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempZipFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // ファイル形式チェック (Zipマジックナンバー)
                    val isZip = tempZipFile.inputStream().use { input ->
                        val header = ByteArray(4)
                        val read = input.read(header)
                        (read == 4) &&
                                (header[0] == 0x50.toByte()) &&
                                (header[1] == 0x4B.toByte()) &&
                                (header[2] == 0x03.toByte()) &&
                                (header[3] == 0x04.toByte())
                    }

                    if (isZip) {
                        if (ZipUtils.isEncrypted(tempZipFile)) {
                            // 1. アプリ設定のパスワードで試行
                            val userPw = backupPassword.value
                            if (userPw.isNotEmpty() && ZipUtils.isValidPassword(tempZipFile, userPw)) {
                                proceedImportZip(context, tempZipFile, userPw)
                                return@launch
                            }

                            // 2. 現在のデバイスのDBキー（Base64）で試行（同じ端末内での移行）
                            val dbKey = DatabaseKeyManager(context).getOrCreatePassphrase()
                            val dbPw = Base64.encodeToString(dbKey, Base64.NO_WRAP)
                            if (ZipUtils.isValidPassword(tempZipFile, dbPw)) {
                                proceedImportZip(context, tempZipFile, dbPw)
                                return@launch
                            }

                            // 3. 自動一致しなかった場合はパスワード入力を求める（エラーは出さない）
                            pendingImportFile = tempZipFile
                            pendingImportUri = uri
                            sendUiEvent(UiEvent.RequestPassword)
                        } else {
                            proceedImportZip(context, tempZipFile, null)
                        }
                    } else {
                        // 直接JSONファイルとして処理
                        val jsonString = tempZipFile.readText()
                        val backup = json.decodeFromString<CareMemoBackup>(jsonString)
                        
                        // バージョンチェック
                        if (backup.appVersionCode > BuildConfig.VERSION_CODE) {
                            showError("復元エラー", "このバックアップは新しいバージョンのCareMemoで作成されています。アプリを更新してください。")
                            tempDir.deleteRecursively()
                            return@launch
                        }

                        maintenanceRepository.replaceAllData(backup)
                        tempDir.deleteRecursively()
                        sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データの復元が完了しました。"))
                    }
                } else {
                    // パスワード入力後の再試行
                    val file = pendingImportFile ?: throw Exception("一時ファイルが見つかりません。")
                    if (ZipUtils.isValidPassword(file, passwordOverride)) {
                        proceedImportZip(context, file, passwordOverride)
                        clearPendingImport()
                    } else {
                        showError("エラー", "パスワードが違います。")
                        // パスワードが違う場合はダイアログを閉じるか再入力を促す（ここでは再度RequestPasswordを送るのも手だが、UI側で制御）
                        sendUiEvent(UiEvent.RequestPassword)
                    }
                }
            } catch (e: Exception) {
                showError("エラー", "復元に失敗しました: ${e.localizedMessage}")
                clearPendingImport()
            }
        }
    }

    private suspend fun proceedImportZip(context: Context, zipFile: File, password: String?) {
        val tempDir = zipFile.parentFile ?: File(context.cacheDir, "import_exec")
        val appPhotosDir = ImageUtils.getPhotosDirPublic(context)
        val backupPhotosDir = File(context.filesDir, "photos_backup_${System.currentTimeMillis()}")
        
        try {
            _isProcessing.value = true
            _processingProgress.value = 0
            
            ZipUtils.unzip(
                zipFile = zipFile,
                targetDir = tempDir,
                password = password,
                onProgress = { _processingProgress.value = it }
            ).getOrThrow()

            val jsonFile = File(tempDir, "backup.json")
            if (!jsonFile.exists()) throw Exception("バックアップファイル(backup.json)が見つかりません。")
            
            val jsonString = jsonFile.readText()
            val backup = json.decodeFromString<CareMemoBackup>(jsonString)

            // バージョンチェック
            if (backup.appVersionCode > BuildConfig.VERSION_CODE) {
                throw Exception("このバックアップは新しいバージョンのCareMemoで作成されています。アプリを更新してください。")
            }

            // --- 写真の退避 (アトミック性の確保) ---
            if (appPhotosDir.exists()) {
                appPhotosDir.renameTo(backupPhotosDir)
            }
            appPhotosDir.mkdirs()

            // データベースの置換
            maintenanceRepository.replaceAllData(backup)
            
            // 新しい写真データのコピー
            val extractedPhotosDir = File(tempDir, "photos")
            if (extractedPhotosDir.exists() && extractedPhotosDir.isDirectory) {
                extractedPhotosDir.listFiles()?.forEach { file ->
                    file.copyTo(File(appPhotosDir, file.name), overwrite = true)
                }
            }
            
            // 全て成功したらバックアップを削除
            backupPhotosDir.deleteRecursively()
            
            sendUiEvent(UiEvent.ShowInfoDialog("復元完了", "データと写真の復元が完了しました。"))
        } catch (e: Exception) {
            // 失敗時は写真をロールバック
            if (backupPhotosDir.exists()) {
                appPhotosDir.deleteRecursively()
                backupPhotosDir.renameTo(appPhotosDir)
            }
            showError("復元失敗", "処理中にエラーが発生したため、元の状態に差し戻しました。\n理由: ${e.localizedMessage}")
        } finally {
            _isProcessing.value = false
            // 解凍に使用した一時ディレクトリのクリーンアップ
            tempDir.deleteRecursively()
        }
    }

    private fun hasAvailableSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            val stats = StatFs(dir.absolutePath)
            val available = stats.availableBlocksLong * stats.blockSizeLong
            available > requiredBytes
        } catch (_: Exception) {
            true // 取得に失敗した場合は念のため通すが、通常は失敗しない
        }
    }


    private fun clearPendingImport() {
        pendingImportFile?.parentFile?.deleteRecursively()
        pendingImportFile = null
        pendingImportUri = null
    }

    fun clearAllData(context: Context) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _processingProgress.value = 0

                // 1. 写真ファイルの全消去を先に試める
                val success = ImageUtils.clearPhotosDir(context)
                if (!success) {
                    throw Exception("写真データの物理削除に失敗しました。")
                }
                _processingProgress.value = 50

                // 2. データベースの全消去（トランザクション）
                maintenanceRepository.clearAllData()
                _processingProgress.value = 100

                sendUiEvent(UiEvent.ShowInfoDialog("完了", "全てのデータと写真を削除しました。アプリを初期状態に戻しました。"))
                // メイン画面へ戻るための通知（必要に応じて）
                sendUiEvent(UiEvent.SaveSuccess) 
            } catch (e: Exception) {
                showError("エラー", "データの削除に失敗しました: ${e.localizedMessage}\nデータ保護のため処理を中断しました。")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * データベースの不整合をスキャンします。
     */
    fun checkIntegrity() {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val results = maintenanceRepository.scanInconsistencies()
                _inconsistencies.value = results
                
                if (results.isEmpty()) {
                    sendUiEvent(UiEvent.ShowInfoDialog("チェック完了", "不整合なデータは見つかりませんでした。"))
                }
            } catch (e: Exception) {
                showError("エラー", "チェック中にエラーが発生しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 検出された不整合を修正（削除）します。
     */
    fun fixInconsistencies() {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                maintenanceRepository.cleanInconsistencies(_inconsistencies.value)
                val count = _inconsistencies.value.size
                _inconsistencies.value = emptyList()
                sendUiEvent(UiEvent.ShowInfoDialog("修復完了", "${count}件の孤立したデータを削除しました。"))
            } catch (e: Exception) {
                showError("エラー", "修復中にエラーが発生しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 不整合なチェック結果をクリアします（ダイアログを閉じる際など）。
     */
    fun clearInconsistencyResults() {
        _inconsistencies.value = emptyList()
    }

    /**
     * 【テスト用】あえて不整合なデータを挿入します。
     */
    fun insertTestInconsistency() {
        viewModelScope.launch {
            try {
                maintenanceRepository.insertTestInconsistency()
                showSnackbar("テスト用不整合データを1件挿入しました。")
            } catch (e: Exception) {
                showError("エラー", "挿入に失敗しました: ${e.localizedMessage}")
            }
        }
    }


    /**
     * デバイスが認証（生体認証または端末ロック）に対応し、かつ設定済みかを判定します。
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    class Factory(
        private val maintenanceRepository: AppMaintenanceRepository,
        private val archivedPersonRepository: DeleteOrRestorePersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(maintenanceRepository, archivedPersonRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
