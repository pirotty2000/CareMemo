package jp.mydns.fujiwara.carememo.ui.screens

/**
 * Screen : DeletedUserListScreen
 *
 * 【画面名】
 * 利用終了者一覧画面
 *
 * 【役割】
 * 「サービス終了（論理削除）」とされた利用者のリストを表示し、
 * 誤って削除した利用者の復元や、不要になったデータの完全抹消（物理削除）を行う管理画面。
 *
 * 【主な機能】
 * ・一覧表示：名前（マスキング対応）、ふりがな、備考、およびサービス終了日時の表示。
 * ・利用者復元：選択した利用者をアクティブな状態に戻し、再度記録可能にする。
 * ・完全抹消：利用者のすべての記録（写真を含む）をデータベースから完全に削除する（警告ダイアログ付き）。
 * ・一括選択：複数の利用者を選択して一括で復元または抹消する機能。
 *
 * 【遷移】
 * ← SettingsScreen（戻るボタン）
 *
 * 【使用するViewModel】
 * PersonListViewModel
 *
 * 【備考】
 * 重要なデータの抹消を扱うため、物理削除実行前には必ず確認ダイアログを表示する。
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.viewmodel.ArchivedPersonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedUserListScreen(
    viewModel: ArchivedPersonViewModel,
    onBack: () -> Unit,
) {
    val endedUsers by viewModel.archivedPersonList.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val selectedUserIds = remember { mutableStateListOf<Int>() }
    
    val snackbarHostState = remember { SnackbarHostState() }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> { /* ダイアログ等は必要に応じて */ }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("利用終了者の復帰", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (endedUsers.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedUserIds.size == endedUsers.size) {
                                    selectedUserIds.clear()
                                } else {
                                    selectedUserIds.clear()
                                    selectedUserIds.addAll(endedUsers.map { it.id })
                                }
                            }
                        ) {
                            Text(if (selectedUserIds.size == endedUsers.size) "全解除" else "全選択")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedUserIds.isNotEmpty()) {
                BottomAppBar {
                    Button(
                        onClick = {
                            selectedUserIds.forEach { id ->
                                endedUsers.find { it.id == id }?.let { 
                                    viewModel.restorePerson(it)
                                }
                            }
                            selectedUserIds.clear()
                            // 複数の復帰時は一覧に戻るのが自然
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("選択した利用者 (${selectedUserIds.size}名) を復帰させる")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (endedUsers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("利用終了した利用者はいません", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                items(endedUsers, key = { it.id }) { user ->
                    ListItem(
                        headlineContent = { Text(user.getMaskedName(isNameMaskingEnabled)) },
                        supportingContent = { Text(user.getMaskedFurigana(isNameMaskingEnabled)) },
                        leadingContent = {
                            Checkbox(
                                checked = selectedUserIds.contains(user.id),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedUserIds.add(user.id)
                                    } else {
                                        selectedUserIds.remove(user.id)
                                    }
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
