package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionList
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionDetailPane
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: PersonConditionScreen (SCR-PC-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-001_PersonConditionScreen.md に準拠
 */
class PersonConditionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("ConditionScreen_PhoneContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ConditionScreen_TabletContent").assertDoesNotExist()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("ConditionScreen_TabletContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ConditionScreen_PhoneContent").assertDoesNotExist()
    }

    //endregion

    //region 3. コンポーネント描画検証 (Components)

    @Test
    fun CPN_01_recordItem_rendersCorrectly() {
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "Morning", condition = "Good", author = "Staff A", recordTime = Instant.now())
        
        composeTestRule.setContent {
            CareMemoTheme {
                // Test ConditionList directly as a component test
                ConditionList(
                    records = listOf(record).toImmutableList(),
                    selectedId = null,
                    conditionPhotoMap = emptyMap<String, Boolean>(),
                    isAnyDialogOpen = false,
                    onSelect = {},
                    onDelete = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Morning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Good").assertIsDisplayed()
        composeTestRule.onNodeWithText("Staff A", substring = true).assertIsDisplayed()
    }

    @Test
    fun CPN_03_photoList_showsPlaceholder_whenEmpty() {
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "Morning", condition = "Good", author = "Staff A", recordTime = Instant.now())
        composeTestRule.setContent {
            CareMemoTheme {
                ConditionDetailPane(
                    uiState = PersonConditionUiState(
                        records = listOf(record).toImmutableList(),
                        selectedConditionId = "c1", 
                        isEditing = false
                    ),
                    onDeletePhoto = {},
                    onSelectedIdChange = {},
                    onCancel = {},
                    onEditClick = {},
                    onEditInputUpdate = {},
                    onSaveClick = {},
                    onCancelEdit = {},
                    onAddPhotoClick = {},
                    onNavigateToFullScreen = { _: String, _: String -> },
                    onMicClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("写真がありません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. 状態・インタラクション検証 (Interaction)

    @Test
    fun ACT_01_memoInput_triggersViewModel() {
        val detailViewModel = createMockDetailViewModel()
        val conditionViewModel = createMockConditionViewModel()
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "", condition = "", author = "", recordTime = Instant.now())
        
        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState(personId = "p1"))
        every { conditionViewModel.uiState } returns MutableStateFlow(PersonConditionUiState(
            personId = "p1", 
            records = persistentListOf(record),
            selectedConditionId = "c1", 
            isEditing = true
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput("Observation text")
        verify { conditionViewModel.updateEditInput(any()) }
    }

    @Test
    fun ACT_03_saveButton_triggersViewModel() {
        val detailViewModel = createMockDetailViewModel()
        val conditionViewModel = createMockConditionViewModel()
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "", condition = "", author = "", recordTime = Instant.now())
        
        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState(personId = "p1"))
        every { conditionViewModel.uiState } returns MutableStateFlow(PersonConditionUiState(
            personId = "p1", 
            records = persistentListOf(record),
            selectedConditionId = "c1", 
            isEditing = true, 
            isSaveEnabled = true
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("Condition_SaveButton").performClick()
        verify { conditionViewModel.saveCurrentEdit(any()) }
    }

    //endregion

    //region 5. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_03_backButton_navigatesBack() {
        val detailViewModel = createMockDetailViewModel()
        val conditionViewModel = createMockConditionViewModel()
        val navController = mockk<NavHostController>(relaxed = true)
        
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = navController,
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("ConditionScreen_BackButton").performClick()
        verify { detailViewModel.navigateBackToMain() }
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState(personId = "p1"))
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun createMockConditionViewModel(): PersonConditionViewModel {
        return mockk<PersonConditionViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonConditionUiState(personId = "p1"))
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        navController: NavHostController = mockk(relaxed = true)
    ) {
        val detailViewModel = createMockDetailViewModel()
        val conditionViewModel = createMockConditionViewModel()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = navController,
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
