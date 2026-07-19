package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.SettingsLogic
import jp.mydns.fujiwara.carememo.logic.feature.SettingsUiState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

/**
 * アプリ設定・バックアップ管理用の ViewModel (System B)
 */
class SettingsViewModel(
    private val maintenanceRepository: AppMaintenanceRepository,
    private val archivedPersonRepository: DeleteOrRestorePersonRepository,
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseUiStateViewModel<SettingsUiState, SettingsViewEvent>(
    userSettingsRepository,
    SettingsUiState()
) {

    companion object {
        private const val FEATURE_NAME = "Settings"
        private const val OP_EXPORT = "exportData"
        private const val OP_IMPORT = "importData"
        private const val OP_PROCEED_IMPORT = "proceedImport"
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
        // (B)系統標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定および統計情報の購読
        safeCollect(
            operation = "initialSettingsSync",
            contextBuilder = { tableName = "all_db" },
            flowProvider = {
                combine(
                    userSettingsRepository.isNameMaskingEnabled,
                    userSettingsRepository.isBiometricEnabled,
                    userSettingsRepository.lockTimeoutMinutes,
                    userSettingsRepository.defaultRecorderName,
                    userSettingsRepository.isBackupPasswordEnabled,
                    userSettingsRepository.backupPassword,
                    userSettingsRepository.themeSetting,
                    userSettingsRepository.auditLogRetentionDays,
                    auditLogRepository.getAuditLogCountFlow(),
                    archivedPersonRepository.getArchivedPersons()
                ) { values ->
                    val masking = values[0] as Boolean
                    val biometric = values[1] as Boolean
                    val timeout = values[2] as Int
                    val recorder = values[3] as String
                    val backupEnabled = values[4] as Boolean
                    val password = values[5] as String
                    val theme = values[6] as ThemeSetting
                    val retention = values[7] as Int
                    val count = values[8] as Int
                    val archived = values[9] as List<Person>

                    currentState.copy(
                        isNameMaskingEnabled = masking,
                        isBiometricEnabled = biometric,
                        lockTimeoutMinutes = timeout,
                        defaultRecorderName = recorder,
                        isBackupPasswordEnabled = backupEnabled,
                        backupPassword = password,
                        themeSetting = theme,
                        auditLogRetentionDays = retention,
                        auditLogCount = count,
                        endedUserCount = archived.size
                    )
                }
            }
        ) { nextState ->
            updateUiState { nextState }
        }
    }

    // インポート用の一時データ
    private var pendingImportFile: File? = null
    private var pendingImportUri: Uri? = null

    override fun copyWithLoadingState(state: SettingsUiState, isLoading: Boolean): SettingsUiState {
        return state.copy(isProcessing = isLoading)
    }

    fun setNameMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.setNameMaskingEnabled(enabled)
        }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                viewModelScope.launch { userSettingsRepository.setBiometricEnabled(true) }
            } else {
                showError("エラー", "この端末では生体認証を利用できません。")
            }
        } else {
            viewModelScope.launch { userSettingsRepository.setBiometricEnabled(false) }
        }
    }

    fun setLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch { userSettingsRepository.setLockTimeoutMinutes(minutes) }
    }

    fun setDefaultRecorderName(name: String) {
        viewModelScope.launch { userSettingsRepository.setDefaultRecorderName(name) }
    }

    fun setBackupPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch { userSettingsRepository.setBackupPasswordEnabled(enabled) }
    }

    fun setBackupPassword(password: String) {
        viewModelScope.launch { userSettingsRepository.setBackupPassword(password) }
    }

    fun setThemeSetting(setting: ThemeSetting) {
        viewModelScope.launch { userSettingsRepository.setThemeSetting(setting) }
    }

    private var versionTapCount: Int = 0
    fun handleVersionClick() {
        versionTapCount++
        if (SettingsLogic.shouldEnableDeveloperMode(versionTapCount)) {
            updateUiState { it.copy(isDeveloperModeEnabled = true) }
        }
    }

    fun clearAuditLogs() {
        safeLaunch(operation = OP_CLEAR_LOGS, loadingState = loadingStateProxy) {
            auditLogRepository.deleteAllLogs()
            showSnackbar(R.string.settings_msg_audit_log_cleared)
        }
    }

    fun rotateLogsManually() {
        safeLaunch(operation = OP_ROTATE_LOGS, loadingState = loadingStateProxy) {
            auditLogRepository.deleteOldLogs(currentState.auditLogRetentionDays)
            showSnackbar(R.string.settings_msg_rotate_success)
        }
    }

    fun deleteEndedPersons() {
        safeLaunch(operation = OP_DELETE_ENDED, loadingState = loadingStateProxy) {
            archivedPersonRepository.deleteAllEndedPersons(featureName, OP_DELETE_ENDED)
            showSnackbar(R.string.settings_msg_delete_ended_success)
        }
    }

    fun exportData(context: Context, uri: Uri) {
        val password = if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null

        safeLaunch(
            operation = OP_EXPORT,
            loadingState = loadingStateProxy,
            contextBuilder = { errorMessageRes = R.string.common_error_save }
        ) {
            maintenanceRepository.exportData(context, uri, password) { progress ->
                updateUiState { it.copy(processingProgress = progress) }
            }
            sendViewEvent(SettingsViewEvent.ExportSuccess)
            showSnackbar(R.string.settings_msg_export_success)
        }
    }

    fun importData(context: Context, uri: Uri, inputPassword: String? = null) {
        // パスワードの決定：
        // 1. 引数で直接渡された場合（ダイアログからの再実行時など）
        // 2. 引数がない場合は、現在の設定のデフォルトパスワードを使用
        val password = inputPassword ?: if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null

        safeLaunch(
            operation = OP_IMPORT,
            loadingState = loadingStateProxy,
            contextBuilder = { errorMessageRes = R.string.common_error_save }
        ) {
            try {
                maintenanceRepository.importData(context, uri, password) { progress ->
                    updateUiState { it.copy(processingProgress = progress) }
                }
                sendViewEvent(SettingsViewEvent.ImportSuccess)
                showSnackbar(R.string.settings_msg_import_success)
            } catch (e: Exception) {
                // パスワード間違い（ZipUtilsから IOException("password...") が投げられる想定）
                if (e.message?.contains("password", ignoreCase = true) == true) {
                    sendViewEvent(SettingsViewEvent.RequestImportPassword)
                } else {
                    throw e // それ以外は通常の例外処理へ
                }
            }
        }
    }

    fun clearPendingImport() {
        pendingImportFile?.delete()
        pendingImportFile = null
        pendingImportUri = null
    }

    fun clearAllData(context: Context) {
        safeLaunch(operation = OP_CLEAR_ALL, loadingState = loadingStateProxy) {
            maintenanceRepository.clearAllData()
            showSnackbar(R.string.settings_msg_clear_all_success)
        }
    }

    fun checkIntegrity() {
        safeLaunch(operation = OP_INTEGRITY, loadingState = loadingStateProxy) {
            val results = maintenanceRepository.scanInconsistencies()
            updateUiState { it.copy(inconsistencies = results) }
            if (results.isEmpty()) {
                showSnackbar(R.string.settings_msg_integrity_ok)
            }
        }
    }

    fun fixInconsistencies() {
        safeLaunch(operation = OP_FIX_INCONSISTENCY, loadingState = loadingStateProxy) {
            maintenanceRepository.cleanInconsistencies(currentState.inconsistencies)
            updateUiState { it.copy(inconsistencies = emptyList()) }
            showSnackbar(R.string.settings_msg_fix_success, currentState.inconsistencies.size)
        }
    }

    fun clearInconsistencyResults() {
        updateUiState { it.copy(inconsistencies = emptyList()) }
    }

    fun insertTestInconsistency() {
        safeLaunch(operation = OP_TEST_INCONSISTENCY) {
            maintenanceRepository.insertTestInconsistency()
            showSnackbar(R.string.settings_msg_test_inconsistency_added)
        }
    }

    fun setAuditLogRetentionDays(days: Int) {
        viewModelScope.launch { userSettingsRepository.setAuditLogRetentionDays(days) }
    }

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    class Factory(
        private val maintenanceRepository: AppMaintenanceRepository,
        private val archivedPersonRepository: DeleteOrRestorePersonRepository,
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                maintenanceRepository,
                archivedPersonRepository,
                auditLogRepository,
                userSettingsRepository
            ) as T
        }
    }
}
