package jp.mydns.fujiwara.carememo.ui.screens.settings

/**
 * Screen : SettingsScreen
 *
 * 【画面名】
 * 設定・管理画面
 */

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavController,
    onNavigateToArchiveManagement: (DeleteOrRestorePersonViewModel.OperationMode) -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val isMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val lockTimeoutMinutes by viewModel.lockTimeoutMinutes.collectAsStateWithLifecycle()
    val persistedRecorderName by viewModel.defaultRecorderName.collectAsStateWithLifecycle()
    val isBackupPasswordEnabled by viewModel.isBackupPasswordEnabled.collectAsStateWithLifecycle()
    val backupPassword by viewModel.backupPassword.collectAsStateWithLifecycle()
    val themeSetting by viewModel.themeSetting.collectAsStateWithLifecycle()
    val auditLogRetentionDays by viewModel.auditLogRetentionDays.collectAsStateWithLifecycle()
    val auditLogCount by viewModel.auditLogCount.collectAsStateWithLifecycle(initialValue = 0)
    val endedUserList by viewModel.deletedUserList.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingProgress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
    val inconsistencies: List<DatabaseInconsistency> by viewModel.inconsistencies.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 設定変更があったかどうかの内部状態
    var isChangedByMe by rememberSaveable { mutableStateOf(false) }

    // 子画面（利用者管理 S-003）からの更新要求を監視
    val childRefreshRequested by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("refresh_needed", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    // 戻る際の処理（親画面への通知準備）
    val handleBack = {
        if (isChangedByMe || childRefreshRequested) {
            // 親画面（MainScreen）の SavedStateHandle にフラグをセット
            navController.previousBackStackEntry?.savedStateHandle?.set("refresh_needed", true)
        }
        onBack()
    }

    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = backupPassword.length >= 6

    var showImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showDevClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }
    var showLogClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }

    var dialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    dialogTitle = context.getString(R.string.common_error_title_info)
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    dialogTitle = context.getString(R.string.common_error_title_info)
                    dialogMessage = context.getString(event.resId, *event.args.toTypedArray())
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowOverwriteConfirm -> {}
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> showPasswordInputDialog = true
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.SaveSuccess -> {
                    onBack()
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
            confirmButtonText = "OK",
        )
    }

    if (showImportUri != null) {
        AppDialog(
            onDismissRequest = { showImportUri = null },
            title = { Text("データの復元") },
            text = {
                AppDialogContent(text = "現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "復元を実行",
                    onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showImportUri = null }
                )
            }
        )
    }

    if (showEraseConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showEraseConfirm = false },
            onDelete = { viewModel.deleteEndedPersons() },
            title = "個人情報の完全抹消",
            message = "現在「利用終了」となっている ${endedUserList.size} 名分のデータを完全に抹消します。記録は復旧できません。よろしいですか？",
            confirmButtonText = "対象者 (${endedUserList.size}名) を抹消する"
        )
    }

    if (showDevClearConfirm) {
        AppDeleteConfirmDialog(
            onDismiss = { showDevClearConfirm = false },
            onDelete = { viewModel.clearAllData(context) },
            title = "(管理者) 全データ消去",
            message = "全てのデータおよび写真を物理削除します。取り消せません。",
            confirmButtonText = "実行する"
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

    // データベース不整合レポート・ダイアログ
    if (inconsistencies.isNotEmpty()) {
        AppDialog(
            onDismissRequest = { viewModel.clearInconsistencyResults() },
            title = { Text("データベース不整合レポート") },
            text = {
                AppDialogContent {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "以下の ${inconsistencies.size} 件の孤立したデータが見つかりました：",
                            style = MaterialTheme.typography.bodySmall
                        )

                        inconsistencies.forEach { inc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = inc.description,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = buildString {
                                            append("元利用者ID: ${inc.personId ?: "不明"}")
                                            append(" | テーブル: ${inc.tableName}")
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    inc.recordTime?.let { time ->
                                        Text(
                                            text = "記録日時: ${DateTimeUtils.formatRecordTime(time)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "これらは親データ（利用者）が存在しない無効な記録です。クリーンアップを実行して削除することをお勧めします。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "クリーンアップ実行",
                    onClick = { viewModel.fixInconsistencies() }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = "閉じる",
                    onClick = { viewModel.clearInconsistencyResults() }
                )
            }
        )
    }

    if (showVersionDialog) {
        AppInfoDialog(
            title = "バージョン情報",
            message = "CareMemo\nバージョン ${BuildConfig.VERSION_NAME}\n\n(C) 2025-2026 pirotty.galaxy",
            onDismiss = { showVersionDialog = false }
        )
    }

    if (showTimeoutDialog) {
        val options = listOf(0 to "即時", 1 to "1分", 5 to "5分", 10 to "10分", 30 to "30分", -1 to "ロックしない")
        AppDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("再ロックまでの時間") },
            text = {
                AppDialogContent {
                    options.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if ((minutes == -1) && (lockTimeoutMinutes != -1)) {
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
                            RadioButton(selected = lockTimeoutMinutes == minutes, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showTimeoutDialog = false }
                )
            }
        )
    }

    if (showThemeDialog) {
        AppDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("配色とモードの選択") },
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
                            RadioButton(selected = themeSetting == selectionOption, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(selectionOption.label)
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showThemeDialog = false }
                )
            }
        )
    }

    if (showRetentionDialog) {
        val options = listOf(7 to "1週間", 14 to "2週間", 30 to "1ヶ月", 90 to "3ヶ月", 180 to "半年", 365 to "1年", 0 to "残さない")
        AppDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text(context.getString(R.string.audit_log_label_retention)) },
            text = {
                AppDialogContent {
                    options.forEach { (days, label) ->
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
                            RadioButton(selected = auditLogRetentionDays == days, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showRetentionDialog = false }
                )
            }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AppDialog(
            onDismissRequest = { showPasswordInputDialog = false },
            title = { Text("パスワードの入力") },
            text = {
                AppDialogContent {
                    Text("このファイルはパスワード保護されています。入力してください。")
                    Spacer(modifier = Modifier.height(16.dp))
                    AppTextField(
                        value = inputPasswordForImport,
                        onValueChange = { inputPasswordForImport = it },
                        type = AppTextFieldType.PASSWORD,
                        label = { Text("パスワード") },
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
                    text = "実行",
                    onClick = {
                        viewModel.importData(context, android.net.Uri.EMPTY, inputPasswordForImport)
                        showPasswordInputDialog = false
                        inputPasswordForImport = ""
                    },
                    enabled = inputPasswordForImport.isNotEmpty()
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
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
        isMaskingEnabled = isMaskingEnabled,
        defaultRecorderName = persistedRecorderName,
        onMaskingChange = {
            viewModel.setNameMaskingEnabled(it)
            isChangedByMe = true // 伏せ字設定が変更されたらフラグを立てる (BH-01)
        },
        onRecorderNameChange = {
            viewModel.setDefaultRecorderName(it)
            isChangedByMe = true
        },
        endedUserCount = endedUserList.size,
        onNavigateToRestore = { onNavigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.RESTORE) },
        onEraseClick = { onNavigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.DELETE) },
        isBackupPasswordEnabled = isBackupPasswordEnabled,
        backupPassword = backupPassword,
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
            if (it.length >= 6 || it.isEmpty()) viewModel.setBackupPassword(it)
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
        isBiometricEnabled = isBiometricEnabled,
        lockTimeoutMinutes = lockTimeoutMinutes,
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
        themeSetting = themeSetting,
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
        auditLogRetentionDays = auditLogRetentionDays,
        auditLogCount = auditLogCount,
        onRetentionClick = { showRetentionDialog = true },
        onViewLogsClick = onNavigateToAuditLog,
        onRotateLogsClick = { viewModel.rotateLogsManually() },
        onClearLogsClick = { showLogClearConfirm = true },
        isDeveloperModeEnabled = isDeveloperModeEnabled,
        isProcessing = isProcessing,
        processingProgress = processingProgress,
        onBack = handleBack
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
    onRotateLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    isDeveloperModeEnabled: Boolean,
    isProcessing: Boolean,
    processingProgress: Int,
    onBack: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("設定・管理", fontWeight = FontWeight.Bold) },
                navigationIcon = { 
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("SettingsScreen_BackButton")
                    ) { 
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") 
                    } 
                },
                colors = appTopAppBarColors(),
            )
        },
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(16.dp),
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
                        onRotateLogsClick = onRotateLogsClick,
                        onClearLogsClick = onClearLogsClick
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
            title = { Text("処理中...") },
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
    onRecorderNameChange: (String) -> Unit
) {
    SettingsSection(title = "表示・記録設定") {
        ListItem(
            headlineContent = { Text("氏名の伏せ字表示") },
            supportingContent = { Text("一覧などの画面で氏名の一部を「○」で表示します") },
            trailingContent = { Switch(checked = isMaskingEnabled, onCheckedChange = onMaskingChange, modifier = Modifier.testTag("Settings_MaskingSwitch")) }
        )
        AppTextField(
            value = defaultRecorderName,
            onValueChange = onRecorderNameChange,
            type = AppTextFieldType.TEXT,
            label = { Text("記録者の名前(デフォルト)") },
            placeholder = { Text("例: 山田") },
            supportingText = { Text("所見メモ作成時に自動入力されます") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("Settings_RecorderName")
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun UserManagementSection(
    endedUserCount: Int,
    onNavigateToRestore: () -> Unit,
    onEraseClick: () -> Unit
) {
    SettingsSection(title = "利用者管理") {
        ListItem(
            headlineContent = { Text("利用終了者の復帰") },
            supportingContent = { Text("現在 $endedUserCount 名が利用終了となっています") },
            trailingContent = { IconButton(onClick = onNavigateToRestore) { Icon(Icons.Rounded.Restore, contentDescription = null) } },
            modifier = Modifier.clickable { onNavigateToRestore() }
        )
        ListItem(
            headlineContent = { Text("利用修了者の完全抹消", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("「利用終了」の方のデータを個別に選択して物理削除します") },
            trailingContent = { 
                IconButton(onClick = onEraseClick, enabled = endedUserCount > 0) { 
                    Icon(
                        Icons.Rounded.DeleteForever, 
                        contentDescription = null, 
                        tint = if (endedUserCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    ) 
                } 
            },
            modifier = Modifier.clickable(enabled = endedUserCount > 0) { onEraseClick() }
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
    onImportClick: () -> Unit
) {
    SettingsSection(title = "データ管理") {
        ListItem(
            headlineContent = { Text("バックアップにパスワードを設定") },
            supportingContent = { 
                Column {
                    Text("Zipファイルを暗号化して保護します")
                    if (!isBackupPasswordEnabled) {
                        Text(
                            text = "※OFFの場合、別の端末では復元できない可能性があります",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            trailingContent = { Switch(checked = isBackupPasswordEnabled, onCheckedChange = onBackupPasswordEnabledChange, modifier = Modifier.testTag("Settings_BackupPasswordSwitch")) }
        )
        if (isBackupPasswordEnabled) {
            AppTextField(
                value = backupPassword,
                onValueChange = onBackupPasswordChange,
                type = AppTextFieldType.PASSWORD,
                label = { Text("デフォルトのパスワード") },
                placeholder = { Text("6桁以上の数字を推奨") },
                supportingText = { 
                    if (!isPasswordValid && backupPassword.isNotEmpty()) 
                        Text("6文字以上で入力してください", color = MaterialTheme.colorScheme.error) 
                    else Text("バックアップ作成時に使用されます") 
                },
                isError = !isPasswordValid && backupPassword.isNotEmpty(),
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
            headlineContent = { Text("データのバックアップ (保存)") },
            supportingContent = { Text("全データと写真をZip書き出しします") },
            trailingContent = { 
                IconButton(onClick = onExportClick, enabled = canExport, modifier = Modifier.testTag("Settings_BackupButton")) { 
                    Icon(Icons.Rounded.Output, contentDescription = null, tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) 
                } 
            },
            modifier = Modifier.clickable(enabled = canExport) { onExportClick() }
        )
        ListItem(
            headlineContent = { Text("データの復元 (読込)") },
            supportingContent = { Text("バックアップからデータを読み込みます") },
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
    onTimeoutClick: () -> Unit
) {
    SettingsSection(title = "セキュリティ") {
        ListItem(
            headlineContent = { Text("アプリのロック") },
            supportingContent = { Text("起動時・復帰時に認証を求めます") },
            trailingContent = { Switch(checked = isBiometricEnabled, onCheckedChange = onBiometricEnabledChange, modifier = Modifier.testTag("Settings_BiometricSwitch")) }
        )
        val timeoutLabel = when (lockTimeoutMinutes) { 0 -> "即時"; -1 -> "ロックしない"; else -> "${lockTimeoutMinutes}分" }
        ListItem(
            headlineContent = { Text("再ロックまでの時間", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
            supportingContent = { Text("指定時間が経過するとロックがかかります", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
            trailingContent = { Text(text = timeoutLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
            modifier = Modifier.clickable(enabled = isBiometricEnabled) { onTimeoutClick() }.testTag("Settings_TimeoutRow")
        )
        Text(text = "※画面消灯設定を短くするとより安全です", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun ThemeSection(
    themeSetting: ThemeSetting,
    onThemeClick: () -> Unit
) {
    SettingsSection(title = "テーマ") {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onThemeClick() }.testTag("Settings_ThemeRow")) {
            OutlinedTextField(
                value = themeSetting.label,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("配色とモード") },
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
        Text(text = "※ ${themeSetting.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun OtherSection(
    onVersionClick: () -> Unit
) {
    SettingsSection(title = "その他") {
        ListItem(
            headlineContent = { Text("バージョン情報") },
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
    onRotateLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit
) {
    val context = LocalContext.current
    SettingsSection(title = "管理者向けツール", modifier = Modifier.testTag("Settings_DevSection")) {
        Text(text = "※ 操作ログの管理を行います。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        
        val retentionLabel = when (auditLogRetentionDays) {
            0 -> "残さない"
            7 -> "1週間"
            14 -> "2週間"
            30 -> "1ヶ月"
            90 -> "3ヶ月"
            180 -> "半年"
            365 -> "1年"
            else -> "${auditLogRetentionDays}日間"
        }
        
        ListItem(
            headlineContent = { Text(context.getString(R.string.audit_log_label_retention)) },
            supportingContent = { Text(context.getString(R.string.audit_log_retention_desc)) },
            trailingContent = { Text(text = retentionLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onRetentionClick() }
        )
        
        ListItem(
            headlineContent = { Text(context.getString(R.string.settings_btn_view_audit_logs)) },
            supportingContent = { Text("現在の記録件数: $auditLogCount 件") },
            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
            modifier = Modifier.clickable { onViewLogsClick() }.testTag("Settings_AuditLogButton")
        )

        ListItem(
            headlineContent = { Text(context.getString(R.string.settings_btn_rotate_logs)) },
            supportingContent = { Text("即座に古いログを消去して整理します") },
            leadingContent = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
            modifier = Modifier.clickable { onRotateLogsClick() }
        )
        
        ListItem(
            headlineContent = { Text(context.getString(R.string.audit_log_clear_confirm_title), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("全ての操作ログを物理削除します") },
            leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onClearLogsClick() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

        Text(text = "※ データベースの状態チェックと修復を行います。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("データベース整合性チェック") },
            supportingContent = { Text("孤立したデータの検出とレポート作成") },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null) },
            modifier = Modifier.clickable { onCheckIntegrity() }.testTag("Settings_IntegrityCheckButton")
        )

        ListItem(
            headlineContent = { Text("[テスト] 不整合データを挿入") },
            supportingContent = { Text("検証用の孤立レコード(バイタル)を1件作成します") },
            leadingContent = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onInsertTestInconsistency() }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        
        Text(text = "※ 全データと写真が完全に消去されます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("■要注意■ 全データ消去", color = MaterialTheme.colorScheme.error) },
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
            onRotateLogsClick = {},
            onClearLogsClick = {},
            isDeveloperModeEnabled = true,
            isProcessing = false,
            processingProgress = 0,
            onBack = {}
        )
    }
}
