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

        // 朝：未 (status 0)
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_未", useUnmergedTree = true)[0].performClick()
        
        // 昼：介助 (status 1)
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_介助", useUnmergedTree = true)[1].performClick()
        
        // 夕：服用 (status 2)
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用", useUnmergedTree = true)[2].performClick()

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("Medication_SaveButton").performClick()

        // Wait for dialog to close
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Medication_SaveButton").fetchSemanticsNodes().isEmpty()
        }

        // 5. カレンダー上での反映確認
        // 安定化のため、セル自体が表示されるまで待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("Medication_DayCell_$targetDate"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // セルまでスクロール
        composeTestRule.onNode(hasTestTag("Medication_DayCell_$targetDate"), useUnmergedTree = true).performScrollTo()

        val cellMatcher = hasAnyAncestor(hasTestTag("Medication_DayCell_$targetDate"))
        
        // Wait for the symbols to appear in the cell (flexible matching)
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasAnyAncestor(hasTestTag("Medication_DayCell_$targetDate"))).fetchSemanticsNodes().size >= 4
        }
        
        composeTestRule.onNode(hasText("×", substring = true).and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("昼", substring = true).and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("夕", substring = true).and(cellMatcher), useUnmergedTree = true).assertExists()
    }
}
