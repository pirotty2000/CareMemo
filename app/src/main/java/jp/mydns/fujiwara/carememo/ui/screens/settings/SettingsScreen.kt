package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.mapping.MaintenanceDisplayMapper
import jp.mydns.fujiwara.carememo.ui.mapping.ThemeDisplayMapper
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.logic.feature.SettingsUiState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * UI Action：設定画面におけるユーザー操作の集約定義
 */
sealed interface SettingsUiAction {
    // 表示・記録設定
    data class MaskingChanged(val enabled: Boolean) : SettingsUiAction
    data class RecorderNameChanged(val name: String) : SettingsUiAction

    // 利用者管理
    data object NavigateToRestore : SettingsUiAction
    data object EraseArchivedClick : SettingsUiAction
    data object ConfirmEraseArchived : SettingsUiAction

    // データ管理
    data class BackupPasswordEnabledChanged(val enabled: Boolean) : SettingsUiAction
    data class BackupPasswordChanged(val password: String) : SettingsUiAction
    data object PasswordVisibilityToggle : SettingsUiAction
    data object ExportClick : SettingsUiAction
    data object ExportLogsClick : SettingsUiAction
    data object ImportClick : SettingsUiAction
    data object UnassignedPhotosClick : SettingsUiAction
    data class PasswordInputForImportChanged(val password: String) : SettingsUiAction
    data object ConfirmImportWithPassword : SettingsUiAction

    // セキュリティ
    data class BiometricEnabledChanged(val enabled: Boolean) : SettingsUiAction

    // テーマ設定
    data object ThemeClick : SettingsUiAction
    data class ThemeSelected(val setting: ThemeSetting) : SettingsUiAction

    // バージョン・開発者ツール
    data object VersionClick : SettingsUiAction
    data object ClearAllClick : SettingsUiAction
    data object ConfirmClearAll : SettingsUiAction
    data object CheckIntegrity : SettingsUiAction
    data object InsertTestInconsistency : SettingsUiAction
    data object RetentionClick : SettingsUiAction
    data class RetentionSelected(val days: Int) : SettingsUiAction
    data object ViewLogsClick : SettingsUiAction
    data object RotateLogsClick : SettingsUiAction
    data object ClearLogsClick : SettingsUiAction
    data object ConfirmClearLogs : SettingsUiAction
    data object ImportSampleDataClick : SettingsUiAction
    data object ConfirmImportSampleData : SettingsUiAction
    data class ForceImportEnabledChanged(val enabled: Boolean) : SettingsUiAction

    // 不整合修正
    data object FixInconsistencies : SettingsUiAction
    data object ClearInconsistencyResults : SettingsUiAction

    // ナビゲーション・共通
    data object Back : SettingsUiAction
    data object DismissDialog : SettingsUiAction
    data object ConfirmImportRestore : SettingsUiAction
}

