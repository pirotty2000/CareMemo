package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：ConditionPhotoFullScreen (写真フル画面表示)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-003_ConditionPhotoFullScreen.md
 */
class ConditionPhotoFullScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var conditionViewModel: PersonConditionViewModel

    private val currentConditionPhotos = MutableStateFlow<List<ConditionPhoto>>(emptyList())

    private val testPhotos = listOf(
        ConditionPhoto(id = 1, conditionId = 100, personId = 1, photoFileName = "photo1.jpg", thumbnailFileName = "thumb1.jpg", capturedAt = Instant.now(), caption = "キャプション1"),
        ConditionPhoto(id = 2, conditionId = 100, personId = 1, photoFileName = "photo2.jpg", thumbnailFileName = "thumb2.jpg", capturedAt = Instant.now(), caption = ""),
        ConditionPhoto(id = 3, conditionId = 100, personId = 1, photoFileName = "photo3.jpg", thumbnailFileName = "thumb3.jpg", capturedAt = Instant.now(), caption = "キャプション3")
    )

    @Before
    fun setup() {
        conditionViewModel = mockk(relaxed = true)
        every { conditionViewModel.currentConditionPhotos } returns currentConditionPhotos.asStateFlow()
        
        currentConditionPhotos.value = testPhotos
    }

    private fun setContent(conditionId: Int = 100, initialPhotoId: Int = 1, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                ConditionPhotoFullScreen(
                    conditionId = conditionId,
                    initialPhotoId = initialPhotoId,
                    viewModel = conditionViewModel,
                    onBack = onBack
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (ConditionPhotoFullScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_layout_display() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").assertIsDisplayed()
    }

    @Test
    fun cp02_initial_display_id_consistency() {
        setContent(initialPhotoId = 3)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_3").assertIsDisplayed()
        composeTestRule.onNodeWithText("キャプション3").assertIsDisplayed()
    }

    @Test
    fun cp03_caption_display_when_exists() {
        setContent(initialPhotoId = 1)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertIsDisplayed()
        composeTestRule.onNodeWithText("キャプション1").assertIsDisplayed()
    }

    @Test
    fun cp04_no_caption_display_when_empty() {
        setContent(initialPhotoId = 2)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertDoesNotExist()
    }

    @Test
    fun cp05_loading_display_when_empty_list() {
        currentConditionPhotos.value = emptyList()
        setContent()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Loading").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (ConditionPhotoFullScreen)
    // ======================================================================================

    @Test
    fun bh01_swipe_switching_works() {
        setContent(initialPhotoId = 1)
        
        // 標準的なスワイプ操作を実行
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_2").assertIsDisplayed()
    }

    @Test
    fun bh02_double_tap_zoom_works() {
        setContent(initialPhotoId = 1)
        val imageNode = composeTestRule.onNodeWithTag("PhotoFullScreen_Image_1")
        
        imageNode.performTouchInput {
            doubleClick()
        }
        
        // ズーム中はスワイプが無効になることを確認
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft(startX = centerX, endX = 0f)
        }
        composeTestRule.waitForIdle()
        imageNode.assertIsDisplayed()
    }

    @Test
    fun bh03_swipe_disabled_during_zoom() {
        setContent(initialPhotoId = 1)
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_1").performTouchInput {
            doubleClick()
        }
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft(startX = centerX, endX = 0f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_1").assertIsDisplayed()
    }

    @Test
    fun bh04_finish_and_return_to_caller() {
        var backCalled = false
        setContent(onBack = { backCalled = true })
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun bh05_pager_id_follow_up_updates_caption() {
        setContent(initialPhotoId = 1)
        
        // 1枚目から2枚目へ (キャプションあり -> なし)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertDoesNotExist()
        
        // 2枚目から3枚目へ (キャプションなし -> あり)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertIsDisplayed()
        composeTestRule.onNodeWithText("キャプション3").assertIsDisplayed()
    }
}
