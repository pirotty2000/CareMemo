package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.feature.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: PersonConditionScreen (SCR-PC-001)
 */
class PersonConditionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("ConditionScreen_PhoneContent").assertIsDisplayed()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("Condition_TabletLayout").assertIsDisplayed()
    }

    //endregion

    //region 3. 表示・状態検証 (Display)

    @Test
    fun DSP_01_historyList_rendersItems() {
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "発熱", condition = "38度", author = "A", recordTime = Instant.now())
        setContent(
            conditionState = PersonConditionUiState(
                screenState = PersonConditionScreenState.Active,
                records = listOf(record).toImmutableList(),
                filteredRecords = listOf(record).toImmutableList()
            )
        )
        composeTestRule.onNodeWithText("発熱").assertIsDisplayed()
        composeTestRule.onNodeWithText("38度").assertIsDisplayed()
    }

    //endregion

    //region 4. インタラクション検証 (Interaction)

    @Test
    fun ACT_01_searchQuery_triggersViewModel() {
        val healthViewModel = createMockViewModel()
        setContent(conditionViewModel = healthViewModel)

        composeTestRule.onNodeWithTag("ConditionScreen_SearchBox").performTextInput("咳")
        verify { healthViewModel.updateSearchQuery("咳") }
    }

    @Test
    fun ACT_02_itemClick_opensDetail() {
        val healthViewModel = createMockViewModel()
        val record = ConditionAtVisit(id = "c1", personId = "p1", title = "T", condition = "C", author = "A", recordTime = Instant.now())
        
        every { healthViewModel.uiState } returns MutableStateFlow(PersonConditionUiState(
            screenState = PersonConditionScreenState.Active,
            records = listOf(record).toImmutableList(),
            filteredRecords = listOf(record).toImmutableList()
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = createMockDetailViewModel(),
                    conditionViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithText("C").performClick()
        verify { healthViewModel.setSelectedConditionId("c1") }
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState())
            every { viewEvent } returns MutableSharedFlow()
        }
    }

    private fun createMockViewModel(): PersonConditionViewModel {
        return mockk<PersonConditionViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonConditionUiState())
            every { uiEventFlow } returns MutableSharedFlow()
            every { viewEvent } returns MutableSharedFlow()
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        conditionState: PersonConditionUiState = PersonConditionUiState(),
        conditionViewModel: PersonConditionViewModel? = null
    ) {
        val vm = conditionViewModel ?: createMockViewModel()
        if (conditionViewModel == null) {
            every { vm.uiState } returns MutableStateFlow(conditionState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = createMockDetailViewModel(),
                    conditionViewModel = vm,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
