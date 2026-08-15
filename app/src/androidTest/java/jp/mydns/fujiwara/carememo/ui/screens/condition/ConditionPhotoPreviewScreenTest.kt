package jp.mydns.fujiwara.carememo.ui.screens.condition

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: ConditionPhotoPreviewScreen (SCR-PC-002)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-002_ConditionPhotoPreviewScreen.md に準拠
 */
class ConditionPhotoPreviewScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
    private val conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val mockUri = Uri.parse("content://media/external/images/media/1")

    @Before
    fun setup() {
        every { detailViewModel.uiState } returns MutableStateFlow(
            PersonDetailUiState(person = Person(id = "u1", lastName = "山田", firstName = "太郎", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        )
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { conditionViewModel.uiState } returns MutableStateFlow(
            PersonConditionUiState(previewUri = mockUri.toString(), selectedConditionId = "c1")
        )
        every { conditionViewModel.viewEvent } returns MutableSharedFlow()
        every { conditionViewModel.uiEventFlow } returns MutableSharedFlow()
    }

    private fun setContent(
        conditionState: PersonConditionUiState = PersonConditionUiState(previewUri = mockUri.toString(), selectedConditionId = "c1")
    ) {
        every { conditionViewModel.uiState } returns MutableStateFlow(conditionState)

        composeTestRule.setContent {
            CareMemoTheme {
                ConditionPhotoPreviewScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_imagePreview_isDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_Image").assertIsDisplayed()
    }

    @Test
    fun DSP_03_loadingIndicator_isDisplayed_duringProcessing() {
        setContent(PersonConditionUiState(previewUri = mockUri.toString(), isProcessing = true))
        composeTestRule.onNodeWithTag("PhotoPreview_Loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").assertDoesNotExist()
    }

    @Test
    fun DSP_04_errorMessage_isDisplayed() {
        setContent(PersonConditionUiState(previewUri = mockUri.toString(), errorMessage = "Load Error"))
        composeTestRule.onNodeWithText("Load Error").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_captionInput_updatesText() {
        setContent()
        val input = composeTestRule.onNodeWithTag("PhotoPreview_CaptionInput")
        input.performTextClearance()
        input.performTextInput("New Caption")
        input.assertTextContains("New Caption")
    }

    @Test
    fun ACT_02_saveButton_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_SaveButton").performClick()
        verify { conditionViewModel.processAndSavePhoto(eq(mockUri), any(), any()) }
        verify { navController.popBackStack() }
    }

    @Test
    fun ACT_03_deleteButton_showsDialog() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").performClick()
        // Default message or title check
        composeTestRule.onNodeWithText("写真を削除", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_02_deleteConfirm_popsBackStack() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoPreview_DeleteButton").performClick()
        // Confirm delete in dialog (ダイアログ内の「削除」ボタンを指定)
        composeTestRule.onNode(hasText("削除") and hasAnyAncestor(isDialog())).performClick()
        verify { navController.popBackStack() }
    }

    //endregion
}
