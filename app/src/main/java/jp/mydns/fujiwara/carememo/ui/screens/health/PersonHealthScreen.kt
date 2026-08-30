package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthViewEvent
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

/**
 * UI Action：健康記録画面におけるユーザー操作の集約定義
 */
sealed interface PersonHealthUiAction {
    // 表示モード・選択
    data class PreferredShowHistoryChanged(val showHistory: Boolean) : PersonHealthUiAction
    data class SelectedRecordIdChanged(val id: String?) : PersonHealthUiAction
    data class ItemClick(val record: HistoryRecord) : PersonHealthUiAction

    // 編集・保存
    data object EditClick : PersonHealthUiAction
    data class EditInputUpdate(val update: (HealthEditInput) -> HealthEditInput) : PersonHealthUiAction
    data class MarkFieldAsTouched(val fieldName: String) : PersonHealthUiAction
    data object SaveClick : PersonHealthUiAction
    data object CancelEdit : PersonHealthUiAction

    // 削除
    data class DeleteRecord(val record: HistoryRecord) : PersonHealthUiAction

    // グラフ・カテゴリ・外部連携
    data class ExpandGraph(val index: Int) : PersonHealthUiAction
    data class NavigateToCategory(val category: Category) : PersonHealthUiAction
    data object ShowPdfSettings : PersonHealthUiAction
    data object Back : PersonHealthUiAction

    // ダイアログ・共通
    data object DismissDialog : PersonHealthUiAction
}

/**
 * Screen：PersonHealthScreen
 *
 * 【役割】
 * 利用者の健康記録（カテゴリA）画面のエントリポイントとなる最上位 Screen コンポーネントです。
 * デバイスの形状（Phone/Tablet）に応じたレイアウトの振り分け、共通ダイアログの制御、
 * および ViewModel からのイベント（通知、遷移等）の購読を担当します。
 *
 * 【主な機能】
 * ・マルチレイアウト制御：WindowWidthSizeClass に基づく Phone/Tablet 版の出し分け。
 * ・イベントハンドリング：ViewModel からの通知（Snackbar, ErrorDialog 等）の UI への反映。
 * ・共有ステート管理：詳細編集、PDF 設定などの表示フラグ制御。
 * ・同期：詳細 ViewModel とカテゴリ別 ViewModel 間のデータ整合性維持。
 *
 * 【全体像：健康記録画面階層（Health Hierarchy）】
 *
 * ■ PersonHealthScreen (★本コンポーネント：全体制御)
 * │
 * ├─ [ A ] PersonHealthScreenPhone (Phone版：シングルペイン)
 * │    └─ PersonHealthScreenContent (履歴・グラフ切り替え)
 * │         └─ HealthRecordDetailPane (詳細・編集：ui/components/health/)
 * │
 * ├─ [ B ] PersonHealthScreenTablet (Tablet版：2ペイン固定)
 * │    └─ PersonHealthScreenContent (履歴リスト ＋ 詳細/グラフ並列)
 * │
 * └─ [ 共通パーツ ]
 *      ├─ PdfExportActionHandler (PDF出力制御)
 *      ├─ AppDeleteConfirmDialog (削除確認)
 *      └─ AppInfoDialog (通知・エラー)
 *
 * 【このコンポーネントでは行わないこと】
 * 実際の UI 描画（下位の ScreenPhone/Tablet または Content が担当）。
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

    // アクションハンドラ：UI からの通知を ViewModel やナビゲーションへ橋渡しする
    // 表示中の利用者IDやカテゴリをキーに含め、状態変更時のみラムダを再生成する
    val handleAction: (PersonHealthUiAction) -> Unit = remember(healthViewModel, detailViewModel, navController, detailState.personId, detailState.currentCategory) {
        { action ->
            when (action) {
                is PersonHealthUiAction.PreferredShowHistoryChanged -> healthViewModel.updatePreferredShowHistory(action.showHistory)
                is PersonHealthUiAction.SelectedRecordIdChanged -> healthViewModel.setSelectedRecordId(action.id)
                is PersonHealthUiAction.ItemClick -> healthViewModel.setSelectedRecordId(action.record.id)
                PersonHealthUiAction.EditClick -> healthViewModel.startEditSession()
                is PersonHealthUiAction.EditInputUpdate -> healthViewModel.updateEditInput(action.update)
                is PersonHealthUiAction.MarkFieldAsTouched -> healthViewModel.markFieldAsTouched(action.fieldName)
                PersonHealthUiAction.SaveClick -> healthViewModel.saveCurrentEdit()
                PersonHealthUiAction.CancelEdit -> healthViewModel.cancelEditSession()
                is PersonHealthUiAction.DeleteRecord -> healthViewModel.deleteRecord(action.record)
                is PersonHealthUiAction.ExpandGraph -> {
                    detailState.personId?.let { pid ->
                        healthViewModel.navigateToGraphExpansion(pid, detailState.currentCategory, action.index)
                    }
                }
                is PersonHealthUiAction.NavigateToCategory -> detailViewModel.navigateToCategory(action.category)
                PersonHealthUiAction.ShowPdfSettings -> showPdfSettingsDialog = true
                PersonHealthUiAction.Back -> detailViewModel.navigateBackToMain()
                PersonHealthUiAction.DismissDialog -> {
                    showPdfSettingsDialog = false
                    dialogMessage = null
                    dialogTitle = null
                }
            }
        }
    }

    if (isExpanded) {
        // Tablet
        PersonHealthScreenTablet(
            uiState = healthState,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            onAction = handleAction,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    } else {
        // Phone
        PersonHealthScreenPhone(
            uiState = healthState,
            currentPerson = detailState.person,
            personCategorySummary = detailState.personSummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            onAction = handleAction,
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
            onDismiss = { handleAction(PersonHealthUiAction.DismissDialog) },
            category = detailState.currentCategory,
            canExport = detailState.person != null,
            onRequireAuthentication = onRequireAuthentication,
            onExportExecute = { range, order, start, end, _, password ->
                healthViewModel.setLockBypassEnabled(true)
                healthViewModel.safeLaunch(
                    operation = "exportAndShare",
                    contextBuilder = { tableName = "pdf_export" }
                ) {
                    detailState.person?.let { person ->
                        PdfExporter.exportAndShare(
                            context = context,
                            person = person,
                            category = detailState.currentCategory,
                            records = healthState.records,
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
}
