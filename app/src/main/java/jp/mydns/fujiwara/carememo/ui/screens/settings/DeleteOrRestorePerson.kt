package jp.mydns.fujiwara.carememo.ui.screens.settings

/**
 * Screen : DeleteOrRestorePerson
 *
 * 【画面名】
 * 利用者の復帰・完全抹消画面
 *
 * 【遷移】
 * ← SettingsScreen（戻るボタン、または操作完了後に自動遷移）
 * 
 * 【遷移】：
 * ViewModel から発行される ViewEvent (DeleteOrRestorePersonViewEvent) に基づき、
 * Composable 側で NavHostController を操作して遷移を行う。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import kotlinx.coroutines.launch

/**
 * 利用者の復帰・抹消画面のメイン Composable
 *
 * @param viewModel 操作と状態を管理する ViewModel
 * @param navController 画面遷移制御用の NavHostController
 * @param mode 初期表示モード（RESTORE:復帰 / DELETE:完全抹消）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteOrRestorePersonScreen(
    viewModel: DeleteOrRestorePersonViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val archivedPersons = uiState.archivedPersons
    val selectedIds = uiState.selectedIds
    val isNameMaskingEnabled = uiState.isNameMaskingEnabled

    val scope = rememberCoroutineScope()
    // 操作が行われた場合に、戻り先の画面にリスト更新を促すためのフラグ
    var isRefreshNeeded by rememberSaveable { mutableStateOf(false) }
    
    // 確認ダイアログの表示制御
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showFinalConfirmDialog by remember { mutableStateOf(false) }
    
    // 汎用情報・エラーダイアログ用の状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    /**
     * 戻る際の処理
     * 復帰や抹消が一度でも行われていれば、遷移元に更新を通知します。
     */
    val handleBack: () -> Unit = {
        if (isRefreshNeeded) {
            navController.previousBackStackEntry?.savedStateHandle?.set("refresh_needed", true)
        }
        navController.popBackStack()
    }

    // ViewModel から発行される一過性のイベント（通知やダイアログ要求）を監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                    // 復帰・抹消の成功メッセージを受信した場合、画面更新が必要と判断
                    if (event.resId == R.string.archive_msg_restored || event.resId == R.string.archive_msg_deleted) {
                        isRefreshNeeded = true
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                    }
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
                else -> {}
            }
        }
    }

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                DeleteOrRestorePersonViewEvent.NavigateBack,
                DeleteOrRestorePersonViewEvent.Finish -> {
                    handleBack()
                }
            }
        }
    }

    val isDeleteMode = uiState.mode == DeleteOrRestorePersonViewModel.OperationMode.DELETE
    
    // 背景色の決定 (DELETEモード時は、注意を促すために薄いエラー色を適用)
    val backgroundColor = if (isDeleteMode) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.background
    }

    // TopBar 配色の決定 (DELETEモード時は、破壊的操作であることを示すためエラー色を適用)
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
        modifier = modifier,
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
                    IconButton(onClick = { viewModel.navigateBack() }, modifier = Modifier.testTag("DeleteOrRestore_BackButton")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = topBarColors,
                actions = {
                    // DELETEモード時は「全選択」による誤抹消を防ぐため、アクションを非表示にする
                    if (!isDeleteMode && archivedPersons.isNotEmpty()) {
                        val isAllSelected = selectedIds.size == archivedPersons.size
                        TextButton(
                            onClick = {
                                if (isAllSelected) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(archivedPersons)
                                }
                            },
                            modifier = Modifier.testTag("DeleteOrRestore_SelectAllButton")
                        ) {
                            Text(
                                text = if (isAllSelected) "全解除" else "全選択",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // いずれかの利用者が選択されている場合のみ、実行ボタンを表示
            if (selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = if (isDeleteMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = {
                            if (isDeleteMode) {
                                showFinalConfirmDialog = true
                            } else {
                                showRestoreConfirmDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("DeleteOrRestore_ActionButton"),
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
                // アーカイブ対象がいない場合の表示
                EmptyState(
                    message = "終了した利用者はいません",
                    icon = Icons.Outlined.PersonOff,
                    modifier = Modifier.testTag("DeleteOrRestore_EmptyState")
                )
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                Column {
                    if (isDeleteMode) {
                        // DELETEモード時の警告バナー
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.testTag("DeleteOrRestore_WarningBanner")
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
                            modifier = Modifier.fillMaxSize().testTag("DeleteOrRestore_List"),
                            state = listState
                        ) {
                            items(archivedPersons, key = { it.id }) { person ->
                                val isSelected = selectedIds.contains(person.id)
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            text = person.getMaskedName(isNameMaskingEnabled),
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    supportingContent = {
                                        Column {
                                            // ふりがな（マスク対応）
                                            Text(person.getMaskedFurigana(isNameMaskingEnabled))
                                            // 生年月日、年齢、および識別メモを表示して人違いを防止
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
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleSelection(person.id) },
                                            colors = if (isDeleteMode) {
                                                // 抹消モード時はチェックボックスも赤色にして警告を強調
                                                CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.error,
                                                    uncheckedColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                                )
                                            } else CheckboxDefaults.colors(),
                                            modifier = Modifier.testTag("DeleteOrRestore_Checkbox_${person.id}")
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.testTag("DeleteOrRestore_Item_${person.id}")
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                        // 画面右端のスクロール位置インジケータ
                        VerticalScrollIndicator(lazyListState = listState)
                    }
                }
            }
        }
    }

    // 復帰実行前の最終確認ダイアログ
    if (showRestoreConfirmDialog) {
        AppDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("利用者の復帰") },
            text = {
                AppDialogContent(text = "選択された ${selectedIds.size} 名の利用者を利用者一覧（アクティブ）に戻します。よろしいですか？")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "復帰を実行する",
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreSelectedPersons(archivedPersons)
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showRestoreConfirmDialog = false }
                )
            }
        )
    }

    // 抹消実行前の最終確認ダイアログ（破壊的操作）
    if (showFinalConfirmDialog) {
        AppDialog(
            onDismissRequest = { showFinalConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("データの完全抹消", modifier = Modifier.testTag("DeleteOrRestore_ConfirmDialog")) },
            text = {
                AppDialogContent(text = "選択された ${selectedIds.size} 名の利用者のすべてのデータを完全に消去します。この操作は取り消せません。本当によろしいですか？")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "抹消を実行する",
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showFinalConfirmDialog = false
                        viewModel.deleteSelectedPersons(archivedPersons)
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = "キャンセル",
                    onClick = { showFinalConfirmDialog = false }
                )
            }
        )
    }

    // エラーまたは情報通知用ダイアログ
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }
}
