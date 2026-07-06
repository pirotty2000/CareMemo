package jp.mydns.fujiwara.carememo.ui.screens.settings

/**
 * Screen : DeleteOrRestorePerson
 *
 * 【画面名】
 * 利用者の復帰・完全抹消画面
 *
 * 【役割】
 * アーカイブ（利用終了）された利用者のリストを表示し、
 * モードに応じて「利用者の復帰（論理削除解除）」または「データの完全抹消（物理削除）」を行う。
 *
 * 【主な機能】
 * ・モード切替（RESTORE / DELETE）：目的（復帰か抹消か）に応じてUIと挙動を動的に変更。
 * ・警告表示：DELETEモード時はTopBarと背景色を警告色に変更し、破壊的な操作であることを明示。
 * ・同姓同名識別：氏名に加え、生年月日と備考を表示し、対象者を確実に特定。
 * ・誤操作防止：DELETEモード時は「全選択」を禁止し、一人ずつのチェック選択を強制。さらに抹消実行前に最終確認ダイアログを表示。
 * ・一括操作：選択した複数の利用者に対して一括で復帰または抹消を実行。
 *
 * 【遷移】
 * ← SettingsScreen（戻るボタン、または操作完了後に自動遷移）
 *
 * 【使用するViewModel】
 * DeleteOrRestorePersonViewModel
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.InfoDialog
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteOrRestorePersonScreen(
    viewModel: DeleteOrRestorePersonViewModel,
    mode: DeleteOrRestorePersonViewModel.OperationMode,
    onBack: () -> Unit,
) {
    val archivedPersons by viewModel.archivedPersonList.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    var showFinalConfirmDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // モードを ViewModel に反映
    LaunchedEffect(mode) {
        viewModel.setMode(mode)
    }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowInfoDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                else -> {}
            }
        }
    }

    val isDeleteMode = mode == DeleteOrRestorePersonViewModel.OperationMode.DELETE
    
    // 背景色の決定 (DELETEモード時は警告色)
    val backgroundColor = if (isDeleteMode) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.background
    }

    // TopBar 色の決定
    val topBarColors = if (isDeleteMode) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.error,
            titleContentColor = MaterialTheme.colorScheme.onError,
            navigationIconContentColor = MaterialTheme.colorScheme.onError,
            actionIconContentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isDeleteMode) "利用者の完全抹消" else "利用者の復帰", 
                        fontWeight = FontWeight.Bold 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = topBarColors,
                actions = {
                    if (!isDeleteMode && archivedPersons.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedIds.size == archivedPersons.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(archivedPersons)
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == archivedPersons.size) "全解除" else "全選択",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = if (isDeleteMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding() // ナビゲーションバーとの重なりを回避
                ) {
                    Button(
                        onClick = {
                            if (isDeleteMode) {
                                showFinalConfirmDialog = true
                            } else {
                                viewModel.restoreSelectedPersons(archivedPersons)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = if (isDeleteMode) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        val actionLabel = if (isDeleteMode) "完全に抹消する" else "復帰させる"
                        Text("選択した利用者 (${selectedIds.size}名) を$actionLabel")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(backgroundColor)
        ) {
            if (archivedPersons.isEmpty()) {
                EmptyState(
                    message = "終了した利用者はいません",
                    icon = Icons.Outlined.PersonOff
                )
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                Column {
                    if (isDeleteMode) {
                        // DELETEモード時の警告文
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "抹消した利用者のデータ（記録、写真）は二度と復元できません。一人ずつ慎重に選択してください。",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState
                        ) {
                            items(archivedPersons, key = { it.id }) { person ->
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            text = person.getMaskedName(isNameMaskingEnabled),
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    supportingContent = {
                                        Column {
                                            Text(person.getMaskedFurigana(isNameMaskingEnabled))
                                            Text(
                                                text = buildString {
                                                    append(DateTimeUtils.formatBirthday(person.birthday))
                                                    append(" (${DateTimeUtils.calculateAge(person.birthday)}歳)")
                                                    if (person.note.isNotBlank()) {
                                                        append(" [${person.note}]")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = selectedIds.contains(person.id),
                                            onCheckedChange = { viewModel.toggleSelection(person.id) },
                                            colors = if (isDeleteMode) {
                                                CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.error,
                                                    uncheckedColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                                )
                                            } else CheckboxDefaults.colors()
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                        VerticalScrollIndicator(lazyListState = listState)
                    }
                }
            }
        }
    }

    // 抹消実行前の最終確認
    if (showFinalConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFinalConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("データの完全抹消") },
            text = { 
                val scrollState = rememberScrollState()
                Box {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        Text("選択された ${selectedIds.size} 名の利用者のすべてのデータを完全に消去します。この操作は取り消せません。本当によろしいですか？")
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinalConfirmDialog = false
                        viewModel.deleteSelectedPersons(archivedPersons)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("抹消を実行する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (dialogMessage != null) {
        InfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }
}
