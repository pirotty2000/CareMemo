package jp.mydns.fujiwara.carememo.ui.screens.scenario

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.mydns.fujiwara.carememo.CareMemoApplication
import jp.mydns.fujiwara.carememo.MainActivity
import jp.mydns.fujiwara.carememo.test.ScenarioTestDataLoader
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Scenario Test: 利用者詳細フロー
 */
class PersonDetailScenarioTest {

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


    private fun robustScrollDownTo(tag: String) {
        val scrollColumn = composeTestRule.onNodeWithTag("Settings_ScrollColumn")
        repeat(20) {
            try {
                composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                return
            } catch (_: Throwable) {
                scrollColumn.performTouchInput {
                    swipeUp(startY = bottom * 0.6f, endY = top * 0.4f, durationMillis = 300)
                }
                composeTestRule.waitForIdle()
            }
        }
    }

    private fun robustScrollUpTo(tag: String) {
        val scrollColumn = composeTestRule.onNodeWithTag("Settings_ScrollColumn")
        repeat(20) {
            try {
                composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                return
            } catch (_: Throwable) {
                scrollColumn.performTouchInput {
                    swipeDown(startY = top * 0.4f, endY = bottom * 0.6f, durationMillis = 300)
                }
                composeTestRule.waitForIdle()
            }
        }
    }

    @Test
    fun SCN_DET_01_PersonDetailNavigationAndDataConsistency() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(targetPersonName).assertIsDisplayed()

        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet").assertIsDisplayed()

        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()
        
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_BP_AND_PULSE"))
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_GLUCOSE_AND_HBA1C"))
        composeTestRule.onNodeWithTag("CategoryChip_GLUCOSE_AND_HBA1C").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_CONDITION_AT_VISIT"))
        composeTestRule.onNodeWithTag("CategoryChip_CONDITION_AT_VISIT").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_MEDICATION"))
        composeTestRule.onNodeWithTag("CategoryChip_MEDICATION").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Medication_ModeSegment").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_HEIGHT_AND_WEIGHT"))
        composeTestRule.onNodeWithTag("CategoryChip_HEIGHT_AND_WEIGHT").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("HealthScreen_BackButton").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun SCN_DET_02_BatchInputNavigation() {
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("CategorySelectionSheet").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_InputScrollColumn").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNode(hasText("愛", substring = true).and(hasText("植夫", substring = true))).assertExists()

        composeTestRule.onNodeWithTag("BatchInputScreen_CancelButton").performScrollTo().performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun SCN_PH_01_GraphExpansionNavigation() {
        // 1. 健康管理画面へ遷移
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()

        // 2. グラフタブへ切り替え (ロード完了後)
        composeTestRule.waitUntil(45000) {
            composeTestRule.onAllNodes(hasTestTag("HealthScreen_Tab_Graph"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasTestTag("HealthScreen_Tab_Graph"), useUnmergedTree = true).performClick()
        
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodes(hasTestTag("HealthScreen_GraphArea"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. 拡大ボタンをタップ (ContentDescription を正確に指定: R.string.health_graph_expand_desc)
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodes(hasContentDescription("拡大表示", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasContentDescription("拡大表示", substring = true), useUnmergedTree = true).onFirst().performClick()

        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("GraphExpansion_BackButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("愛", substring = true)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_Tab_Graph").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun SCN_PC_01_PhotoCaptureButtonPresence() {
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_CONDITION_AT_VISIT").performClick()

        val recordId = "0a711cef-3ffa-4e2a-98d1-76664d8c9d59" 
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HistoryItem_$recordId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HistoryItem_$recordId").performScrollTo().performClick()

        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasContentDescription("編集", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasContentDescription("編集", substring = true)).performClick()

        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("Condition_AddPhotoButton", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("Condition_AddPhotoButton", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        
        composeTestRule.onNode(hasText("キャンセル", substring = true).or(hasText("戻る", substring = true))).performClick()
    }

    @Test
    fun SCN_PC_02_PhotoFullScreenView() {
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_CONDITION_AT_VISIT").performClick()

        val recordIdWithPhoto = "9024b623-7c9c-45d1-b118-ff2cfd0cec52"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HistoryItem_$recordIdWithPhoto").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HistoryItem_$recordIdWithPhoto").performScrollTo().performClick()

        val photoId = "ed9cefd7-5c4f-412e-9c8f-7e7f3918b625"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("ConditionPhoto_$photoId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ConditionPhoto_$photoId").performScrollTo().performClick()

        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PhotoFullScreen_BackButton", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").assertIsDisplayed()

        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").performClick()
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()
    }

    @Test
    fun SCN_SET_01_SettingsNavigation() {
        // 1. 設定画面へ
        composeTestRule.waitUntil(30000) { composeTestRule.onAllNodesWithTag("MainScreen_UserList").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("MainScreen_MenuButton").performClick()
        
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodes(hasTestTag("MainScreen_MenuItem_Settings"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasTestTag("MainScreen_MenuItem_Settings"), useUnmergedTree = true).performClick()
        
        // Settings 画面のロード完了を待つ (LoadingScreen が消えるまで)
        composeTestRule.waitUntil(45000) {
            composeTestRule.onAllNodes(hasTestTag("Settings_Loading")).fetchSemanticsNodes().isEmpty()
        }
        // コンテンツが表示されていることを確認
        composeTestRule.onNode(hasText("氏名の伏せ字表示", substring = true)).assertIsDisplayed()

        repeat(7) {
            robustScrollDownTo("Settings_VersionRow")
            if (composeTestRule.onAllNodesWithText("閉じる").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithText("閉じる").performClick()
                composeTestRule.waitForIdle()
            }
            composeTestRule.onNodeWithTag("Settings_VersionRow").performClick()
            composeTestRule.waitForIdle()
        }
        
        if (composeTestRule.onAllNodesWithText("閉じる").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("閉じる").performClick()
            composeTestRule.waitForIdle()
        }
        
        robustScrollDownTo("Settings_AuditLogButton")
        composeTestRule.onNodeWithTag("Settings_AuditLogButton").performClick()
        composeTestRule.waitUntil(30000) { composeTestRule.onAllNodesWithText("操作ログ").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("AuditLogScreen_BackButton").performClick()
        composeTestRule.waitForIdle()

        robustScrollUpTo("Settings_RestoreUserButton")
        composeTestRule.onNodeWithTag("Settings_RestoreUserButton").performClick()
        composeTestRule.waitUntil(30000) { composeTestRule.onAllNodesWithText("利用者の復帰").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        composeTestRule.waitForIdle()

        robustScrollDownTo("Settings_UnassignedPhotosButton")
        composeTestRule.onNodeWithTag("Settings_UnassignedPhotosButton").performClick()
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithTag("UnassignedPhoto_BackButton", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("UnassignedPhoto_BackButton").performClick()
        composeTestRule.waitForIdle()

        robustScrollUpTo("SettingsScreen_BackButton")
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").performClick()
        composeTestRule.onNodeWithTag("MainScreen_UserList").assertIsDisplayed()
    }
}
