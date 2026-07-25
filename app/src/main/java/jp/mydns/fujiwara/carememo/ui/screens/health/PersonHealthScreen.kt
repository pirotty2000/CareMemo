package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
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
    widthSizeClass: WindowWidthSizeClass,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToGraphExpansion: (String, Category, Int) -> Unit
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val healthState by healthViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

    // 1.2.2項に基づき、ID変更時のみロードをトリガー
    LaunchedEffect(detailState.personId) {
        detailState.personId?.let { healthViewModel.loadPerson(it) }
    }

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

    // イベント監視
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

    if (isExpanded) {
        PersonHealthScreenTablet(
            personId = detailState.personId ?: "",
            currentCategory = detailState.currentCategory,
            records = healthState.records,
            isLoading = healthState.isLoading,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            selectedRecordId = healthState.selectedRecordId ?: "",
            onSelectedRecordIdChange = { healthViewModel.setSelectedRecordId(it.ifEmpty { null }) },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = { showPdfSettingsDialog = true },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { healthViewModel.saveRecord(it, healthState.selectedRecordId ?: "") },
            snackbarHostState = snackbarHostState
        )
    } else {
        PersonHealthScreenPhone(
            personId = detailState.personId ?: "",
            currentCategory = detailState.currentCategory,
            records = healthState.records,
            isLoading = healthState.isLoading,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            preferredShowHistory = healthState.preferredShowHistory,
            onPreferredShowHistoryChange = { healthViewModel.updatePreferredShowHistory(it) },
            selectedRecordId = healthState.selectedRecordId ?: "",
            onSelectedRecordIdChange = { healthViewModel.setSelectedRecordId(it.ifEmpty { null }) },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = { showPdfSettingsDialog = true },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { healthViewModel.saveRecord(it, healthState.selectedRecordId ?: "") },
            snackbarHostState = snackbarHostState
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
        @Suppress("ConstantConditions")
        PdfExportActionHandler(
            showDialog = showPdfSettingsDialog,
            onDismiss = { showPdfSettingsDialog = false },
            category = detailState.currentCategory,
            person = detailState.person,
            records = healthState.records,
            viewModel = healthViewModel,
            onRequireAuthentication = onRequireAuthentication
        )
    }
}
