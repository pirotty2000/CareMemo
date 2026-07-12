package jp.mydns.fujiwara.carememo.ui.screens.main

/**
 * Screen : MainScreen
 *
 * 【画面名】：
 * 利用者一覧画面
 *
 * 【役割】：
 * 登録された利用者（ケア対象者）の一覧を表示し、各記録カテゴリへの橋渡しや、
 * 利用者情報の管理（登録・変更・サービス終了処理）を行うアプリのメインエントランス。
 *
 * 【主な機能】：
 * ・利用者一覧表示（名前のマスキング、年齢、最新記録状況のバッジ表示、誕生日通知）
 * ・絞り込み検索（五十音順インデックスおよび検索バーによるフリーワード検索）
 * ・利用者管理（論理削除とUndo機能）
 * ・カテゴリ遷移（利用者選択時のボトムシートから健康記録・所見メモ・服薬管理・一括入力へ遷移）
 *
 * 【遷移】：
 * → PersonHealthScreen (詳細画面：健康記録「身長・体重」「バイタル」「血糖値・HbA1c」)
 * → PersonConditionScreen (詳細画面：「所見メモ」)
 * → PersonMedicationScreen (詳細画面：「服薬管理」)
 * → BatchInputScreen (健康記録の一括入力)
 * → PersonEditScreen (利用者登録・編集)
 * → SettingsScreen (アプリ設定)
 *
 * 【使用するViewModel】：
 * PersonListViewModel
 *
 * 【備考】：
 * ViewModelとの接続、ナビゲーション、スナックバー／ダイアログのイベント制御を担当。
 * 実際のUIレイアウトは MainScreenContent.kt に委譲。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import kotlinx.coroutines.launch

/**
 * 利用者一覧画面のメインエントランス。
 * ViewModelとの接続、UI状態の監視、ダイアログやボトムシートの表示制御を行う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    onNavigateToDetail: (Int, Category) -> Unit,    // 各カテゴリ
    onNavigateToBatchInput: (Int) -> Unit,          // 一括入力画面
    onNavigateToAddPerson: () -> Unit,              // 利用者の新規登録
    onNavigateToEditPerson: (Int) -> Unit,          // 利用者の編集
    onNavigateToSettings: () -> Unit                // 設定・管理画面
) {
    val userList by viewModel.userList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val userEndedFormat = stringResource(R.string.main_snackbar_user_ended)
    val undoLabel = stringResource(R.string.common_undo)
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                else -> {}
            }
        }
    }

    MainScreenContent(
        userList = userList,
        isLoading = isLoading,
        isNameMaskingEnabled = isNameMaskingEnabled,
        searchQuery = searchQuery,
        selectedSection = selectedSection,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },                 // 所見メモ検索
        onSectionSelect = { viewModel.setSelectedSection(it) },                 // 五十音カナ検索
        snackbarHostState = snackbarHostState,                                  //
        lazyListState = lazyListState,                                          //
        onUserClick = { person -> selectedPerson = person; showSheet = true },  // 選択された利用者
        onEditUser = { person -> onNavigateToEditPerson(person.id) },           // 利用者情報の編集
        onAddClick = { onNavigateToAddPerson() },                               // 新規利用者登録
        onEndUser = { person ->                                                 // 利用終了
            viewModel.logicalDeletePerson(person)
            scope.launch {
                val fullName = person.getMaskedName(isNameMaskingEnabled)
                val result = snackbarHostState.showSnackbar(
                    message = userEndedFormat.format(fullName), 
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) { 
                    viewModel.restorePerson(person)
                    lazyListState.animateScrollToItem(0) 
                }
            }
        },
        onNavigateToSettings = onNavigateToSettings
    )

    // 通知ダイアログの表示
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

    // カテゴリ選択メニュー（下からスライド）
    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            CategorySelectionSheet(
                personName = selectedPerson!!.getMaskedName(isNameMaskingEnabled), 
                onCategorySelect = { category -> 
                    showSheet = false
                    onNavigateToDetail(selectedPerson!!.id, category) 
                },
                onBatchInputSelect = {
                    showSheet = false
                    onNavigateToBatchInput(selectedPerson!!.id)
                }
            )
        }
    }
}
