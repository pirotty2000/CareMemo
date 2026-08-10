package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/**
 * Instrumented Test: PersonMedicationScreen (SCR-PM-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PM-001_PersonMedicationScreen.md に準拠
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_TabletContent").assertDoesNotExist()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("MedicationScreen_TabletContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertDoesNotExist()
    }

    //endregion

    //region 3. コンポーネント描画検証 (Components)

    @Test
    fun CPN_01_calendar_rendersSpecifiedMonth() {
        val month = YearMonth.of(2024, 1)
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false,
                    selectedMonth = month,
                    isLoading = false,
                    recordsByDate = persistentMapOf(),
                    isHistoryMode = false,
                    onHistoryModeChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {}
                )
            }
        }
        // "2024年1月" should be visible in the header
        composeTestRule.onNodeWithText("2024年1月", substring = true).assertIsDisplayed()
    }

    @Test
    fun CPN_03_emptyState_isDisplayed_whenNoRecords() {
        // In history mode, empty records should show EmptyState
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false,
                    selectedMonth = YearMonth.now(),
                    isLoading = false,
                    recordsByDate = persistentMapOf(),
                    isHistoryMode = true, // History mode
                    onHistoryModeChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. 状態・インタラクション検証 (Interaction)

    @Test
    fun ACT_01_nextMonthButton_triggersViewModel() {
        val medicationViewModel = mockk<PersonMedicationViewModel>(relaxed = true)
        every { medicationViewModel.uiState } returns MutableStateFlow(PersonMedicationUiState())
        every { medicationViewModel.viewEvent } returns MutableSharedFlow()
        every { medicationViewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = mockk(relaxed = true) {
                        every { uiState } returns MutableStateFlow(PersonDetailUiState())
                    },
                    medicationViewModel = medicationViewModel, 
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // Tap Next Month
        composeTestRule.onNodeWithTag("Medication_MonthNext_Phone").performClick()
        verify { medicationViewModel.nextMonth() }
    }

    //endregion

    //region 5. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_navigatesBack() {
        val detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState())

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = mockk(relaxed = true) {
                        every { uiState } returns MutableStateFlow(PersonMedicationUiState())
                        every { viewEvent } returns MutableSharedFlow()
                        every { uiEventFlow } returns MutableSharedFlow()
                    },
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("MedicationScreen_BackButton").performClick()
        verify { detailViewModel.navigateBackToMain() }
    }

    //endregion

    // --- Helpers ---

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        medicationState: PersonMedicationUiState = PersonMedicationUiState()
    ) {
        val detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        val medicationViewModel = mockk<PersonMedicationViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState())
        every { medicationViewModel.uiState } returns MutableStateFlow(medicationState)
        every { medicationViewModel.viewEvent } returns MutableSharedFlow()
        every { medicationViewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
