package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonListViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.navigation.EditResult
import jp.mydns.fujiwara.carememo.ui.navigation.NavigationKeys
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * UI Action：メイン画面（利用者一覧）におけるユーザー操作の集約定義
 */
sealed interface MainUiAction {
    // 検索・フィルタ
    data class SearchQueryChange(val query: String) : MainUiAction
    data class SectionSelect(val section: String) : MainUiAction

    // ナビゲーション
    data object AddClick : MainUiAction
    data object NavigateToSettings : MainUiAction
    data class UserClick(val person: Person) : MainUiAction
    data class EditUser(val person: Person) : MainUiAction
    data class EmergencyContactManageClick(val person: Person) : MainUiAction

    // クイックメニュー
    data class QuickMenuClick(val person: Person) : MainUiAction
    data class EmergencyContactClick(val person: Person) : MainUiAction
    data object DismissQuickMenu : MainUiAction

    // 利用終了・Undo
    data class EndUser(val person: Person) : MainUiAction
    data class UndoEndUser(val person: Person) : MainUiAction

    // カテゴリ選択シート
    data class CategorySelect(val category: Category) : MainUiAction
    data object BatchInputSelect : MainUiAction
    data object DismissSheet : MainUiAction

    // 緊急連絡先選択シート
    data class EmergencyContactCall(val contact: EmergencyContact) : MainUiAction
    data object DismissContactSheet : MainUiAction

    // ダイアログ・共通
    data object DismissDialog : MainUiAction
}

