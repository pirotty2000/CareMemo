package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.logic.feature.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/**
 * Instrumented Test: PersonMedicationScreen (SCR-MED-001)
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(
            widthClass = WindowWidthSizeClass.Compact,
            medicationState = PersonMedicationUiState(screenState = PersonMedicationScreenState.Active)
        )
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertIsDisplayed()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(
            widthClass = WindowWidthSizeClass.Expanded,
            medicationState = PersonMedicationUiState(screenState = PersonMedicationScreenState.Active)
        )
        composeTestRule.onNodeWithTag("MedicationScreen_TabletContent").assertIsDisplayed()
    }

    //endregion

    //region 3. カレンダー描画検証 (Calendar)

    @Test
    fun CAL_01_monthHeader_displaysCorrectMonth() {
        val month = YearMonth.of(2023, 11)
        setContent(
            medicationState = PersonMedicationUiState(
                screenState = PersonMedicationScreenState.Active,
                selectedMonth = month
            )
        )
        // Match only the month part or use substring = true correctly
        composeTestRule.onNodeWithText("11月", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. インタラクション検証 (Interaction)

    @Test
    fun ACT_01_nextMonth_triggersViewModel() {
        val medicationViewModel = createMockViewModel()
        setContent(
            medicationViewModel = medicationViewModel,
            medicationState = PersonMedicationUiState(screenState = PersonMedicationScreenState.Active)
        )

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("Medication_MonthNext_Phone"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasTestTag("Medication_MonthNext_Phone"), useUnmergedTree = true).performClick()
        verify { medicationViewModel.nextMonth() }
    }

    @Test
    fun ACT_02_modeSwitch_triggersStateChange() {
        val medicationViewModel = createMockViewModel()
        setContent(
            medicationViewModel = medicationViewModel,
            medicationState = PersonMedicationUiState(screenState = PersonMedicationScreenState.Active)
        )

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasText("履歴", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // 履歴モードに切り替え
        composeTestRule.onNodeWithText("履歴", substring = true).performClick()
        // Match substring from R.string.p_med_msg_no_edit_in_history
        composeTestRule.onNodeWithText("編集できません", substring = true).assertIsDisplayed()
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState(personId = "p1"))
            every { viewEvent } returns MutableSharedFlow()
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
        }
    }

    private fun createMockViewModel(): PersonMedicationViewModel {
        return mockk<PersonMedicationViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonMedicationUiState())
            every { uiEventFlow } returns MutableSharedFlow()
            every { viewEvent } returns MutableSharedFlow()
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        medicationState: PersonMedicationUiState = PersonMedicationUiState(screenState = PersonMedicationScreenState.Active),
        medicationViewModel: PersonMedicationViewModel? = null
    ) {
        val vm = medicationViewModel ?: createMockViewModel()
        if (medicationViewModel == null) {
            every { vm.uiState } returns MutableStateFlow(medicationState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = createMockDetailViewModel(),
                    medicationViewModel = vm,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
