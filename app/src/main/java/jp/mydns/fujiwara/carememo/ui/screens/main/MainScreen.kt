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
 * ・利用者管理（ダイアログによる登録・編集、論理削除とUndo機能）
 * ・カテゴリ遷移（利用者選択時のボトムシートから健康記録・所見メモ・服薬管理・一括入力へ遷移）
 * ・システムメニュー（アプリ設定、ヘルプ、バージョン情報）
 *
 * 【遷移】：
 * → PersonHealthScreen (詳細画面：健康記録)
 * → PersonConditionScreen (詳細画面：所見メモ)
 * → PersonMedicationScreen (詳細画面：服薬管理)
 * → BatchInputScreen (健康記録の一括入力)
 * → SettingsScreen (アプリ設定)
 *
 * 【使用するViewModel】：
 * PersonListViewModel
 *
 * 【使用するComponents】：
 * ・main/CategoryBadges.kt
 * ・main/CompactTextField.kt
 * ・main/KanaIndexBar.kt
 * ・base/BirthdayInputFields.kt
 * ・base/EmptyState.kt
 * ・base/InfoDialog.kt
 * ・base/SearchBox.kt
 * ・base/AppTopAppBarColors.kt
 * ・base/LoadingScreen.kt
 *
 * 【備考】：
 * UIの状態管理とイベント処理（Snackbar表示等）を担当。データ操作および検索ロジックはViewModelに集約。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.ui.components.main.KanaIndexBar
import jp.mydns.fujiwara.carememo.ui.components.main.UserEditDialog
import jp.mydns.fujiwara.carememo.ui.components.main.UserListItem
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonUiState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 利用者一覧画面のメインエントランス。
 * ViewModelとの接続、UI状態の監視、ダイアログやボトムシートの表示制御を行う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    onNavigateToDetail: (Int, Category) -> Unit,
    onNavigateToBatchInput: (Int) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userList by viewModel.userList.collectAsState() // 表示対象の利用者リスト
    val isLoading by viewModel.isLoading.collectAsState() // データ読み込み中フラグ
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState() // 名前マスキングの有効状態
    val selectedSection by viewModel.selectedSection.collectAsState() // 五十音インデックスの選択状態
    val searchQuery by viewModel.searchQuery.collectAsState() // 検索クエリ
    
    val snackbarHostState = remember { SnackbarHostState() } // スナックバー制御用
    val scope = rememberCoroutineScope() // 非同期処理用スコープ
    val lazyListState = rememberLazyListState() // リストの表示位置管理用
    val userEndedFormat = stringResource(R.string.snackbar_user_ended) // 終了メッセージ用フォーマット
    val undoLabel = stringResource(R.string.undo) // Undoボタン用ラベル

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // ボトムシートの状態管理
    var showSheet by remember { mutableStateOf(false) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    // ダイアログ表示用の状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.SaveSuccess -> {
                    showEditDialog = false
                }
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> {
                    // MainScreenでは使用しない
                }
            }
        }
    }

    MainScreenContent(
        userList = userList,
        isLoading = isLoading,
        isNameMaskingEnabled = isNameMaskingEnabled, // 追加
        searchQuery = searchQuery,
        selectedSection = selectedSection,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSectionSelect = { viewModel.setSelectedSection(it) },
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        onUserClick = { person -> selectedPerson = person; showSheet = true },
        onEditUser = { person -> editingPerson = person; showEditDialog = true },
        onAddClick = { editingPerson = null; showEditDialog = true },
        onEndUser = { person ->
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
        InfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }

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

    if (showEditDialog) {
        UserEditDialog(
            person = editingPerson, 
            onDismiss = { showEditDialog = false }, 
            onSave = { person -> 
                if (editingPerson == null) viewModel.addPerson(person) else viewModel.updatePerson(person)
                // ここで showEditDialog = false にしない（ViewModelからの成功通知を待つ）
            }
        )
    }
}

