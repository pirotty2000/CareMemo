package jp.mydns.fujiwara.carememo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PersonListViewModel,
    onNavigateToRestore: () -> Unit,
    onBack: () -> Unit
) {
    val isMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val endedUserList by viewModel.deletedUserList.collectAsState()
    val errorMsg by viewModel.errorFlow.collectAsState()
    val infoMsg by viewModel.infoFlow.collectAsState()

    val context = LocalContext.current

    // ダイアログ管理用状態
    var showImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showLegacyFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by remember { mutableStateOf(false) }
    var showDevClearConfirm by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // ファイル・フォルダ選択ランチャー
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportData(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportUri = it } }

    val legacyFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { showLegacyFolderUri = it } }

    // 各種ダイアログ表示
    if (errorMsg != null) {
        AlertDialog(onDismissRequest = { viewModel.clearError() }, title = { Text("エラー") }, text = { Text(errorMsg!!) }, confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } })
    }
    if (infoMsg != null) {
        AlertDialog(onDismissRequest = { viewModel.clearInfo() }, title = { Text("通知") }, text = { Text(infoMsg!!) }, confirmButton = { TextButton(onClick = { viewModel.clearInfo() }) { Text("OK") } })
    }

    if (showImportUri != null) {
        AlertDialog(onDismissRequest = { showImportUri = null }, title = { Text("データの復元") }, text = { Text("現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？") }, confirmButton = { Button(onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }) { Text("復元を実行") } }, dismissButton = { TextButton(onClick = { showImportUri = null }) { Text("キャンセル") } })
    }

    if (showLegacyFolderUri != null) {
        AlertDialog(onDismissRequest = { showLegacyFolderUri = null }, title = { Text("旧アプリデータの引き継ぎ", color = MaterialTheme.colorScheme.error) }, text = { Text("現在のデータはすべて削除され、選択したフォルダ内の旧アプリ用JSONデータで初期化されます。一度きりの移行処理として実行してください。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.importLegacyDataFromFolder(context, showLegacyFolderUri!!); showLegacyFolderUri = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("削除して引き継ぐ") } }, dismissButton = { TextButton(onClick = { showLegacyFolderUri = null }) { Text("キャンセル") } })
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
        AlertDialog(onDismissRequest = { showDevClearConfirm = false }, title = { Text("(開発用) 全データ消去", color = MaterialTheme.colorScheme.error) }, text = { Text("データベース内のすべてのデータを物理削除します。この操作は取り消せません。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.clearAllData(); showDevClearConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("実行する") } }, dismissButton = { TextButton(onClick = { showDevClearConfirm = false }) { Text("キャンセル") } })
    }

    if (showVersionDialog) {
        AlertDialog(onDismissRequest = { showVersionDialog = false }, title = { Text("バージョン情報") }, text = { Text("CareMemo\nバージョン 1.0.0\n\n(C) 2025 pirotty.galaxy") }, confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text("閉じる") } })
    }

    if (showHelpDialog) {
        AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text("ヘルプ") }, text = { Text("・利用者一覧から利用者を選択して記録を行います。\n・利用を終了した方は「利用者管理」から復帰できます。\n・データは定期的にバックアップすることをお勧めします。") }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("閉じる") } })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定・管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
                    value = defaultRecorderName,
                    onValueChange = { viewModel.setDefaultRecorderName(it) },
                    label = { Text("記録者の名前(デフォルト)") },
                    placeholder = { Text("例: 山田") },
                    supportingText = { Text("所見メモ作成時に自動的に入力されます") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
            }

            // --- 2. 利用者管理 ---
            SettingsSection(title = "利用者管理") {
                ListItem(
                    headlineContent = { Text("利用終了者の復帰") },
                    supportingContent = { Text("現在 ${endedUserList.size} 名が利用終了となっています") },
                    trailingContent = {
                        IconButton(onClick = onNavigateToRestore) {
                            Icon(Icons.Default.Restore, contentDescription = "復帰画面へ")
                        }
                    },
                    modifier = Modifier.clickable { onNavigateToRestore() }
                )
                ListItem(
                    headlineContent = { Text("利用終了者のデータを完全抹消", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("「利用終了」となっている方のデータをDBから物理削除します") },
                    modifier = Modifier.clickable(enabled = endedUserList.isNotEmpty()) { showEraseConfirm = true }
                )
            }

            // --- 3. データ管理（バックアップ） ---
            SettingsSection(title = "データ管理") {
                ListItem(
                    headlineContent = { Text("データのバックアップ (保存)") },
                    supportingContent = { Text("現在の全データをJSONファイルとして書き出します") },
                    modifier = Modifier.clickable { exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.json") }
                )
                ListItem(
                    headlineContent = { Text("データの復元 (読込)") },
                    supportingContent = { Text("バックアップファイルからデータを読み込みます（現在のデータは消去されます）") },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) }
                )
                
                // データベースが空の時のみ表示（旧アプリ移行）
                if (userList.isEmpty() && endedUserList.isEmpty()) {
                    ListItem(
                        headlineContent = { Text("旧アプリデータの引き継ぎ", color = MaterialTheme.colorScheme.secondary) },
                        supportingContent = { Text("旧アプリのJSONフォルダを選択してデータを移行します") },
                        modifier = Modifier.clickable { legacyFolderLauncher.launch(null) }
                    )
                }
            }

            // --- 4. その他 ---
            SettingsSection(title = "その他") {
                ListItem(
                    headlineContent = { Text("ヘルプ") },
                    modifier = Modifier.clickable { showHelpDialog = true }
                )
                ListItem(
                    headlineContent = { Text("バージョン情報") },
                    modifier = Modifier.clickable { showVersionDialog = true }
                )
            }

            // --- 5. 開発者向け ---
            SettingsSection(title = "開発者オプション") {
                ListItem(
                    headlineContent = { Text("(開発用) assetsから初期データを読込") },
                    modifier = Modifier.clickable { viewModel.importLegacyDataFromAssets(context) }
                )
                ListItem(
                    headlineContent = { Text("(開発用) データベースの全消去", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showDevClearConfirm = true }
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
