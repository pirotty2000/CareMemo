package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonUiState
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * MainScreen (利用者一覧) の UI テスト
 * 
 * 仕様書：doc/test/TEST_SPEC_UI_Main.md に準拠
 */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ======================================================================================
    // 1. コンポーネント単体テスト (MainScreenContent)
    // ======================================================================================

    @Test
    fun cp01_emptyState_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(),
                    isLoading = false,
                    isNameMaskingEnabled = false,
                    searchQuery = "",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: 空状態のメッセージが表示されていること
        composeTestRule.onNodeWithTag("MainScreen_EmptyState").assertIsDisplayed()
        Thread.sleep(2000)
    }

    @Test
    fun cp02_loadingState_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(),
                    isLoading = true,
                    isNameMaskingEnabled = false,
                    searchQuery = "",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: ローディング画面が表示されていること
        composeTestRule.onNodeWithTag("MainScreen_Loading").assertIsDisplayed()
        Thread.sleep(2000)
    }

    @Test
    fun cp03_userList_isDisplayed() {
        val mockUserList = listOf(
            PersonUiState(
                person = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now()),
                maskedName = "山田　太郎", maskedFurigana = "ヤマダ　タロウ", age = 70, formattedBirthday = "", summary = PersonCategorySummary()
            )
        )

        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = mockUserList,
                    isLoading = false,
                    isNameMaskingEnabled = false,
                    searchQuery = "",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: 利用者リストと特定の項目が表示されていること
        composeTestRule.onNodeWithTag("MainScreen_UserList").assertExists()
        composeTestRule.onNodeWithTag("UserListItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田", substring = true).assertIsDisplayed()
        Thread.sleep(2000)
    }

    @Test
    fun cp04_nameMasking_isApplied() {
        val mockUserList = listOf(
            PersonUiState(
                person = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now()),
                maskedName = "山○　太○", maskedFurigana = "ヤ○ダ　タ○ウ", age = 70, formattedBirthday = "", summary = PersonCategorySummary()
            )
        )

        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = mockUserList,
                    isLoading = false,
                    isNameMaskingEnabled = true, // 有効
                    searchQuery = "",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: 伏せ字の名前が表示されていること
        composeTestRule.onNodeWithText("山○", substring = true).assertExists()
        Thread.sleep(2000)
    }

    @Test
    fun cp05_recordBadges_areDisplayed() {
        val mockUserList = listOf(
            PersonUiState(
                person = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now()),
                maskedName = "山田　太郎", maskedFurigana = "ヤマダ　タロウ", age = 70, formattedBirthday = "",
                summary = PersonCategorySummary(hasBpAndPulse = true, hasCondition = true, hasMedication = true)
            )
        )

        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = mockUserList,
                    isLoading = false,
                    isNameMaskingEnabled = false,
                    searchQuery = "",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: 各種バッジ（アイコン）が表示されていること
        composeTestRule.onNodeWithContentDescription("血圧・脈拍の記録あり", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("所見メモの記録あり", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("服薬の記録あり", substring = true).assertExists()
        Thread.sleep(2000)
    }

    @Test
    fun cp06_searchNoResult_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(),
                    isLoading = false,
                    isNameMaskingEnabled = false,
                    searchQuery = "該当者なし",
                    selectedSection = "全",
                    onSearchQueryChange = {},
                    onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(),
                    onUserClick = {},
                    onEditUser = {},
                    onAddClick = {},
                    onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // Then: 「一致する利用者は見つかりませんでした」が表示されていること
        composeTestRule.onNodeWithText("一致する利用者は見つかりませんでした", substring = true).assertIsDisplayed()
        Thread.sleep(2000)
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (MainScreen)
    // ======================================================================================

    @Test
    fun bh01_hamburgerMenu_navigation() {
        var navigated = false
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, onAddClick = {}, onEndUser = {},
                    onNavigateToSettings = { navigated = true }
                )
            }
        }

        // 1. メニューボタンをタップ
        composeTestRule.onNodeWithTag("MainScreen_MenuButton").performClick()
        Thread.sleep(1000)

        // 2. 「設定」をタップ
        composeTestRule.onNodeWithTag("MainScreen_MenuItem_Settings").performClick()
        assert(navigated)
        Thread.sleep(1000)
    }

    @Test
    fun bh02_searchFunction_callsCallback() {
        var queryInput = ""
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = { queryInput = it }, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, onAddClick = {}, onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // 1. 検索窓に入力
        composeTestRule.onNodeWithTag("MainScreen_SearchBox").performTextInput("テスト")
        assert(queryInput == "テスト")
        Thread.sleep(2000)
    }

    @Test
    fun bh03_kanaIndex_callsCallback() {
        var sectionInput = ""
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = { sectionInput = it }, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, onAddClick = {}, onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // 1. 「か」をタップ
        composeTestRule.onNodeWithText("か").performClick()
        assert(sectionInput == "か")
        Thread.sleep(2000)
    }

    @Test
    fun bh04_userMenu_edit_callsCallback() {
        var editCalled = false
        val mockPerson = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now())
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = listOf(PersonUiState(person = mockPerson, maskedName = "山田　太郎", maskedFurigana = "ヤマダ　タロウ", age = 70, formattedBirthday = "", summary = PersonCategorySummary())),
                    isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, 
                    onEditUser = { editCalled = true }, 
                    onAddClick = {}, onEndUser = {},
                    onNavigateToSettings = {}
                )
            }
        }

        // 1. 各項目のメニューボタン（鉛筆アイコン）をタップ
        // UserListItem 内部のタグが必要
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        Thread.sleep(1000)

        // 2. 「利用者情報を編集」をタップ
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Edit").performClick()
        assert(editCalled)
        Thread.sleep(2000)
    }

    @Test
    fun bh05_endUser_and_undo_works() {
        var deleteCalled = false
        var undoCalled = false
        val mockPerson = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now())

        composeTestRule.setContent {
            CareMemoTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                MainScreenContent(
                    userList = listOf(PersonUiState(person = mockPerson, maskedName = "山田　太郎", maskedFurigana = "ヤマダ　タロウ", age = 70, formattedBirthday = "", summary = PersonCategorySummary())),
                    isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = snackbarHostState,
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, onAddClick = {}, 
                    onEndUser = {
                        deleteCalled = true
                        scope.launch {
                            val result = snackbarHostState.showSnackbar("利用終了", actionLabel = "元に戻す")
                            if (result == SnackbarResult.ActionPerformed) undoCalled = true
                        }
                    },
                    onNavigateToSettings = {}
                )
            }
        }

        // 1. 利用終了操作
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        Thread.sleep(500)
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Delete").performClick()
        assert(deleteCalled)
        Thread.sleep(1000)

        // 2. Undo
        composeTestRule.onNodeWithText("元に戻す").performClick()
        assert(undoCalled)
        Thread.sleep(2000)
    }

    @Test
    fun bh06_addButton_callsCallback() {
        var addCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, 
                    onAddClick = { addCalled = true }, 
                    onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }

        // 1. ＋ボタンをタップ
        composeTestRule.onNodeWithTag("MainScreen_AddButton").performClick()
        assert(addCalled)
        Thread.sleep(2000)
    }
}
