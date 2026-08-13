package jp.mydns.fujiwara.carememo.ui.screens.settings

/**
 * Screen : SettingsScreen
 *
 * 【画面名】
 * 設定・管理画面
 *
 * 【遷移】：
 * ViewModel から発行される ViewEvent (SettingsViewEvent) に基づき、
 * Composable 側で NavHostController を操作して遷移を行う。
 */

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
import jp.mydns.fujiwara.carememo.ui.mapping.ThemeDisplayMapper
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavHostController,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 設定変更があったかどうかの内部状態
    var isChangedByMe by rememberSaveable { mutableStateOf(false) }

    // 子画面（利用者管理 S-003）からの更新要求を監視
    val childRefreshRequested by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("refresh_needed", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    // 戻る際の処理（親画面への通知準備）
    val performBack: () -> Unit = {
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
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }
    var showLogClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showImportSampleConfirm by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }

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
                    else -> {}
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
                    SettingsViewEvent.NavigateToOrphanedPhotos -> {
                        navController.navigate(Destination.OrphanedPhotos)
                    }
                    SettingsViewEvent.NavigateBack -> {
                        performBack()
                    }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.exportData(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { showImportUri = it } }

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
            onDismissRequest = { showImportUri = null },
            title = { Text(stringResource(R.string.settings_dialog_restore_confirm_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.settings_dialog_restore_confirm_msg))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.settings_dialog_restore_confirm_btn),
                    onClick = {
                        pendingImportUri = showImportUri
                        viewModel.importData(context, showImportUri!!)
                        showImportUri = null
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showImportUri = null }
                )
            }
        )
    }

    if (showEraseConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showEraseConfirm = false },
            onDelete = { viewModel.deleteEndedPersons() },
            title = stringResource(R.string.settings_dialog_permanent_delete_confirm_title),
            message = stringResource(R.string.settings_dialog_permanent_delete_confirm_msg, uiState.endedUserCount),
            confirmButtonText = stringResource(R.string.settings_dialog_permanent_delete_confirm_btn, uiState.endedUserCount)
        )
    }

    if (showDevClearConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showDevClearConfirm = false },
            onDelete = { viewModel.clearAllData() },
            title = stringResource(R.string.settings_dialog_clear_all_confirm_title),
            message = stringResource(R.string.settings_dialog_clear_all_confirm_msg),
            confirmButtonText = stringResource(R.string.decision)
        )
    }

    if (showLogClearConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showLogClearConfirm = false },
            onDelete = { viewModel.clearAuditLogs() },
            title = context.getString(R.string.audit_log_clear_confirm_title),
            message = context.getString(R.string.audit_log_clear_confirm_msg),
            confirmButtonText = context.getString(R.string.common_delete)
        )
    }

    if (showImportSampleConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showImportSampleConfirm = false },
            onDelete = { viewModel.importSampleData() },
            title = context.getString(R.string.settings_import_sample_confirm_title),
            message = context.getString(R.string.settings_import_sample_confirm_msg),
            confirmButtonText = context.getString(R.string.settings_btn_import_sample_data)
        )
    }

    // データベース不整合レポート・ダイアログ
    if (uiState.inconsistencies.isNotEmpty()) {
        AppDialog(
            onDismissRequest = { viewModel.clearInconsistencyResults() },
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = stringResource(inc.descriptionResId),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = buildString {
                                            append(stringResource(R.string.settings_dialog_inconsistency_person_id, inc.personId ?: stringResource(R.string.audit_result_unknown)))
                                            append(" | ")
                                            append(stringResource(R.string.settings_dialog_inconsistency_table, inc.tableName))
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    inc.recordTime?.let { time ->
                                        Text(
                                            text = stringResource(R.string.settings_dialog_inconsistency_record_time, DateTimeUtils.formatRecordTime(time)),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.settings_dialog_inconsistency_summary),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.settings_dialog_inconsistency_cleanup_btn),
                    onClick = { viewModel.fixInconsistencies() }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_close),
                    onClick = { viewModel.clearInconsistencyResults() }
                )
            }
        )
    }

    if (showVersionDialog) {
        AppInfoDialog(
            title = stringResource(R.string.main_dialog_version_title),
            message = stringResource(R.string.settings_version_msg, BuildConfig.VERSION_NAME),
            onDismiss = { showVersionDialog = false }
        )
    }

    if (showTimeoutDialog) {
        val options = AppSpecifications.Settings.LOCK_TIMEOUT_OPTIONS
        AppDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_lock_timeout_title)) },
            text = {
                AppDialogContent {
                    options.forEach { (minutes, _) ->
                        val displayLabel = when (minutes) {
                            0 -> stringResource(R.string.settings_timeout_immediate)
                            -1 -> stringResource(R.string.settings_timeout_none)
                            else -> stringResource(R.string.settings_timeout_minutes, minutes)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if ((minutes == -1) && (uiState.lockTimeoutMinutes != -1)) {
                                        if (viewModel.canAuthenticate(context)) {
                                            onRequireAuthentication(
                                                R.string.security_auth_title,
                                                R.string.security_auth_reason_change_settings
                                            ) {
                                                viewModel.setLockTimeoutMinutes(minutes)
                                                showTimeoutDialog = false
                                            }
                                        } else {
                                            viewModel.setLockTimeoutMinutes(minutes)
                                            showTimeoutDialog = false
                                        }
                                    } else {
                                        viewModel.setLockTimeoutMinutes(minutes)
                                        showTimeoutDialog = false
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.lockTimeoutMinutes == minutes, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(displayLabel)
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showTimeoutDialog = false }
                )
            }
        )
    }

    if (showThemeDialog) {
        AppDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_theme_title)) },
            text = {
                AppDialogContent {
                    ThemeSetting.entries.forEach { selectionOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeSetting(selectionOption)
                                    showThemeDialog = false
                                }.padding(16.dp),
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
                    onClick = { showThemeDialog = false }
                )
            }
        )
    }

    if (showRetentionDialog) {
        val options = AppSpecifications.Settings.AUDIT_LOG_RETENTION_OPTIONS
        AppDialog(
            onDismissRequest = { showRetentionDialog = false },
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAuditLogRetentionDays(days)
                                    showRetentionDialog = false
                                }
                                .padding(16.dp),
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
                    onClick = { showRetentionDialog = false }
                )
            }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AppDialog(
            onDismissRequest = { showPasswordInputDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_import_password_title)) },
            text = {
                AppDialogContent {
                    Text(stringResource(R.string.settings_dialog_import_password_msg))
                    Spacer(modifier = Modifier.height(16.dp))
                    AppTextField(
                        value = inputPasswordForImport,
                        onValueChange = { inputPasswordForImport = it },
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
                    onClick = {
                        pendingImportUri?.let { viewModel.importData(context, it, inputPasswordForImport) }
                        showPasswordInputDialog = false
                        inputPasswordForImport = ""
                    },
                    enabled = inputPasswordForImport.isNotEmpty()
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = {
                        showPasswordInputDialog = false
                        inputPasswordForImport = ""
                    }
                )
            }
        )
    }

    SettingsScreenContent(
        snackbarHostState = snackbarHostState,
        isMaskingEnabled = uiState.isNameMaskingEnabled,
        defaultRecorderName = uiState.defaultRecorderName,
        onMaskingChange = {
            viewModel.setNameMaskingEnabled(it)
            isChangedByMe = true // 伏せ字設定が変更されたらフラグを立てる (BH-01)
        },
        onRecorderNameChange = {
            viewModel.setDefaultRecorderName(it)
            isChangedByMe = true
        },
        endedUserCount = uiState.endedUserCount,
        onNavigateToRestore = { viewModel.navigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.RESTORE) },
        onEraseClick = { viewModel.navigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.DELETE) },
        isBackupPasswordEnabled = uiState.isBackupPasswordEnabled,
        backupPassword = uiState.backupPassword,
        isPasswordValid = isPasswordValid,
        isPasswordVisible = isPasswordVisible,
        onBackupPasswordEnabledChange = { enabled ->
            if (enabled) {
                viewModel.setBackupPasswordEnabled(true)
                isPasswordVisible = false
            } else {
                if (viewModel.canAuthenticate(context)) {
                    onRequireAuthentication(
                        R.string.security_auth_title,
                        R.string.security_auth_reason_change_settings
                    ) {
                        viewModel.setBackupPasswordEnabled(false)
                    }
                } else {
                    viewModel.setBackupPasswordEnabled(false)
                }
            }
            isChangedByMe = true
        },
        onBackupPasswordChange = {
            if (it.length >= AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH || it.isEmpty()) viewModel.setBackupPassword(it)
            isChangedByMe = true
        },
        onPasswordVisibilityToggle = {
            if (isPasswordVisible) {
                isPasswordVisible = false
            } else {
                if (viewModel.canAuthenticate(context)) {
                    onRequireAuthentication(
                        R.string.security_auth_title,
                        R.string.security_auth_reason_show_password
                    ) {
                        isPasswordVisible = true
                    }
                } else {
                    isPasswordVisible = true
                }
            }
        },
        onExportClick = {
            viewModel.setLockBypassEnabled(true)
            exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip")
        },
        onImportClick = {
            viewModel.setLockBypassEnabled(true)
            importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
        },
        isBiometricEnabled = uiState.isBiometricEnabled,
        lockTimeoutMinutes = uiState.lockTimeoutMinutes,
        onBiometricEnabledChange = { enabled ->
            if (enabled) {
                viewModel.setBiometricEnabled(context, true)
            } else {
                if (viewModel.canAuthenticate(context)) {
                    onRequireAuthentication(
                        R.string.security_auth_title,
                        R.string.security_auth_reason_change_settings
                    ) {
                        viewModel.setBiometricEnabled(context, false)
                    }
                } else {
                    viewModel.setBiometricEnabled(context, false)
                }
            }
            isChangedByMe = true
        },
        onTimeoutClick = { showTimeoutDialog = true },
        themeSetting = uiState.themeSetting,
        onThemeClick = { showThemeDialog = true },
        onVersionClick = {
            viewModel.handleVersionClick()
            showVersionDialog = true
        },
        onClearAllClick = {
            if (viewModel.canAuthenticate(context)) {
                onRequireAuthentication(
                    R.string.security_auth_title,
                    R.string.security_auth_reason_change_settings
                ) {
                    showDevClearConfirm = true
                }
            } else {
                showDevClearConfirm = true
            }
        },
        onCheckIntegrity = { viewModel.checkIntegrity() },
        onInsertTestInconsistency = { viewModel.insertTestInconsistency() },
        auditLogRetentionDays = uiState.auditLogRetentionDays,
        auditLogCount = uiState.auditLogCount,
        onRetentionClick = { showRetentionDialog = true },
        onViewLogsClick = { viewModel.navigateToAuditLog() },
        onOrphanedPhotosClick = { viewModel.navigateToOrphanedPhotos() },
        onRotateLogsClick = { viewModel.rotateLogsManually() },
        onClearLogsClick = { showLogClearConfirm = true },
        onImportSampleDataClick = {
            if (viewModel.canAuthenticate(context)) {
                onRequireAuthentication(
                    R.string.security_auth_title,
                    R.string.security_auth_reason_change_settings
                ) {
                    showImportSampleConfirm = true
                }
            } else {
                showImportSampleConfirm = true
            }
        },
        isForceImportEnabled = uiState.isForceImportEnabled,
        onForceImportEnabledChange = { viewModel.setForceImportEnabled(it) },
        isDeveloperModeEnabled = uiState.isDeveloperModeEnabled,
        isProcessing = uiState.isProcessing,
        processingProgress = uiState.processingProgress,
        onBack = { viewModel.navigateBack() },
        modifier = modifier
    )
}

