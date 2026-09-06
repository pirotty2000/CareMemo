package jp.mydns.fujiwara.carememo.ui.screens.scenario

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import jp.mydns.fujiwara.carememo.CareMemoApplication
import jp.mydns.fujiwara.carememo.MainActivity
import jp.mydns.fujiwara.carememo.test.ScenarioTestDataLoader
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * UI Scenario Test: 服薬管理登録フロー (SCN-REG-06)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MedicationRegistrationScenarioTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val targetPersonName = "愛\u3000植夫"

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CareMemoApplication

        runBlocking {
            appContext.userSettingsRepository.setNameMaskingEnabled(false)
            ScenarioTestDataLoader.restoreFromBackup()
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("IS_TEST_MODE", true)
        }
        ActivityScenario.launch<MainActivity>(intent)
    }

    /**
     * SCN-01-REG: 服薬記録の新規入力
     */
    @Test
    fun SCN_01_REG_AddMedicationFlow() {
        val firstSaturday = LocalDate.now()
            .withDayOfMonth(1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val targetDate = firstSaturday.toString()

        composeTestRule.waitUntil(40000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("CategorySelectionSheet_Button_MEDICATION").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_MEDICATION").performClick()

        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("Medication_DayCell_$targetDate").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("Medication_DayCell_$targetDate").performClick()

        // Match string from R.string.p_med_status_none ("記録なし")
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Medication_StatusChip_記録なし").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_記録なし", useUnmergedTree = true).onFirst().performClick()
        
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_介助・促し", useUnmergedTree = true).onFirst().performClick()
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用", useUnmergedTree = true).onFirst().performClick()

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("Medication_SaveButton").performClick()

        // Wait for dialog to close
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Medication_SaveButton").fetchSemanticsNodes().isEmpty()
        }

        val cellMatcher = hasAnyAncestor(hasTestTag("Medication_DayCell_$targetDate"))
        
        // Use more generic matching for symbols
        composeTestRule.onNode(hasText("×").and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("△").and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("〇").and(cellMatcher), useUnmergedTree = true).assertExists()
    }
}