/**
 * 利用者一覧画面のUIレイアウト本体。
 * Scaffoldによる基本構造、検索ボックス、インデックスバー、利用者リストを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<PersonUiState>,
    isLoading: Boolean,
    isNameMaskingEnabled: Boolean, // 追加
    searchQuery: String,
    selectedSection: String,
    onSearchQueryChange: (String) -> Unit,
    onSectionSelect: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Person) -> Unit,
    onEditUser: (Person) -> Unit,
    onAddClick: () -> Unit,
    onEndUser: (Person) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }

    // バージョン情報ダイアログ
    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text(stringResource(R.string.dialog_version_title)) },
            text = {
                val scrollState = rememberScrollState()
                Box {
                    Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("バージョン: ${BuildConfig.VERSION_NAME}")
                        HorizontalDivider()
                        Text("ターゲット環境:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Android 15 (API 35)")
                        Text("KYOCERA TORQUE G06 最適化済")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("(C) 2026 pirotty.galaxy"
                            , style = MaterialTheme.typography.bodySmall)
                    }
                    VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
                }
            },
            confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    // ハンバーガーメニューの内容
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = appTopAppBarColors(),
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.Menu, contentDescription = "メニュー") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { showMenu = false; onNavigateToSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_version)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { showMenu = false; showVersionDialog = true }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Rounded.PersonAddAlt1, contentDescription = stringResource(R.string.user_registration)) } }
    ) 
    
    // メインのコンテンツ
    { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize().padding(paddingValues)
            // 全体の左右の余白
            .padding(horizontal = 4.dp),
            // ########## 検索ボックス／五十音かなインデックス／利用者一覧の上下の余白 ##########
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ---------- 所見メモ検索 ----------
            SearchBox(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                label = stringResource(R.string.search_memo_placeholder)
            )

            // ---------- 名前(ふりがな)インデックス ----------
            KanaIndexBar(
                selectedSection = selectedSection,
                onSectionSelect = onSectionSelect
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ---------- 利用者一覧 ----------
            if (isLoading) {
                LoadingScreen()
            } else if (userList.isEmpty()) {
                EmptyState(
                    message = if (searchQuery.isNotEmpty()) stringResource(R.string.no_user_found) else stringResource(R.string.no_user_registered),
                    icon = if (searchQuery.isNotEmpty()) Icons.Rounded.Search else Icons.Rounded.PersonAddAlt1
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(userList, key = { it.person.id }) { userUiState ->
                        UserListItem(
                            person = userUiState.person,
                            summary = userUiState.summary,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            onClick = { onUserClick(userUiState.person) },
                            onEditClick = { onEditUser(userUiState.person) },
                            onDeleteClick = { onEndUser(userUiState.person) },
                            modifier = Modifier.animateItem()
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}


/**
 * MainScreenのプレビュー用コンポーザブル。
 * 開発時のUI確認用にモックデータを使用して画面を表示する。
 */
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    
    // 通常の利用者
    val person1 = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = LocalDate.of(1950, 1, 1).atStartOfDay(zoneId).toInstant())
    
    // もうすぐ誕生日の利用者 (明日が誕生日と仮定)
    val birthdaySoon = today.plusDays(1).minusYears(70)
    val person2 = Person(id = 2, lastName = "佐藤", firstName = "花子", lastNameFurigana = "サトウ", firstNameFurigana = "ハナコ", birthday = birthdaySoon.atStartOfDay(zoneId).toInstant())
    
    // 今日が誕生日の利用者
    val birthdayToday = today.minusYears(80)
    val person3 = Person(id = 3, lastName = "田中", firstName = "梅", lastNameFurigana = "タナカ", firstNameFurigana = "ウメ", birthday = birthdayToday.atStartOfDay(zoneId).toInstant())

    val mockUserList = listOf(
        PersonUiState(
            person = person1,
            maskedName = "山○\u3000太○",
            maskedFurigana = "ヤ○ダ\u3000タ○ウ",
            age = 75,
            formattedBirthday = "昭和25年1月1日",
            summary = PersonCategorySummary(hasBpAndPulse = true)
        ),
        PersonUiState(
            person = person2,
            maskedName = "佐○\u3000花○",
            maskedFurigana = "サ○ウ\u3000ハ○コ",
            age = 70,
            formattedBirthday = "昭和30年10月10日",
            summary = PersonCategorySummary(hasCondition = true)
        ),
        PersonUiState(
            person = person3,
            maskedName = "田○\u3000梅",
            maskedFurigana = "タ○カ\u3000ウメ",
            age = 80,
            formattedBirthday = "昭和20年2月10日",
            summary = PersonCategorySummary(hasMedication = true)
        )
    )
    CareMemoTheme { 
        MainScreenContent(
            userList = mockUserList, 
            isLoading = false,
            searchQuery = "",
            selectedSection = "全",
            onSearchQueryChange = {},
            onSectionSelect = {},
            isNameMaskingEnabled = false,
            snackbarHostState = remember { SnackbarHostState() }, 
            lazyListState = rememberLazyListState(), 
            onUserClick = { }, 
            onEditUser = { }, 
            onAddClick = { }, 
            onEndUser = { }, 
            onNavigateToSettings = { }
        ) 
    }
}