/**
 * Screen：MainScreen
 *
 * 【役割】
 * アプリケーションのメイン画面（利用者一覧）を統括する最上位 Screen コンポーネントです。
 * ViewModel からの状態購読、画面遷移の実行、およびボトムシートやダイアログといった「副作用」の制御を集中管理します。
 *
 * 【主な機能】
 * ・状態購読：`PersonListViewModel` からの UI 状態（利用者リスト、検索クエリ等）の収集。
 * ・イベントハンドリング：ViewModel からの通知（Snackbar, InfoDialog 等）の適切な表示。
 * ・画面遷移：Type-safe Navigation に基づく詳細画面、編集画面、設定画面への遷移制御。
 * ・副作用管理：カテゴリ選択シート、緊急連絡先選択シート、論理削除後の Undo スナックバーの表示制御。
 *
 * 【全体像：メイン画面階層（Main Hierarchy）】
 *
 * ■ MainScreen (★本コンポーネント：制御層)
 * │
 * ├─ [1] MainScreenContent (表示層：ui/screens/main/MainScreenContent.kt)
 * │    ├─ TopAppBar (ハンバーガーメニュー：設定、バージョン)
 * │    └─ Column (コンテンツエリア)
 * │         ├─ SearchBox (検索バー)
 * │         ├─ KanaIndexBar (五十音バー)
 * │         └─ UserList (LazyColumn)
 * │              └─ UserListItem (カード ＋ クイックメニュー [ QuickActionMenu ])
 * │
 * └─ [2] 副作用・シート群 (制御層内で完結)
 *      ├─ CategorySelectionSheet (機能選択ボトムシート)
 *      ├─ EmergencyContactSelectionSheet (緊急連絡先選択ボトムシート)
 *      └─ AppInfoDialog (通知・エラー用共通ダイアログ)
 *
 * 【このコンポーネントでは行わないこと】
 * ・具体的な UI レイアウトの構築（MainScreenContent が担当）。
 * ・ビジネスロジックの判定（ViewModel および Logic 層が担当）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // スクロール位置の復元 (rememberLazyListState は内部で rememberSaveable を使用しているため、そのまま維持で復元される)
    val lazyListState = rememberLazyListState()
    val userEndedFormat = stringResource(R.string.main_snackbar_user_ended)
    val undoLabel = stringResource(R.string.common_undo)
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // 緊急連絡先選択用のボトムシート状態
    val contactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ナビゲーションの結果（SavedStateHandle）を監視
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val editResultFlow = remember(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle?.getStateFlow<String?>(NavigationKeys.PERSON_EDIT_RESULT, null)
    }
    val editResult by editResultFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    val editNameFlow = remember(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle?.getStateFlow<String?>(NavigationKeys.PERSON_EDIT_NAME, null)
    }
    val editName by editNameFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    LaunchedEffect(editResult) {
        editResult?.let { result ->
            val messageRes = if (result == EditResult.ADDED.name) {
                R.string.main_msg_user_added
            } else {
                R.string.main_msg_user_updated
            }
            snackbarHostState.showSnackbar(context.getString(messageRes, editName ?: ""))
            // 通知を消費
            navBackStackEntry?.savedStateHandle?.remove<String>(NavigationKeys.PERSON_EDIT_RESULT)
            navBackStackEntry?.savedStateHandle?.remove<String>(NavigationKeys.PERSON_EDIT_NAME)
        }
    }

    // ViewModelからのイベントを監視 (共通通知イベントを使用)
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
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

    // ViewModelからの画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is PersonListViewEvent.NavigateToDetail -> {
                    navController.navigate(event.category.toDestination(event.personId, event.query))
                }
                is PersonListViewEvent.NavigateToBatchInput -> {
                    navController.navigate(Destination.BatchInput(event.personId))
                }
                is PersonListViewEvent.NavigateToAddPerson -> {
                    navController.navigate(Destination.PersonEdit(null))
                }
                is PersonListViewEvent.NavigateToEditPerson -> {
                    navController.navigate(Destination.PersonEdit(event.personId))
                }
                is PersonListViewEvent.NavigateToSettings -> {
                    navController.navigate(Destination.Settings)
                }
                is PersonListViewEvent.NavigateToMedicalContacts -> {
                    navController.navigate(Destination.MedicalContacts(event.personId))
                }
            }
        }
    }

    // アクションハンドラ：UI からの通知を ViewModel やナビゲーション、シート制御へ橋渡しする
    // 氏名マスク設定をキーに含めることで、設定変更時のみラムダを再生成し、通常時の不要な再描画を抑制する
    val handleAction: (MainUiAction) -> Unit = remember(viewModel, navController, scope, context, lazyListState, uiState.isNameMaskingEnabled) {
        { action ->
            when (action) {
                is MainUiAction.SearchQueryChange -> viewModel.setSearchQuery(action.query)
                is MainUiAction.SectionSelect -> viewModel.setSelectedSection(action.section)
                MainUiAction.AddClick -> viewModel.navigateToAddPerson()
                MainUiAction.NavigateToSettings -> viewModel.navigateToSettings()
                is MainUiAction.UserClick -> {
                    selectedPerson = action.person
                    showSheet = true
                }
                is MainUiAction.EditUser -> viewModel.navigateToEditPerson(action.person.id)
                is MainUiAction.EmergencyContactManageClick -> viewModel.navigateToMedicalContacts(action.person.id)
                is MainUiAction.QuickMenuClick -> viewModel.showQuickMenu(action.person)
                is MainUiAction.EmergencyContactClick -> viewModel.loadEmergencyContacts(action.person.id)
                MainUiAction.DismissQuickMenu -> viewModel.dismissQuickMenu()
                is MainUiAction.EndUser -> {
                    viewModel.logicalDeletePerson(action.person)
                    scope.launch {
                        val fullName = action.person.getMaskedName(uiState.isNameMaskingEnabled)
                        val result = snackbarHostState.showSnackbar(
                            message = userEndedFormat.format(fullName),
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restorePerson(action.person)
                            lazyListState.animateScrollToItem(0)
                        }
                    }
                }
                is MainUiAction.UndoEndUser -> {
                    viewModel.restorePerson(action.person)
                    scope.launch { lazyListState.animateScrollToItem(0) }
                }
                is MainUiAction.CategorySelect -> {
                    showSheet = false
                    selectedPerson?.let { viewModel.navigateToDetail(it.id, action.category) }
                }
                MainUiAction.BatchInputSelect -> {
                    showSheet = false
                    selectedPerson?.let { viewModel.navigateToBatchInput(it.id) }
                }
                MainUiAction.DismissSheet -> showSheet = false
                is MainUiAction.EmergencyContactCall -> {
                    val tel = action.contact.phoneNumber
                    if (!tel.isNullOrBlank()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, "tel:$tel".toUri())
                        context.startActivity(intent)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.medical_msg_no_phone_number))
                        }
                    }
                    viewModel.clearEmergencyContactState()
                }
                MainUiAction.DismissContactSheet -> viewModel.clearEmergencyContactState()
                MainUiAction.DismissDialog -> {
                    dialogMessage = null
                    dialogTitle = null
                }
            }
        }
    }

    //-- ui/screens/main/MainScreenContent.kt
    MainScreenContent(
        userList = uiState.userList,
        isLoading = uiState.isLoading,
        isNameMaskingEnabled = uiState.isNameMaskingEnabled,
        searchQuery = uiState.searchQuery,
        selectedSection = uiState.selectedSection,
        selectedPersonForQuickMenu = uiState.selectedPersonForQuickMenu,
        isQuickActionMenuExpanded = uiState.isQuickActionMenuExpanded,
        onAction = handleAction,
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        modifier = modifier,
    )

    // 通知ダイアログの表示
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = { handleAction(MainUiAction.DismissDialog) }
        )
    }

    // カテゴリ選択メニュー（下からスライド）
    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = { handleAction(MainUiAction.DismissSheet) }, sheetState = sheetState) {
            //-- ui/components/main/MainComponents.kt
            CategorySelectionSheet(
                personName = selectedPerson!!.getMaskedName(uiState.isNameMaskingEnabled),
                onAction = handleAction
            )
        }
    }

    // 緊急連絡先選択メニュー
    if (uiState.emergencyContactsForSheet != null && uiState.selectedPersonForQuickMenu != null) {
        ModalBottomSheet(
            onDismissRequest = { handleAction(MainUiAction.DismissContactSheet) },
            sheetState = contactSheetState
        ) {
            //-- (MainScreen.kt 内部または共通部品)
            EmergencyContactSelectionSheet(
                contacts = uiState.emergencyContactsForSheet!!,
                personName = uiState.selectedPersonForQuickMenu!!.getMaskedName(uiState.isNameMaskingEnabled),
                onAction = handleAction
            )
        }
    }
}

/**
 * Component：EmergencyContactSelectionSheet
 *
 * 【役割】
 * 特定の利用者に紐付く緊急連絡先を一覧表示し、タップによる発信操作、
 * または管理画面への遷移を選択するためのボトムシートです。
 *
 * @param contacts 表示対象の連絡先リスト
 * @param personName 利用者名（表示用）
 * @param onContactClick 連絡先がタップされた際のコールバック（発信等を想定）
 * @param onManageClick 管理画面への遷移が選択された際のコールバック
 */
@Composable
fun EmergencyContactSelectionSheet(
    contacts: ImmutableList<EmergencyContact>,
    personName: String,
    onAction: (MainUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.medical_contacts_title_format, personName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        contacts.forEach { contact ->
            ListItem(
                headlineContent = { Text(contact.facilityName) },
                supportingContent = {
                    val formattedPhone = EmergencyContactMapping.formatPhoneNumber(contact.phoneNumber)
                    val details = listOfNotNull(contact.personName, formattedPhone).joinToString(" / ")
                    if (details.isNotBlank()) Text(details)
                },
                leadingContent = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                modifier = Modifier.clickable { onAction(MainUiAction.EmergencyContactCall(contact)) }
            )
            HorizontalDivider()
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { onAction(MainUiAction.DismissContactSheet) }, // または専用のアクションがあればそれ
            modifier = Modifier.align(Alignment.End)
        ) {
            // ここは「管理」ボタンだったので、適切にアクションを割り当てる
            Text(stringResource(R.string.medical_contacts_manage_label))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
