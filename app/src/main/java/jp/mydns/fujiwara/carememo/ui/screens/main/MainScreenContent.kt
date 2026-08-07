package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.KanaIndexBar
import jp.mydns.fujiwara.carememo.ui.components.main.QuickActionMenu
import jp.mydns.fujiwara.carememo.ui.components.main.UserListItem
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.logic.feature.PersonUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate
import java.time.ZoneId

/**
 * 全体像：利用者一覧（Main）
 *
 * ■ ui/screens/main/MainScreenContent.kt の MainScreenContent (画面全体の器)
 * │
 * ├─ [Scaffold]
 * │    ├─ TopAppBar (アプリタイトル、設定・バージョンメニュー)
 * │    ├─ FloatingActionButton (利用者の新規追加ボタン ➔ [5] 画面へ遷移)
 * │    └─ SnackbarHost (メッセージ通知領域)
 * │
 * ├─【コンテンツエリア：Column】
 * │    ├─ [1] SearchBox (氏名・所見メモのリアルタイム検索：ui/components/base/SearchBox.kt)
 * │    ├─ [2] KanaIndexBar (五十音インデックスバー：ui/components/main/KanaIndexBar.kt)
 * │    ├─ <区切り線> HorizontalDivider
 * │    └─ [3] LazyColumn (メインリスト)
 * │         └─ [3-1] UserListItem (利用者カード：ui/components/main/MainComponents.kt)
 * │              ├─ [3-1-1] CategoryBadges (入力済み情報のバッジ：ui/components/main/CategoryBadges.kt)
 * │              ├─ CakeIcon (本日/近日誕生日の通知アイコン)
 * │              ├─ <表示情報> フリガナ、氏名(マスク対応)、識別メモ、生年月日、年齢
 * │              └─ <操作メニュー> DropdownMenu (情報編集 ➔ [5] 画面へ、利用終了)
 * │
 * └─【遷移・シート・ダイアログ群】
 *      ├─ [4] CategorySelectionSheet (機能選択シート：ui/components/main/MainComponents.kt)
 *      ├─ [5] PersonEditScreen (利用者の登録・編集画面：ui/screens/main/PersonEditScreen.kt)
 *      └─ [6] VersionDialog (アプリ情報・バージョン表示：MainScreenContent内に定義)
 */


