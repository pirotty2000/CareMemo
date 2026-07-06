package jp.mydns.fujiwara.carememo.ui.screens.settings

/**
 * Screen : SettingsScreen
 *
 * 【画面名】
 * 設定・管理画面
 *
 * 【役割】
 * アプリ全体の動作設定、利用者のデータ管理（復元・抹消）、セキュリティ設定、
 * およびバックアップ・リストア等のシステムメンテナンス機能を提供する画面。
 *
 * 【主な機能】
 * ・表示・記録設定：氏名の伏せ字表示（マスキング）の切替、デフォルト記録者名の設定。
 * ・利用者管理：利用終了（論理削除）した利用者の復帰操作、およびデータの完全抹消（物理削除）。
 * ・データ管理：全データと写真のバックアップ（Zip形式）および復元（インポート）機能。
 * ・セキュリティ：生体認証によるアプリロックの制御、再ロック待機時間の設定、バックアップのパスワード保護。
 * ・テーマ設定：アプリ全体の配色モード（ライト/ダーク/システム連携等）の切り替え。
 * ・システム情報：操作ヘルプの閲覧、アプリのバージョン情報確認、および全データのリセット機能。
 *
 * 【遷移】
 * ← MainScreen（戻るボタン）
 * → UserRestoreScreen（「利用終了者の復帰」選択時）
 *
 * 【使用するViewModel】
 * SettingsViewModel
 *
 * 【備考】
 * ストレージアクセスフレームワーク（SAF）を利用したファイル入出力や、生体認証（BiometricPrompt）の制御、
 * 外部アプリ連携時のロックバイパス管理など、アプリの基盤となる重要な管理ロジックを担当する。
 */

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.ui.components.base.DeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.InfoDialog
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToArchiveManagement: (DeleteOrRestorePersonViewModel.OperationMode) -> Unit,
    onRequireAuthentication: (onSuccess: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val isMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val lockTimeoutMinutes by viewModel.lockTimeoutMinutes.collectAsState()
    val persistedRecorderName by viewModel.defaultRecorderName.collectAsState()
    val isBackupPasswordEnabled by viewModel.isBackupPasswordEnabled.collectAsState()
    val backupPassword by viewModel.backupPassword.collectAsState()
    val themeSetting by viewModel.themeSetting.collectAsState()
    val endedUserList by viewModel.deletedUserList.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val inconsistencies: List<DatabaseInconsistency> by viewModel.inconsistencies.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var localRecorderName by remember { mutableStateOf(persistedRecorderName) }
    var localBackupPassword by remember { mutableStateOf(backupPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = localBackupPassword.length >= 6

    LaunchedEffect(persistedRecorderName) {
        if (localRecorderName != persistedRecorderName) localRecorderName = persistedRecorderName
    }
    LaunchedEffect(backupPassword) {
        if (localBackupPassword != backupPassword) localBackupPassword = backupPassword
    }

    var showImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showDevClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }

    // 開発者モード（リセット表示用）の状態管理
    var versionTapCount by remember { mutableIntStateOf(0) }
    var isDeveloperModeEnabled by rememberSaveable { mutableStateOf(false) }

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
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> showPasswordInputDialog = true
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.SaveSuccess -> {
                    // 全消去成功時など
                    onBack()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.exportData(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { showImportUri = it } }

    if (dialogMessage != null) {
        InfoDialog(
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
        AlertDialog(
            onDismissRequest = { showImportUri = null },
            title = { Text("データの復元") },
            text = {
                val scrollState = rememberScrollState()
                Box {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        Text("現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？")
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }) {
                    Text("復元を実行")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportUri = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showEraseConfirm) {
        DeleteConfirmDialog(
            onDismiss = { showEraseConfirm = false },
            onDelete = { viewModel.deleteEndedPersons() },
            title = "個人情報の完全抹消",
            message = "現在「利用終了」となっている ${endedUserList.size} 名分のデータを完全に抹消します。記録は復旧できません。よろしいですか？",
            confirmButtonText = "対象者 (${endedUserList.size}名) を抹消する"
        )
    }

    if (showDevClearConfirm) {
        DeleteConfirmDialog(
            onDismiss = { showDevClearConfirm = false },
            onDelete = { viewModel.clearAllData(context) },
            title = "(管理者) 全データ消去",
            message = "全てのデータおよび写真を物理削除します。取り消せません。",
            confirmButtonText = "実行する"
        )
    }

    // データベース不整合レポート・ダイアログ
    if (inconsistencies.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearInconsistencyResults() },
            title = { Text("データベース不整合レポート") },
            text = {
                val scrollState = rememberScrollState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("以下の ${inconsistencies.size} 件の孤立したデータが見つかりました：", style = MaterialTheme.typography.bodySmall)
                    
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        Column(modifier = Modifier.verticalScroll(scrollState)) {
                            inconsistencies.forEach { inc ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(text = inc.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = buildString {
                                                append("元利用者ID: ${inc.personId ?: "不明"}")
                                                append(" | テーブル: ${inc.tableName}")
                                            }, 
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        inc.recordTime?.let { time ->
                                            Text(text = "記録日時: ${DateTimeUtils.formatRecordTime(time)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                    }
                    Text("これらは親データ（利用者）が存在しない無効な記録です。クリーンアップを実行して削除することをお勧めします。", 
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.fixInconsistencies() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("クリーンアップ実行") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearInconsistencyResults() }) { Text("閉じる") }
            }
        )
    }

    if (showVersionDialog) {
        InfoDialog(
            title = "バージョン情報",
            message = "CareMemo\nバージョン ${BuildConfig.VERSION_NAME}\n\n(C) 2025-2026 pirotty.galaxy",
            onDismiss = { showVersionDialog = false }
        )
    }

    if (showTimeoutDialog) {
        val options = listOf(0 to "即時", 1 to "1分", 5 to "5分", 10 to "10分", 30 to "30分", -1 to "ロックしない")
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("再ロックまでの時間") },
            text = {
                val scrollState = rememberScrollState()
                Box {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        options.forEach { (minutes, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if ((minutes == -1) && (lockTimeoutMinutes != -1)) {
                                            // 「ロックしない」を新しく選ぶ場合は認証を求める
                                            if (viewModel.canAuthenticate(context)) {
                                                onRequireAuthentication {
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
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = { TextButton(onClick = { showTimeoutDialog = false }) { Text("キャンセル") } }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("配色とモードの選択") },
            text = {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        ThemeSetting.entries.forEach { selectionOption ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setThemeSetting(selectionOption); showThemeDialog = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = themeSetting == selectionOption, onClick = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(selectionOption.label)
                            }
                        }
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("キャンセル") } }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPasswordInputDialog = false },
            title = { Text("パスワードの入力") },
            text = {
                val scrollState = rememberScrollState()
                Box {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
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
                                    Icon(imageVector = if (isInputPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null)
                                }
                            }
                        )
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importData(context, android.net.Uri.EMPTY, inputPasswordForImport)
                        showPasswordInputDialog = false
                        inputPasswordForImport = ""
                    },
                    enabled = inputPasswordForImport.isNotEmpty()
                ) {
                    Text("実行")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordInputDialog = false; inputPasswordForImport = "" }) {
                    Text("キャンセル")
                }
            }
        )
    }

    SettingsScreenContent(
        snackbarHostState = snackbarHostState,
        isMaskingEnabled = isMaskingEnabled,
        localRecorderName = localRecorderName,
        onMaskingChange = { viewModel.setNameMaskingEnabled(it) },
        onRecorderNameChange = {
            localRecorderName = it
            viewModel.setDefaultRecorderName(it)
        },
        endedUserCount = endedUserList.size,
        onNavigateToRestore = { onNavigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.RESTORE) },
        onEraseClick = { onNavigateToArchiveManagement(DeleteOrRestorePersonViewModel.OperationMode.DELETE) },
        isBackupPasswordEnabled = isBackupPasswordEnabled,
        localBackupPassword = localBackupPassword,
        isPasswordValid = isPasswordValid,
        isPasswordVisible = isPasswordVisible,
        onBackupPasswordEnabledChange = { viewModel.setBackupPasswordEnabled(it) },
        onBackupPasswordChange = {
            localBackupPassword = it
            if (it.length >= 6 || it.isEmpty()) viewModel.setBackupPassword(it)
        },
        onPasswordVisibilityToggle = {
            if (isPasswordVisible) {
                // 非表示にする場合は認証不要
                isPasswordVisible = false
            } else {
                // 表示する場合は、デバイスが認証に対応していれば認証を求める
                if (viewModel.canAuthenticate(context)) {
                    onRequireAuthentication {
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
        onBiometricEnabledChange = { viewModel.setBiometricEnabled(context, it) },
        onTimeoutClick = { showTimeoutDialog = true },
        themeSetting = themeSetting,
        onThemeClick = { showThemeDialog = true },
        onVersionClick = {
            if (!isDeveloperModeEnabled) {
                versionTapCount++
                if (versionTapCount >= 7) {
                    isDeveloperModeEnabled = true
                    scope.launch { snackbarHostState.showSnackbar("管理者向け設定が有効になりました") }
                }
            }
            showVersionDialog = true
        },
        onClearAllClick = {
            // 実行前にまず認証を求める
            if (viewModel.canAuthenticate(context)) {
                onRequireAuthentication {
                    showDevClearConfirm = true
                }
            } else {
                showDevClearConfirm = true
            }
        },
        onCheckIntegrity = { viewModel.checkIntegrity() },
        onInsertTestInconsistency = { viewModel.insertTestInconsistency() },
        isDeveloperModeEnabled = isDeveloperModeEnabled,
        isProcessing = isProcessing,
        processingProgress = processingProgress,
        onBack = onBack
    )
}

/**
 * 設定画面のUIレイアウト本体。
 * ViewModelに依存しないStatelessな設計により、プレビューを可能にしている。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    snackbarHostState: SnackbarHostState,
    isMaskingEnabled: Boolean,
    localRecorderName: String,
    onMaskingChange: (Boolean) -> Unit,
    onRecorderNameChange: (String) -> Unit,
    endedUserCount: Int,
    onNavigateToRestore: () -> Unit,
    onEraseClick: () -> Unit,
    isBackupPasswordEnabled: Boolean,
    localBackupPassword: String,
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") } },
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
                    .navigationBarsPadding() // スクロール最下部がナビゲーションバーに隠れないようにする
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DisplayAndRecordingSection(
                    isMaskingEnabled = isMaskingEnabled,
                    localRecorderName = localRecorderName,
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
                    localBackupPassword = localBackupPassword,
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
                        onInsertTestInconsistency = onInsertTestInconsistency
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }

    if (isProcessing) {
        AlertDialog(
            onDismissRequest = { }, // 処理中は閉じられない
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("処理中...") },
            text = {
                val scrollState = rememberScrollState()
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { processingProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(text = "$processingProgress%")
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = {}
        )
    }
}

/**
 * 表示・記録に関する設定セクション。
 * 氏名のマスキング設定や、記録時に入力されるデフォルトの名前を管理する。
 */
@Composable
private fun DisplayAndRecordingSection(
    isMaskingEnabled: Boolean,
    localRecorderName: String,
    onMaskingChange: (Boolean) -> Unit,
    onRecorderNameChange: (String) -> Unit
) {
    SettingsSection(title = "表示・記録設定") {
        ListItem(
            headlineContent = { Text("氏名の伏せ字表示") },
            supportingContent = { Text("一覧などの画面で氏名の一部を「○」で表示します") },
            trailingContent = { Switch(checked = isMaskingEnabled, onCheckedChange = onMaskingChange) }
        )
        AppTextField(
            value = localRecorderName,
            onValueChange = onRecorderNameChange,
            type = AppTextFieldType.TEXT,
            label = { Text("記録者の名前(デフォルト)") },
            placeholder = { Text("例: 山田") },
            supportingText = { Text("所見メモ作成時に自動入力されます") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 利用者データに関する管理セクション。
 * 論理削除された利用者の復帰や、データの完全抹消操作を管理する。
 */
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

/**
 * データのバックアップと復元に関するセクション。
 * パスワード保護の設定、Zip形式でのエクスポート、およびインポート機能を管理する。
 */
@Composable
private fun DataManagementSection(
    isBackupPasswordEnabled: Boolean,
    localBackupPassword: String,
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
            trailingContent = { Switch(checked = isBackupPasswordEnabled, onCheckedChange = onBackupPasswordEnabledChange) }
        )
        if (isBackupPasswordEnabled) {
            AppTextField(
                value = localBackupPassword,
                onValueChange = onBackupPasswordChange,
                type = AppTextFieldType.PASSWORD,
                label = { Text("デフォルトのパスワード") },
                placeholder = { Text("6桁以上の数字を推奨") },
                supportingText = { 
                    if (!isPasswordValid && localBackupPassword.isNotEmpty()) 
                        Text("6文字以上で入力してください", color = MaterialTheme.colorScheme.error) 
                    else Text("バックアップ作成時に使用されます") 
                },
                isError = !isPasswordValid && localBackupPassword.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                trailingIcon = { 
                    IconButton(onClick = onPasswordVisibilityToggle) { 
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
                IconButton(onClick = onExportClick, enabled = canExport) { 
                    Icon(Icons.Rounded.Output, contentDescription = null, tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) 
                } 
            },
            modifier = Modifier.clickable(enabled = canExport) { onExportClick() }
        )
        ListItem(
            headlineContent = { Text("データの復元 (読込)") },
            supportingContent = { Text("バックアップからデータを読み込みます") },
            trailingContent = { IconButton(onClick = onImportClick) { Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null) } },
            modifier = Modifier.clickable { onImportClick() }
        )
    }
}

/**
 * アプリのセキュリティに関するセクション。
 * 生体認証ロックの有効化や、自動再ロックまでの待機時間を管理する。
 */
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
            trailingContent = { Switch(checked = isBiometricEnabled, onCheckedChange = onBiometricEnabledChange) }
        )
        val timeoutLabel = when (lockTimeoutMinutes) { 0 -> "即時"; -1 -> "ロックしない"; else -> "${lockTimeoutMinutes}分" }
        ListItem(
            headlineContent = { Text("再ロックまでの時間", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
            supportingContent = { Text("指定時間が経過するとロックがかかります", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
            trailingContent = { Text(text = timeoutLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
            modifier = Modifier.clickable(enabled = isBiometricEnabled) { onTimeoutClick() }
        )
        Text(text = "※画面消灯設定を短くするとより安全です", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/**
 * アプリの表示テーマに関するセクション。
 * ダークモードやライトモード、システム連携などの配色設定を管理する。
 */
@Composable
private fun ThemeSection(
    themeSetting: ThemeSetting,
    onThemeClick: () -> Unit
) {
    SettingsSection(title = "テーマ") {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onThemeClick() }) {
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

/**
 * 操作ヘルプやアプリ情報に関するセクション。
 */
@Composable
private fun OtherSection(
    onVersionClick: () -> Unit
) {
    SettingsSection(title = "その他") {
        ListItem(
            headlineContent = { Text("バージョン情報") },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier.clickable { onVersionClick() }
        )
    }
}

/**
 * アプリデータのリセットに関するセクション。
 */
@Composable
private fun ResetSection(
    onClearAllClick: () -> Unit,
    onCheckIntegrity: () -> Unit,
    onInsertTestInconsistency: () -> Unit
) {
    SettingsSection(title = "管理者向けツール") {
        Text(text = "※ データベースの状態チェックと修復を行います。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("データベース整合性チェック") },
            supportingContent = { Text("孤立したデータの検出とレポート作成") },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null) },
            modifier = Modifier.clickable { onCheckIntegrity() }
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
            modifier = Modifier.clickable { onClearAllClick() }
        )
    }
}

/**
 * 設定画面の各セクションを共通のスタイル（背景、タイトル、余白）で描画するための枠組み。
 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            localRecorderName = "記録者名",
            onMaskingChange = {},
            onRecorderNameChange = {},
            endedUserCount = 2,
            onNavigateToRestore = {},
            onEraseClick = {},
            isBackupPasswordEnabled = true,
            localBackupPassword = "password",
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
            isDeveloperModeEnabled = true,
            isProcessing = false,
            processingProgress = 0,
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenProcessingPreview() {
    CareMemoTheme {
        SettingsScreenContent(
            snackbarHostState = remember { SnackbarHostState() },
            isMaskingEnabled = false,
            localRecorderName = "記録者名",
            onMaskingChange = {},
            onRecorderNameChange = {},
            endedUserCount = 2,
            onNavigateToRestore = {},
            onEraseClick = {},
            isBackupPasswordEnabled = true,
            localBackupPassword = "password",
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
            isDeveloperModeEnabled = true,
            isProcessing = true,
            processingProgress = 45,
            onBack = {}
        )
    }
}
