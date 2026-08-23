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
 * UI Scenario Test: 緊急連絡先登録フロー (SCN-REG-07)
 *
 * 仕様書: doc/test/scenario/TEST_SCENARIO_DataRegistrationFlow.md に準拠
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmergencyContactRegistrationScenarioTest {

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
     * SCN-01-REG: 緊急連絡先の新規登録
     */
    @Test
    fun SCN_01_REG_AddEmergencyContactFlow() {
        val facility = "テスト病院"
        val person = "テスト先生"
        val phone = "0120000000"
        val displayPhone = "0120-000-000"
        val priority = "1"

        // 1. 利用者のメニューを開き「緊急連絡先の管理」へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        
        // 鉛筆アイコン（メニューボタン）をタップ
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        // 「緊急連絡先の管理」メニューをタップ
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_EmergencyContact").performClick()

        // 2. 緊急連絡先一覧画面が表示されるのを待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("MedicalContactList_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        
        // FAB（追加ボタン）をタップ
        composeTestRule.onNodeWithTag("MedicalContactList_AddButton").performClick()

        // 3. 登録画面での入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("EmergencyContact_FacilityField").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("EmergencyContact_FacilityField").performTextReplacement(facility)
        composeTestRule.onNodeWithTag("EmergencyContact_PersonField").performTextReplacement(person)
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").performTextReplacement(phone)
        composeTestRule.onNodeWithTag("EmergencyContact_PriorityField").performTextReplacement(priority)

        // キーボードを閉じる
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").performClick()

        // 5. 一覧画面に戻り、反映を確認
        // まずは画面遷移（一覧への復帰）自体を待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("MedicalContactList_AddButton").fetchSemanticsNodes().isNotEmpty()
        }

        // リスト（MedicalContactList）をスクロールして対象を表示させる
        // LazyColumn の場合は performScrollToNode を使用して確実にツリーへ引き込む
        composeTestRule.onNodeWithTag("MedicalContactList")
            .performScrollToNode(hasText(facility))
        
        // 反映を確認
        composeTestRule.onNodeWithText(facility).assertIsDisplayed()
        composeTestRule.onNodeWithText(person).assertIsDisplayed()
        composeTestRule.onNodeWithText(displayPhone).assertIsDisplayed()

        // 6. 戻るボタンをタップしてメイン画面へ
        composeTestRule.onNodeWithTag("MedicalContactList_BackButton").performClick()

        // 7. 利用者一覧に戻ったことを確認
        // 利用者リストが表示されるまで待つ
        composeTestRule.waitUntil(20000) {
            try {
                composeTestRule.onNodeWithTag("MainScreen_UserList").assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }
        
        // 利用者名が表示されていることを確認
        composeTestRule.onNodeWithText(targetPersonName).performScrollTo().assertIsDisplayed()
    }
}
