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
 * UI Scenario Test: データ登録フロー (SCN-REG-01)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersonRegistrationScenarioTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

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

    @Test
    fun SCN_01_REG_AddNewPersonFlow() {
        val lastName = "柿"
        val firstName = "くけ子"
        val fullName = "${lastName}\u3000${firstName}"

        composeTestRule.waitUntil(40000) {
            composeTestRule.onAllNodesWithText("愛\u3000植夫").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("MainScreen_AddButton").performClick()

        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("PersonEdit_LastName").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("PersonEdit_LastName").performTextInput(lastName)
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").performTextInput(firstName)
        composeTestRule.onNodeWithTag("PersonEdit_LastNameKana").performTextInput("かき")
        composeTestRule.onNodeWithTag("PersonEdit_FirstNameKana").performTextInput("くけこ")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").performClick()
        // Use testTag for dropdown item selection to avoid ambiguity
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("EraItem_SHOWA", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("EraItem_SHOWA", useUnmergedTree = true).performClick()
        
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").performTextInput("44")
        composeTestRule.onNodeWithTag("PersonEdit_BirthMonth").performTextInput("5")
        composeTestRule.onNodeWithTag("PersonEdit_BirthDay").performTextInput("29")

        Espresso.closeSoftKeyboard()

        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performScrollTo().performClick()

        // 6. 一覧画面に戻り、新規登録した名前が表示されるのを待つ (タグを指定して重複を避ける)
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodes(hasText(fullName).and(hasAnyAncestor(hasTestTag("MainScreen_UserList")))).fetchSemanticsNodes().isNotEmpty()
        }

        // 7. スナックバーの表示を確認する
        composeTestRule.onNodeWithText("登録しました", substring = true).assertIsDisplayed()
        
        // 8. 最終確認
        composeTestRule.onNode(hasText(fullName).and(hasAnyAncestor(hasTestTag("MainScreen_UserList")))).assertIsDisplayed()
    }

    @Test
    fun SCN_02_EDIT_EditPersonBasicInfoFlow() {
        val targetName = "愛\u3000植夫"

        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetName).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Edit").performClick()

        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonEdit_LastName").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("PersonEdit_BirthDay").performTextReplacement("28")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performScrollTo().performClick()

        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(targetName).assertIsDisplayed()
        composeTestRule.onNodeWithText("2月28日", substring = true).assertIsDisplayed()
    }
}
