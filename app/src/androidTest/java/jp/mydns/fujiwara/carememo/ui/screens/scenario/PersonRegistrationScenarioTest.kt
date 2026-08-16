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
 *
 * テスト実行時はセキュリティロックをバイパスして実行します。
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersonRegistrationScenarioTest {

    // Activity の自動起動を避けるため EmptyComposeRule を使用
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CareMemoApplication

        runBlocking {
            // 1. マスキング設定を無効化
            appContext.userSettingsRepository.setNameMaskingEnabled(false)

            // 2. テスト用データのリストア
            ScenarioTestDataLoader.restoreFromBackup()
        }

        // 3. セキュリティロックをバイパスするためのフラグを Intent に設定して起動
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("IS_TEST_MODE", true)
        }
        ActivityScenario.launch<MainActivity>(intent)
    }

    /**
     * SCN-REG-01: 利用者の新規登録
     */
    @Test
    fun SCN_01_REG_AddNewPersonFlow() {
        val lastName = "柿"
        val firstName = "くけ子"
        val fullName = "${lastName}\u3000${firstName}"

        // 【重要】既存のデータ（愛 植夫）が表示されるまで十分に待つ
        // これにより、初期化（INITIALIZING）を抜けてリストアが完了したことを担保します
        composeTestRule.waitUntil(40000) {
            composeTestRule.onAllNodesWithText("愛\u3000植夫").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. 追加ボタンタップ
        composeTestRule.onNodeWithTag("MainScreen_AddButton").performClick()

        // 2. 利用者登録画面が表示されるのを待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("PersonEdit_LastName").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. 氏名・ふりがなを入力する
        composeTestRule.onNodeWithTag("PersonEdit_LastName").performTextInput(lastName)
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").performTextInput(firstName)
        composeTestRule.onNodeWithTag("PersonEdit_LastNameKana").performTextInput("かき")
        composeTestRule.onNodeWithTag("PersonEdit_FirstNameKana").performTextInput("くけこ")

        // 4. 生年月日を入力する
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").performClick()
        composeTestRule.onNodeWithText("昭和").performClick()
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").performTextInput("44")
        composeTestRule.onNodeWithTag("PersonEdit_BirthMonth").performTextInput("5")
        composeTestRule.onNodeWithTag("PersonEdit_BirthDay").performTextInput("29")

        // キーボードを閉じる（保存ボタンが隠れるのを防ぐ）
        Espresso.closeSoftKeyboard()

        // 5. 「保存」ボタンをタップする（念のためスクロールも行う）
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performScrollTo().performClick()

        // 6. 一覧画面に戻り、新規登録した名前が表示されるのを待つ
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(fullName).fetchSemanticsNodes().isNotEmpty()
        }

        // 7. スナックバーの表示を確認する (名前 + さんを登録しました)
        composeTestRule.onNodeWithText("${fullName} さんを登録しました").assertIsDisplayed()

        // 8. 最終確認
        composeTestRule.onNodeWithText(fullName).assertIsDisplayed()
    }

    /**
     * SCN-02-EDIT: 利用者の基本情報編集
     */
    @Test
    fun SCN_02_EDIT_EditPersonBasicInfoFlow() {
        val targetName = "愛\u3000植夫"

        // 1. 利用者一覧画面で「愛 植夫」が表示されるまで待つ
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetName).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. 「愛 植夫」の右側メニューボタンをタップ (長押しから変更)
        // 特定の利用者の行にあるメニューボタンを指定（ここでは単純にタグで指定）
        composeTestRule.onNodeWithTag("UserListItem_MenuButton").performClick()
        
        // 3. 「基本情報の編集」をタップ (タグで確実に指定)
        composeTestRule.onNodeWithTag("UserListItem_MenuItem_Edit").performClick()

        // 4. 編集画面が表示されるのを待つ
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("PersonEdit_LastName").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. 生年月日の「日」を修正 (6 -> 28)
        composeTestRule.onNodeWithTag("PersonEdit_BirthDay").performTextReplacement("28")

        // キーボードを閉じる
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 6. 「保存」ボタンをタップ (NAV-M-003)
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performScrollTo().performClick()

        // 7. 一覧画面に戻るのを待つ（メイン画面の FAB が表示されれば戻ったとみなす）
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("MainScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. 一覧画面に正しく反映されていることを確認
        composeTestRule.onNodeWithText(targetName).assertIsDisplayed()
        // 生年月日が昭和20年2月28日に更新されていることを確認
        composeTestRule.onNodeWithText("2月28日", substring = true).assertIsDisplayed()
    }
}
