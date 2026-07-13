package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.CareMemoBackup
import jp.mydns.fujiwara.carememo.data.DatabaseInconsistency
import jp.mydns.fujiwara.carememo.data.DatabaseKeyManager
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.SettingsLogic
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.milliseconds

/**
 * 設定画面・バックアップ管理用の ViewModel
 */
class SettingsViewModel(
    private val maintenanceRepository: AppMaintenanceRepository,
    private val archivedPersonRepository: DeleteOrRestorePersonRepository,
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "Settings"
        private const val OP_EXPORT = "exportData"
        private const val OP_IMPORT = "importData"
        private const val OP_PROCEED_IMPORT = "proceedImportZip"
        private const val OP_CLEAR_ALL = "clearAllData"
        private const val OP_CLEAR_LOGS = "clearAuditLogs"
        private const val OP_ROTATE_LOGS = "rotateLogsManually"
        private const val OP_DELETE_ENDED = "deleteEndedPersons"
        private const val OP_INTEGRITY = "checkIntegrity"
        private const val OP_FIX_INCONSISTENCY = "fixInconsistencies"
        private const val OP_TEST_INCONSISTENCY = "insertTestInconsistency"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

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
            initialValue = true,
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

    val auditLogRetentionDays: StateFlow<Int> = userSettingsRepository.auditLogRetentionDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 30,
        )

    val auditLogCount: StateFlow<Int> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(auditLogRepository.getLogCount())
            kotlinx.coroutines.delay(5000.milliseconds)
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, "auditLogCountFlow"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 復元処理用の一時保持
    private var pendingImportFile: File? = null
    private var pendingImportUri: Uri? = null

    val deletedUserList: StateFlow<List<Person>> = archivedPersonRepository.getArchivedPersons()
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "deletedUserListFlow", "person_db"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _isProcessing = MutableStateFlow(value = false)
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
                        R.string.settings_err_title_biometric,
                        R.string.settings_err_biometric_unsupported,
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

    fun clearAuditLogs() {
        safeLaunch(
            operation = OP_CLEAR_LOGS,
            contextBuilder = { tableName = "audit_log" }
        ) {
            auditLogRepository.deleteAllLogs()
            showSnackbar(R.string.settings_msg_audit_log_cleared)
        }
    }

    fun rotateLogsManually() {
        safeLaunch(
            operation = OP_ROTATE_LOGS,
            loadingState = _isProcessing,
            contextBuilder = { tableName = "audit_log" }
        ) {
            val days = auditLogRetentionDays.value
            auditLogRepository.deleteOldLogs(days)
            showInfo(R.string.common_error_title_info, R.string.settings_msg_rotate_success)
        }
    }

    fun deleteEndedPersons() {
        safeLaunch(
            operation = OP_DELETE_ENDED,
            contextBuilder = { tableName = "person_db" }
        ) {
            archivedPersonRepository.deleteAllEndedPersons()
            showInfo(R.string.common_error_title_info, R.string.settings_msg_delete_ended_success)
        }
    }

    fun exportData(context: Context, uri: Uri) {
        var tempDir: File? = null
        var tempZipFile: File? = null
        safeLaunch(
            operation = OP_EXPORT,
            loadingState = _isProcessing,
            contextBuilder = { tableName = "all_db" }
        ) {
            try {
                // 容量チェック
                if (!SettingsLogic.hasAvailableSpace(context.cacheDir, 50 * 1024 * 1024)) { // 最低 50MB 
                    showError(R.string.common_error_title_error, R.string.common_error_no_space, "50MB")
                    return@safeLaunch
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

                _processingProgress.value = 0
                
                ZipUtils.zip(
                    files = filesToZip,
                    zipFile = tempZipFile,
                    password = password,
                ) { progress ->
                    _processingProgress.value = progress
                }.getOrThrow()

                context.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    tempZipFile.inputStream().use { input: InputStream ->
                        input.copyTo(output)
                    }
                }
                showInfo(R.string.common_error_title_info, R.string.settings_msg_export_success)
            } finally {
                // 一時ファイルの確実な削除
                tempDir?.deleteRecursively()
                tempZipFile?.delete()
            }
        }
    }

    fun importData(context: Context, uri: Uri, passwordOverride: String? = null) {
        safeLaunch(
            operation = OP_IMPORT,
            contextBuilder = { tableName = "all_db" }
        ) {
            if (passwordOverride == null) {
                // 初回試行：一時ディレクトリの作成とファイルのコピー
                clearPendingImport()
                
                // 容量チェック
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val fileSize = pfd?.statSize ?: 0L
                pfd?.close()
                
                if (!SettingsLogic.hasAvailableSpace(context.cacheDir, (fileSize * 2.5).toLong())) {
                    showError(R.string.common_error_title_error, R.string.common_error_no_space, (fileSize * 2.5).toLong())
                    return@safeLaunch
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
                    (read == 4) && SettingsLogic.isValidZipHeader(header)
                }

                if (isZip) {
                    if (ZipUtils.isEncrypted(tempZipFile)) {
                        // 1. アプリ設定のパスワードで試行
                        val userPw = backupPassword.value
                        if (userPw.isNotEmpty() && ZipUtils.isValidPassword(tempZipFile, userPw)) {
                            proceedImportZip(context, tempZipFile, userPw)
                            return@safeLaunch
                        }

                        // 2. 現在のデバイスのDBキー（Base64）で試行（同じ端末内での移行）
                        val dbKey = DatabaseKeyManager(context).getOrCreatePassphrase()
                        val dbPw = Base64.encodeToString(dbKey, Base64.NO_WRAP)
                        if (ZipUtils.isValidPassword(tempZipFile, dbPw)) {
                            proceedImportZip(context, tempZipFile, dbPw)
                            return@safeLaunch
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
                if (!SettingsLogic.isVersionCompatible(backup.appVersionCode, BuildConfig.VERSION_CODE)) {
                    showError(R.string.common_error_title_update, R.string.settings_err_import_version_mismatch)
                    tempDir.deleteRecursively()
                    return@safeLaunch
                }

                    maintenanceRepository.replaceAllData(backup)
                    tempDir.deleteRecursively()
                    showInfo(R.string.common_error_title_info, R.string.settings_msg_import_success_data_only)
                }
            } else {
                // パスワード入力後の再試行
                val file = pendingImportFile ?: throw Exception("一時ファイルが見つかりません。")
                if (ZipUtils.isValidPassword(file, passwordOverride)) {
                    proceedImportZip(context, file, passwordOverride)
                    clearPendingImport()
                } else {
                    showError(R.string.common_error_title_error, R.string.settings_err_import_wrong_password)
                    sendUiEvent(UiEvent.RequestPassword)
                }
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
            ) { progress ->
                _processingProgress.value = progress
            }.getOrThrow()

            val jsonFile = File(tempDir, "backup.json")
            if (!jsonFile.exists()) throw Exception("バックアップファイル(backup.json)が見つかりません。")
            
            val jsonString = jsonFile.readText()
            val backup = json.decodeFromString<CareMemoBackup>(jsonString)

            // バージョンチェック
            if (!SettingsLogic.isVersionCompatible(backup.appVersionCode, BuildConfig.VERSION_CODE)) {
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
            
            showInfo(R.string.common_error_title_info, R.string.settings_msg_import_success)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, OP_PROCEED_IMPORT, "all_db"))
            // 失敗時は写真をロールバック
            if (backupPhotosDir.exists()) {
                appPhotosDir.deleteRecursively()
                backupPhotosDir.renameTo(appPhotosDir)
            }
            showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
        } finally {
            _isProcessing.value = false
            // 解凍に使用した一時ディレクトリのクリーンアップ
            tempDir.deleteRecursively()
        }
    }

    private fun clearPendingImport() {
        pendingImportFile?.parentFile?.deleteRecursively()
        pendingImportFile = null
        pendingImportUri = null
    }

    fun clearAllData(context: Context) {
        safeLaunch(
            operation = OP_CLEAR_ALL,
            loadingState = _isProcessing,
            contextBuilder = { tableName = "all_db" }
        ) {
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

            showInfo(R.string.common_error_title_info, R.string.settings_msg_clear_all_success)
            sendUiEvent(UiEvent.SaveSuccess) 
        }
    }

    /**
     * データベースの不整合をスキャンします。
     */
    fun checkIntegrity() {
        safeLaunch(
            operation = OP_INTEGRITY,
            loadingState = _isProcessing,
            contextBuilder = { tableName = "maintenance" }
        ) {
            val results = maintenanceRepository.scanInconsistencies()
            _inconsistencies.value = results
            
            if (results.isEmpty()) {
                showInfo(R.string.common_error_title_info, R.string.settings_msg_integrity_ok)
            }
        }
    }

    /**
     * 検出された不整合を修正（削除）します。
     */
    fun fixInconsistencies() {
        safeLaunch(
            operation = OP_FIX_INCONSISTENCY,
            loadingState = _isProcessing,
            contextBuilder = { tableName = "maintenance" }
        ) {
            maintenanceRepository.cleanInconsistencies(_inconsistencies.value)
            val count = _inconsistencies.value.size
            _inconsistencies.value = emptyList()
            showInfo(R.string.common_error_title_info, R.string.settings_msg_fix_success, count)
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
        safeLaunch(operation = OP_TEST_INCONSISTENCY) {
            maintenanceRepository.insertTestInconsistency()
            showSnackbar(R.string.settings_msg_test_inconsistency_added)
        }
    }

    fun setAuditLogRetentionDays(days: Int) {
        viewModelScope.launch {
            userSettingsRepository.setAuditLogRetentionDays(days)
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
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
