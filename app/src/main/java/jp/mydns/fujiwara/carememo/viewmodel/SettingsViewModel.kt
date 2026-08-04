package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
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

/**
 * ViewModel：SettingsViewModel
 */
class SettingsViewModel(
    private val maintenanceRepository: AppMaintenanceRepository,
    private val archivedPersonRepository: DeleteOrRestorePersonRepository,
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseUiStateViewModel<SettingsUiState, SettingsViewEvent>(
    userSettingsRepository,
    SettingsUiState()
) {

    companion object {
        private const val FEATURE_NAME = "Settings"
        private const val OP_EXPORT = "exportData"
        private const val OP_IMPORT = "importData"
        private const val OP_CLEAR_ALL = "clearAllData"
        private const val OP_CLEAR_LOGS = "clearAuditLogs"
        private const val OP_ROTATE_LOGS = "rotateLogsManually"
        private const val OP_DELETE_ENDED = "deleteEndedPersons"
        private const val OP_INTEGRITY = "checkIntegrity"
        private const val OP_FIX_INCONSISTENCY = "fixInconsistencies"
        private const val OP_TEST_INCONSISTENCY = "insertTestInconsistency"
        private const val OP_IMPORT_SAMPLE = "importSampleData"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 初期化完了後に購読を開始
        scope.launch {
            startInitialSettingsSync()
        }
    }

    private fun startInitialSettingsSync() {
        safeCollect(
            operation = "initialSettingsSync",
            mode = CollectMode.INITIAL,
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
                    val archived = values[9] as List<Person>
                    currentState.copy(
                        isNameMaskingEnabled = values[0] as Boolean,
                        isBiometricEnabled = values[1] as Boolean,
                        lockTimeoutMinutes = values[2] as Int,
                        defaultRecorderName = values[3] as String,
                        isBackupPasswordEnabled = values[4] as Boolean,
                        backupPassword = values[5] as String,
                        themeSetting = values[6] as ThemeSetting,
                        auditLogRetentionDays = values[7] as Int,
                        auditLogCount = values[8] as Int,
                        endedUserCount = archived.size
                    )
                }
            }
        ) { nextState ->
            updateUiState { nextState }
        }
    }

    override fun copyWithLoadingState(state: SettingsUiState, isLoading: Boolean): SettingsUiState {
        return state.copy(isProcessing = isLoading)
    }

    fun setNameMaskingEnabled(enabled: Boolean) { viewModelScope.launch { userSettingsRepository.setNameMaskingEnabled(enabled) } }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            val biometricManager = BiometricManager.from(context)
            if (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                viewModelScope.launch { userSettingsRepository.setBiometricEnabled(true) }
            } else {
                showError("この端末では生体認証を利用できません。")
            }
        } else {
            viewModelScope.launch { userSettingsRepository.setBiometricEnabled(false) }
        }
    }

    fun setLockTimeoutMinutes(minutes: Int) { viewModelScope.launch { userSettingsRepository.setLockTimeoutMinutes(minutes) } }
    fun setDefaultRecorderName(name: String) { viewModelScope.launch { userSettingsRepository.setDefaultRecorderName(name) } }
    fun setBackupPasswordEnabled(enabled: Boolean) { viewModelScope.launch { userSettingsRepository.setBackupPasswordEnabled(enabled) } }
    fun setBackupPassword(password: String) { viewModelScope.launch { userSettingsRepository.setBackupPassword(password) } }
    fun setThemeSetting(setting: ThemeSetting) { viewModelScope.launch { userSettingsRepository.setThemeSetting(setting) } }

    private var versionTapCount: Int = 0
    fun handleVersionClick() {
        versionTapCount++
        if (SettingsLogic.shouldEnableDeveloperMode(versionTapCount)) updateUiState { it.copy(isDeveloperModeEnabled = true) }
    }

    fun setForceImportEnabled(enabled: Boolean) { updateUiState { it.copy(isForceImportEnabled = enabled) } }

    fun clearAuditLogs() { safeLaunch(OP_CLEAR_LOGS) { auditLogRepository.deleteAllLogs(); showSnackbar(R.string.settings_msg_audit_log_cleared) } }
    fun rotateLogsManually() { safeLaunch(OP_ROTATE_LOGS) { auditLogRepository.deleteOldLogs(currentState.auditLogRetentionDays); showSnackbar(R.string.settings_msg_rotate_success) } }
    fun deleteEndedPersons() { safeLaunch(OP_DELETE_ENDED) { archivedPersonRepository.deleteAllEndedPersons(featureName, OP_DELETE_ENDED); showSnackbar(R.string.settings_msg_delete_ended_success) } }

    fun exportData(context: Context, uri: Uri) {
        val password = if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null
        safeLaunch(OP_EXPORT, contextBuilder = { errorMessageRes = R.string.common_error_save }) {
            maintenanceRepository.exportData(context, uri, password) { updateUiState { s -> s.copy(processingProgress = it) } }
            sendViewEvent(SettingsViewEvent.ExportSuccess); showSnackbar(R.string.settings_msg_export_success)
        }
    }

    fun importData(context: Context, uri: Uri, inputPassword: String? = null) {
        val password = inputPassword ?: if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null
        safeLaunch(OP_IMPORT, contextBuilder = { errorMessageRes = R.string.common_error_save }) {
            try {
                maintenanceRepository.importData(context, uri, password, currentState.isForceImportEnabled) { updateUiState { s -> s.copy(processingProgress = it) } }
                sendViewEvent(SettingsViewEvent.ImportSuccess); showSnackbar(R.string.settings_msg_import_success)
            } catch (e: Exception) {
                if (e.message?.contains("password", ignoreCase = true) == true) sendViewEvent(SettingsViewEvent.RequestImportPassword)
                else throw e
            }
        }
    }

    fun clearAllData() { safeLaunch(OP_CLEAR_ALL) { maintenanceRepository.clearAllData(); showSnackbar(R.string.settings_msg_clear_all_success) } }
    fun importSampleData() { safeLaunch(OP_IMPORT_SAMPLE) { maintenanceRepository.replaceAllData(jp.mydns.fujiwara.carememo.logic.sample.SampleDataGenerator.generate()); showSnackbar(R.string.settings_msg_import_sample_success) } }
    fun checkIntegrity() { safeLaunch(OP_INTEGRITY) { val results = maintenanceRepository.scanInconsistencies(); updateUiState { it.copy(inconsistencies = results) }; if (results.isEmpty()) showSnackbar(R.string.settings_msg_integrity_ok) } }
    fun fixInconsistencies() { safeLaunch(OP_FIX_INCONSISTENCY) { maintenanceRepository.cleanInconsistencies(currentState.inconsistencies); val count = currentState.inconsistencies.size; updateUiState { it.copy(inconsistencies = emptyList()) }; showSnackbar(R.string.settings_msg_fix_success, count) } }
    fun clearInconsistencyResults() { updateUiState { it.copy(inconsistencies = emptyList()) } }
    fun insertTestInconsistency() { safeLaunch(OP_TEST_INCONSISTENCY) { maintenanceRepository.insertTestInconsistency(); showSnackbar(R.string.settings_msg_test_inconsistency_added) } }
    fun setAuditLogRetentionDays(days: Int) { viewModelScope.launch { userSettingsRepository.setAuditLogRetentionDays(days) } }
    fun canAuthenticate(context: Context): Boolean { val biometricManager = BiometricManager.from(context); return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }
    fun navigateToArchiveManagement(mode: DeleteOrRestorePersonViewModel.OperationMode) { sendViewEvent(SettingsViewEvent.NavigateToArchiveManagement(mode)) }
    fun navigateToAuditLog() { sendViewEvent(SettingsViewEvent.NavigateToAuditLog) }
    fun navigateToOrphanedPhotos() { sendViewEvent(SettingsViewEvent.NavigateToOrphanedPhotos) }
    fun navigateBack() { sendViewEvent(SettingsViewEvent.NavigateBack) }

    class Factory(
        private val maintenanceRepository: AppMaintenanceRepository,
        private val archivedPersonRepository: DeleteOrRestorePersonRepository,
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return SettingsViewModel(maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository, savedStateHandle) as T
        }
    }
}
