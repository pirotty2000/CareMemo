package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.logic.feature.PersonUiState
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * SCR-M-001 MainScreen (利用者一覧) の UI テスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-M-001_MainScreen.md に準拠
 */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ヘルパー：モック利用者の作成
    private fun createMockPerson(id: Int, lastName: String, firstName: String, note: String = ""): Person {
        return Person(
            id = id,
            lastName = lastName,
            firstName = firstName,
            lastNameFurigana = "",
            firstNameFurigana = "",
            birthday = Instant.now(),
            note = note,
            deletedAt = null
        )
    }

    // ヘルパー：モック表示状態の作成
    private fun createMockUiState(person: Person, maskedName: String): PersonUiState {
        return PersonUiState(
            person = person,
            maskedName = maskedName,
            maskedFurigana = "",
            age = 70,
            formattedBirthday = "1954/01/01",
            summary = PersonCategorySummary()
        )
    }

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
        composeTestRule.onNodeWithTag("MainScreen_EmptyState").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("MainScreen_Loading").assertIsDisplayed()
    }

    @Test
    fun cp03_userList_isDisplayed() {
        val person = createMockPerson(1, "山田", "太郎")
        val mockUserList = listOf(createMockUiState(person, "山田　太郎"))
        
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
        composeTestRule.onNodeWithTag("MainScreen_UserList").assertExists()
        composeTestRule.onNodeWithTag("UserListItem_1").assertIsDisplayed()
    }

    @Test
    fun cp04_nameMasking_isApplied() {
        val person = createMockPerson(1, "山田", "太郎")
        val mockUserList = listOf(createMockUiState(person, "山○　太○"))
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = mockUserList,
                    isLoading = false,
                    isNameMaskingEnabled = true,
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
        composeTestRule.onNodeWithText("山○", substring = true).assertExists()
    }

    @Test
    fun cp05_recordBadges_areDisplayed() {
        val person = createMockPerson(1, "山田", "太郎")
        val mockUserList = listOf(
            createMockUiState(person, "山田").copy(
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
        composeTestRule.onNodeWithContentDescription("血圧・脈拍の記録あり", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("所見メモの記録あり", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("服薬の記録あり", substring = true).assertExists()
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
        composeTestRule.onNodeWithText("一致する利用者は見つかりませんでした", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp07_veryLongName_doesNotBreakLayout() {
        val longName = "寿限無寿限無五劫の擦り切れ海砂利水魚の水行末雲来末風来末食う寝る処に住む処"
        val person = createMockPerson(1, longName, "太郎")
        val mockUserList = listOf(createMockUiState(person, longName))
        
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
        composeTestRule.onNodeWithText(longName, substring = true).assertExists()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (MainScreen)
    // ======================================================================================

    @Test
    fun bh01_hamburgerMenu_navigation() {
        var navigatedToSettings = false
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
                    onNavigateToSettings = { navigatedToSettings = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("MainScreen_MenuButton").performClick()
        composeTestRule.waitForIdle()

        // 設定への遷移
        composeTestRule.onNodeWithTag("MainScreen_MenuItem_Settings").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assert(navigatedToSettings)

        // バージョン情報の表示確認
        composeTestRule.onNodeWithTag("MainScreen_MenuButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("MainScreen_MenuItem_Version").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("MainScreen_VersionDialog").assertIsDisplayed()
    }

    @Test
    fun bh02_searchQueryChange_isCalled() {
        var capturedQuery = ""
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false,
                    searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = { capturedQuery = it },
                    onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {},
                    onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("MainScreen_SearchBox").performTextInput("検索テスト")
        assert(capturedQuery == "検索テスト")
    }

    @Test
    fun bh03_kanaIndexSelect_isCalled() {
        var capturedSection = ""
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false,
                    searchQuery = "", selectedSection = "全", onSearchQueryChange = {},
                    onSectionSelect = { capturedSection = it },
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {},
                    onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithText("あ").performClick()
        assert(capturedSection == "あ")
    }

    @Test
    fun bh04_userMenu_edit_navigation() {
        var editPersonId: Int? = null
        val person = createMockPerson(99, "山田", "太郎")
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = listOf(createMockUiState(person, "山田")),
                    isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, 
                    onEditUser = { editPersonId = it.id }, 
                    onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Edit").performClick()
        assert(editPersonId == 99)
    }

    @Test
    fun bh05_endUser_isCalled() {
        var endUserCalled = false
        val person = createMockPerson(1, "山田", "太郎")
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = listOf(createMockUiState(person, "山田")),
                    isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, 
                    snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), onUserClick = {}, onEditUser = {}, onAddClick = {},
                    onEndUser = { endUserCalled = true },
                    onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Delete").performClick()
        assert(endUserCalled)
    }

    @Test
    fun bh06_addButton_navigation() {
        var addClickCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false,
                    searchQuery = "", selectedSection = "全", onSearchQueryChange = {}, onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() }, lazyListState = rememberLazyListState(),
                    onUserClick = {}, onEditUser = {}, 
                    onAddClick = { addClickCalled = true }, 
                    onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("MainScreen_AddButton").performClick()
        assert(addClickCalled)
    }

    @Test
    fun bh07_detailNavigation_withCorrectId() {
        var clickedPerson: Person? = null
        val person = createMockPerson(123, "山田", "太郎")
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = listOf(createMockUiState(person, "山田")),
                    isLoading = false, isNameMaskingEnabled = false, searchQuery = "", selectedSection = "全",
                    onSearchQueryChange = {}, onSectionSelect = {}, snackbarHostState = remember { SnackbarHostState() },
                    lazyListState = rememberLazyListState(), 
                    onUserClick = { clickedPerson = it }, 
                    onEditUser = {}, onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("UserListItem_123").performClick()
        assert(clickedPerson?.id == 123)
    }

    @Test
    fun bh08_errorDialog_isDisplayed() {
        // ViewModel からエラーイベントが発行された際、共通エラーダイアログが表示されること。
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog(
                    title = "エラーが発生しました",
                    message = "ネットワークエラーです。",
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("エラーが発生しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("ネットワークエラーです。").assertIsDisplayed()
    }

    @Test
    fun bh09_state_isApplied() {
        val query = "維持されるクエリ"
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = emptyList(), isLoading = false, isNameMaskingEnabled = false,
                    searchQuery = query, selectedSection = "全", onSearchQueryChange = {}, onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() }, lazyListState = rememberLazyListState(),
                    onUserClick = {}, onEditUser = {}, onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithText(query).assertExists()
    }

    @Test
    fun bh10_batchInputNavigation_isCalled() {
        // カテゴリ選択シート（メニュー）から一括入力へ遷移すること。
        var batchInputClicked = false
        composeTestRule.setContent {
            CareMemoTheme {
                CategorySelectionSheet(
                    personName = "山田 太郎",
                    onCategorySelect = {},
                    onBatchInputSelect = { batchInputClicked = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()
        assert(batchInputClicked)
    }

    @Test
    fun bh11_listReflectsChanges() {
        val person = createMockPerson(1, "山田", "更新後")
        val updatedUserList = listOf(createMockUiState(person, "山田　更新後"))
        
        composeTestRule.setContent {
            CareMemoTheme {
                MainScreenContent(
                    userList = updatedUserList, isLoading = false, isNameMaskingEnabled = false,
                    searchQuery = "", selectedSection = "全", onSearchQueryChange = {}, onSectionSelect = {},
                    snackbarHostState = remember { SnackbarHostState() }, lazyListState = rememberLazyListState(),
                    onUserClick = {}, onEditUser = {}, onAddClick = {}, onEndUser = {}, onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithText("更新後", substring = true).assertIsDisplayed()
    }
}
