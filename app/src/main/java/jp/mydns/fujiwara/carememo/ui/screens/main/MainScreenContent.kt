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
import jp.mydns.fujiwara.carememo.ui.components.main.UserListItem
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonUiState
import java.time.LocalDate
import java.time.ZoneId

/**
 * 利用者一覧画面のUIレイアウト本体。
 * Scaffoldによる基本構造、検索ボックス、インデックスバー、利用者リストを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<PersonUiState>,
    isLoading: Boolean,
    isNameMaskingEnabled: Boolean,
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
        AppDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text(stringResource(R.string.main_dialog_version_title)) },
            text = {
                AppDialogContent {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("バージョン: ${BuildConfig.VERSION_NAME}")
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
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_close),
                    onClick = { showVersionDialog = false }
                )
            }
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
                            text = { Text(stringResource(R.string.main_menu_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { showMenu = false; onNavigateToSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_version)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { showMenu = false; showVersionDialog = true }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Rounded.PersonAddAlt1, contentDescription = stringResource(R.string.main_user_registration)) } }
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
                label = stringResource(R.string.main_search_placeholder)
            )

            // ---------- 名前(ふりがな)インデックス ----------
            KanaIndexBar(
                selectedSection = selectedSection,
                onSectionSelect = onSectionSelect
            )

            //Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            // ---------- 利用者一覧 ----------
            if (isLoading) {
                LoadingScreen()
            } else if (userList.isEmpty()) {
                EmptyState(
                    message = if (searchQuery.isNotEmpty()) stringResource(R.string.main_no_user_found) else stringResource(R.string.main_no_user_registered),
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
