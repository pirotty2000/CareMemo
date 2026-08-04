package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 利用者服薬記録画面
 */
@Composable
fun PersonMedicationScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    medicationViewModel: PersonMedicationViewModel,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> }
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val medicationState by medicationViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

    // カテゴリ変更の同期
    LaunchedEffect(detailState.currentCategory) {
        medicationViewModel.setCategory(detailState.currentCategory)
    }

    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf<LocalDate?>(null) }
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val noRecordsMsgFormat = stringResource(R.string.p_detail_error_no_records_for_pdf)
    val medicationCategoryName = stringResource(Category.MEDICATION.displayNameRes)

    // 通知イベント監視
    LaunchedEffect(Unit) {
        medicationViewModel.uiEventFlow.collect { event ->
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

    if (isExpanded) {
        PersonMedicationScreenTablet(
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = medicationState.isLoading,
            selectedMonth = medicationState.selectedMonth,
            recordsByDate = medicationState.recordsByDate,
            personCategorySummary = detailState.personSummary,
            onPreviousMonth = { medicationViewModel.previousMonth() },
            onNextMonth = { medicationViewModel.nextMonth() },
            onBack = { detailViewModel.navigateBackToMain() },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            onShowPdfSettings = {
                if (medicationState.allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(medicationCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDayClick = { date -> showDialog = date },
            snackbarHostState = snackbarHostState
        )
    } else {
        PersonMedicationScreenPhone(
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = medicationState.isLoading,
            selectedMonth = medicationState.selectedMonth,
            recordsByDate = medicationState.recordsByDate,
            personCategorySummary = detailState.personSummary,
            isHistoryMode = isHistoryMode,
            onHistoryModeChange = { isHistoryMode = it },
            onPreviousMonth = { medicationViewModel.previousMonth() },
            onNextMonth = { medicationViewModel.nextMonth() },
            onBack = { detailViewModel.navigateBackToMain() },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            onShowPdfSettings = {
                if (medicationState.allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(medicationCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDayClick = { date -> showDialog = date },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        PdfExportActionHandler(
            showDialog = true,
            onDismiss = { showPdfSettingsDialog = false },
            category = Category.MEDICATION,
            person = detailState.person,
            records = medicationState.allRecords,
            viewModel = medicationViewModel,
            onRequireAuthentication = onRequireAuthentication
        )
    }

    if (showDialog != null) {
        val dateStr = showDialog.toString()
        MedicationInputDialog(
            date = showDialog!!,
            personId = detailState.personId ?: "",
            records = medicationState.recordsByDate[dateStr] ?: emptyList(),
            onDismiss = { showDialog = null },
            onConfirm = { slotRecords ->
                medicationViewModel.syncMedicationDay(dateStr, slotRecords)
            }
        )
    }

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
