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

/**
 * UI Scenario Test: 一括入力フロー (SCN-BATCH-01)
 * 
 * 仕様書: doc/test/scenario/TEST_SCENARIO_BatchInputFlow.md に準拠
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BatchInputScenarioTest {

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
     * 共通：日時入力 (2024/01/01 12:00)
     */
    private fun inputCommonDateTime() {
        composeTestRule.onNodeWithTag("DateTimeUnit_Year").performTextReplacement("2024")
        composeTestRule.onNodeWithTag("DateTimeUnit_Month").performTextReplacement("1")
        composeTestRule.onNodeWithTag("DateTimeUnit_Day").performTextReplacement("1")
        composeTestRule.onNodeWithTag("DateTimeUnit_Hour").performTextReplacement("12")
        composeTestRule.onNodeWithTag("DateTimeUnit_Minute").performTextReplacement("0")
    }

    /**
     * SCN-BATCH-01: 健康記録の一括入力と反映確認
     */
    @Test
    fun SCN_01_BatchInput_SaveAndVerifyFlow() {
        // 1. 利用者を選択して一括入力画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()

        // 2. 情報を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_HeightField").fetchSemanticsNodes().isNotEmpty()
        }

        // 記録日時：2024/01/01 12:00
        inputCommonDateTime()

        // 身長・体重
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performTextReplacement("180.0")
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").performTextReplacement("100.0")

        // バイタル (必要に応じてスクロール)
        composeTestRule.onNodeWithTag("BatchInputScreen_BpSystolicField").performScrollTo().performTextReplacement("200")
        composeTestRule.onNodeWithTag("BatchInputScreen_BpDiastolicField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_SatField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_PulseField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_TempField").performScrollTo().performTextReplacement("40.0")

        // 血糖値
        composeTestRule.onNodeWithTag("BatchInputScreen_GlucoseField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_Hba1cField").performScrollTo().performTextReplacement("10.0")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 3. 保存
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()

        // 保存成功スナックバーの出現と消失を待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("健康記録を一括保存しました").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("健康記録を一括保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 4. 一覧に戻る
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()

        // 5. 各詳細画面で反映を確認
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        
        // 身長・体重
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("180.0", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("180.0", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)

        // バイタルへ切り替え
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performClick()
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("200/100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("200/100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("40.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)

        // 血糖値へ切り替え
        composeTestRule.onNodeWithTag("CategoryChip_GLUCOSE_AND_HBA1C").performClick()
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("10.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)
    }

    /**
     * SCN-BATCH-02: 健康記録の一括入力：身長・体重のみ
     */
    @Test
    fun SCN_02_BatchInput_HeightWeightFlow() {
        // 1. 一括入力画面へ
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()

        // 2. 情報を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_HeightField").fetchSemanticsNodes().isNotEmpty()
        }
        inputCommonDateTime()
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performTextReplacement("180.0")
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").performTextReplacement("100.0")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 3. 保存
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("健康記録を一括保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 4. 一覧に戻る
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()

        // 5. 反映確認
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()
        
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("180.0", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("180.0", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)
    }

    /**
     * SCN-BATCH-03: 健康記録の一括入力：バイタルのみ
     */
    @Test
    fun SCN_03_BatchInput_VitalFlow() {
        // 1. 一括入力画面へ
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()

        // 2. 情報を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_BpSystolicField").fetchSemanticsNodes().isNotEmpty()
        }
        inputCommonDateTime()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpSystolicField").performScrollTo().performTextReplacement("200")
        composeTestRule.onNodeWithTag("BatchInputScreen_BpDiastolicField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_SatField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_PulseField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_TempField").performScrollTo().performTextReplacement("40.0")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 3. 保存
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("健康記録を一括保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 4. 一覧に戻る
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()

        // 5. 反映確認
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_BP_AND_PULSE").performClick()
        
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("200/100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("200/100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("40.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)
    }

    /**
     * SCN-BATCH-04: 健康記録の一括入力：血糖値・HbA1cのみ
     */
    @Test
    fun SCN_04_BatchInput_GlucoseFlow() {
        // 1. 一括入力画面へ
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()

        // 2. 情報を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_GlucoseField").fetchSemanticsNodes().isNotEmpty()
        }
        inputCommonDateTime()
        composeTestRule.onNodeWithTag("BatchInputScreen_GlucoseField").performScrollTo().performTextReplacement("100")
        composeTestRule.onNodeWithTag("BatchInputScreen_Hba1cField").performScrollTo().performTextReplacement("10.0")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 3. 保存
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("健康記録を一括保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 4. 一覧に戻る
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()

        // 5. 反映確認
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_GLUCOSE_AND_HBA1C").performClick()
        
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("10.0", substring = true).performScrollTo().assertIsDisplayed()
        Thread.sleep(2000)
    }
}
