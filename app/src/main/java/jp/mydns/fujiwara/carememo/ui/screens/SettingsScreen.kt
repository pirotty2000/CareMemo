package jp.mydns.fujiwara.carememo.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Help
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
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToRestore: () -> Unit,
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

    val context = LocalContext.current

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
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }

    var dialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> showPasswordInputDialog = true
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    dialogTitle = "通知"
                    dialogMessage = event.message
                }
                else -> {}
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.exportData(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { showImportUri = it } }

    if (dialogMessage != null) {
        AlertDialog(onDismissRequest = { dialogMessage = null; dialogTitle = null }, title = { dialogTitle?.let { Text(it) } }, text = { Text(dialogMessage!!) }, confirmButton = { TextButton(onClick = { dialogMessage = null; dialogTitle = null }) { Text("OK") } })
    }

    if (showImportUri != null) {
        AlertDialog(onDismissRequest = { showImportUri = null }, title = { Text("データの復元") }, text = { Text("現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？") }, confirmButton = { Button(onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }) { Text("復元を実行") } }, dismissButton = { TextButton(onClick = { showImportUri = null }) { Text("キャンセル") } })
    }

    if (showEraseConfirm) {
        AlertDialog(onDismissRequest = { showEraseConfirm = false }, title = { Text("個人情報の完全抹消", color = MaterialTheme.colorScheme.error) }, text = { Text("現在「利用終了」となっている ${endedUserList.size} 名分のデータを完全に抹消します。記録は復旧できません。よろしいですか？", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.deleteEndedPersons(); showEraseConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("対象者 (${endedUserList.size}名) を抹消する") } }, dismissButton = { TextButton(onClick = { showEraseConfirm = false }) { Text("キャンセル") } })
    }

    if (showDevClearConfirm) {
        AlertDialog(onDismissRequest = { showDevClearConfirm = false }, title = { Text("(開発用) 全データ消去", color = MaterialTheme.colorScheme.error) }, text = { Text("すべてのデータおよび写真を物理削除します。取り消せません。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.clearAllData(context); showDevClearConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("実行する") } }, dismissButton = { TextButton(onClick = { showDevClearConfirm = false }) { Text("キャンセル") } })
    }

    if (showVersionDialog) {
        AlertDialog(onDismissRequest = { showVersionDialog = false }, title = { Text("バージョン情報") }, text = { Text("CareMemo\nバージョン ${BuildConfig.VERSION_NAME}\n\n(C) 2025-2026 pirotty.galaxy") }, confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text("閉じる") } })
    }

    if (showHelpDialog) {
        AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text("ヘルプ") }, text = { Text("・利用者一覧から利用者を選択して記録を行います。\n・利用を終了した方は「利用者管理」から復帰できます。\n・データは定期的にバックアップすることをお勧めします。") }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("閉じる") } })
    }

    if (showTimeoutDialog) {
        val options = listOf(0 to "即時", 1 to "1分", 5 to "5分", 10 to "10分", 30 to "30分", -1 to "ロックしない")
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("再ロックまでの時間") },
            text = {
                val scrollState = rememberScrollState()
                val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
                val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
                Box {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        options.forEach { (minutes, label) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setLockTimeoutMinutes(minutes); showTimeoutDialog = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = lockTimeoutMinutes == minutes, onClick = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(label)
                            }
                        }
                    }
                    if (canScrollUp) {
                        Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (canScrollDown) {
                        Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape), tint = MaterialTheme.colorScheme.primary)
                    }
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
                val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
                val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
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
                    if (canScrollUp) {
                        Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (canScrollDown) {
                        Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("キャンセル") } }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showPasswordInputDialog = false }, title = { Text("パスワードの入力") }, text = { Column { Text("このファイルはパスワード保護されています。入力してください。"); Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = inputPasswordForImport, onValueChange = { inputPasswordForImport = it }, label = { Text("パスワード") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (isInputPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isInputPasswordVisible = !isInputPasswordVisible }) { Icon(imageVector = if (isInputPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null) } }) } }, confirmButton = { Button(onClick = { viewModel.importData(context, android.net.Uri.EMPTY, inputPasswordForImport); showPasswordInputDialog = false; inputPasswordForImport = "" }, enabled = inputPasswordForImport.isNotEmpty()) { Text("実行") } }, dismissButton = { TextButton(onClick = { showPasswordInputDialog = false; inputPasswordForImport = "" }) { Text("キャンセル") } })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定・管理", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer, navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // --- 1. 表示・記録設定 ---
            SettingsSection(title = "表示・記録設定") {
                ListItem(headlineContent = { Text("氏名の伏せ字表示") }, supportingContent = { Text("一覧などの画面で氏名の一部を「○」で表示します") }, trailingContent = { Switch(checked = isMaskingEnabled, onCheckedChange = { viewModel.setNameMaskingEnabled(it) }) })
                OutlinedTextField(value = localRecorderName, onValueChange = { localRecorderName = it; viewModel.setDefaultRecorderName(it) }, label = { Text("記録者の名前(デフォルト)") }, placeholder = { Text("例: 山田") }, supportingText = { Text("所見メモ作成時に自動入力されます") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- 2. 利用者管理 ---
            SettingsSection(title = "利用者管理") {
                ListItem(headlineContent = { Text("利用終了者の復帰") }, supportingContent = { Text("現在 ${endedUserList.size} 名が利用終了となっています") }, trailingContent = { IconButton(onClick = onNavigateToRestore) { Icon(Icons.Rounded.Restore, contentDescription = null) } }, modifier = Modifier.clickable { onNavigateToRestore() })
                ListItem(headlineContent = { Text("利用終了者のデータを完全抹消", color = MaterialTheme.colorScheme.error) }, supportingContent = { Text("「利用終了」の方のデータを物理削除します") }, trailingContent = { IconButton(onClick = { if (endedUserList.isNotEmpty()) showEraseConfirm = true }, enabled = endedUserList.isNotEmpty()) { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = if (endedUserList.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline) } }, modifier = Modifier.clickable(enabled = endedUserList.isNotEmpty()) { showEraseConfirm = true })
            }

            // --- 3. データ管理 ---
            SettingsSection(title = "データ管理") {
                ListItem(headlineContent = { Text("バックアップにパスワードを設定") }, supportingContent = { Text("Zipファイルを暗号化して保護します") }, trailingContent = { Switch(checked = isBackupPasswordEnabled, onCheckedChange = { viewModel.setBackupPasswordEnabled(it) }) })
                if (isBackupPasswordEnabled) {
                    OutlinedTextField(value = localBackupPassword, onValueChange = { localBackupPassword = it; if (it.length >= 6 || it.isEmpty()) viewModel.setBackupPassword(it) }, label = { Text("デフォルトのパスワード") }, placeholder = { Text("6桁以上の数字を推奨") }, supportingText = { if (!isPasswordValid && localBackupPassword.isNotEmpty()) Text("6文字以上で入力してください", color = MaterialTheme.colorScheme.error) else Text("バックアップ作成時に使用されます") }, isError = !isPasswordValid && localBackupPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) { Icon(imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null) } })
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val canExport = !isBackupPasswordEnabled || isPasswordValid
                ListItem(headlineContent = { Text("データのバックアップ (保存)") }, supportingContent = { Text("全データと写真をZip書き出しします") }, trailingContent = { IconButton(onClick = { viewModel.setLockBypassEnabled(true); exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip") }, enabled = canExport) { Icon(Icons.Rounded.Output, contentDescription = null, tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) } }, modifier = Modifier.clickable(enabled = canExport) { viewModel.setLockBypassEnabled(true); exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip") })
                ListItem(headlineContent = { Text("データの復元 (読込)") }, supportingContent = { Text("バックアップからデータを読み込みます") }, trailingContent = { IconButton(onClick = { viewModel.setLockBypassEnabled(true); importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }) { Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null) } }, modifier = Modifier.clickable { viewModel.setLockBypassEnabled(true); importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) })
            }

            // --- 4. セキュリティ ---
            SettingsSection(title = "セキュリティ") {
                ListItem(headlineContent = { Text("アプリのロック") }, supportingContent = { Text("起動時・復帰時に認証を求めます") }, trailingContent = { Switch(checked = isBiometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(context, it) }) })
                val timeoutLabel = when (lockTimeoutMinutes) { 0 -> "即時"; -1 -> "ロックしない"; else -> "${lockTimeoutMinutes}分" }
                ListItem(headlineContent = { Text("再ロックまでの時間", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) }, supportingContent = { Text("指定時間が経過するとロックがかかります", color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) }, trailingContent = { Text(text = timeoutLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }, modifier = Modifier.clickable(enabled = isBiometricEnabled) { showTimeoutDialog = true })
                Text(text = "※画面消灯設定を短くするとより安全です", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // --- 5. テーマ ---
            SettingsSection(title = "テーマ") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { showThemeDialog = true }) {
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

            // --- 6. その他 ---
            SettingsSection(title = "その他") {
                ListItem(headlineContent = { Text("ヘルプ") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null) }, modifier = Modifier.clickable { showHelpDialog = true })
                ListItem(headlineContent = { Text("バージョン情報") }, leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) }, modifier = Modifier.clickable { showVersionDialog = true })
            }

            // --- 7. リセット ---
            SettingsSection(title = "リセット") {
                Text(text = "※ 全データと写真が消去されます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ListItem(headlineContent = { Text("■要注意■ 全データ消去", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Rounded.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { showDevClearConfirm = true })
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(content = content) }
    }
}
