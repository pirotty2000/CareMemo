package jp.mydns.fujiwara.carememo.ui.screens.scenario

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.mydns.fujiwara.carememo.CareMemoApplication
import jp.mydns.fujiwara.carememo.MainActivity
import jp.mydns.fujiwara.carememo.test.ScenarioTestDataLoader
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Scenario Test: 利用者詳細フロー (SCN-DET-01, SCN-DET-02, SCN-PH-01, SCN-PC-01, SCN-PC-02, SCN-SET-01)
 * 実機抽出データ (backup.json) をベースに検証。
 */
class PersonDetailScenarioTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // 名前（全角スペース）
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
    }

    private fun hasTestTagPrefix(prefix: String): SemanticsMatcher {
        return SemanticsMatcher("Matches test tag starting with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
    }

    /**
     * 要素が画面に完全に表示されるまで、小刻みに下へスクロール（スワイプアップ）します。
     */
    private fun robustScrollDownTo(tag: String) {
        val scrollColumn = composeTestRule.onNodeWithTag("Settings_ScrollColumn")
        repeat(20) {
            try {
                // 完全に表示されているか確認（少しでも欠けていれば例外を投げる）
                composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                return
            } catch (_: Throwable) {
                scrollColumn.performTouchInput {
                    // 座標ズレを防ぐため、中央付近で小さめにスワイプ
                    swipeUp(startY = bottom * 0.6f, endY = top * 0.4f, durationMillis = 300)
                }
                composeTestRule.waitForIdle()
            }
        }
    }

    /**
     * 要素が画面に完全に表示されるまで、小刻みに上へスクロール（スワイプダウン）します。
     */
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

    /**
     * SCN_DET_01: 利用者「愛 植夫」の詳細確認
     */
    @Test
    fun SCN_DET_01_PersonDetailNavigationAndDataConsistency() {
        // 利用者が表示されるまで待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }

        // 1. 利用者一覧で「愛 植夫」が表示されていることを確認
        composeTestRule.onNodeWithText(targetPersonName).assertIsDisplayed()

        // 2. 「愛 植夫」をタップし、ボトムシートを表示
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet").assertIsDisplayed()

        // 3. メニューの「身長・体重」を選択 (NAV-PH-001)
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()
        
        // SCR-PH-001 に遷移したことを確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }
        
        // 4. カテゴリバーの「バイタル」を選択
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_BP_AND_PULSE"))
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performClick()
        // ロードを待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()

        // 5. カテゴリバーの「血糖値」を選択
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_GLUCOSE_AND_HBA1C"))
        composeTestRule.onNodeWithTag("CategoryChip_GLUCOSE_AND_HBA1C").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()

        // 6. カテゴリバーの「所見メモ」を選択 (NAV-COM-001)
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_CONDITION_AT_VISIT"))
        composeTestRule.onNodeWithTag("CategoryChip_CONDITION_AT_VISIT").performClick()
        
        // SCR-PC-001 に遷移し、レコードが存在することを検証
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()
        composeTestRule.onAllNodes(hasTestTagPrefix("HistoryItem_")).assertAny(hasTestTagPrefix("HistoryItem_"))

        // 7. カテゴリバーの「服薬管理」を選択 (NAV-COM-002)
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_MEDICATION"))
        composeTestRule.onNodeWithTag("CategoryChip_MEDICATION").performClick()
        
        // SCR-PM-001 に遷移したことを確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Medication_ModeSegment").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. カテゴリバーの「身長・体重」を選択 (NAV-COM-003)
        composeTestRule.onNodeWithTag("CategorySelectorBar_List").performScrollToNode(hasTestTag("CategoryChip_HEIGHT_AND_WEIGHT"))
        composeTestRule.onNodeWithTag("CategoryChip_HEIGHT_AND_WEIGHT").performClick()
        
        // SCR-PH-001 に戻り、データが表示されていることを確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonHistoryList").fetchSemanticsNodes().isNotEmpty()
        }

        // 9. 「戻る」アイコンをタップ
        composeTestRule.onNodeWithTag("HealthScreen_BackButton").performClick()
        
        // 利用者一覧に戻ったことを確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).assertIsDisplayed()
    }

    /**
     * SCN-DET-02: 一括入力への遷移確認
     */
    @Test
    fun SCN_DET_02_BatchInputNavigation() {
        // 利用者が表示されるまで待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }

        // 1. 利用者を選択してメニューを表示
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        
        // シートが表示されるのを待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("CategorySelectionSheet").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. メニューの「健康記録の一括入力」を選択 (NAV-M-004)
        composeTestRule.onNodeWithTag("CategorySelectionSheet_BatchInput").performClick()
        
        // SCR-PH-002 に遷移したことを確認
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("BatchInputScreen_InputScrollColumn").fetchSemanticsNodes().isNotEmpty()
        }
        
        // 名前の一部が含まれる要素を探す
        composeTestRule.onNode(hasText("愛", substring = true).and(hasText("植夫", substring = true))).assertExists()

        // 3. キャンセルをタップ
        composeTestRule.onNodeWithTag("BatchInputScreen_CancelButton").performScrollTo().performClick()
        
        // 利用者一覧画面に戻る
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).assertIsDisplayed()
    }

    /**
     * SCN-PH-01: 健康記録：グラフ拡大表示 (NAV-PH-002)
     */
    @Test
    fun SCN_PH_01_GraphExpansionNavigation() {
        // 1. 健康管理画面へ遷移
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_HEIGHT_AND_WEIGHT").performClick()

        // 2. グラフタブへ切り替え (ロード完了後)
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_Tab_Graph").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        
        // グラフエリアが表示されるのを待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_GraphArea").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. 拡大ボタンをタップ (NAV-PH-002)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasContentDescription("拡大表示", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasContentDescription("拡大表示", substring = true)).onFirst().performClick()

        // 4. グラフ拡大画面が表示されたことを確認
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("GraphExpansion_BackButton").fetchSemanticsNodes().isNotEmpty()
        }
        // 名前も含まれていることを確認
        composeTestRule.onNode(hasText("愛", substring = true)).assertIsDisplayed()

        // 5. 戻る (タグで明示的に指定)
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        
        // グラフタブが表示されていることを確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("HealthScreen_Tab_Graph").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").assertIsDisplayed()
    }

    /**
     * SCN_PC_01: 所見メモ：写真撮影フロー (追加ボタンの存在確認)
     */
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
            composeTestRule.onAllNodes(hasContentDescription("編集")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasContentDescription("編集")).performClick()

        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithTag("Condition_AddPhotoButton", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("Condition_AddPhotoButton", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        
        composeTestRule.onNodeWithText("キャンセル").performClick()
    }

    /**
     * SCN_PC_02: 所見メモ：写真全画面表示 (NAV-PC-004)
     */
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

        // 5. 閉じる
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").performClick()
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()
    }

    /**
     * SCN_SET_01: 設定・ログ画面への遷移 (NAV-S-001 ~ 004)
     */
    @Test
    fun SCN_SET_01_SettingsNavigation() {
        // 1. 設定画面へ (確実な遷移待ち)
        composeTestRule.waitUntil(15000) { composeTestRule.onAllNodesWithTag("MainScreen_UserList").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("MainScreen_MenuButton").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("MainScreen_MenuItem_Settings", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("MainScreen_MenuItem_Settings", useUnmergedTree = true).performClick()
        
        // 「設定・管理」画面に到達したことを確認
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithText("設定・管理", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. 開発者モードを有効化（確実に7回カウントを進める）
        repeat(7) {
            robustScrollDownTo("Settings_VersionRow")
            
            // ダイアログが出ていたら消す（テスト環境の制約への対処）
            if (composeTestRule.onAllNodesWithText("閉じる").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithText("閉じる").performClick()
                composeTestRule.waitForIdle()
            }
            
            // バージョン情報をクリック
            composeTestRule.onNodeWithTag("Settings_VersionRow").performClick()
            composeTestRule.waitForIdle()
        }
        
        // 最後に残ったダイアログを閉じる
        if (composeTestRule.onAllNodesWithText("閉じる").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("閉じる").performClick()
            composeTestRule.waitForIdle()
        }
        
        // 3. 操作ログの参照 (NAV-S-002) - 下にある
        robustScrollDownTo("Settings_AuditLogButton")
        composeTestRule.onNodeWithTag("Settings_AuditLogButton").performClick()
        composeTestRule.waitUntil(30000) { composeTestRule.onAllNodesWithText("操作ログ").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("AuditLogScreen_BackButton").performClick()
        composeTestRule.waitForIdle()

        // 4. 利用終了者の管理（復帰） (NAV-S-003) - 上にある
        robustScrollUpTo("Settings_RestoreUserButton")
        composeTestRule.onNodeWithTag("Settings_RestoreUserButton").performClick()
        // タイトルは「利用者の復帰」
        composeTestRule.waitUntil(30000) { composeTestRule.onAllNodesWithText("利用者の復帰").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        composeTestRule.waitForIdle()

        // 5. 迷子写真の確認 (NAV-S-004) - 下にある
        robustScrollDownTo("Settings_OrphanedPhotosButton")
        composeTestRule.onNodeWithTag("Settings_OrphanedPhotosButton").performClick()
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithTag("OrphanedPhoto_BackButton", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("OrphanedPhoto_BackButton").performClick()
        composeTestRule.waitForIdle()

        // 6. メイン画面に戻る
        robustScrollUpTo("SettingsScreen_BackButton")
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").performClick()
        composeTestRule.onNodeWithTag("MainScreen_UserList").assertIsDisplayed()
    }
}
