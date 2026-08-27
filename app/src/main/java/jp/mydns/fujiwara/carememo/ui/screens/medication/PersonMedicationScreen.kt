package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.logic.common.MedicationStatus
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * UI Action：服薬記録画面におけるユーザー操作の集約定義
 */
sealed interface PersonMedicationUiAction {
    // ナビゲーション・月操作
    data object PreviousMonth : PersonMedicationUiAction
    data object NextMonth : PersonMedicationUiAction
    data object Back : PersonMedicationUiAction
    data class NavigateToCategory(val category: Category) : PersonMedicationUiAction

    // 表示モード・表示制御
    data class HistoryModeChange(val isHistoryMode: Boolean) : PersonMedicationUiAction
    data object ShowPdfSettings : PersonMedicationUiAction
    data class DayClick(val date: LocalDate) : PersonMedicationUiAction

    // ダイアログ操作
    data object DismissDialog : PersonMedicationUiAction
    data class DialogStatusToggle(val slotIndex: Int, val status: MedicationStatus, val time: Instant) : PersonMedicationUiAction
    data object DialogConfirm : PersonMedicationUiAction
}

/**
 * Screen：PersonMedicationScreen
 *
 * 【役割】
 * 利用者の服薬記録（カテゴリC）画面のエントリポイントとなる最上位 Screen コンポーネントです。
 * デバイスの形状（Phone/Tablet）に応じたレイアウトの振り分け、共通ダイアログの制御、
 * および ViewModel からのイベント（通知、遷移等）の購読を担当します。
 *
 * 【主な機能】
 * ・マルチレイアウト制御：WindowWidthSizeClass に基づく Phone/Tablet 版の出し分け。
 * ・イベントハンドリング：ViewModel からの通知（Snackbar, ErrorDialog 等）の UI への反映。
 * ・入力制御：`MedicationInputDialog` を起動し、特定の日付に対する服薬状況（朝/昼/夕/寝る前）の一括保存を仲介。
 * ・同期：詳細 ViewModel とカテゴリ別 ViewModel 間のデータ整合性維持。
 *
 * 【全体像：服薬管理画面階層（Medication Hierarchy）】
 *
 * ■ PersonMedicationScreen (★本コンポーネント：全体制御)
 * │
 * ├─ [ A ] PersonMedicationScreenPhone (Phone版：シングルペイン)
 * │    └─ PersonMedicationScreenContent (カレンダー・履歴トグル)
 * │         ├─ CalendarGrid (ui/components/medication/)
 * │         └─ MedicationHistoryTable (ui/components/medication/)
 * │
 * ├─ [ B ] PersonMedicationScreenTablet (Tablet版：2ペイン固定)
 * │    └─ PersonMedicationScreenContent (カレンダー ＋ 履歴並列表示)
 * │
 * └─ [ 共通ダイアログ ]
 *      ├─ MedicationInputDialog (服薬状況入力：ui/components/medication/)
 *      ├─ PdfExportActionHandler (PDF出力：ui/components/common/)
 *      └─ AppInfoDialog (通知・エラー)
 *
 * 【このコンポーネントでは行わないこと】
 * 実際の UI 描画（下位の ScreenPhone/Tablet または Content が担当）。
 */
@Composable
fun PersonMedicationScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    medicationViewModel: PersonMedicationViewModel,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
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

    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

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

    // アクションハンドラ：UI からの通知を ViewModel やナビゲーションへ橋渡しする
    val handleAction: (PersonMedicationUiAction) -> Unit = remember(medicationViewModel, detailViewModel, navController, scope, medicationState.allRecords.isEmpty()) {
        { action ->
            when (action) {
                PersonMedicationUiAction.PreviousMonth -> medicationViewModel.previousMonth()
                PersonMedicationUiAction.NextMonth -> medicationViewModel.nextMonth()
                PersonMedicationUiAction.Back -> detailViewModel.navigateBackToMain()
                is PersonMedicationUiAction.NavigateToCategory -> detailViewModel.navigateToCategory(action.category)
                is PersonMedicationUiAction.HistoryModeChange -> isHistoryMode = action.isHistoryMode
                PersonMedicationUiAction.ShowPdfSettings -> {
                    if (medicationState.allRecords.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.p_detail_error_no_records_for_pdf,
                                    context.getString(Category.MEDICATION.displayNameRes)
                                )
                            )
                        }
                    } else {
                        showPdfSettingsDialog = true
                    }
                }
                is PersonMedicationUiAction.DayClick -> medicationViewModel.onDayClick(action.date)
                PersonMedicationUiAction.DismissDialog -> {
                    showPdfSettingsDialog = false
                    dialogMessage = null
                    dialogTitle = null
                    medicationViewModel.dismissDialog()
                }
                is PersonMedicationUiAction.DialogStatusToggle -> {
                    medicationViewModel.updateDialogRecord(action.slotIndex, action.status, action.time)
                }
                PersonMedicationUiAction.DialogConfirm -> medicationViewModel.syncMedicationDay()
            }
        }
    }

    if (isExpanded) {
        PersonMedicationScreenTablet(
            uiState = medicationState,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            onAction = handleAction,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    } else {
        PersonMedicationScreenPhone(
            uiState = medicationState,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isHistoryMode = isHistoryMode,
            onAction = handleAction,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        PdfExportActionHandler(
            showDialog = true,
            onDismiss = { handleAction(PersonMedicationUiAction.DismissDialog) },
            category = Category.MEDICATION,
            canExport = detailState.person != null,
            onRequireAuthentication = onRequireAuthentication,
            onExportExecute = { range, order, start, end, _, password ->
                medicationViewModel.setLockBypassEnabled(true)
                medicationViewModel.safeLaunch(
                    operation = "exportAndShare",
                    contextBuilder = { tableName = "pdf_export" }
                ) {
                    detailState.person?.let { person ->
                        PdfExporter.exportAndShare(
                            context = context,
                            person = person,
                            category = Category.MEDICATION,
                            records = medicationState.allRecords,
                            range = range,
                            order = order,
                            customStartDate = start,
                            customEndDate = end,
                            password = password
                        )
                    }
                }
            }
        )
    }

    if (medicationState.selectedDialogDate != null) {
        MedicationInputDialog(
            date = medicationState.selectedDialogDate!!,
            tempRecords = medicationState.dialogTempRecords,
            onAction = handleAction
        )
    }

    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = { handleAction(PersonMedicationUiAction.DismissDialog) }
        )
    }
}
