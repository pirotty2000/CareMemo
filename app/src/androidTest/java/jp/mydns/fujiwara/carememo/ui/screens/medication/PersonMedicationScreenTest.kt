package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.feature.*
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
 * Instrumented Test: PersonMedicationScreen (SCR-MED-001)
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertIsDisplayed()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
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
        // DateTimeUtils.formatYearMonthHeader の結果（2023年11月）が含まれるか
        composeTestRule.onNodeWithText("2023年11月", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. インタラクション検証 (Interaction)

    @Test
    fun ACT_01_nextMonth_triggersViewModel() {
        val medicationViewModel = createMockViewModel()
        setContent(medicationViewModel = medicationViewModel)

        composeTestRule.onNodeWithTag("Medication_MonthNext_Phone").performClick()
        verify { medicationViewModel.nextMonth() }
    }

    @Test
    fun ACT_02_modeSwitch_triggersStateChange() {
        // UI Action 経由ではなく、内部ステート (isHistoryMode) の変更を検証するため、
        // 実際にタップして表示が切り替わるか（または Action が発行されるか）を確認
        val medicationViewModel = createMockViewModel()
        setContent(medicationViewModel = medicationViewModel)

        // 履歴モードに切り替え
        composeTestRule.onNodeWithText("履歴").performClick()
        composeTestRule.onNodeWithText("履歴表示では編集できません").assertIsDisplayed()
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState())
            every { viewEvent } returns MutableSharedFlow()
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
        medicationState: PersonMedicationUiState = PersonMedicationUiState(),
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
    }
}
