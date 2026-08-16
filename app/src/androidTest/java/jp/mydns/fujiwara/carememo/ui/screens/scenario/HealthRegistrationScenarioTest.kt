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
 * UI Scenario Test: 健康記録登録フロー (SCN-REG-02, SCN-REG-03, SCN-REG-04)
 * 
 * 仕様書: doc/test/scenario/TEST_SCENARIO_DataRegistrationFlow.md に準拠
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HealthRegistrationScenarioTest {

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
     * SCN-REG-02: 健康記録の新規入力：身長・体重
     */
    @Test
    fun SCN_01_REG_AddHeightWeightFlow() {
        // 1. 利用者を選択して健康管理画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()

        // 2. 追加ボタン（FAB）をタップ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()

        // 3. 各項目を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HealthField_Height").fetchSemanticsNodes().isNotEmpty()
        }

        // 身長・体重を整数で入力（型変換の堅牢性を検証）
        composeTestRule.onNodeWithTag("HealthField_Height").performTextReplacement("180")
        composeTestRule.onNodeWithTag("HealthField_Weight").performTextReplacement("100")
        composeTestRule.waitForIdle()

        // 日時を入力
        inputCommonDateTime()
        composeTestRule.waitForIdle()

        // 入力後にキーボードを閉じ、同期を確実に完了させる
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 保存成功スナックバーの出現を待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isNotEmpty()
        }

        // スナックバーが消えるのを待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 検証：身長 180.0, 体重 100.0 (整数入力が Double へ正しく変換されていること)
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithText("180.0", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("180.0", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100.0", substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-REG-03: 健康記録の新規入力：バイタル
     */
    @Test
    fun SCN_02_REG_AddVitalFlow() {
        // 1. 利用者を選択してバイタル画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_BP_AND_PULSE").performClick()

        // 2. 追加ボタン（FAB）をタップ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()

        // 3. 各項目を入力 (2024/01/01 12:00, 200/100, 100, 100, 40.0)
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HealthField_BpSystolic").fetchSemanticsNodes().isNotEmpty()
        }

        // 数値を入力して確定を待つ (体温はわざと整数 "40" で入力)
        composeTestRule.onNodeWithTag("HealthField_BpSystolic").performTextReplacement("200")
        composeTestRule.onNodeWithTag("HealthField_BpDiastolic").performTextReplacement("100")
        composeTestRule.onNodeWithTag("HealthField_Sat").performTextReplacement("100")
        composeTestRule.onNodeWithTag("HealthField_Pulse").performTextReplacement("100")
        composeTestRule.onNodeWithTag("HealthField_Temp").performTextReplacement("40")
        composeTestRule.waitForIdle()

        // 日時を入力して確定を待つ
        inputCommonDateTime()
        composeTestRule.waitForIdle()

        // 入力後にキーボードを閉じ、同期を確実に完了させる
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 保存成功スナックバーの出現を待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isNotEmpty()
        }

        // スナックバーが消えるのを待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 5. 履歴一覧に戻り、反映を確認 (新しく追加したデータが表示されるのを待つ)
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithText("200/100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // 表示形式（200/100）や体温を確認
        composeTestRule.onNodeWithText("200/100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("40.0", substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-REG-04: 健康記録の新規入力：血糖値・HbA1c
     */
    @Test
    fun SCN_03_REG_AddGlucoseFlow() {
        // 1. 利用者を選択して血糖値画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_GLUCOSE_AND_HBA1C").performClick()

        // 2. 追加ボタン（FAB）をタップ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()

        // 3. 各項目を入力 (2024/01/01 12:00, 100, 10.0)
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HealthField_Glucose").fetchSemanticsNodes().isNotEmpty()
        }

        // 数値を入力して確定を待つ (HbA1c はわざと整数 "10" で入力)
        composeTestRule.onNodeWithTag("HealthField_Glucose").performTextReplacement("100")
        composeTestRule.onNodeWithTag("HealthField_HbA1c").performTextReplacement("10")
        composeTestRule.waitForIdle()

        // 日時を入力して確定を待つ
        inputCommonDateTime()
        composeTestRule.waitForIdle()

        // 入力後にキーボードを閉じ、同期を確実に完了させる
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 保存成功スナックバーの出現を待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isNotEmpty()
        }

        // スナックバーが消えるのを待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 5. 履歴一覧に戻り、反映を確認 (新しく追加したデータが表示されるのを待つ)
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithText("100", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("100", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("10.0", substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-04-EDIT: 健康記録の編集：身長・体重
     */
    @Test
    fun SCN_04_EDIT_EditHeightWeightFlow() {
        val recordId = "09fdb0b7-0988-480e-b2f5-27783018c358"

        // 1. 利用者を選択して健康管理画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()

        // 2. 2023/12/01 のレコードを選択
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HistoryItem_$recordId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HistoryItem_$recordId").performScrollTo().performClick()

        // 3. 編集ボタンをタップ
        composeTestRule.onNodeWithContentDescription("編集").performClick()

        // 4. 体重を 100.0 に修正
        composeTestRule.onNodeWithTag("HealthField_Weight").performTextReplacement("100")
        
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 5. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 保存成功を待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("記録を保存しました").fetchSemanticsNodes().isEmpty()
        }

        // 6. 反映確認
        composeTestRule.onNodeWithText("100.0", substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-05-EDIT: 健康記録の編集：バイタル
     */
    @Test
    fun SCN_05_EDIT_EditVitalFlow() {
        val recordId = "d1141c7a-f55d-42c9-8b71-bcbad790cc2e"

        // 1. バイタル画面へ
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_BP_AND_PULSE").performClick()

        // 2. レコード選択
        composeTestRule.onNodeWithTag("HistoryItem_$recordId").performScrollTo().performClick()

        // 3. 編集
        composeTestRule.onNodeWithContentDescription("編集").performClick()
        composeTestRule.onNodeWithTag("HealthField_BpSystolic").performTextReplacement("200")
        
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 5. 反映確認 (200/xxx)
        composeTestRule.onNodeWithText("200/", substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-06-EDIT: 健康記録の編集：血糖値・HbA1c
     */
    @Test
    fun SCN_06_EDIT_EditGlucoseFlow() {
        val recordId = "d8f443f2-4304-48a9-84a5-47c84cd599fd"

        // 1. 血糖値画面へ
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_GLUCOSE_AND_HBA1C").performClick()

        // 2. レコード選択
        composeTestRule.onNodeWithTag("HistoryItem_$recordId").performScrollTo().performClick()

        // 3. 編集
        composeTestRule.onNodeWithContentDescription("編集").performClick()
        composeTestRule.onNodeWithTag("HealthField_Glucose").performTextReplacement("100")
        
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performScrollTo().performClick()

        // 5. 反映確認
        composeTestRule.onNodeWithText("100", substring = true).performScrollTo().assertIsDisplayed()
    }
}
