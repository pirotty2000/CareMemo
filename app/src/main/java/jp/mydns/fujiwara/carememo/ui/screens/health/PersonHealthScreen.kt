package jp.mydns.fujiwara.carememo.ui.screens.health

/**
 * Screen : PersonHealthScreen
 *
 * 【画面名】：
 * 利用者健康記録画面
 *
 * 【役割】：
 * 「身長・体重」「バイタル」「血糖値・HbA1c」の各健康カテゴリを統合的に管理・閲覧するための画面。
 * 複数のViewModelを横断的に使用し、利用者の最新の健康状態を可視化する。
 *
 * 【主な機能】：
 * ・カテゴリ切替（画面上部のタブまたはメニューからカテゴリを自在に切り替え）
 * ・データ入力（各カテゴリに応じた専用の登録フォームを提供：血圧、体温、血糖値、体重、受診メモ）
 * ・統計閲覧（記録データの推移をグラフで表示し、異常値の早期発見をサポート）
 * ・PDFエクスポート（カテゴリごとの記録履歴をPDFとして出力）
 * ・マルチレイアウト（PhoneとTabletの双方に最適化されたUIを提供）
 *
 * 【遷移】：
 * ← MainScreen (戻るボタン)
 * → PersonHealthScreenPhone / PersonHealthScreenTablet (デバイスサイズによる内部分岐)
 * → GraphExpansionScreen (グラフ拡大表示)
 *
 * 【使用するViewModel】：
 * PersonDetailViewModel, PersonHealthViewModel
 *
 * 【使用するComponents】：
 * ・detail/health/PersonHealthScreenPhone.kt
 * ・detail/health/PersonHealthScreenTablet.kt
 * ・detail/common/PdfExportActionHandler.kt
 * ・base/AppInfoDialog.kt
 *
 * 【備考】：
 * カテゴリ間の移動がスムーズに行えるよう、共通のヘッダーUIとナビゲーション構造を採用している。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.coroutines.launch

/**
 * 利用者健康記録画面のメインエントランス。
 * デバイスの画面サイズ（Phone/Tablet）に応じて最適なレイアウトを選択し、
 * 健康データの読み込み、表示、およびPDF出力などのアクションを統合管理する。
 */
@Composable
fun PersonHealthScreen(
    viewModel: PersonDetailViewModel,
    healthViewModel: PersonHealthViewModel,
    initialCategoryType: Category,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded // 画面が拡張レイアウト（タブレット等）かどうか
    val scope = rememberCoroutineScope() // コルーチンスコープ
    val context = androidx.compose.ui.platform.LocalContext.current

    val records by healthViewModel.records.collectAsState() // 記録データのリスト

    val isLoading by healthViewModel.isLoading.collectAsState() // ローディング状態
    val currentPerson by viewModel.currentPerson.collectAsState() // 現在選択されている利用者情報
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState() // 名前マスキング設定の有効状態
    val personCategorySummary by viewModel.personCategorySummary.collectAsState() // 各カテゴリの記録有無サマリー

    val snackbarHostState = remember { SnackbarHostState() } // スナックバー制御用
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var selectedRecordId by rememberSaveable { mutableIntStateOf(-1) }
    var lastLoadedPersonId by rememberSaveable { mutableIntStateOf(-1) }

    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }
    var preferredShowHistory by rememberSaveable { mutableStateOf(true) }
    val noRecordsMsgFormat = stringResource(R.string.p_detail_error_no_records_for_pdf) // PDF出力時のデータ無しメッセージ用フォーマット
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var onConfirmOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }

    // アプリからの通知を受け付ける窓口
    LaunchedEffect(Unit) {
        healthViewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseViewModel.UiEvent.ShowOverwriteConfirm -> {
                    onConfirmOverwrite = event.onConfirm
                }
                is BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is BaseViewModel.UiEvent.SaveSuccess -> {
                    selectedRecordId = -1
                }
                else -> {}
            }
        }
    }
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
                is BaseViewModel.UiEvent.SaveSuccess -> {
                    selectedRecordId = -1
                }
                else -> {}
            }
        }
    }

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++
    // 利用者コンテキストの確立（利用者ID変更時のみ実行）
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
        healthViewModel.loadPerson(personId)
        
        // 実際に別の利用者の画面へ遷移した時だけ、選択状態を完全にリセットする
        if (lastLoadedPersonId != personId) {
            selectedRecordId = -1
            lastLoadedPersonId = personId
        }
    }

    // カテゴリコンテキストの同期（カテゴリまたは利用者変更時に実行）
    LaunchedEffect(currentCategory, personId) {
        viewModel.setCategory(currentCategory)
        healthViewModel.setCategory(currentCategory)
        // カテゴリが切り替わったら選択をリセット（異なるデータ構造のIDは維持しない）
        selectedRecordId = -1
    }

    val currentCategoryName = stringResource(currentCategory.displayNameRes)

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++
    if (isExpanded) {
        PersonHealthScreenTablet(
            personId = personId,
            currentCategory = currentCategory,
            records = records,
            isLoading = isLoading,
            currentPerson = currentPerson,
            personCategorySummary = personCategorySummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            selectedRecordId = selectedRecordId,
            onSelectedRecordIdChange = { selectedRecordId = it },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = { category ->
                if (category.hasGraph) {
                    currentCategory = category
                } else {
                    onNavigateToCategory(category)
                }
            },
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(currentCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { healthViewModel.saveRecord(it) },
            snackbarHostState = snackbarHostState
        )
    } else {
        PersonHealthScreenPhone(
            personId = personId,
            currentCategory = currentCategory,
            records = records,
            isLoading = isLoading,
            currentPerson = currentPerson,
            personCategorySummary = personCategorySummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            preferredShowHistory = preferredShowHistory,
            onPreferredShowHistoryChange = { preferredShowHistory = it },
            selectedRecordId = selectedRecordId,
            onSelectedRecordIdChange = { selectedRecordId = it },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = { category ->
                if (category.hasGraph) {
                    currentCategory = category
                } else {
                    onNavigateToCategory(category)
                }
            },
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(currentCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { healthViewModel.deleteRecord(it) },
            onSaveRecord = { healthViewModel.saveRecord(it) },
            snackbarHostState = snackbarHostState
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

    if (onConfirmOverwrite != null) {
        AppDialog(
            onDismissRequest = { onConfirmOverwrite = null },
            title = { Text(stringResource(R.string.common_confirm_overwrite_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_overwrite_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onConfirmOverwrite?.invoke()
                        onConfirmOverwrite = null
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onConfirmOverwrite = null }
                )
            }
        )
    }

    // PDF出力共通ハンドラー
    PdfExportActionHandler(
        showDialog = showPdfSettingsDialog,
        onDismiss = { showPdfSettingsDialog = false },
        category = currentCategory,
        person = currentPerson,
        records = records,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        onRequireAuthentication = onRequireAuthentication
    )
}
