package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonHealthScreen
 *
 * 【画面名】
 * 利用者健康記録画面
 *
 * 【役割】
 * バイタル、血糖値、身体計測、受診記録の各健康カテゴリを統合的に管理・閲覧するための画面。
 * 複数のViewModelを横断的に使用し、利用者の最新の健康状態を可視化する。
 *
 * 【主な機能】
 * ・カテゴリ切替：画面上部のタブまたはメニューからカテゴリを自在に切り替え。
 * ・データ入力：各カテゴリ（血圧、体温、血糖値、体重、受診メモ）に応じた専用の登録フォームを提供。
 * ・統計閲覧：記録データの推移をグラフで表示し、異常値の早期発見をサポート。
 * ・PDFエクスポート：カテゴリごとの記録履歴をPDFとして出力。
 * ・マルチレイアウト：PhoneとTabletの双方に最適化されたUIを提供。
 *
 * 【遷移】
 * ← MainScreen（戻るボタン）
 * → PersonHealthScreenPhone / PersonHealthScreenTablet（内部分岐）
 * → GraphExpansionScreen（グラフ拡大表示）
 *
 * 【使用するViewModel】
 * PersonDetailViewModel, PersonHealthViewModel
 *
 * 【備考】
 * カテゴリ間の移動がスムーズに行えるよう、共通のヘッダーUIとナビゲーション構造を採用している。
 */

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.coroutines.launch

@Composable
fun PersonHealthScreen(
    viewModel: PersonDetailViewModel,
    healthViewModel: PersonHealthViewModel,
    initialCategoryType: Category,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }

    // ユーザーの表示モード設定（初期値は履歴: true）
    var preferredShowHistory by rememberSaveable { mutableStateOf(true) }

    // タブレット用／編集用：現在選択されている記録のID (-1: 未選択, 0: 新規作成)
    var selectedRecordId by rememberSaveable { mutableIntStateOf(-1) }

    val records by healthViewModel.records.collectAsState()
    val isLoading by healthViewModel.isLoading.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noRecordsMsgFormat = stringResource(R.string.error_no_records_for_pdf)

    var showPdfSettingsDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(
                    event.message
                )
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        healthViewModel.loadPerson(personId)
        viewModel.setCategory(currentCategory)
        healthViewModel.setCategory(currentCategory)
        // カテゴリが切り替わったら選択をリセット
        selectedRecordId = -1
    }

    val currentCategoryName = stringResource(currentCategory.displayNameRes)

    if (isExpanded) {
        PersonHealthScreenTablet(
            healthViewModel = healthViewModel,
            personId = personId,
            currentCategory = currentCategory,
            records = records,
            isLoading = isLoading,
            currentPerson = currentPerson,
            personCategorySummary = personCategorySummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            selectedConditionId = selectedRecordId,
            onSelectedConditionIdChange = { selectedRecordId = it },
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
            snackbarHostState = snackbarHostState
        )
    } else {
        PersonHealthScreenPhone(
            healthViewModel = healthViewModel,
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
            snackbarHostState = snackbarHostState
        )
    }

    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                dialogMessage = null
                dialogTitle = null
            },
            title = { dialogTitle?.let { Text(it) } },
            text = { Text(dialogMessage!!) },
            confirmButton = {
                TextButton(onClick = {
                    dialogMessage = null
                    dialogTitle = null
                }) { Text("閉じる") }
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
        isNameMaskingEnabled = isNameMaskingEnabled,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel
    )
}
