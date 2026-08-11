package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: PersonHealthScreen (SCR-PH-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-001_PersonHealthScreen.md に準拠
 */
class PersonHealthScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("HealthScreen_PhoneContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_TabletContent").assertDoesNotExist()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("HealthScreen_TabletContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_PhoneContent").assertDoesNotExist()
    }

    //endregion

    //region 3. コンポーネント描画検証 (Components)

    @Test
    fun CPN_01_historyList_rendersItems() {
        val record = HeightAndWeight(id = "h1", personId = "p1", height = 170.0, weight = 65.0, recordTime = Instant.now())
        setContent(
            healthState = PersonHealthUiState(
                currentCategory = Category.HEIGHT_AND_WEIGHT,
                records = listOf(record).toImmutableList(),
                preferredShowHistory = true
            )
        )
        composeTestRule.onNodeWithTag("HealthScreen_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithText("170.0", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("65.0", substring = true).assertIsDisplayed()
    }

    @Test
    fun CPN_03_emptyState_isDisplayed_whenNoRecords() {
        setContent(
            healthState = PersonHealthUiState(records = emptyList<jp.mydns.fujiwara.carememo.data.HistoryRecord>().toImmutableList(), preferredShowHistory = true)
        )
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. 状態・インタラクション検証 (Interaction)

    @Test
    fun ACT_02_modeSwitch_triggersViewModel() {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        // Add a dummy record to avoid EmptyState, which hides the segmented buttons
        val record = HeightAndWeight(id = "h1", personId = "p1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(
            preferredShowHistory = true,
            records = persistentListOf(record)
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // Tap the graph tab/segmented button
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        verify { healthViewModel.updatePreferredShowHistory(false) }
    }

    @Test
    fun ACT_04_saveButton_triggersViewModel() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        // Set state to editing with valid input
        every { healthViewModel.uiState } returns MutableStateFlow(
            PersonHealthUiState(selectedRecordId = newId, isEditing = true, isSaveEnabled = true)
        )

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // The tag is "HealthField_SaveButton" in PersonHealthComponents.kt
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performClick()
        verify { healthViewModel.saveCurrentEdit() }
    }

    //endregion

    //region 5. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_02_backButton_navigatesBack() {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("HealthScreen_BackButton").performClick()
        verify { detailViewModel.navigateBackToMain() }
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState())
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun createMockHealthViewModel(): PersonHealthViewModel {
        return mockk<PersonHealthViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonHealthUiState())
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        detailState: PersonDetailUiState = PersonDetailUiState(),
        healthState: PersonHealthUiState = PersonHealthUiState()
    ) {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()

        every { detailViewModel.uiState } returns MutableStateFlow(detailState)
        every { healthViewModel.uiState } returns MutableStateFlow(healthState)

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
