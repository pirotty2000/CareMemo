@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.condition

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val currentPerson = MutableStateFlow<Person?>(null)
    private val isNameMaskingEnabled = MutableStateFlow(false)

    private val mockUri = Uri.parse("content://media/external/images/media/1")

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { conditionViewModel.isProcessing } returns isProcessing
        every { detailViewModel.currentPerson } returns currentPerson
        every { detailViewModel.isNameMaskingEnabled } returns isNameMaskingEnabled

        currentPerson.value = Person(
            id = 1, lastName = "山田", firstName = "太郎",
            lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
            birthday = Instant.parse("1950-01-01T00:00:00Z")
        )
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
    fun cp01_photo_preview_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_Image").assertIsDisplayed()
    }

    @Test
    fun cp02_caption_input_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput").assertIsDisplayed()
    }

    @Test
    fun cp03_action_buttons_are_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").assertIsDisplayed() // コード上は「キャンセル」
        composeTestRule.onNodeWithTag("PhotoPreview_BackButton").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (ConditionPhotoPreviewScreen)
    // ======================================================================================

    @Test
    fun bh01_caption_editing_works() {
        setContent()
        val inputField = composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput")
        
        inputField.performTextClearance()
        inputField.performTextInput("新しいキャプション")
        
        inputField.assertTextContains("新しいキャプション")
    }

    @Test
    fun bh02_save_button_calls_viewModel_and_callback() {
        var savedCalled = false
        setContent(onSaved = { savedCalled = true })

        val inputField = composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput")
        inputField.performTextClearance()
        inputField.performTextInput("保存テスト")

        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").performClick()
        
        verify { 
            conditionViewModel.processAndSavePhoto(any(), eq(mockUri), eq(1), eq(100), eq("保存テスト")) 
        }
        assert(savedCalled)
    }

    @Test
    fun bh03_cancel_button_calls_viewModel_and_callback() {
        var backCalled = false
        setContent(onBack = { backCalled = true })

        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").performClick()
        
        verify { 
            conditionViewModel.deleteTempFile(any(), eq(mockUri)) 
        }
        assert(backCalled)
    }

    @Test
    fun bh04_back_button_calls_callback() {
        var backCalled = false
        setContent(onBack = { backCalled = true })

        composeTestRule.onNodeWithTag("PhotoPreview_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun bh05_loading_state_disables_input_and_shows_loading() {
        isProcessing.value = true
        setContent()

        composeTestRule.onNodeWithTag("PhotoPreview_Loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").assertDoesNotExist()
    }
}
