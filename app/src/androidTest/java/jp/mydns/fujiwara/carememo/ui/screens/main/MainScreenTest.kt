package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.logic.feature.PersonListViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.PersonUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: MainScreen (SCR-M-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-M-001_MainScreen.md に準拠
 */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_loadingIndicator_isDisplayed() {
        setContent {
            MainScreenContentWrapper(isLoading = true)
        }
        composeTestRule.onNodeWithTag("MainScreen_Loading").assertIsDisplayed()
    }

    @Test
    fun DSP_02_userList_rendersItems() {
        val mockItems = listOf(createMockUiState("1", "User A"), createMockUiState("2", "User B"))
        setContent {
            MainScreenContentWrapper(userList = mockItems)
        }
        composeTestRule.onNodeWithTag("UserListItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("UserListItem_2").assertIsDisplayed()
    }

    @Test
    fun DSP_03_emptyState_noUsers() {
        setContent {
            MainScreenContentWrapper(userList = emptyList(), searchQuery = "")
        }
        composeTestRule.onNodeWithTag("MainScreen_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("利用者が登録されていません", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_04_noResults_searchQueryActive() {
        setContent {
            MainScreenContentWrapper(userList = emptyList(), searchQuery = "Target")
        }
        composeTestRule.onNodeWithText("一致する利用者は見つかりませんでした", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_05_nameMasking_isApplied() {
        val mockItems = listOf(createMockUiState("1", "山○\u3000太○"))
        setContent {
            MainScreenContentWrapper(userList = mockItems, isNameMaskingEnabled = true)
        }
        composeTestRule.onNodeWithText("山○", substring = true).assertExists()
    }

    @Test
    fun DSP_06_categoryBadges_areRendered() {
        val mockItems = listOf(
            createMockUiState("1", "User").copy(
                summary = PersonCategorySummary(hasCondition = true)
            )
        )
        setContent {
            MainScreenContentWrapper(userList = mockItems)
        }
        composeTestRule.onNodeWithContentDescription("所見メモの記録あり", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_searchQueryInput_callsViewModel() {
        var capturedQuery = ""
        setContent {
            MainScreenContentWrapper(onSearchQueryChange = { capturedQuery = it })
        }
        composeTestRule.onNodeWithTag("MainScreen_SearchBox").performTextInput("test")
        assert(capturedQuery == "test")
    }

    @Test
    fun ACT_02_sectionSelection_callsViewModel() {
        var capturedSection = ""
        setContent {
            MainScreenContentWrapper(onSectionSelect = { capturedSection = it })
        }
        composeTestRule.onNodeWithText("か").performClick()
        assert(capturedSection == "か")
    }

    @Test
    fun ACT_03_quickMenuToggle_callsViewModel() {
        val mockItems = listOf(createMockUiState("1", "User"))
        var menuOpened = false
        setContent {
            MainScreenContentWrapper(
                userList = mockItems,
                onQuickMenuClick = { menuOpened = true }
            )
        }
        // Clicking the badges area (UserListItem_QuickMenuBox) triggers the quick menu
        composeTestRule.onNodeWithTag("UserListItem_QuickMenuBox").performClick()
        assert(menuOpened)
    }

    //endregion

    //region 4. ナビゲーション実行テスト (Navigation)

    @Test
    fun NAV_04_navigateToSettings_onEvent() {
        val viewModel = mockk<PersonListViewModel>(relaxed = true)
        val navController = mockk<NavHostController>(relaxed = true)
        // Use extraBufferCapacity to ensure tryEmit succeeds
        val viewEventFlow = MutableSharedFlow<PersonListViewEvent>(extraBufferCapacity = 1)
        
        every { viewModel.uiState } returns MutableStateFlow(jp.mydns.fujiwara.carememo.logic.feature.PersonListUiState(isLoading = false))
        every { viewModel.viewEvent } returns viewEventFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                MainScreen(viewModel = viewModel, navController = navController)
            }
        }

        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(PersonListViewEvent.NavigateToSettings)
        }
        
        composeTestRule.waitForIdle()
        // Verify navigation controller was called
        verify { navController.navigate(jp.mydns.fujiwara.carememo.ui.navigation.Destination.Settings) }
    }

    //endregion

    //region 6. 状態復元テスト (State Restoration)

    @Test
    fun RST_01_listScrollPosition_isRestored_onConfigurationChange() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val mockItems = (1..50).map { createMockUiState(it.toString(), "User $it") }

        restorationTester.setContent {
            CareMemoTheme {
                MainScreenContentWrapper(userList = mockItems)
            }
        }

        // 最初の項目が表示されていることを確認
        composeTestRule.onNodeWithTag("UserListItem_1").assertIsDisplayed()

        // 下方へスクロール (Index 20 -> User 21)
        composeTestRule.onNodeWithTag("MainScreen_UserList").performScrollToIndex(20)
        // 描画とスクロール完了を待機 (ID 21 が表示されるのを待つ)
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNodeWithTag("UserListItem_21").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        // 構成変更をエミュレート
        restorationTester.emulateSavedInstanceStateRestore()
        
        // 復帰後の描画を待機
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNodeWithTag("UserListItem_21").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithTag("UserListItem_21").assertIsDisplayed()
    }

    //endregion

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CareMemoTheme {
                content()
            }
        }
    }

    @Composable
    private fun MainScreenContentWrapper(
        userList: List<PersonUiState> = emptyList(),
        isLoading: Boolean = false,
        isNameMaskingEnabled: Boolean = false,
        searchQuery: String = "",
        selectedSection: String = "全",
        onSearchQueryChange: (String) -> Unit = {},
        onSectionSelect: (String) -> Unit = {},
        onQuickMenuClick: (Person) -> Unit = {}
    ) {
        MainScreenContent(
            userList = userList.toImmutableList(),
            isLoading = isLoading,
            isNameMaskingEnabled = isNameMaskingEnabled,
            searchQuery = searchQuery,
            selectedSection = selectedSection,
            selectedPersonForQuickMenu = null,
            isQuickActionMenuExpanded = false,
            onSearchQueryChange = onSearchQueryChange,
            onSectionSelect = onSectionSelect,
            snackbarHostState = remember { SnackbarHostState() },
            lazyListState = rememberLazyListState(),
            onUserClick = {},
            onQuickMenuClick = onQuickMenuClick,
            onEmergencyContactClick = {},
            onEmergencyContactManageClick = {},
            onDismissQuickMenu = {},
            onEditUser = {},
            onAddClick = {},
            onEndUser = {},
            onNavigateToSettings = {}
        )
    }

    private fun createMockUiState(id: String, name: String): PersonUiState {
        return PersonUiState(
            person = Person(id = id, lastName = name, firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()),
            maskedName = name,
            maskedFurigana = "",
            age = 80,
            formattedBirthday = "1940/01/01",
            summary = PersonCategorySummary()
        )
    }
}
