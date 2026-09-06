package jp.mydns.fujiwara.carememo.ui.screens.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonOperation
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonScreenState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import kotlinx.coroutines.launch

/**
 * UI Action：利用者の復帰・抹消画面におけるユーザー操作の集約定義
 */
sealed interface DeleteOrRestorePersonUiAction {
    data class ToggleSelection(val id: String) : DeleteOrRestorePersonUiAction
    data object SelectAll : DeleteOrRestorePersonUiAction
    data object ClearSelection : DeleteOrRestorePersonUiAction
    data object ActionClick : DeleteOrRestorePersonUiAction
    data object ConfirmRestore : DeleteOrRestorePersonUiAction
    data object ConfirmDelete : DeleteOrRestorePersonUiAction
    data object Back : DeleteOrRestorePersonUiAction
    data object DismissDialog : DeleteOrRestorePersonUiAction
}

/**
 * Screen：DeleteOrRestorePersonScreen
 *
 * 【役割】
 * アーカイブ済み（論理削除済み）の利用者を、一覧（MainScreen）へ「復帰」させるか、
 * あるいは DB から「完全抹消」するための管理画面（SCR-S-003）です。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteOrRestorePersonScreen(
    viewModel: DeleteOrRestorePersonViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var isRefreshNeeded by rememberSaveable { mutableStateOf(false) }
    
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showFinalConfirmDialog by remember { mutableStateOf(false) }
    
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val handleBack: () -> Unit = {
        if (isRefreshNeeded) {
            navController.previousBackStackEntry?.savedStateHandle?.set("refresh_needed", true)
        }
        navController.popBackStack()
    }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
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

    val handleAction: (DeleteOrRestorePersonUiAction) -> Unit = remember(viewModel, uiState.mode) {
        { action ->
            when (action) {
                is DeleteOrRestorePersonUiAction.ToggleSelection -> viewModel.toggleSelection(action.id)
                DeleteOrRestorePersonUiAction.SelectAll -> viewModel.selectAll(uiState.archivedPersons)
                DeleteOrRestorePersonUiAction.ClearSelection -> viewModel.clearSelection()
                DeleteOrRestorePersonUiAction.ActionClick -> {
                    if (uiState.mode == DeleteOrRestorePersonViewModel.OperationMode.DELETE) {
                        showFinalConfirmDialog = true
                    } else {
                        showRestoreConfirmDialog = true
                    }
                }
                DeleteOrRestorePersonUiAction.ConfirmRestore -> {
                    showRestoreConfirmDialog = false
                    viewModel.restoreSelectedPersons(uiState.archivedPersons)
                }
                DeleteOrRestorePersonUiAction.ConfirmDelete -> {
                    showFinalConfirmDialog = false
                    viewModel.deleteSelectedPersons(uiState.archivedPersons)
                }
                DeleteOrRestorePersonUiAction.Back -> viewModel.navigateBack()
                DeleteOrRestorePersonUiAction.DismissDialog -> {
                    showRestoreConfirmDialog = false
                    showFinalConfirmDialog = false
                    dialogMessage = null
                    dialogTitle = null
                }
            }
        }
    }

    DeleteOrRestorePersonContent(
        uiState = uiState,
        onAction = handleAction,
        showRestoreConfirmDialog = showRestoreConfirmDialog,
        showFinalConfirmDialog = showFinalConfirmDialog,
        dialogTitle = dialogTitle,
        dialogMessage = dialogMessage,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Screen：DeleteOrRestorePersonContent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteOrRestorePersonContent(
    uiState: DeleteOrRestorePersonUiState,
    onAction: (DeleteOrRestorePersonUiAction) -> Unit,
    showRestoreConfirmDialog: Boolean,
    showFinalConfirmDialog: Boolean,
    dialogTitle: String?,
    dialogMessage: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.mode
    val archivedPersons = uiState.archivedPersons
    val selectedIds = uiState.selectedIds
    val isNameMaskingEnabled = uiState.isNameMaskingEnabled
    
    val isDeleteMode = mode == DeleteOrRestorePersonViewModel.OperationMode.DELETE
    val isOperating = uiState.operation != DeleteOrRestorePersonOperation.Idle
    
    val backgroundColor = if (isDeleteMode) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.background
    }

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
                        text = if (isDeleteMode) stringResource(R.string.archive_title_delete) else stringResource(R.string.archive_title_restore), 
                        fontWeight = FontWeight.Bold 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(DeleteOrRestorePersonUiAction.Back) }, enabled = !isOperating, modifier = Modifier.testTag("DeleteOrRestore_BackButton")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = topBarColors,
                actions = {
                    if (!isDeleteMode && archivedPersons.isNotEmpty() && !isOperating) {
                        val isAllSelected = selectedIds.size == archivedPersons.size
                        TextButton(
                            onClick = {
                                if (isAllSelected) {
                                    onAction(DeleteOrRestorePersonUiAction.ClearSelection)
                                } else {
                                    onAction(DeleteOrRestorePersonUiAction.SelectAll)
                                }
                            },
                            modifier = Modifier.testTag("DeleteOrRestore_SelectAllButton")
                        ) {
                            Text(
                                text = if (isAllSelected) stringResource(R.string.archive_btn_deselect_all) else stringResource(R.string.archive_btn_select_all),
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
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = { onAction(DeleteOrRestorePersonUiAction.ActionClick) },
                        enabled = !isOperating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("DeleteOrRestore_ActionButton"),
                        colors = if (isDeleteMode) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        val actionLabel = if (isDeleteMode) stringResource(R.string.archive_action_delete_label) else stringResource(R.string.archive_action_restore_label)
                        Text(stringResource(R.string.archive_action_confirm_btn_format, selectedIds.size, actionLabel))
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
            when (uiState.screenState) {
                is DeleteOrRestorePersonScreenState.Loading -> {
                    if (archivedPersons.isEmpty()) {
                        LoadingScreen(modifier = Modifier.testTag("DeleteOrRestore_Loading"))
                    }
                }
                is DeleteOrRestorePersonScreenState.Error -> {
                    ErrorState(
                        message = stringResource(R.string.common_error_load_failed),
                        onRetry = { /* viewModel.startArchivedListObservation() */ }
                    )
                }
                is DeleteOrRestorePersonScreenState.Active -> {
                    if (archivedPersons.isEmpty()) {
                        EmptyState(
                            message = stringResource(R.string.archive_empty_msg),
                            icon = Icons.Outlined.PersonOff,
                            modifier = Modifier.testTag("DeleteOrRestore_EmptyState")
                        )
                    } else {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        Column {
                            if (isOperating) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                            }
                            if (isDeleteMode) {
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
                                            text = stringResource(R.string.archive_permanent_delete_warning),
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
                                                    checked = isSelected,
                                                    onCheckedChange = { onAction(DeleteOrRestorePersonUiAction.ToggleSelection(person.id)) },
                                                    enabled = !isOperating,
                                                    colors = if (isDeleteMode) {
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
                                VerticalScrollIndicator(lazyListState = listState)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AppDialog(
            onDismissRequest = { onAction(DeleteOrRestorePersonUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.archive_title_restore)) },
            text = {
                AppDialogContent(text = stringResource(R.string.archive_dialog_restore_confirm_msg, selectedIds.size))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.archive_dialog_restore_confirm_btn),
                    onClick = { onAction(DeleteOrRestorePersonUiAction.ConfirmRestore) }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onAction(DeleteOrRestorePersonUiAction.DismissDialog) }
                )
            }
        )
    }

    if (showFinalConfirmDialog) {
        AppDialog(
            onDismissRequest = { onAction(DeleteOrRestorePersonUiAction.DismissDialog) },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.archive_dialog_delete_confirm_title), modifier = Modifier.testTag("DeleteOrRestore_ConfirmDialog")) },
            text = {
                AppDialogContent(text = stringResource(R.string.archive_dialog_delete_confirm_msg, selectedIds.size))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.archive_dialog_delete_confirm_btn),
                    type = AppDialogActionType.DELETE,
                    onClick = { onAction(DeleteOrRestorePersonUiAction.ConfirmDelete) }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onAction(DeleteOrRestorePersonUiAction.DismissDialog) }
                )
            }
        )
    }

    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage,
            onDismiss = { onAction(DeleteOrRestorePersonUiAction.DismissDialog) }
        )
    }
}
