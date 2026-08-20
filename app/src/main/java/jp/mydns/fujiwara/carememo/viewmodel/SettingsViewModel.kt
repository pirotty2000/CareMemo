package jp.mydns.fujiwara.carememo.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.logic.common.PersonLogic
import jp.mydns.fujiwara.carememo.logic.feature.ImportValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.SettingsLogic
import jp.mydns.fujiwara.carememo.logic.feature.SettingsUiState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * ViewModel：SettingsViewModel
 *
 * 【役割】
 * アプリケーションの設定管理、バックアップ・復元、およびシステムメンテナンス操作の実行制御を担当します。
 *
 * 【設計指針：レイヤー責務と課題】
 * 1. 統括制御：複数のリポジトリを横断して、アプリ全体の動作設定や整合性修復などの「副作用を伴う操作」を安全に実行します。
 * 2. 疎結合：リポジトリを介して設定やメンテナンス操作を行い、プラットフォーム固有の機能（生体認証等）は UI 層からのフラグ制御により抽象化されています。
 *
 * 【この ViewModel では行わないこと】
 * ・ZIP ファイルの物理的な圧縮・解凍処理（AppMaintenanceRepository または Utils が担当）。
 * ・データのインポート/エクスポートにおける具体的な整合性検証（SettingsLogic が担当）。
 */