/**
 * Screen：SettingsScreen
 *
 * 【役割】
 * アプリケーションの設定・管理機能を統括する最上位 Screen コンポーネントです。
 * 表示設定、セキュリティ、データ管理、および開発者向けツールに至るまで、アプリの振る舞いをカスタマイズする全設定項目を集約します。
 *
 * 【全体像：設定画面階層（Settings Hierarchy）】
 *
 * ■ SettingsScreen (★本コンポーネント：全体制御・ダイアログ管理)
 * │
 * └─ [1] SettingsScreenContent (表示層：各セクションの配置)
 *      ├─ DisplayAndRecordingSection (表示・記録設定)
 *      ├─ UserManagementSection (利用者管理) ➔ [ 子画面 ] DeleteOrRestorePerson
 *      ├─ DataManagementSection (データ管理) ➔ [ 子画面 ] UnassignedPhotoManagement
 *      ├─ SecuritySection (セキュリティ)
 *      ├─ ThemeSection (テーマ設定)
 *      ├─ OtherSection (その他) ➔ バージョン情報
 *      └─ ResetSection (開発者用：条件付き表示) ➔ [ 子画面 ] AuditLogScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavHostController,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit,
    onCheckBiometricSupport: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 設定変更があったかどうかの内部状態
    var isChangedByMe by rememberSaveable { mutableStateOf(false) }

    // 子画面（利用者管理 S-003）からの更新要求を監視
    val childRefreshRequested by remember(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("refresh_needed", false)
            ?: MutableStateFlow(false)
    }.collectAsStateWithLifecycle()

    // 戻る際の処理（親画面への通知準備）
    val performBack = {
        if (isChangedByMe || childRefreshRequested) {
            navController.previousBackStackEntry?.savedStateHandle?.set("refresh_needed", true)
        }
        navController.popBackStack()
    }

    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = uiState.backupPassword.length >= AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH

    var showImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showDevClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }
    var showLogClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showImportSampleConfirm by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }
    val identifierSuffix = stringResource(R.string.common_identifier_suffix)

    var dialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        launch {
            viewModel.uiEventFlow.collect { event ->
                when (event) {
                    is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                        dialogTitle = context.getString(R.string.common_error_title_info)
                        dialogMessage = context.getString(event.resId, *event.args.toTypedArray())
                    }
                    is BaseUiStateViewModel.UiEvent.ShowInfoDialog -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                    }
                    is BaseUiStateViewModel.UiEvent.ShowInfoDialogRes -> {
                        dialogTitle = context.getString(event.titleResId)
                        dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                    }
                    is BaseUiStateViewModel.UiEvent.ShowErrorDialog -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                    }
                    is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes -> {
                        dialogTitle = context.getString(event.titleResId)
                        dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                    }
                    is BaseUiStateViewModel.UiEvent.SaveSuccess -> {
                        performBack()
                    }
                    is BaseUiStateViewModel.UiEvent.ShowSnackbar -> {
                        snackbarHostState.showSnackbar(event.message)
                    }
                    is BaseUiStateViewModel.UiEvent.ShowOverwriteConfirm -> {}
                }
            }
        }
        launch {
            viewModel.viewEvent.collect { event ->
                when (event) {
                    SettingsViewEvent.RequestImportPassword -> showPasswordInputDialog = true
                    SettingsViewEvent.ExportSuccess -> {}
                    SettingsViewEvent.ImportSuccess -> {}
                    is SettingsViewEvent.NavigateToArchiveManagement -> {
                        navController.navigate(Destination.ArchiveManagement(event.mode.name))
                    }
                    SettingsViewEvent.NavigateToAuditLog -> {
                        navController.navigate(Destination.AuditLog)
                    }
                    SettingsViewEvent.NavigateToUnassignedPhotos -> {
                        navController.navigate(Destination.UnassignedPhotos)
                    }
                    SettingsViewEvent.NavigateBack -> {
                        performBack()
                    }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.exportData(it) } }
    val exportLogsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.exportAuditLogs(it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { showImportUri = it } }

    val handleAction: (SettingsUiAction) -> Unit = remember(viewModel, navController, onCheckBiometricSupport, onRequireAuthentication) {
        { action ->
            when (action) {
                is SettingsUiAction.MaskingChanged -> {
                    viewModel.setNameMaskingEnabled(action.enabled)
                    isChangedByMe = true
                }
                is SettingsUiAction.RecorderNameChanged -> {
                    viewModel.setDefaultRecorderName(action.name)
                    isChangedByMe = true
                }
                SettingsUiAction.NavigateToRestore -> {
                    viewModel.navigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
                }
                SettingsUiAction.EraseArchivedClick -> {
                    showEraseConfirm = true
                }
                SettingsUiAction.ConfirmEraseArchived -> {
                    viewModel.deleteEndedPersons()
                    showEraseConfirm = false
                }
                is SettingsUiAction.BackupPasswordEnabledChanged -> {
                    if (action.enabled) {
                        viewModel.setBackupPasswordEnabled(enabled = true)
                        isPasswordVisible = false
                    } else {
                        if (onCheckBiometricSupport()) {
                            onRequireAuthentication(R.string.security_auth_title, R.string.security_auth_reason_change_settings) {
                                viewModel.setBackupPasswordEnabled(enabled = false)
                            }
                        } else {
                            viewModel.setBackupPasswordEnabled(enabled = false)
                        }
                    }
                    isChangedByMe = true
                }
                is SettingsUiAction.BackupPasswordChanged -> {
                    if (action.password.length >= AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH || action.password.isEmpty()) {
                        viewModel.setBackupPassword(action.password)
                    }
                    isChangedByMe = true
                }
                SettingsUiAction.PasswordVisibilityToggle -> {
                    if (isPasswordVisible) {
                        isPasswordVisible = false
                    } else {
                        if (onCheckBiometricSupport()) {
                            onRequireAuthentication(R.string.security_auth_title, R.string.security_auth_reason_show_password) {
                                isPasswordVisible = true
                            }
                        } else {
                            isPasswordVisible = true
                        }
                    }
                }
                SettingsUiAction.ExportClick -> {
                    viewModel.setLockBypassEnabled(enabled = true)
                    exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip")
                }
                SettingsUiAction.ExportLogsClick -> {
                    viewModel.setLockBypassEnabled(enabled = true)
                    exportLogsLauncher.launch("carememo_audit_logs_${System.currentTimeMillis()}.zip")
                }
                SettingsUiAction.ImportClick -> {
                    viewModel.setLockBypassEnabled(enabled = true)
                    importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                }
                SettingsUiAction.UnassignedPhotosClick -> {
                    viewModel.navigateToUnassignedPhotos()
                }
                is SettingsUiAction.PasswordInputForImportChanged -> {
                    inputPasswordForImport = action.password
                }
                SettingsUiAction.ConfirmImportWithPassword -> {
                    pendingImportUri?.let { viewModel.importData(it, identifierSuffix, inputPasswordForImport) }
                    showPasswordInputDialog = false
                    inputPasswordForImport = ""
                }
                is SettingsUiAction.BiometricEnabledChanged -> {
                    if (action.enabled) {
                        viewModel.setBiometricEnabled(isSupported = onCheckBiometricSupport(), enabled = true)
                    } else {
                        if (onCheckBiometricSupport()) {
                            onRequireAuthentication(R.string.security_auth_title, R.string.security_auth_reason_change_settings) {
                                viewModel.setBiometricEnabled(isSupported = true, enabled = false)
                            }
                        } else {
                            viewModel.setBiometricEnabled(isSupported = false, enabled = false)
                        }
                    }
                    isChangedByMe = true
                }
                SettingsUiAction.ThemeClick -> showThemeDialog = true
                is SettingsUiAction.ThemeSelected -> {
                    viewModel.setThemeSetting(action.setting)
                    showThemeDialog = false
                }
                SettingsUiAction.VersionClick -> {
                    viewModel.handleVersionClick()
                    showVersionDialog = true
                }
                SettingsUiAction.ClearAllClick -> {
                    if (onCheckBiometricSupport()) {
                        onRequireAuthentication(R.string.security_auth_title, R.string.security_auth_reason_change_settings) {
                            showDevClearConfirm = true
                        }
                    } else {
                        showDevClearConfirm = true
                    }
                }
                SettingsUiAction.ConfirmClearAll -> {
                    viewModel.clearAllData()
                    showDevClearConfirm = false
                }
                SettingsUiAction.CheckIntegrity -> viewModel.checkIntegrity()
                SettingsUiAction.InsertTestInconsistency -> viewModel.insertTestInconsistency()
                SettingsUiAction.RetentionClick -> showRetentionDialog = true
                is SettingsUiAction.RetentionSelected -> {
                    viewModel.setAuditLogRetentionDays(action.days)
                    showRetentionDialog = false
                }
                SettingsUiAction.ViewLogsClick -> viewModel.navigateToAuditLog()
                SettingsUiAction.RotateLogsClick -> viewModel.rotateLogsManually()
                SettingsUiAction.ClearLogsClick -> showLogClearConfirm = true
                SettingsUiAction.ConfirmClearLogs -> {
                    viewModel.clearAuditLogs()
                    showLogClearConfirm = false
                }
                SettingsUiAction.ImportSampleDataClick -> {
                    if (onCheckBiometricSupport()) {
                        onRequireAuthentication(R.string.security_auth_title, R.string.security_auth_reason_change_settings) {
                            showImportSampleConfirm = true
                        }
                    } else {
                        showImportSampleConfirm = true
                    }
                }
                SettingsUiAction.ConfirmImportSampleData -> {
                    viewModel.importSampleData()
                    showImportSampleConfirm = false
                }
                is SettingsUiAction.ForceImportEnabledChanged -> {
                    viewModel.setForceImportEnabled(action.enabled)
                }
                SettingsUiAction.FixInconsistencies -> viewModel.fixInconsistencies()
                SettingsUiAction.ClearInconsistencyResults -> viewModel.clearInconsistencyResults()
                SettingsUiAction.Back -> performBack()
                SettingsUiAction.DismissDialog -> {
                    showImportUri = null
                    showEraseConfirm = false
                    showDevClearConfirm = false
                    showVersionDialog = false
                    showThemeDialog = false
                    showRetentionDialog = false
                    showLogClearConfirm = false
                    showImportSampleConfirm = false
                    showPasswordInputDialog = false
                    inputPasswordForImport = ""
                }
                SettingsUiAction.ConfirmImportRestore -> {
                    pendingImportUri = showImportUri
                    viewModel.importData(showImportUri!!, identifierSuffix)
                    showImportUri = null
                }
            }
        }
    }

    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            },
            confirmButtonText = stringResource(R.string.common_close),
        )
    }

    if (showImportUri != null) {
        AppDialog(
            onDismissRequest = { handleAction(SettingsUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.settings_dialog_restore_confirm_title)) },
            text = { AppDialogContent(text = stringResource(R.string.settings_dialog_restore_confirm_msg)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.settings_dialog_restore_confirm_btn),
                    onClick = { handleAction(SettingsUiAction.ConfirmImportRestore) }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { handleAction(SettingsUiAction.DismissDialog) }
                )
            }
        )
    }

    if (showEraseConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { handleAction(SettingsUiAction.DismissDialog) },
            onDelete = { handleAction(SettingsUiAction.ConfirmEraseArchived) },
            title = stringResource(R.string.settings_dialog_permanent_delete_confirm_title),
            message = stringResource(R.string.settings_dialog_permanent_delete_confirm_msg, uiState.endedUserCount),
            confirmButtonText = stringResource(R.string.settings_dialog_permanent_delete_confirm_btn, uiState.endedUserCount)
        )
    }

    if (showDevClearConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { handleAction(SettingsUiAction.DismissDialog) },
            onDelete = { handleAction(SettingsUiAction.ConfirmClearAll) },
            title = stringResource(R.string.settings_dialog_clear_all_confirm_title),
            message = stringResource(R.string.settings_dialog_clear_all_confirm_msg),
            confirmButtonText = stringResource(R.string.decision)
        )
    }

    if (showLogClearConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { handleAction(SettingsUiAction.DismissDialog) },
            onDelete = { handleAction(SettingsUiAction.ConfirmClearLogs) },
            title = context.getString(R.string.audit_log_clear_confirm_title),
            message = context.getString(R.string.audit_log_clear_confirm_msg),
            confirmButtonText = context.getString(R.string.common_delete)
        )
    }

    if (showImportSampleConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { handleAction(SettingsUiAction.DismissDialog) },
            onDelete = { handleAction(SettingsUiAction.ConfirmImportSampleData) },
            title = context.getString(R.string.settings_import_sample_confirm_title),
            message = context.getString(R.string.settings_import_sample_confirm_msg),
            confirmButtonText = context.getString(R.string.settings_btn_import_sample_data)
        )
    }

    // データベース不整合レポート・ダイアログ
    if (uiState.inconsistencies.isNotEmpty()) {
        AppDialog(
            onDismissRequest = { handleAction(SettingsUiAction.ClearInconsistencyResults) },
            title = { Text(stringResource(R.string.settings_dialog_inconsistency_report_title)) },
            text = {
                AppDialogContent {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.settings_dialog_inconsistency_report_intro, uiState.inconsistencies.size),
                            style = MaterialTheme.typography.bodySmall
                        )

                        uiState.inconsistencies.forEach { inc ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = stringResource(MaintenanceDisplayMapper.getDescriptionResId(inc.type)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(text = buildString { append(stringResource(R.string.settings_dialog_inconsistency_person_id, inc.personId ?: stringResource(R.string.audit_result_unknown))); append(" | "); append(stringResource(R.string.settings_dialog_inconsistency_table, inc.tableName)) }, style = MaterialTheme.typography.labelSmall)
                                    inc.recordTime?.let { time -> Text(text = stringResource(R.string.settings_dialog_inconsistency_record_time, DateTimeUtils.formatRecordTime(time)), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        Text(stringResource(R.string.settings_dialog_inconsistency_summary), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.settings_dialog_inconsistency_cleanup_btn),
                    onClick = { handleAction(SettingsUiAction.FixInconsistencies) }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_close),
                    onClick = { handleAction(SettingsUiAction.ClearInconsistencyResults) }
                )
            }
        )
    }

    if (showVersionDialog) {
        AppInfoDialog(
            title = stringResource(R.string.main_dialog_version_title),
            message = stringResource(R.string.settings_version_msg, BuildConfig.VERSION_NAME),
            onDismiss = { handleAction(SettingsUiAction.DismissDialog) }
        )
    }

    if (showThemeDialog) {
        AppDialog(
            onDismissRequest = { handleAction(SettingsUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.settings_dialog_theme_title)) },
            text = {
                AppDialogContent {
                    ThemeSetting.entries.forEach { selectionOption ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { handleAction(SettingsUiAction.ThemeSelected(selectionOption)) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.themeSetting == selectionOption, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(ThemeDisplayMapper.getLabelRes(selectionOption)))
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { handleAction(SettingsUiAction.DismissDialog) }
                )
            }
        )
    }

    if (showRetentionDialog) {
        val options = AppSpecifications.Settings.AUDIT_LOG_RETENTION_OPTIONS
        AppDialog(
            onDismissRequest = { handleAction(SettingsUiAction.DismissDialog) },
            title = { Text(context.getString(R.string.audit_log_label_retention)) },
            text = {
                AppDialogContent {
                    options.forEach { (days, _) ->
                        val displayLabel = when (days) {
                            0 -> stringResource(R.string.settings_retention_none)
                            7 -> stringResource(R.string.settings_retention_one_week)
                            14 -> stringResource(R.string.settings_retention_two_weeks)
                            30 -> stringResource(R.string.settings_retention_one_month)
                            90 -> stringResource(R.string.settings_retention_three_months)
                            180 -> stringResource(R.string.settings_retention_half_year)
                            365 -> stringResource(R.string.settings_retention_one_year)
                            else -> stringResource(R.string.settings_retention_days, days)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { handleAction(SettingsUiAction.RetentionSelected(days)) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.auditLogRetentionDays == days, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(displayLabel)
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { handleAction(SettingsUiAction.DismissDialog) }
                )
            }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AppDialog(
            onDismissRequest = { handleAction(SettingsUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.settings_dialog_import_password_title)) },
            text = {
                AppDialogContent {
                    Text(stringResource(R.string.settings_dialog_import_password_msg))
                    Spacer(modifier = Modifier.height(16.dp))
                    AppTextField(
                        value = inputPasswordForImport,
                        onValueChange = { handleAction(SettingsUiAction.PasswordInputForImportChanged(it)) },
                        type = AppTextFieldType.PASSWORD,
                        label = { Text(stringResource(R.string.settings_dialog_import_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isInputPasswordVisible = !isInputPasswordVisible }) {
                                Icon(
                                    imageVector = if (isInputPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.decision),
                    onClick = { handleAction(SettingsUiAction.ConfirmImportWithPassword) },
                    enabled = inputPasswordForImport.isNotEmpty()
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { handleAction(SettingsUiAction.DismissDialog) }
                )
            }
        )
    }

    SettingsScreenContent(
        uiState = uiState,
        onAction = handleAction,
        isPasswordValid = isPasswordValid,
        isPasswordVisible = isPasswordVisible,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Component：SettingsScreenContent
 *
 * 【役割】
 * 設定画面の UI レイアウト本体を構築します。
 * 各設定項目（表示、ユーザー、データ、セキュリティ、テーマ等）をセクションごとに整理して表示します。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    isPasswordValid: Boolean,
    isPasswordVisible: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_feature_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = { 
                    IconButton(
                        onClick = { onAction(SettingsUiAction.Back) },
                        modifier = Modifier.testTag("SettingsScreen_BackButton")
                    ) { 
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) 
                    } 
                },
                colors = appTopAppBarColors(),
            )
        },
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding() // キーボード回避
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .testTag("Settings_ScrollColumn"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DisplayAndRecordingSection(
                    uiState = uiState,
                    onAction = onAction
                )

                UserManagementSection(
                    uiState = uiState,
                    onAction = onAction
                )

                DataManagementSection(
                    uiState = uiState,
                    onAction = onAction,
                    isPasswordValid = isPasswordValid,
                    isPasswordVisible = isPasswordVisible
                )

                SecuritySection(
                    uiState = uiState,
                    onAction = onAction
                )

                ThemeSection(
                    uiState = uiState,
                    onAction = onAction
                )

                OtherSection(
                    onAction = onAction
                )

                if (uiState.isDeveloperModeEnabled) {
                    ResetSection(
                        uiState = uiState,
                        onAction = onAction
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }

    if (uiState.isProcessing) {
        AppDialog(
            onDismissRequest = { },
            modifier = Modifier.testTag("Settings_ProcessingDialog"),
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text(stringResource(R.string.common_loading)) },
            text = {
                AppDialogContent {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { uiState.processingProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(text = "${uiState.processingProgress}%")
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun DisplayAndRecordingSection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_display_record), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_name_masking_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_name_masking_desc)) },
            trailingContent = { 
                Switch(
                    checked = uiState.isNameMaskingEnabled, 
                    onCheckedChange = null
                ) 
            },
            modifier = Modifier
                .testTag("Settings_MaskingRow")
                .toggleable(
                    value = uiState.isNameMaskingEnabled,
                    role = Role.Switch,
                    onValueChange = { onAction(SettingsUiAction.MaskingChanged(it)) }
                )
        )
        AppTextField(
            value = uiState.defaultRecorderName,
            onValueChange = { onAction(SettingsUiAction.RecorderNameChanged(it)) },
            type = AppTextFieldType.TEXT,
            label = { Text(stringResource(R.string.settings_item_default_recorder_title)) },
            placeholder = { Text(stringResource(R.string.settings_item_default_recorder_placeholder)) },
            supportingText = { Text(stringResource(R.string.settings_item_default_recorder_desc)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("Settings_RecorderName")
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun UserManagementSection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_person_management), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_restore_archived_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_restore_archived_desc, uiState.endedUserCount)) },
            trailingContent = { IconButton(onClick = { onAction(SettingsUiAction.NavigateToRestore) }) { Icon(Icons.Rounded.Restore, contentDescription = null) } },
            modifier = Modifier.clickable { onAction(SettingsUiAction.NavigateToRestore) }.testTag("Settings_RestoreUserButton")
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_permanent_delete_archived_title), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(stringResource(R.string.settings_item_permanent_delete_archived_desc)) },
            trailingContent = { 
                IconButton(onClick = { onAction(SettingsUiAction.EraseArchivedClick) }, enabled = uiState.endedUserCount > 0) { 
                    Icon(
                        Icons.Rounded.DeleteForever, 
                        contentDescription = null, 
                        tint = if (uiState.endedUserCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    ) 
                } 
            },
            modifier = Modifier.clickable(enabled = uiState.endedUserCount > 0) { onAction(SettingsUiAction.EraseArchivedClick) }.testTag("Settings_PermanentDeleteButton")
        )
    }
}

@Composable
private fun DataManagementSection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    isPasswordValid: Boolean,
    isPasswordVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_data_management), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_backup_password_title)) },
            supportingContent = { 
                Column {
                    Text(stringResource(R.string.settings_item_backup_password_desc))
                    if (!uiState.isBackupPasswordEnabled) {
                        Text(
                            text = stringResource(R.string.settings_item_backup_password_off_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            trailingContent = { 
                Switch(
                    checked = uiState.isBackupPasswordEnabled, 
                    onCheckedChange = null, 
                    modifier = Modifier.testTag("Settings_BackupPasswordSwitch")
                ) 
            },
            modifier = Modifier.toggleable(
                value = uiState.isBackupPasswordEnabled,
                role = Role.Switch,
                onValueChange = { onAction(SettingsUiAction.BackupPasswordEnabledChanged(it)) }
            )
        )
        if (uiState.isBackupPasswordEnabled) {
            AppTextField(
                value = uiState.backupPassword,
                onValueChange = { onAction(SettingsUiAction.BackupPasswordChanged(it)) },
                type = AppTextFieldType.PASSWORD,
                label = { Text(stringResource(R.string.settings_item_default_password_title)) },
                placeholder = { Text(stringResource(R.string.settings_item_default_password_placeholder, AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH)) },
                supportingText = { 
                    if (!isPasswordValid && uiState.backupPassword.isNotEmpty()) 
                        Text(stringResource(R.string.settings_item_default_password_error, AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH), color = MaterialTheme.colorScheme.error) 
                    else Text(stringResource(R.string.settings_item_default_password_hint)) 
                },
                isError = !isPasswordValid && uiState.backupPassword.isNotEmpty(),
                maxLength = AppSpecifications.Constraints.System.Security.MAX_PASSWORD_LENGTH,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("Settings_BackupPasswordInput"),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { 
                    IconButton(onClick = { onAction(SettingsUiAction.PasswordVisibilityToggle) }, modifier = Modifier.testTag("Settings_PasswordVisibilityToggle")) { 
                        Icon(imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null) 
                    } 
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val canExport = !uiState.isBackupPasswordEnabled || isPasswordValid
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_export_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_export_desc)) },
            trailingContent = { 
                IconButton(onClick = { onAction(SettingsUiAction.ExportClick) }, enabled = canExport, modifier = Modifier.testTag("Settings_BackupButton")) { 
                    Icon(Icons.Rounded.Output, contentDescription = null, tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) 
                } 
            },
            modifier = Modifier.clickable(enabled = canExport) { onAction(SettingsUiAction.ExportClick) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_import_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_import_desc)) },
            trailingContent = { IconButton(onClick = { onAction(SettingsUiAction.ImportClick) }, modifier = Modifier.testTag("Settings_ImportButton")) { Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null) } },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ImportClick) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_unassigned_photos_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_unassigned_photos_desc)) },
            leadingContent = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.UnassignedPhotosClick) }.testTag("Settings_UnassignedPhotosButton")
        )
    }
}

@Composable
private fun SecuritySection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_security), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_app_lock_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_app_lock_desc)) },
            trailingContent = { 
                Switch(
                    checked = uiState.isBiometricEnabled, 
                    onCheckedChange = null, 
                    modifier = Modifier.testTag("Settings_BiometricSwitch")
                ) 
            },
            modifier = Modifier.toggleable(
                value = uiState.isBiometricEnabled,
                role = Role.Switch,
                onValueChange = { onAction(SettingsUiAction.BiometricEnabledChanged(it)) }
            )
        )
    }
}

@Composable
private fun ThemeSection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_theme), modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onAction(SettingsUiAction.ThemeClick) }.testTag("Settings_ThemeRow")) {
            OutlinedTextField(
                value = stringResource(ThemeDisplayMapper.getLabelRes(uiState.themeSetting)),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.settings_item_theme_selection_title)) },
                trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Text(text = "※ ${stringResource(ThemeDisplayMapper.getDescriptionRes(uiState.themeSetting))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun OtherSection(
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_other), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_version_title)) },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.VersionClick) }.testTag("Settings_VersionRow")
        )
    }
}

@Composable
private fun ResetSection(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_dev_tools), modifier = modifier.testTag("Settings_DevSection")) {
        Text(text = stringResource(R.string.settings_dev_audit_log_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        
        val retentionLabel = when (uiState.auditLogRetentionDays) {
            0 -> stringResource(R.string.settings_retention_none)
            7 -> stringResource(R.string.settings_retention_one_week)
            14 -> stringResource(R.string.settings_retention_two_weeks)
            30 -> stringResource(R.string.settings_retention_one_month)
            90 -> stringResource(R.string.settings_retention_three_months)
            180 -> stringResource(R.string.settings_retention_half_year)
            365 -> stringResource(R.string.settings_retention_one_year)
            else -> stringResource(R.string.settings_retention_days, uiState.auditLogRetentionDays)
        }
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.audit_log_label_retention)) },
            supportingContent = { Text(stringResource(R.string.audit_log_retention_desc)) },
            trailingContent = { Text(text = retentionLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.RetentionClick) }
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_view_audit_logs)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_audit_log_count, uiState.auditLogCount)) },
            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ViewLogsClick) }.testTag("Settings_AuditLogButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_export_audit_logs)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_export_audit_logs_desc)) },
            leadingContent = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ExportLogsClick) }.testTag("Settings_AuditLogExportButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_rotate_logs)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_rotate_logs_desc)) },
            leadingContent = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.RotateLogsClick) }
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.audit_log_clear_confirm_title), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(stringResource(R.string.settings_dev_clear_logs_desc)) },
            leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ClearLogsClick) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

        Text(text = stringResource(R.string.settings_dev_data_maintenance_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_import_sample_data)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_import_sample_desc)) },
            leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ImportSampleDataClick) }.testTag("Settings_ImportSampleButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_force_import_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_force_import_desc)) },
            leadingContent = { Icon(Icons.Rounded.PublishedWithChanges, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = uiState.isForceImportEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.testTag("Settings_ForceImportSwitch")
                )
            },
            modifier = Modifier.toggleable(
                value = uiState.isForceImportEnabled,
                role = Role.Switch,
                onValueChange = { onAction(SettingsUiAction.ForceImportEnabledChanged(it)) }
            )
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_integrity_check_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_integrity_check_desc)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.CheckIntegrity) }.testTag("Settings_IntegrityCheckButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_insert_inconsistency_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_insert_inconsistency_desc)) },
            leadingContent = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.InsertTestInconsistency) }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        
        Text(text = stringResource(R.string.settings_dev_clear_all_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_clear_all_title), color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Rounded.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onAction(SettingsUiAction.ClearAllClick) }.testTag("Settings_ClearAllButton")
        )
    }
}

@Composable
private fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(content = content) }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    CareMemoTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                isNameMaskingEnabled = false,
                defaultRecorderName = "記録者名",
                endedUserCount = 2,
                isBackupPasswordEnabled = true,
                backupPassword = "password",
                isBiometricEnabled = true,
                themeSetting = ThemeSetting.SYSTEM,
                auditLogRetentionDays = 30,
                auditLogCount = 120,
                isDeveloperModeEnabled = true,
                isProcessing = false,
                processingProgress = 0,
            ),
            onAction = {},
            isPasswordValid = true,
            isPasswordVisible = false,
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
