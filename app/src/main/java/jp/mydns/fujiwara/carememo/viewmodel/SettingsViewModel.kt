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

/**
 * ViewModel：SettingsViewModel
 *
 * 【役割】
 * アプリ全体のシステム設定、データメンテナンス、バックアップ/リストア、および開発者向け機能を管理します。
 * セキュリティ設定（生体認証）、表示設定（テーマ、氏名表示）、データ整合性チェック、
 * 操作ログの管理といった、アプリの根幹に関わる制御を一手に引き受けます。
 *
 * 【主要な機能】
 * ・ユーザー設定（マスキング、生体認証、タイムアウト、デフォルト記録者、テーマ等）の永続化と同期。
 * ・データの外部エクスポートおよびインポート（暗号化バックアップ対応）。
 * ・操作ログ（監査ログ）の削除および手動ローテーション。
 * ・アーカイブ済み（削除済み）利用者の物理削除。
 * ・データベースの整合性スキャンおよび不整合データの修正。
 * ・開発者モードの制御とサンプルデータのインポート。
 *
 * 【依存している Repository】
 * ・AppMaintenanceRepository: データのインポート/エクスポート、整合性チェック、データ全削除。
 * ・DeleteOrRestorePersonRepository: アーカイブ済み利用者データの物理削除。
 * ・AuditLogRepository: 操作ログの購読、削除、ローテーション。
 * ・UserSettingsRepository: 各種ユーザー設定の永続化と購読。
 *
 * 【設計指針】
 * 1. 同期性の確保：`combine` を使用して多数の設定値を単一の UiState に集約し、設定変更が即座に UI へ反映されるようにする。
 * 2. 安全なメンテナンス：エクスポートやインポート、データ修正といった破壊的な操作は `safeLaunch` 下で実行し、
 *    進捗状況のフィードバックと例外時の安全なロールバック（または通知）を行う。
 * 3. セキュリティ：生体認証の有効化可否の判定や、バックアップパスワードのハンドリングを適切に行う。
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
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "Settings"
        /** 監査ログ用：データエクスポート操作名 */
        private const val OP_EXPORT = "exportData"
        /** 監査ログ用：データインポート操作名 */
        private const val OP_IMPORT = "importData"
        /** 監査ログ用：データ全削除操作名 */
        private const val OP_CLEAR_ALL = "clearAllData"
        /** 監査ログ用：ログ削除操作名 */
        private const val OP_CLEAR_LOGS = "clearAuditLogs"
        /** 監査ログ用：ログ手動ローテーション操作名 */
        private const val OP_ROTATE_LOGS = "rotateLogsManually"
        /** 監査ログ用：アーカイブ利用者物理削除操作名 */
        private const val OP_DELETE_ENDED = "deleteEndedPersons"
        /** 監査ログ用：整合性チェック操作名 */
        private const val OP_INTEGRITY = "checkIntegrity"
        /** 監査ログ用：不整合修正操作名 */
        private const val OP_FIX_INCONSISTENCY = "fixInconsistencies"
        /** 監査ログ用：テスト不整合データ挿入操作名 */
        private const val OP_TEST_INCONSISTENCY = "insertTestInconsistency"
        /** 監査ログ用：サンプルデータインポート操作名 */
        private const val OP_IMPORT_SAMPLE = "importSampleData"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定、統計情報、およびアーカイブ済み利用者リストを統合して購読
        // いずれかの設定値が変更された場合、原子的に UiState を更新する
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
                    val masking = values[0] as Boolean
                    val biometric = values[1] as Boolean
                    val timeout = values[2] as Int
                    val recorder = values[3] as String
                    val backupEnabled = values[4] as Boolean
                    val password = values[5] as String
                    val theme = values[6] as ThemeSetting
                    val retention = values[7] as Int
                    val count = values[8] as Int
                    @Suppress("UNCHECKED_CAST")
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

    override fun copyWithLoadingState(state: SettingsUiState, isLoading: Boolean): SettingsUiState {
        // 処理中フラグの更新
        return state.copy(isProcessing = isLoading)
    }

    /** 氏名マスキング設定を更新します。 */
    fun setNameMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.setNameMaskingEnabled(enabled)
        }
    }

    /**
     * 生体認証の有効/無効を設定します。
     * 有効化する場合、端末側が認証可能状態（指紋登録済み等）であるかを事前にチェックします。
     */
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                viewModelScope.launch { userSettingsRepository.setBiometricEnabled(true) }
            } else {
                showError("この端末では生体認証を利用できません。")
            }
        } else {
            viewModelScope.launch { userSettingsRepository.setBiometricEnabled(false) }
        }
    }

    /** アプリロックのタイムアウト時間（分）を設定します。 */
    fun setLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch { userSettingsRepository.setLockTimeoutMinutes(minutes) }
    }

    /** 新規記録時のデフォルト記録者名を更新します。 */
    fun setDefaultRecorderName(name: String) {
        viewModelScope.launch { userSettingsRepository.setDefaultRecorderName(name) }
    }

    /** バックアップファイルの暗号化パスワード使用の有無を設定します。 */
    fun setBackupPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch { userSettingsRepository.setBackupPasswordEnabled(enabled) }
    }

    /** バックアップファイルの暗号化パスワードを設定します。 */
    fun setBackupPassword(password: String) {
        viewModelScope.launch { userSettingsRepository.setBackupPassword(password) }
    }

    /** アプリのテーマ設定（ライト/ダーク/システム）を更新します。 */
    fun setThemeSetting(setting: ThemeSetting) {
        viewModelScope.launch { userSettingsRepository.setThemeSetting(setting) }
    }

    /** バージョン情報のクリック回数をカウントし、一定回数に達すると開発者モードを有効化します。 */
    private var versionTapCount: Int = 0
    fun handleVersionClick() {
        versionTapCount++
        if (SettingsLogic.shouldEnableDeveloperMode(versionTapCount)) {
            updateUiState { it.copy(isDeveloperModeEnabled = true) }
        }
    }

    /** インポート時の強制上書きモードを有効にするかどうかを設定します（開発者向け）。 */
    fun setForceImportEnabled(enabled: Boolean) {
        updateUiState { it.copy(isForceImportEnabled = enabled) }
    }

    /** 全ての操作ログ（監査ログ）を物理削除します。 */
    fun clearAuditLogs() {
        safeLaunch(operation = OP_CLEAR_LOGS, loadingState = loadingStateProxy) {
            auditLogRepository.deleteAllLogs()
            showSnackbar(R.string.settings_msg_audit_log_cleared)
        }
    }

    /** 設定された保持期間を超えた古い操作ログを削除します。 */
    fun rotateLogsManually() {
        safeLaunch(operation = OP_ROTATE_LOGS, loadingState = loadingStateProxy) {
            auditLogRepository.deleteOldLogs(currentState.auditLogRetentionDays)
            showSnackbar(R.string.settings_msg_rotate_success)
        }
    }

    /** アーカイブ済み（論理削除状態）の利用者をデータベースから物理削除します。 */
    fun deleteEndedPersons() {
        safeLaunch(operation = OP_DELETE_ENDED, loadingState = loadingStateProxy) {
            archivedPersonRepository.deleteAllEndedPersons(featureName, OP_DELETE_ENDED)
            showSnackbar(R.string.settings_msg_delete_ended_success)
        }
    }

    /**
     * 現在の全てのデータをバックアップファイルとしてエクスポートします。
     *
     * @param context コンテキスト
     * @param uri 保存先の URI
     */
    fun exportData(context: Context, uri: Uri) {
        val password = if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null

        safeLaunch(
            operation = OP_EXPORT,
            loadingState = loadingStateProxy,
            contextBuilder = { errorMessageRes = R.string.common_error_save }
        ) {
            maintenanceRepository.exportData(context, uri, password) { progress ->
                // エクスポートの進捗状況を UI へフィードバック
                updateUiState { it.copy(processingProgress = progress) }
            }
            sendViewEvent(SettingsViewEvent.ExportSuccess)
            showSnackbar(R.string.settings_msg_export_success)
        }
    }

    /**
     * 指定されたバックアップファイルからデータをインポート（リストア）します。
     *
     * @param context コンテキスト
     * @param uri インポート元ファイルの URI
     * @param inputPassword パスワード（必要な場合）
     */
    fun importData(context: Context, uri: Uri, inputPassword: String? = null) {
        // パスワードの決定ロジック：
        // 1. 引数で直接渡された場合（パスワード再入力ダイアログからの再実行時など）を優先
        // 2. 引数がない場合は、現在の設定に保存されているパスワードを使用
        val password = inputPassword ?: if (currentState.isBackupPasswordEnabled) currentState.backupPassword else null

        safeLaunch(
            operation = OP_IMPORT,
            loadingState = loadingStateProxy,
            contextBuilder = { errorMessageRes = R.string.common_error_save }
        ) {
            try {
                maintenanceRepository.importData(
                    context = context,
                    uri = uri,
                    password = password,
                    isDeveloperMode = currentState.isForceImportEnabled
                ) { progress ->
                    // インポートの進捗状況を UI へフィードバック
                    updateUiState { it.copy(processingProgress = progress) }
                }
                sendViewEvent(SettingsViewEvent.ImportSuccess)
                showSnackbar(R.string.settings_msg_import_success)
            } catch (e: Exception) {
                // パスワードが間違っている場合、再入力ダイアログの表示イベントを送出
                if (e.message?.contains("password", ignoreCase = true) == true) {
                    sendViewEvent(SettingsViewEvent.RequestImportPassword)
                } else {
                    throw e // それ以外の致命的なエラーは標準のハンドラへ委譲
                }
            }
        }
    }

    /** アプリ内の全てのデータ（設定以外）を完全に消去します。 */
    fun clearAllData() {
        safeLaunch(operation = OP_CLEAR_ALL, loadingState = loadingStateProxy) {
            maintenanceRepository.clearAllData()
            showSnackbar(R.string.settings_msg_clear_all_success)
        }
    }

    /** デモや検証用のサンプルデータを一括投入します。 */
    fun importSampleData() {
        safeLaunch(operation = OP_IMPORT_SAMPLE, loadingState = loadingStateProxy) {
            val sampleData = jp.mydns.fujiwara.carememo.logic.sample.SampleDataGenerator.generate()
            maintenanceRepository.replaceAllData(sampleData)
            showSnackbar(R.string.settings_msg_import_sample_success)
        }
    }

    /** データベースの参照整合性チェックを実行し、不整合（浮いたデータ等）を特定します。 */
    fun checkIntegrity() {
        safeLaunch(operation = OP_INTEGRITY, loadingState = loadingStateProxy) {
            val results = maintenanceRepository.scanInconsistencies()
            updateUiState { it.copy(inconsistencies = results) }
            if (results.isEmpty()) {
                showSnackbar(R.string.settings_msg_integrity_ok)
            }
        }
    }

    /** 特定された不整合データを自動修正（削除等）します。 */
    fun fixInconsistencies() {
        safeLaunch(operation = OP_FIX_INCONSISTENCY, loadingState = loadingStateProxy) {
            maintenanceRepository.cleanInconsistencies(currentState.inconsistencies)
            val count = currentState.inconsistencies.size
            updateUiState { it.copy(inconsistencies = emptyList()) }
            showSnackbar(R.string.settings_msg_fix_success, count)
        }
    }

    /** 整合性チェックの結果表示をクリアします。 */
    fun clearInconsistencyResults() {
        updateUiState { it.copy(inconsistencies = emptyList()) }
    }

    /** デバッグ用に意図的な不整合データを挿入します（開発者向け）。 */
    fun insertTestInconsistency() {
        safeLaunch(operation = OP_TEST_INCONSISTENCY) {
            maintenanceRepository.insertTestInconsistency()
            showSnackbar(R.string.settings_msg_test_inconsistency_added)
        }
    }

    /** 操作ログの保持期間（日数）を設定します。 */
    fun setAuditLogRetentionDays(days: Int) {
        viewModelScope.launch { userSettingsRepository.setAuditLogRetentionDays(days) }
    }

    /** 端末が生体認証を実行可能な状態かどうかを判定します。 */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * SettingsViewModel を生成するための Factory クラス。
     */
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