class SettingsViewModel(
    private val maintenanceRepository: AppMaintenanceRepository,
    private val archivedPersonRepository: DeleteOrRestorePersonRepository,
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession
) : BaseUiStateViewModel<SettingsUiState, SettingsViewEvent>(
    userSettingsRepository,
    securitySession,
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

    /** 重い処理や破壊的操作用の Job */
    private var actionJob: Job? = null

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
                    userSettingsRepository.defaultRecorderName,
                    userSettingsRepository.isBackupPasswordEnabled,
                    userSettingsRepository.backupPassword,
                    userSettingsRepository.themeSetting,
                    userSettingsRepository.auditLogRetentionDays,
                    auditLogRepository.getAuditLogCountFlow(),
                    archivedPersonRepository.getArchivedPersons()
                ) { values ->
                    @Suppress("UNCHECKED_CAST")
                    val archived = values[8] as List<Person>
                    currentState.copy(
                        isNameMaskingEnabled = values[0] as Boolean,
                        isBiometricEnabled = values[1] as Boolean,
                        defaultRecorderName = values[2] as String,
                        isBackupPasswordEnabled = values[3] as Boolean,
                        backupPassword = values[4] as String,
                        themeSetting = values[5] as ThemeSetting,
                        auditLogRetentionDays = values[6] as Int,
                        auditLogCount = values[7] as Int,
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

    fun setBiometricEnabled(isSupported: Boolean, enabled: Boolean) {
        if (enabled) {
            if (isSupported) {
                viewModelScope.launch { userSettingsRepository.setBiometricEnabled(true) }
            } else {
                showError("この端末では生体認証を利用できません。")
            }
        } else {
            viewModelScope.launch { userSettingsRepository.setBiometricEnabled(false) }
        }
    }

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

    fun clearAuditLogs() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_CLEAR_LOGS) { auditLogRepository.deleteAllLogs(); showSnackbar(R.string.settings_msg_audit_log_cleared) }
    }

    fun rotateLogsManually() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_ROTATE_LOGS) { auditLogRepository.deleteOldLogs(currentState.auditLogRetentionDays); showSnackbar(R.string.settings_msg_rotate_success) }
    }

    fun deleteEndedPersons() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_DELETE_ENDED) { archivedPersonRepository.deleteAllEndedPersons(featureName, OP_DELETE_ENDED); showSnackbar(R.string.settings_msg_delete_ended_success) }
    }

    fun exportData(uri: Uri) {
        if (actionJob?.isActive == true) return
        val password = if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null
        actionJob = safeLaunch(OP_EXPORT, contextBuilder = { errorMessageRes = R.string.common_error_save }) {
            maintenanceRepository.exportData(uri, password) { updateUiState { s -> s.copy(processingProgress = it) } }
            sendViewEvent(SettingsViewEvent.ExportSuccess); showSnackbar(R.string.settings_msg_export_success)
        }
    }

    fun importData(uri: Uri, identifierSuffix: String, inputPassword: String? = null) {
        if (actionJob?.isActive == true) return
        val password = inputPassword ?: if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null
        actionJob = safeLaunch(OP_IMPORT, contextBuilder = { errorMessageRes = R.string.common_error_save }) {
            try {
                maintenanceRepository.importData(
                    uri = uri,
                    password = password,
                    onProgress = { updateUiState { s -> s.copy(processingProgress = it) } }
                ) { backup ->
                    // 1. バージョンチェック (SettingsLogic)
                    val versionResult = SettingsLogic.validateVersion(
                        backupVersionCode = backup.appVersionCode,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        isDeveloperMode = currentState.isForceImportEnabled
                    )
                    
                    if (versionResult == ImportValidationResult.INCOMPATIBLE) {
                        // 本来は文字列リソースを返したいが、ViewModel は Context 非依存のため例外を投げる。
                        // エラーメッセージの構築は Repository 側で従来行っていたものを踏襲。
                        throw IOException("BACKUP_NEWER_THAN_APP") 
                    }

                    // 2. データのクレンジングとフィルタリング
                    val validMedication = MedicationLogic.filterValidRecords(
                        backup.medicationRecords.map { it.toEntity() }
                    ).map { it.toBackupDto() }
                    
                    val cleansedPersons = PersonLogic.cleansePersonData(
                        persons = backup.persons.map { it.toEntity() },
                        identifierSuffixGenerator = { id -> identifierSuffix.format(id) }
                    ).map { it.toBackupDto() }

                    backup.copy(
                        persons = cleansedPersons,
                        medicationRecords = validMedication
                    )
                }
                sendViewEvent(SettingsViewEvent.ImportSuccess); showSnackbar(R.string.settings_msg_import_success)
            } catch (e: Exception) {
                if (e.message?.contains("password", ignoreCase = true) == true) {
                    sendViewEvent(SettingsViewEvent.RequestImportPassword)
                } else if (e.message == "BACKUP_NEWER_THAN_APP") {
                    showError(R.string.common_error_title_save, R.string.maintenance_err_newer_version, 0, BuildConfig.VERSION_CODE)
                } else {
                    throw e
                }
            }
        }
    }

    fun clearAllData() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_CLEAR_ALL) { maintenanceRepository.clearAllData(); showSnackbar(R.string.settings_msg_clear_all_success) }
    }

    fun importSampleData() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_IMPORT_SAMPLE) { maintenanceRepository.replaceAllData(jp.mydns.fujiwara.carememo.logic.sample.SampleDataGenerator.generate()); showSnackbar(R.string.settings_msg_import_sample_success) }
    }

    fun checkIntegrity() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_INTEGRITY) { val results = maintenanceRepository.scanInconsistencies(); updateUiState { it.copy(inconsistencies = results.toImmutableList()) }; if (results.isEmpty()) showSnackbar(R.string.settings_msg_integrity_ok) }
    }

    fun fixInconsistencies() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_FIX_INCONSISTENCY) { maintenanceRepository.cleanInconsistencies(currentState.inconsistencies); val count = currentState.inconsistencies.size; updateUiState { it.copy(inconsistencies = persistentListOf()) }; showSnackbar(R.string.settings_msg_fix_success, count) }
    }

    fun clearInconsistencyResults() { updateUiState { it.copy(inconsistencies = persistentListOf()) } }

    fun insertTestInconsistency() {
        if (actionJob?.isActive == true) return
        actionJob = safeLaunch(OP_TEST_INCONSISTENCY) { maintenanceRepository.insertTestInconsistency(); showSnackbar(R.string.settings_msg_test_inconsistency_added) }
    }
    fun setAuditLogRetentionDays(days: Int) { viewModelScope.launch { userSettingsRepository.setAuditLogRetentionDays(days) } }
    fun navigateToArchiveManagement(mode: DeleteOrRestorePersonViewModel.OperationMode) { sendViewEvent(SettingsViewEvent.NavigateToArchiveManagement(mode)) }
    fun navigateToAuditLog() { sendViewEvent(SettingsViewEvent.NavigateToAuditLog) }
    fun navigateToUnassignedPhotos() { sendViewEvent(SettingsViewEvent.NavigateToUnassignedPhotos) }
    fun navigateBack() { sendViewEvent(SettingsViewEvent.NavigateBack) }

    class Factory(
        private val maintenanceRepository: AppMaintenanceRepository,
        private val archivedPersonRepository: DeleteOrRestorePersonRepository,
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return SettingsViewModel(
                maintenanceRepository,
                archivedPersonRepository,
                auditLogRepository,
                userSettingsRepository,
                securitySession
            ) as T
        }
    }
}
