package jp.mydns.fujiwara.carememo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToRestore: () -> Unit,
    onBack: () -> Unit
) {
    val isMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val lockTimeoutMinutes by viewModel.lockTimeoutMinutes.collectAsState()
    val persistedRecorderName by viewModel.defaultRecorderName.collectAsState()
    val isBackupPasswordEnabled by viewModel.isBackupPasswordEnabled.collectAsState()
    val backupPassword by viewModel.backupPassword.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val endedUserList by viewModel.deletedUserList.collectAsState()

    val context = LocalContext.current

    // 入力バグ対策用のローカル状態
    var localRecorderName by remember { mutableStateOf(persistedRecorderName) }
    var localBackupPassword by remember { mutableStateOf(backupPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // パスワードのバリデーション (6文字以上)
    val isPasswordValid = localBackupPassword.length >= 6

    // ViewModel側の値が外部から変わった場合のみ同期
    LaunchedEffect(persistedRecorderName) {
        if (localRecorderName != persistedRecorderName) {
            localRecorderName = persistedRecorderName
        }
    }

    LaunchedEffect(backupPassword) {
        if (localBackupPassword != backupPassword) {
            localBackupPassword = backupPassword
        }
    }

    // ダイアログ管理用状態
    var showImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showLegacyFolderUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showDevClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputPasswordForImport by remember { mutableStateOf("") }

    // 通知ダイアログ用の共通状態
    var dialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視
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
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> {
                    showPasswordInputDialog = true
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    dialogTitle = "通知"
                    dialogMessage = event.message
                }
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.SaveSuccess -> {
                    // 設定画面では特に何もしない
                }
            }
        }
    }

    // ファイル・フォルダ選択ランチャー
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportData(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportUri = it } }

    val legacyFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { showLegacyFolderUri = it } }

    // 各種ダイアログ表示
    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogMessage = null; dialogTitle = null },
            title = { dialogTitle?.let { Text(it) } },
            text = { Text(dialogMessage!!) },
            confirmButton = { TextButton(onClick = { dialogMessage = null; dialogTitle = null }) { Text("OK") } }
        )
    }

    if (showImportUri != null) {
        AlertDialog(onDismissRequest = { showImportUri = null }, title = { Text("データの復元") }, text = { Text("現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？") }, confirmButton = { Button(onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }) { Text("復元を実行") } }, dismissButton = { TextButton(onClick = { showImportUri = null }) { Text("キャンセル") } })
    }


    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("個人情報の完全抹消", color = MaterialTheme.colorScheme.error) },
            text = { Text("現在「利用終了」となっている ${endedUserList.size} 名分のデータを完全に抹消します。この操作を行うと記録は二度と復旧できません。よろしいですか？", color = MaterialTheme.colorScheme.error) },
            confirmButton = { Button(onClick = { viewModel.deleteEndedPersons(); showEraseConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("対象者 (${endedUserList.size}名) を抹消する") } },
            dismissButton = { TextButton(onClick = { showEraseConfirm = false }) { Text("キャンセル") } }
        )
    }

    if (showDevClearConfirm) {
        AlertDialog(onDismissRequest = { showDevClearConfirm = false }, title = { Text("(開発用) 全データ消去", color = MaterialTheme.colorScheme.error) }, text = { Text("データベース内のすべてのデータおよび保存された写真を物理削除します。この操作は取り消せません。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.clearAllData(context); showDevClearConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("実行する") } }, dismissButton = { TextButton(onClick = { showDevClearConfirm = false }) { Text("キャンセル") } })
    }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text("バージョン情報") },
            text = { Text("CareMemo\nバージョン ${BuildConfig.VERSION_NAME}\n\n(C) 2025-2026 pirotty.galaxy") },
            confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text("閉じる") } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text("ヘルプ") }, text = { Text("・利用者一覧から利用者を選択して記録を行います。\n・利用を終了した方は「利用者管理」から復帰できます。\n・データは定期的にバックアップすることをお勧めします。") }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("閉じる") } })
    }

    if (showTimeoutDialog) {
        val options = listOf(
            0 to "即時",
            1 to "1分",
            5 to "5分",
            10 to "10分",
            30 to "30分",
            -1 to "ロックしない"
        )
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("再ロックまでの時間") },
            text = {
                Column {
                    options.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLockTimeoutMinutes(minutes)
                                    showTimeoutDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = lockTimeoutMinutes == minutes,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showPasswordInputDialog) {
        var isInputPasswordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPasswordInputDialog = false },
            title = { Text("パスワードの入力") },
            text = {
                Column {
                    Text("このバックアップファイルはパスワードで保護されています。設定されたパスワードを入力してください。")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPasswordForImport,
                        onValueChange = { inputPasswordForImport = it },
                        label = { Text("パスワード") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (isInputPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isInputPasswordVisible = !isInputPasswordVisible }) {
                                Icon(
                                    imageVector = if (isInputPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = if (isInputPasswordVisible) "非表示" else "表示"
                                )
                            }
                        }
                    )
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
                ) { Text("実行") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordInputDialog = false; inputPasswordForImport = "" }) { Text("キャンセル") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定・管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. 表示・記録設定 ---
            SettingsSection(title = "表示・記録設定") {
                ListItem(
                    headlineContent = { Text("氏名の伏せ字表示") },
                    supportingContent = { Text("一覧などの画面で氏名の一部を「○」で表示します") },
                    trailingContent = {
                        Switch(
                            checked = isMaskingEnabled,
                            onCheckedChange = { viewModel.setNameMaskingEnabled(it) }
                        )
                    }
                )
                OutlinedTextField(
                    value = localRecorderName,
                    onValueChange = {
                        localRecorderName = it
                        viewModel.setDefaultRecorderName(it)
                    },
                    label = { Text("記録者の名前(デフォルト)") },
                    placeholder = { Text("例: 山田") },
                    supportingText = { Text("所見メモ作成時に自動的に入力されます") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- 2. 利用者管理 ---
            SettingsSection(title = "利用者管理") {
                ListItem(
                    headlineContent = { Text("利用終了者の復帰") },
                    supportingContent = { Text("現在 ${endedUserList.size} 名が利用終了となっています") },
                    trailingContent = {
                        IconButton(onClick = onNavigateToRestore) {
                            Icon(Icons.Rounded.Restore, contentDescription = "復帰画面へ")
                        }
                    },
                    modifier = Modifier.clickable { onNavigateToRestore() }
                )
                ListItem(
                    headlineContent = { Text("利用終了者のデータを完全抹消", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("「利用終了」となっている方のデータをDBから物理削除します") },
                    trailingContent = {
                        IconButton(onClick = { if (endedUserList.isNotEmpty()) showEraseConfirm = true }, enabled = endedUserList.isNotEmpty()) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = "完全抹消", tint = if (endedUserList.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                        }
                    },
                    modifier = Modifier.clickable(enabled = endedUserList.isNotEmpty()) { showEraseConfirm = true }
                )
            }

            // --- 3. データ管理 ---
            SettingsSection(title = "データ管理") {
                ListItem(
                    headlineContent = { Text("データのバックアップにパスワードを設定") },
                    supportingContent = { Text("Zipファイルを暗号化して保護します") },
                    trailingContent = {
                        Switch(
                            checked = isBackupPasswordEnabled,
                            onCheckedChange = { viewModel.setBackupPasswordEnabled(it) }
                        )
                    }
                )
                if (isBackupPasswordEnabled) {
                    OutlinedTextField(
                        value = localBackupPassword,
                        onValueChange = {
                            localBackupPassword = it
                            if (it.length >= 6 || it.isEmpty()) {
                                viewModel.setBackupPassword(it)
                            }
                        },
                        label = { Text("デフォルトのパスワード") },
                        placeholder = { Text("6桁以上の数字を推奨") },
                        supportingText = {
                            if (!isPasswordValid && localBackupPassword.isNotEmpty()) {
                                Text("パスワードは6文字以上で入力してください", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("バックアップ作成時に使用されます (6文字以上必須)")
                            }
                        },
                        isError = !isPasswordValid && localBackupPassword.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "非表示" else "表示"
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val canExport = !isBackupPasswordEnabled || isPasswordValid
                ListItem(
                    headlineContent = { Text("データのバックアップ (保存)") },
                    supportingContent = { Text("現在の全データと写真をZipファイルとして書き出します") },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                viewModel.setLockBypassEnabled(true)
                                exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip")
                            },
                            enabled = canExport
                        ) {
                            Icon(
                                Icons.Rounded.Output,
                                contentDescription = "バックアップ",
                                tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = canExport) {
                        viewModel.setLockBypassEnabled(true)
                        exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.zip")
                    }
                )
                ListItem(
                    headlineContent = { Text("データの復元 (読込)") },
                    supportingContent = { Text("バックアップファイル(ZipまたはJSON)からデータを読み込みます") },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.setLockBypassEnabled(true)
                            importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = "復元")
                        }
                    },
                    modifier = Modifier.clickable {
                        viewModel.setLockBypassEnabled(true)
                        importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                    }
                )
                
            }

            // --- 4. セキュリティ ---
            SettingsSection(title = "セキュリティ") {
                ListItem(
                    headlineContent = { Text("アプリのロック") },
                    supportingContent = { Text("起動時・復帰時に認証を求めます") },
                    trailingContent = {
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(context, it) }
                        )
                    }
                )

                val timeoutLabel = when (lockTimeoutMinutes) {
                    0 -> "即時"
                    -1 -> "ロックしない"
                    else -> "${lockTimeoutMinutes}分"
                }
                val contentAlpha = if (isBiometricEnabled) 1.0f else 0.38f
                CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha)) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                "再ロックまでの時間",
                                color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                "アプリを閉じてから指定時間が経過するとロックがかかります",
                                color = if (isBiometricEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                            ) 
                        },
                        trailingContent = {
                            Text(
                                text = timeoutLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.clickable(enabled = isBiometricEnabled) { showTimeoutDialog = true }
                    )
                }
                Text(
                    text = "※端末の画面消灯設定を短くするとより安全です",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- 5. その他 ---
            SettingsSection(title = "その他") {
                ListItem(
                    headlineContent = { Text("ヘルプ") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null) },
                    modifier = Modifier.clickable { showHelpDialog = true }
                )
                ListItem(
                    headlineContent = { Text("バージョン情報") },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    modifier = Modifier.clickable { showVersionDialog = true }
                )
            }

            // --- 5. 全データ削除 ---
            SettingsSection(title = "リセット") {
                Text(
                    text = "※ 利用者データと撮影した写真が全て消去されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("■要注意■ データと写真の全消去", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showDevClearConfirm = true }
                )
                Text(
                    text = "※ 十分注意して実行してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}
