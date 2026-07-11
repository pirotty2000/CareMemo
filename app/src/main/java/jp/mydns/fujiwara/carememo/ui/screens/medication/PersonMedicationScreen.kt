package jp.mydns.fujiwara.carememo.ui.screens.medication

/**
 * Screen : PersonMedicationScreen
 *
 * 【画面名】：
 * 利用者服薬記録画面
 *
 * 【役割】：
 * 特定の利用者の服薬履歴を管理し、日ごとの服薬状況（朝・昼・夕・寝る前）の登録、
 * 月間状況の確認、および服薬レポートのPDF出力を行う画面。
 *
 * 【主な機能】：
 * ・利用者情報の表示（ヘッダー）
 * ・月間状況の可視化（カレンダー表示 / 履歴リスト表示の切り替え）
 * ・一括入力ダイアログによる服薬状況の登録・更新・削除
 * ・PDFエクスポート機能（服薬状況の月間一覧出力）
 * ・画面最適化（Phone/Tabletの動的レイアウト切り替え）
 *
 * 【遷移】：
 * ← MainScreen (戻るボタン)
 * → PersonMedicationScreenPhone / PersonMedicationScreenTablet (デバイスサイズによる内部分岐)
 *
 * 【使用するViewModel】：
 * ・PersonDetailViewModel (詳細画面共通フレームワーク)
 * ・PersonMedicationViewModel (服薬記録固有ロジック)
 *
 * 【使用するComponents】：
 * ・screens/detail/medication/PersonMedicationScreenPhone.kt
 * ・screens/detail/medication/PersonMedicationScreenTablet.kt
 * ・detail/medication/MedicationInputDialog (PersonMedicationComponents.kt)
 * ・detail/common/PdfExportActionHandler.kt
 *
 * 【備考】：
 * 履歴データは月単位でロードされ、カレンダーによる直感的な確認とリストによる詳細確認が可能。
 */

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PersonMedicationScreen(
    viewModel: PersonDetailViewModel,
    medicationViewModel: PersonMedicationViewModel,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val isLoading by medicationViewModel.isLoading.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    val selectedMonth by medicationViewModel.selectedMonth.collectAsState()
    val recordsByDate by medicationViewModel.recordsByDate.collectAsState()
    val allRecords by medicationViewModel.allRecords.collectAsState()
    var showDialog by remember { mutableStateOf<LocalDate?>(null) }
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++
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

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
        medicationViewModel.loadPerson(personId)
        viewModel.setCategory(Category.MEDICATION)
    }

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++
    if (isExpanded) {
        PersonMedicationScreenTablet(
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = isLoading,
            selectedMonth = selectedMonth,
            recordsByDate = recordsByDate,
            personCategorySummary = personCategorySummary,
            onPreviousMonth = { medicationViewModel.previousMonth() },
            onNextMonth = { medicationViewModel.nextMonth() },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = {
                if (allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("服薬記録がないため出力できません")
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
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = isLoading,
            selectedMonth = selectedMonth,
            recordsByDate = recordsByDate,
            personCategorySummary = personCategorySummary,
            isHistoryMode = isHistoryMode,
            onHistoryModeChange = { isHistoryMode = it },
            onPreviousMonth = { medicationViewModel.previousMonth() },
            onNextMonth = { medicationViewModel.nextMonth() },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = {
                if (allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("服薬記録がないため出力できません")
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
    PdfExportActionHandler(
        showDialog = showPdfSettingsDialog,
        onDismiss = { showPdfSettingsDialog = false },
        category = Category.MEDICATION,
        person = currentPerson,
        records = allRecords,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        onRequireAuthentication = onRequireAuthentication
    )

    if (showDialog != null) {
        val dateStr = showDialog.toString()
        MedicationInputDialog(
            date = showDialog!!,
            personId = personId,
            records = recordsByDate[dateStr] ?: emptyList(),
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
