package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthViewEvent
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

/**
 * 利用者健康記録画面 (エントリポイント)
 *
 * 画面サイズに応じて Phone 版または Tablet 版のレイアウトに振り分けます。
 */
@Composable
fun PersonHealthScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    healthViewModel: PersonHealthViewModel,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> }
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val healthState by healthViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

    // カテゴリ変更の同期
    LaunchedEffect(detailState.currentCategory) {
        healthViewModel.setCategory(detailState.currentCategory)
    }

    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 画面状態の管理
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // 通知イベント監視
    LaunchedEffect(Unit) {
        healthViewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
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
                    healthViewModel.setSelectedRecordId(null)
                }
                else -> {}
            }
        }
    }

    // 共通的な画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        detailViewModel.viewEvent.collect { event ->
            when (event) {
                is PersonDetailViewEvent.NavigateToCategory -> {
                    detailState.personId?.let { personId ->
                        navController.navigate(event.category.toDestination(personId)) {
                            popUpTo<Destination.Main>()
                            launchSingleTop = true
                        }
                    }
                }
                is PersonDetailViewEvent.NavigateBackToMain -> {
                    navController.popBackStack(Destination.Main, inclusive = false)
                }
            }
        }
    }

    // 健康記録固有の画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        healthViewModel.viewEvent.collect { event ->
            when (event) {
                is PersonHealthViewEvent.NavigateToGraphExpansion -> {
                    navController.navigate(
                        Destination.GraphExpansion(event.personId, event.category.name, event.initialIndex)
                    )
                }
            }
        }
    }

    if (isExpanded) {
        // Tablet
        PersonHealthScreenTablet(
            currentCategory = detailState.currentCategory,
            records = healthState.records,
            isLoading = healthState.isLoading,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            selectedRecordId = healthState.selectedRecordId,
            onSelectedRecordIdChange = { healthViewModel.setSelectedRecordId(it) },
            onBack = { detailViewModel.navigateBackToMain() },
            onExpandGraph = { index ->
                detailState.personId?.let { pid ->
                    healthViewModel.navigateToGraphExpansion(pid, detailState.currentCategory, index)
                }
            },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            onShowPdfSettings = { showPdfSettingsDialog = true },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { cat, recordId, time, values -> 
                healthViewModel.saveRecord(cat, recordId, time, values) 
            },
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    } else {
        // Phone
        PersonHealthScreenPhone(
            currentCategory = detailState.currentCategory,
            records = healthState.records,
            isLoading = healthState.isLoading,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            preferredShowHistory = healthState.preferredShowHistory,
            onPreferredShowHistoryChange = { healthViewModel.updatePreferredShowHistory(it) },
            selectedRecordId = healthState.selectedRecordId,
            onSelectedRecordIdChange = { healthViewModel.setSelectedRecordId(it) },
            onBack = { detailViewModel.navigateBackToMain() },
            onExpandGraph = { index ->
                detailState.personId?.let { pid ->
                    healthViewModel.navigateToGraphExpansion(pid, detailState.currentCategory, index)
                }
            },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            onShowPdfSettings = { showPdfSettingsDialog = true },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { cat, recordId, time, values -> 
                healthViewModel.saveRecord(cat, recordId, time, values) 
            },
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    }

    if (dialogMessage != null) {
        jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        PdfExportActionHandler(
            showDialog = true,
            onDismiss = { showPdfSettingsDialog = false },
            category = detailState.currentCategory,
            person = detailState.person,
            records = healthState.records,
            viewModel = healthViewModel,
            onRequireAuthentication = onRequireAuthentication
        )
    }
}