/**
 * ■  MainScreenContent (画面全体の器)
 * 利用者一覧画面のUIレイアウト本体。
 * Scaffoldによる基本構造、検索ボックス、インデックスバー、利用者リストを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: ImmutableList<PersonUiState>,
    isLoading: Boolean,
    isNameMaskingEnabled: Boolean,
    searchQuery: String,
    selectedSection: String,
    selectedPersonForQuickMenu: Person?,
    isQuickActionMenuExpanded: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSectionSelect: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Person) -> Unit,
    onQuickMenuClick: (Person) -> Unit,
    onEmergencyContactClick: (Person) -> Unit,
    onEmergencyContactManageClick: (Person) -> Unit,
    onDismissQuickMenu: () -> Unit,
    onEditUser: (Person) -> Unit,
    onAddClick: () -> Unit,
    onEndUser: (Person) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val changelog = remember {
        try {
            context.assets.open("change.log").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    // [6] VersionDialog (アプリ情報・バージョン表示：MainScreenContent内に定義)
    if (showVersionDialog) {
        AppDialog(
            onDismissRequest = { showVersionDialog = false },
            modifier = Modifier.testTag("MainScreen_VersionDialog"),
            title = { Text(stringResource(R.string.main_dialog_version_title)) },
            text = {
                AppDialogContent {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("バージョン: ${BuildConfig.VERSION_NAME}")
                        Text("ビルド日時: ${BuildConfig.BUILD_TIME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        if (changelog.isNotBlank()) {
                            HorizontalDivider()
                            Text("更新履歴:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            
                            // 1行ずつ分解して「ぶら下げインデント」で描画
                            changelog.lines().filter { it.isNotBlank() }.forEach { line ->
                                val displayText = line.removePrefix("-").trim()
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("・", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("ターゲット環境:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Android 15 (API 35)")
                        Text("KYOCERA TORQUE G06 最適化済")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("(C) 2026 pirotty.galaxy", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_close),
                    onClick = { showVersionDialog = false }
                )
            }
        )
    }

    // [Scaffold] ----------------------------------------------------------------------------------
    Scaffold(
        topBar = {
            // --TopAppBar (アプリタイトル、設定・バージョンメニュー)
            TopAppBar(
                // アプリタイトル
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = appTopAppBarColors(),
                // ハンバーガー・メニュー
                actions = {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("MainScreen_MenuButton")) { Icon(Icons.Rounded.Menu, contentDescription = "メニュー") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // 設定
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { showMenu = false; onNavigateToSettings() },
                            modifier = Modifier.testTag("MainScreen_MenuItem_Settings")
                        )
                        // バージョン情報
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_version)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { showMenu = false; showVersionDialog = true },
                            modifier = Modifier.testTag("MainScreen_MenuItem_Version")
                        )
                    }
                }
            )
        },
        // SnackbarHost (メッセージ通知領域)
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // FloatingActionButton (利用者の新規追加ボタン)
        floatingActionButton = { FloatingActionButton(onClick = onAddClick, modifier = Modifier.testTag("MainScreen_AddButton")) { Icon(Icons.Rounded.PersonAddAlt1, contentDescription = stringResource(R.string.main_user_registration)) } }
    ) 
    
    // --【コンテンツエリア：Column】-------------------------------------------------------------------
    { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 全体の左右の余白
                    .padding(horizontal = 4.dp),
                // ########## 検索ボックス／五十音かなインデックス／利用者一覧の上下の余白 ##########
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // [1] SearchBox (氏名・所見メモのリアルタイム検索：ui/components/base/SearchBox.kt)
                SearchBox(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    label = stringResource(R.string.main_search_placeholder),
                    modifier = Modifier.testTag("MainScreen_SearchBox")
                )

                // [2] KanaIndexBar (五十音インデックスバー：ui/components/main/KanaIndexBar.kt)
                KanaIndexBar(
                    selectedSection = selectedSection,
                    onSectionSelect = onSectionSelect,
                    modifier = Modifier.testTag("MainScreen_KanaIndexBar")
                )

                //Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                //  [3] LazyColumn (メインリスト・利用者一覧)
                if (isLoading) {
                    LoadingScreen(modifier = Modifier.testTag("MainScreen_Loading"))
                } else if (userList.isEmpty()) {
                    EmptyState(
                        message = if (searchQuery.isNotEmpty()) stringResource(R.string.main_no_user_found) else stringResource(R.string.main_no_user_registered),
                        icon = if (searchQuery.isNotEmpty()) Icons.Rounded.Search else Icons.Rounded.PersonAddAlt1,
                        modifier = Modifier.testTag("MainScreen_EmptyState")
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("MainScreen_UserList"),
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(userList, key = { it.person.id }) { userUiState ->
                            Box {
                                // [3-1] UserListItem (利用者カード：ui/components/main/MainComponents.kt)
                                UserListItem(
                                    person = userUiState.person,
                                    summary = userUiState.summary,
                                    isNameMaskingEnabled = isNameMaskingEnabled,
                                    onClick = { onUserClick(userUiState.person) },
                                    onQuickMenuClick = { onQuickMenuClick(userUiState.person) },
                                    onEmergencyContactManageClick = { onEmergencyContactManageClick(userUiState.person) },
                                    onEditClick = { onEditUser(userUiState.person) },
                                    onDeleteClick = { onEndUser(userUiState.person) },
                                    modifier = Modifier
                                        .animateItem()
                                        .testTag("UserListItem_${userUiState.person.id}")
                                )

                                // クイックメニュー
                                QuickActionMenu(
                                    expanded = isQuickActionMenuExpanded && selectedPersonForQuickMenu?.id == userUiState.person.id,
                                    person = userUiState.person,
                                    isNameMaskingEnabled = isNameMaskingEnabled,
                                    onDismissRequest = onDismissQuickMenu,
                                    onEmergencyContactClick = { onEmergencyContactClick(userUiState.person) }
                                )
                            }
                            HorizontalDivider()
                        }
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
    val person1 = Person(id = "1", lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = LocalDate.of(1950, 1, 1).atStartOfDay(zoneId).toInstant())
    
    // もうすぐ誕生日の利用者 (明日が誕生日と仮定)
    val birthdaySoon = today.plusDays(1).minusYears(70)
    val person2 = Person(id = "2", lastName = "佐藤", firstName = "花子", lastNameFurigana = "サトウ", firstNameFurigana = "ハナコ", birthday = birthdaySoon.atStartOfDay(zoneId).toInstant())
    
    // 今日が誕生日の利用者
    val birthdayToday = today.minusYears(80)
    val person3 = Person(id = "3", lastName = "田中", firstName = "梅", lastNameFurigana = "タナカ", firstNameFurigana = "ウメ", birthday = birthdayToday.atStartOfDay(zoneId).toInstant())

    val mockUserList = persistentListOf(
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
            selectedPersonForQuickMenu = null,
            isQuickActionMenuExpanded = false,
            onSearchQueryChange = {},
            onSectionSelect = {},
            isNameMaskingEnabled = false,
            snackbarHostState = remember { SnackbarHostState() }, 
            lazyListState = rememberLazyListState(), 
            onUserClick = { }, 
            onQuickMenuClick = { },
            onEmergencyContactClick = { },
            onEmergencyContactManageClick = { },
            onDismissQuickMenu = { },
            onEditUser = { }, 
            onAddClick = { }, 
            onEndUser = { }, 
            onNavigateToSettings = { }
        ) 
    }
}
