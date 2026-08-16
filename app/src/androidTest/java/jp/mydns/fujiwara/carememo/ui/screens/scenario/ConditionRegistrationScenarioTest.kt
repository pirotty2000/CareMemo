package jp.mydns.fujiwara.carememo.ui.screens.scenario

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import jp.mydns.fujiwara.carememo.CareMemoApplication
import jp.mydns.fujiwara.carememo.MainActivity
import jp.mydns.fujiwara.carememo.test.ScenarioTestDataLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

/**
 * UI Scenario Test: 所見メモ登録フロー (SCN-REG-05)
 *
 * 仕様書: doc/test/scenario/TEST_SCENARIO_DataRegistrationFlow.md に準拠
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ConditionRegistrationScenarioTest {

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
            
            // 3. テスト用写真ファイルの準備 (assets/photos/TEST_PHOTO.JPG -> cacheDir/TEST_PHOTO.JPG)
            prepareTestPhoto(appContext)
        }

        // 4. セキュリティロックをバイパスして起動
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("IS_TEST_MODE", true)
        }
        ActivityScenario.launch<MainActivity>(intent)
        
        // 5. Intent のスタブ化を開始
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    /**
     * assets からキャッシュディレクトリへテスト用写真をコピーします。
     */
    private suspend fun prepareTestPhoto(appContext: CareMemoApplication) = withContext(Dispatchers.IO) {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val cacheFile = File(appContext.cacheDir, "TEST_PHOTO.JPG")
        
        instrumentationContext.assets.open("photos/TEST_PHOTO.JPG").use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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
     * SCN-REG-05: 所見メモの新規登録：テキスト＋写真
     */
    @Test
    fun SCN_01_REG_AddConditionMemoWithPhotoFlow() {
        val title = "シナリオ・テスト"
        val author = "テスト・ユーザ"
        val memo = "これはシナリオ・テストで入力された文字列です。"

        // カメラ起動のインテントをスタブ化
        // 撮影要求が来たら、アプリが指定した出力先にテスト用写真をコピーして RESULT_OK を返す
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWithFunction { intent ->
            val outputUri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
            if (outputUri != null) {
                val appContext = InstrumentationRegistry.getInstrumentation().targetContext
                val cacheFile = File(appContext.cacheDir, "TEST_PHOTO.JPG")
                appContext.contentResolver.openOutputStream(outputUri)?.use { output ->
                    cacheFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }

        // 1. 利用者を選択して所見メモ画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_CONDITION_AT_VISIT").performClick()

        // 2. 追加ボタン（FAB）をタップ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("ConditionScreen_AddButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()

        // 3. 各項目を入力
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("Condition_MemoInput").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("Condition_TitleInput").performTextReplacement(title)
        composeTestRule.onNodeWithTag("Condition_AuthorInput").performTextReplacement(author)
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextReplacement(memo)
        inputCommonDateTime()

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("Condition_SaveButton").performScrollTo().performClick()

        // 5. 「記録の詳細」画面（閲覧モード）での表示確認
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("記録の詳細").fetchSemanticsNodes().isNotEmpty()
        }
        
        val detailPane = hasAnyAncestor(hasTestTag("ConditionDetailPane"))
        composeTestRule.onNode(hasText(title).and(detailPane)).assertIsDisplayed()

        // 6. カメラアイコンをタップして写真を撮影（スタブが起動）
        composeTestRule.onNodeWithTag("Condition_AddPhotoButton").performScrollTo().performClick()

        // 7. 写真のプレビュー画面(SCR-PC-002)が表示されるのを待つ
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("PhotoPreview_Image").fetchSemanticsNodes().isNotEmpty()
        }
        
        // 8. プレビュー画面で「保存」をタップ
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").performClick()

        // 9. 「記録の詳細」画面に戻り、写真サムネイルが存在することを確認
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("Condition_PhotoList").fetchSemanticsNodes().isNotEmpty()
        }
        // 詳細パネル内に写真リストがあるか
        composeTestRule.onNode(hasTestTag("Condition_PhotoList").and(detailPane)).assertIsDisplayed()

        // 10. 戻るボタンをタップして一覧へ
        composeTestRule.onNode(hasContentDescription("戻る").and(detailPane)).performClick()

        // 11. 履歴一覧に戻り、反映を確認（写真ありアイコンをチェック）
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        
        // 一覧画面の該当レコードが表示されていることを確認
        composeTestRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
    }

    /**
     * SCN-02-EDIT: 所見メモの編集
     */
    @Test
    fun SCN_02_EDIT_EditConditionMemoFlow() {
        val recordId = "9024b623-7c9c-45d1-b118-ff2cfd0cec52"
        val newTitle = "シナリオ・テスト"

        // 1. 利用者を選択して所見メモ画面へ遷移
        composeTestRule.waitUntil(30000) {
            composeTestRule.onAllNodesWithText(targetPersonName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetPersonName).performClick()
        composeTestRule.onNodeWithTag("CategorySelectionSheet_Button_CONDITION_AT_VISIT").performClick()

        // 2. 2023/12/01 のレコードを選択
        composeTestRule.waitUntil(20000) {
            composeTestRule.onAllNodesWithTag("HistoryItem_$recordId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("HistoryItem_$recordId").performScrollTo().performClick()

        // 3. 編集
        composeTestRule.onNodeWithContentDescription("編集").performClick()
        composeTestRule.onNodeWithTag("Condition_TitleInput").performTextReplacement(newTitle)

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 4. 保存
        composeTestRule.onNodeWithTag("Condition_SaveButton").performScrollTo().performClick()

        // 5. 反映確認
        val detailPane = hasAnyAncestor(hasTestTag("ConditionDetailPane"))
        composeTestRule.onNode(hasText(newTitle).and(detailPane)).assertIsDisplayed()

        // 6. 戻る
        composeTestRule.onNode(hasContentDescription("戻る").and(detailPane)).performClick()

        // 7. 履歴一覧に戻り、反映を確認
        // 詳細パネルが閉じるのを待つ
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("ConditionDetailPane").fetchSemanticsNodes().isEmpty()
        }
        // 一覧画面に新しいタイトルが表示されるのを待つ
        composeTestRule.waitUntil(25000) {
            composeTestRule.onAllNodesWithText(newTitle).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText(newTitle).performScrollTo().assertIsDisplayed()
    }
}

