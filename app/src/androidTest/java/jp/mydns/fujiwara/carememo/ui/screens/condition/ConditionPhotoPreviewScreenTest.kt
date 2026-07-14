package jp.mydns.fujiwara.carememo.ui.screens.condition

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：ConditionPhotoPreviewScreen (写真プレビュー)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-002_ConditionPhotoPreviewScreen.md
 */
class ConditionPhotoPreviewScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val isProcessing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val currentPerson = MutableStateFlow<Person?>(null)
    private val isNameMaskingEnabled = MutableStateFlow(false)

    private val mockUri = Uri.parse("content://media/external/images/media/1")

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { conditionViewModel.isProcessing } returns isProcessing.asStateFlow()
        every { conditionViewModel.errorMessage } returns errorMessage.asStateFlow()
        every { detailViewModel.currentPerson } returns currentPerson.asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns isNameMaskingEnabled.asStateFlow()

        currentPerson.value = Person(
            id = 1, lastName = "山田", firstName = "太郎",
            lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
            birthday = Instant.parse("1950-01-01T00:00:00Z")
        )
        errorMessage.value = null
        isProcessing.value = false
    }

    private fun setContent(onBack: () -> Unit = {}, onSaved: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                ConditionPhotoPreviewScreen(
                    viewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    uri = mockUri,
                    personId = 1,
                    conditionId = 100,
                    onBack = onBack,
                    onSaved = onSaved
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (ConditionPhotoPreviewScreen)
    // ======================================================================================

    @Test
    fun cp01_photo_preview_display() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_Image").assertIsDisplayed()
    }

    @Test
    fun cp02_caption_input_field() {
        setContent()
        // 入力欄が表示されていることを確認
        val input = composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput")
        input.assertIsDisplayed()
        
        // ラベルまたは初期値（日付）が含まれていることを確認
        input.assertTextContains("キャプション", substring = true)
    }

    @Test
    fun cp03_action_buttons_placement() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_BackButton").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (ConditionPhotoPreviewScreen)
    // ======================================================================================

    @Test
    fun bh01_caption_editing_persistence() {
        setContent()
        val inputField = composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput")
        
        inputField.performTextClearance()
        inputField.performTextInput("編集後のキャプション")
        
        inputField.assertTextContains("編集後のキャプション")
    }

    @Test
    fun bh02_save_and_return_to_pc001() {
        var savedCalled = false
        setContent(onSaved = { savedCalled = true })

        composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput").performTextInput("保存テスト")
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").performClick()
        
        verify { 
            conditionViewModel.processAndSavePhoto(any(), eq(mockUri), eq(1), eq(100), any()) 
        }
        assert(savedCalled)
    }

    @Test
    fun bh03_photo_deletion_with_confirmation_dialog() {
        var backCalled = false
        setContent(onBack = { backCalled = true })

        // 1. 画面上の削除ボタン押下
        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").performClick()
        
        // 2. 確認ダイアログが表示されること
        composeTestRule.onNodeWithText("写真を削除しますか？").assertIsDisplayed()
        
        // 3. ダイアログ内の「削除」ボタンを選択
        // onAllNodes を使って最後（ダイアログ側）のノードを取得する
        composeTestRule.onAllNodesWithText("削除").onLast().performClick()
        
        verify { 
            conditionViewModel.deleteTempFile(any(), eq(mockUri)) 
        }
        assert(backCalled)
    }

    @Test
    fun bh04_back_with_discard_changes_confirmation() {
        var backCalled = false
        setContent(onBack = { backCalled = true })

        // キャプションを変更する
        composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput").performTextInput("変更あり")

        // 戻るボタン押下
        composeTestRule.onNodeWithTag("PhotoPreview_BackButton").performClick()
        
        // 破棄確認ダイアログが表示されること
        composeTestRule.onNodeWithText("変更を破棄しますか？").assertIsDisplayed()
        
        // 破棄を選択
        composeTestRule.onNodeWithText("破棄").performClick()
        
        assert(backCalled)
    }

    @Test
    fun bh05_error_display_and_ui_consistency() {
        // 1. エラーメッセージの表示確認
        errorMessage.value = "読み込みエラー"
        setContent()
        composeTestRule.onNodeWithText("読み込みエラー").assertIsDisplayed()

        // 2. 処理中のUI無効化確認
        isProcessing.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("PhotoPreview_Loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").assertDoesNotExist()
        composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput").assertIsNotEnabled()
    }
}