/**
 * 設定画面のUIレイアウト本体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    snackbarHostState: SnackbarHostState,
    isMaskingEnabled: Boolean,
    defaultRecorderName: String,
    onMaskingChange: (Boolean) -> Unit,
    onRecorderNameChange: (String) -> Unit,
    endedUserCount: Int,
    onNavigateToRestore: () -> Unit,
    onEraseClick: () -> Unit,
    isBackupPasswordEnabled: Boolean,
    backupPassword: String,
    isPasswordValid: Boolean,
    isPasswordVisible: Boolean,
    onBackupPasswordEnabledChange: (Boolean) -> Unit,
    onBackupPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    isBiometricEnabled: Boolean,
    lockTimeoutMinutes: Int,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onTimeoutClick: () -> Unit,
    themeSetting: ThemeSetting,
    onThemeClick: () -> Unit,
    onVersionClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onCheckIntegrity: () -> Unit,
    onInsertTestInconsistency: () -> Unit,
    auditLogRetentionDays: Int,
    auditLogCount: Int,
    onRetentionClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onOrphanedPhotosClick: () -> Unit,
    onRotateLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    onImportSampleDataClick: () -> Unit,
    isForceImportEnabled: Boolean,
    onForceImportEnabledChange: (Boolean) -> Unit,
    isDeveloperModeEnabled: Boolean,
    isProcessing: Boolean,
    processingProgress: Int,
    onBack: () -> Unit,
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
                        onClick = onBack,
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
                    isMaskingEnabled = isMaskingEnabled,
                    defaultRecorderName = defaultRecorderName,
                    onMaskingChange = onMaskingChange,
                    onRecorderNameChange = onRecorderNameChange
                )

                UserManagementSection(
                    endedUserCount = endedUserCount,
                    onNavigateToRestore = onNavigateToRestore,
                    onEraseClick = onEraseClick
                )

                DataManagementSection(
                    isBackupPasswordEnabled = isBackupPasswordEnabled,
                    backupPassword = backupPassword,
                    isPasswordValid = isPasswordValid,
                    isPasswordVisible = isPasswordVisible,
                    onBackupPasswordEnabledChange = onBackupPasswordEnabledChange,
                    onBackupPasswordChange = onBackupPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onExportClick = onExportClick,
                    onImportClick = onImportClick
                )

                SecuritySection(
                    isBiometricEnabled = isBiometricEnabled,
                    lockTimeoutMinutes = lockTimeoutMinutes,
                    onBiometricEnabledChange = onBiometricEnabledChange,
                    onTimeoutClick = onTimeoutClick
                )

                ThemeSection(
                    themeSetting = themeSetting,
                    onThemeClick = onThemeClick
                )

                OtherSection(
                    onVersionClick = onVersionClick
                )

                if (isDeveloperModeEnabled) {
                    ResetSection(
                        onClearAllClick = onClearAllClick,
                        onCheckIntegrity = onCheckIntegrity,
                        onInsertTestInconsistency = onInsertTestInconsistency,
                        auditLogRetentionDays = auditLogRetentionDays,
                        auditLogCount = auditLogCount,
                        onRetentionClick = onRetentionClick,
                        onViewLogsClick = onViewLogsClick,
                        onOrphanedPhotosClick = onOrphanedPhotosClick,
                        onRotateLogsClick = onRotateLogsClick,
                        onClearLogsClick = onClearLogsClick,
                        onImportSampleDataClick = onImportSampleDataClick,
                        isForceImportEnabled = isForceImportEnabled,
                        onForceImportEnabledChange = onForceImportEnabledChange
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }

    if (isProcessing) {
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
                            progress = { processingProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(text = "$processingProgress%")
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun DisplayAndRecordingSection(
    isMaskingEnabled: Boolean,
    defaultRecorderName: String,
    onMaskingChange: (Boolean) -> Unit,
    onRecorderNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_display_record), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_name_masking_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_name_masking_desc)) },
            trailingContent = { 
                Switch(
                    checked = isMaskingEnabled, 
                    onCheckedChange = null
                ) 
            },
            modifier = Modifier
                .testTag("Settings_MaskingRow")
                .toggleable(
                    value = isMaskingEnabled,
                    role = Role.Switch,
                    onValueChange = onMaskingChange
                )
        )
        AppTextField(
            value = defaultRecorderName,
            onValueChange = onRecorderNameChange,
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
    endedUserCount: Int,
    onNavigateToRestore: () -> Unit,
    onEraseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_person_management), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_restore_archived_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_restore_archived_desc, endedUserCount)) },
            trailingContent = { IconButton(onClick = onNavigateToRestore) { Icon(Icons.Rounded.Restore, contentDescription = null) } },
            modifier = Modifier.clickable { onNavigateToRestore() }.testTag("Settings_RestoreUserButton")
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_permanent_delete_archived_title), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(stringResource(R.string.settings_item_permanent_delete_archived_desc)) },
            trailingContent = { 
                IconButton(onClick = onEraseClick, enabled = endedUserCount > 0) { 
                    Icon(
                        Icons.Rounded.DeleteForever, 
                        contentDescription = null, 
                        tint = if (endedUserCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    ) 
                } 
            },
            modifier = Modifier.clickable(enabled = endedUserCount > 0) { onEraseClick() }.testTag("Settings_PermanentDeleteButton")
        )
    }
}

@Composable
private fun DataManagementSection(
    isBackupPasswordEnabled: Boolean,
    backupPassword: String,
    isPasswordValid: Boolean,
    isPasswordVisible: Boolean,
    onBackupPasswordEnabledChange: (Boolean) -> Unit,
    onBackupPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_data_management), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_backup_password_title)) },
            supportingContent = { 
                Column {
                    Text(stringResource(R.string.settings_item_backup_password_desc))
                    if (!isBackupPasswordEnabled) {
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
                    checked = isBackupPasswordEnabled, 
                    onCheckedChange = null, 
                    modifier = Modifier.testTag("Settings_BackupPasswordSwitch")
                ) 
            },
            modifier = Modifier.toggleable(
                value = isBackupPasswordEnabled,
                role = Role.Switch,
                onValueChange = onBackupPasswordEnabledChange
            )
        )
        if (isBackupPasswordEnabled) {
            AppTextField(
                value = backupPassword,
                onValueChange = onBackupPasswordChange,
                type = AppTextFieldType.PASSWORD,
                label = { Text(stringResource(R.string.settings_item_default_password_title)) },
                placeholder = { Text(stringResource(R.string.settings_item_default_password_placeholder, AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH)) },
                supportingText = { 
                    if (!isPasswordValid && backupPassword.isNotEmpty()) 
                        Text(stringResource(R.string.settings_item_default_password_error, AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH), color = MaterialTheme.colorScheme.error) 
                    else Text(stringResource(R.string.settings_item_default_password_hint)) 
                },
                isError = !isPasswordValid && backupPassword.isNotEmpty(),
                maxLength = AppSpecifications.Constraints.System.Security.MAX_PASSWORD_LENGTH,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("Settings_BackupPasswordInput"),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { 
                    IconButton(onClick = onPasswordVisibilityToggle, modifier = Modifier.testTag("Settings_PasswordVisibilityToggle")) { 
                        Icon(imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null) 
                    } 
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val canExport = !isBackupPasswordEnabled || isPasswordValid
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_export_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_export_desc)) },
            trailingContent = { 
                IconButton(onClick = onExportClick, enabled = canExport, modifier = Modifier.testTag("Settings_BackupButton")) { 
                    Icon(Icons.Rounded.Output, contentDescription = null, tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) 
                } 
            },
            modifier = Modifier.clickable(enabled = canExport) { onExportClick() }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_import_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_import_desc)) },
            trailingContent = { IconButton(onClick = onImportClick, modifier = Modifier.testTag("Settings_ImportButton")) { Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null) } },
            modifier = Modifier.clickable { onImportClick() }
        )
    }
}

@Composable
private fun SecuritySection(
    isBiometricEnabled: Boolean,
    lockTimeoutMinutes: Int,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onTimeoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_security), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_app_lock_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_app_lock_desc)) },
            trailingContent = { 
                Switch(
                    checked = isBiometricEnabled, 
                    onCheckedChange = null, 
                    modifier = Modifier.testTag("Settings_BiometricSwitch")
                ) 
            },
            modifier = Modifier.toggleable(
                value = isBiometricEnabled,
                role = Role.Switch,
                onValueChange = onBiometricEnabledChange
            )
        )
        val timeoutLabel = when (lockTimeoutMinutes) {
            0 -> stringResource(R.string.settings_timeout_immediate)
            -1 -> stringResource(R.string.settings_timeout_none)
            else -> stringResource(R.string.settings_timeout_minutes, lockTimeoutMinutes)
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_lock_timeout_title), color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
            supportingContent = { Text(stringResource(R.string.settings_item_lock_timeout_desc), color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
            trailingContent = { Text(text = timeoutLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
            modifier = Modifier.clickable(enabled = isBiometricEnabled) { onTimeoutClick() }.testTag("Settings_TimeoutRow")
        )
        Text(text = stringResource(R.string.settings_item_lock_timeout_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun ThemeSection(
    themeSetting: ThemeSetting,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_theme), modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onThemeClick() }.testTag("Settings_ThemeRow")) {
            OutlinedTextField(
                value = stringResource(ThemeDisplayMapper.getLabelRes(themeSetting)),
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
        Text(text = "※ ${stringResource(ThemeDisplayMapper.getDescriptionRes(themeSetting))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun OtherSection(
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_other), modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_version_title)) },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier.clickable { onVersionClick() }.testTag("Settings_VersionRow")
        )
    }
}

@Composable
private fun ResetSection(
    onClearAllClick: () -> Unit,
    onCheckIntegrity: () -> Unit,
    onInsertTestInconsistency: () -> Unit,
    auditLogRetentionDays: Int,
    auditLogCount: Int,
    onRetentionClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onOrphanedPhotosClick: () -> Unit,
    onRotateLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    onImportSampleDataClick: () -> Unit,
    isForceImportEnabled: Boolean,
    onForceImportEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_section_dev_tools), modifier = modifier.testTag("Settings_DevSection")) {
        Text(text = stringResource(R.string.settings_dev_audit_log_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        
        val retentionLabel = when (auditLogRetentionDays) {
            0 -> stringResource(R.string.settings_retention_none)
            7 -> stringResource(R.string.settings_retention_one_week)
            14 -> stringResource(R.string.settings_retention_two_weeks)
            30 -> stringResource(R.string.settings_retention_one_month)
            90 -> stringResource(R.string.settings_retention_three_months)
            180 -> stringResource(R.string.settings_retention_half_year)
            365 -> stringResource(R.string.settings_retention_one_year)
            else -> stringResource(R.string.settings_retention_days, auditLogRetentionDays)
        }
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.audit_log_label_retention)) },
            supportingContent = { Text(stringResource(R.string.audit_log_retention_desc)) },
            trailingContent = { Text(text = retentionLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onRetentionClick() }
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_view_audit_logs)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_audit_log_count, auditLogCount)) },
            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
            modifier = Modifier.clickable { onViewLogsClick() }.testTag("Settings_AuditLogButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_rotate_logs)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_rotate_logs_desc)) },
            leadingContent = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
            modifier = Modifier.clickable { onRotateLogsClick() }
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.audit_log_clear_confirm_title), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(stringResource(R.string.settings_dev_clear_logs_desc)) },
            leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onClearLogsClick() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

        Text(text = stringResource(R.string.settings_dev_data_maintenance_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_btn_import_sample_data)) },
            supportingContent = { Text(stringResource(R.string.settings_dev_import_sample_desc)) },
            leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
            modifier = Modifier.clickable { onImportSampleDataClick() }.testTag("Settings_ImportSampleButton")
        )
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_orphaned_photos_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_orphaned_photos_desc)) },
            leadingContent = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
            modifier = Modifier.clickable { onOrphanedPhotosClick() }.testTag("Settings_OrphanedPhotosButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_force_import_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_force_import_desc)) },
            leadingContent = { Icon(Icons.Rounded.PublishedWithChanges, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = isForceImportEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.testTag("Settings_ForceImportSwitch")
                )
            },
            modifier = Modifier.toggleable(
                value = isForceImportEnabled,
                role = Role.Switch,
                onValueChange = onForceImportEnabledChange
            )
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_integrity_check_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_integrity_check_desc)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null) },
            modifier = Modifier.clickable { onCheckIntegrity() }.testTag("Settings_IntegrityCheckButton")
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_insert_inconsistency_title)) },
            supportingContent = { Text(stringResource(R.string.settings_item_insert_inconsistency_desc)) },
            leadingContent = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onInsertTestInconsistency() }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        
        Text(text = stringResource(R.string.settings_dev_clear_all_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_item_clear_all_title), color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Rounded.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onClearAllClick() }.testTag("Settings_ClearAllButton")
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
            snackbarHostState = remember { SnackbarHostState() },
            isMaskingEnabled = false,
            defaultRecorderName = "記録者名",
            onMaskingChange = {},
            onRecorderNameChange = {},
            endedUserCount = 2,
            onNavigateToRestore = {},
            onEraseClick = {},
            isBackupPasswordEnabled = true,
            backupPassword = "password",
            isPasswordValid = true,
            isPasswordVisible = false,
            onBackupPasswordEnabledChange = {},
            onBackupPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onExportClick = {},
            onImportClick = {},
            isBiometricEnabled = true,
            lockTimeoutMinutes = 5,
            onBiometricEnabledChange = {},
            onTimeoutClick = {},
            themeSetting = ThemeSetting.SYSTEM,
            onThemeClick = {},
            onVersionClick = {},
            onClearAllClick = {},
            onCheckIntegrity = {},
            onInsertTestInconsistency = {},
            auditLogRetentionDays = 30,
            auditLogCount = 120,
            onRetentionClick = {},
            onViewLogsClick = {},
            onOrphanedPhotosClick = {},
            onRotateLogsClick = {},
            onClearLogsClick = {},
            onImportSampleDataClick = {},
            isForceImportEnabled = false,
            onForceImportEnabledChange = {},
            isDeveloperModeEnabled = true,
            isProcessing = false,
            processingProgress = 0,
            onBack = {}
        )
    }
}
