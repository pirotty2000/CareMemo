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
 *
 * 仕様書: doc/test/scenario/TEST_SCENARIO_DataRegistrationFlow.md に準拠
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
            // 1. マスキング設定を無効化
            appContext.userSettingsRepository.setNameMaskingEnabled(false)

            // 2. テスト用データのリストア
            ScenarioTestDataLoader.restoreFromBackup()
        }

        // 3. セキュリティロックをバイパスして起動
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
        // 実行時の「今月の第1土曜日」を動的に計算する
        val firstSaturday = LocalDate.now()
            .withDayOfMonth(1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val targetDate = firstSaturday.toString() // "yyyy-MM-dd" 形式

        // 1. 利用者を選択して服薬管理画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_MEDICATION").performClick()

        // 2. 計算した第1土曜日のセルをタップ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("Medication_DayCell_$targetDate").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("Medication_DayCell_$targetDate").performClick()

        // 3. ダイアログでの入力
        // 朝：未
        // 昼：介助
        // 夕：服用
        
        // 朝の行の「未」をタップ
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_未").onFirst().performClick()
        
        // 昼の行の「介助」をタップ (2番目の「介助」ボタン)
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_介助").get(1).performClick()
        
        // 夕の行の「服用」をタップ (3番目の「服用」ボタン)
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用").get(2).performClick()

        // 入力により日時フィールドが表示されるのを待ち、キーボードを閉じる
        composeTestRule.waitForIdle()
        Espresso.closeSoftKeyboard()

        // 4. 保存
        composeTestRule.onNodeWithTag("Medication_SaveButton").performClick()

        // 保存成功スナックバーの出現と消失を待つ (安定化のため)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("服薬状況を更新しました").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("服薬状況を更新しました").fetchSemanticsNodes().isEmpty()
        }

        // 5. カレンダー上での反映確認
        // アイコン内のテキストは階層が深いため、useUnmergedTree = true を指定して確実に検出します
        val cellMatcher = hasAnyAncestor(hasTestTag("Medication_DayCell_$targetDate"))
        
        composeTestRule.onNode(hasText("×").and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("昼").and(cellMatcher), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("夕").and(cellMatcher), useUnmergedTree = true).assertExists()
    }
}
