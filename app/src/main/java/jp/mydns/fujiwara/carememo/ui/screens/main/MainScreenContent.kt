package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.BuildConfig
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
 * Screen：MainScreenContent
 *
 * 【役割】
 * 利用者一覧画面（MainScreen）の主要な UI レイアウト本体を構築します。
 *
 * @param userList 利用者リスト
 * @param isLoading ロード中かどうか
 * @param isNameMaskingEnabled 氏名マスク有効かどうか
 * @param searchQuery 検索クエリ
 * @param selectedSection 五十音選択セクション
 * @param selectedPersonForQuickMenu クイックメニュー対象
 * @param isQuickActionMenuExpanded クイックメニュー展開中か
 * @param onAction アクションハンドラ
 * @param snackbarHostState スナックバー制御
 * @param lazyListState リスト状態
 * @param modifier 修飾子
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
    onAction: (MainUiAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyLog = remember(showVersionDialog) {
        if (!showVersionDialog) ""
        else {
            try {
                val text = context.assets.open("change_history.log").bufferedReader().use { it.readText() }
                android.util.Log.d("VersionDialog", "Successfully read change_history.log: ${text.length} chars")
                text
            } catch (e: Exception) {
                android.util.Log.e("VersionDialog", "Failed to read change_history.log", e)
                ""
            }
        }
    }

    // [6] VersionDialog
    if (showVersionDialog) {
        AppDialog(
            onDismissRequest = { showVersionDialog = false },
            modifier = Modifier.testTag("MainScreen_VersionDialog"),
            title = { Text(stringResource(R.string.main_dialog_version_title)) },
            text = {
                AppDialogContent {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // --- 固定ヘッダー部分 ---
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("バージョン: ${BuildConfig.VERSION_NAME}")
                        Text(
                            text = "ビルド日時: ${BuildConfig.BUILD_TIME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (historyLog.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            // --- スクロール可能な履歴エリア ---
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                historyLog.lines().forEach { line ->
                                    val trimmedLine = line.trim()
                                    when {
                                        trimmedLine.startsWith("#") -> {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = trimmedLine.removePrefix("#").trim(),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        trimmedLine.startsWith("-") -> {
                                            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                                                Text("・", style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    text = trimmedLine.removePrefix("-").trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        trimmedLine.isBlank() -> {
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        else -> {
                                            Text(
                                                text = trimmedLine,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        // --- 固定フッター部分 ---
                                    Text(stringResource(R.string.main_dialog_version_build_time, BuildConfig.BUILD_TIME), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.main_dialog_version_optimized_device), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.main_dialog_version_copyright),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = appTopAppBarColors(),
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("MainScreen_MenuButton")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.main_desc_op_menu)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { showMenu = false; onAction(MainUiAction.NavigateToSettings) },
                            modifier = Modifier.testTag("MainScreen_MenuItem_Settings")
                        )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { 
            FloatingActionButton(
                onClick = { onAction(MainUiAction.AddClick) }, 
                modifier = Modifier.testTag("MainScreen_AddButton")
            ) { 
                Icon(Icons.Rounded.PersonAddAlt1, contentDescription = stringResource(R.string.main_user_registration)) 
            } 
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SearchBox(
                    query = searchQuery,
                    onQueryChange = { onAction(MainUiAction.SearchQueryChange(it)) },
                    label = stringResource(R.string.main_search_placeholder),
                    modifier = Modifier.testTag("MainScreen_SearchBox")
                )

                KanaIndexBar(
                    selectedSection = selectedSection,
                    onSectionSelect = { onAction(MainUiAction.SectionSelect(it)) },
                    modifier = Modifier.testTag("MainScreen_KanaIndexBar")
                )

                HorizontalDivider()

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
                                UserListItem(
                                    person = userUiState.person,
                                    summary = userUiState.summary,
                                    isNameMaskingEnabled = isNameMaskingEnabled,
                                    onAction = onAction,
                                    modifier = Modifier
                                        .animateItem()
                                        .testTag("UserListItem_${userUiState.person.id}")
                                )

                                QuickActionMenu(
                                    expanded = isQuickActionMenuExpanded && selectedPersonForQuickMenu?.id == userUiState.person.id,
                                    person = userUiState.person,
                                    isNameMaskingEnabled = isNameMaskingEnabled,
                                    onAction = onAction
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
            isNameMaskingEnabled = false,
            searchQuery = "",
            selectedSection = "全",
            selectedPersonForQuickMenu = null,
            isQuickActionMenuExpanded = false,
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() }, 
            lazyListState = rememberLazyListState()
        ) 
    }
}
