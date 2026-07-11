@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：ConditionPhotoFullScreen (写真フル画面表示)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_ConditionPhotoFullScreen.md
 */
class ConditionPhotoFullScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: PersonConditionViewModel
    private val photosFlow = MutableStateFlow<List<ConditionPhoto>>(emptyList())

    private val mockPhotos = listOf(
        ConditionPhoto(id = 101, conditionId = 1, personId = 1, photoFileName = "p1.jpg", thumbnailFileName = "t1.jpg", capturedAt = Instant.now(), caption = "キャプション1"),
        ConditionPhoto(id = 102, conditionId = 1, personId = 1, photoFileName = "p2.jpg", thumbnailFileName = "t2.jpg", capturedAt = Instant.now(), caption = ""),
        ConditionPhoto(id = 103, conditionId = 1, personId = 1, photoFileName = "p3.jpg", thumbnailFileName = "t3.jpg", capturedAt = Instant.now(), caption = "キャプション3")
    )

    @Before
    fun setup() {
        viewModel = mockk<PersonConditionViewModel>(relaxed = true)
        every { viewModel.currentConditionPhotos } returns photosFlow
    }

    private fun setContent(conditionId: Int = 1, initialPhotoId: Int = 101, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                ConditionPhotoFullScreen(
                    conditionId = conditionId,
                    initialPhotoId = initialPhotoId,
                    viewModel = viewModel,
                    onBack = onBack
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. 画面表示テスト (ConditionPhotoFullScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_layout_is_displayed() {
        photosFlow.value = mockPhotos
        setContent()

        // 戻るボタンの表示確認
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").assertIsDisplayed()
    }

    @Test
    fun cp02_initial_photo_is_displayed() {
        photosFlow.value = mockPhotos
        // 2枚目(ID: 102)を初期表示として指定
        setContent(initialPhotoId = 102)

        // 指定された ID の画像コンポーネントが表示されていること
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_102").assertIsDisplayed()
        // 前後の画像は（Pager内にあるが表示はされていないはず）
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_101").assertIsNotDisplayed()
    }

    @Test
    fun cp03_caption_is_displayed_when_present() {
        photosFlow.value = mockPhotos
        setContent(initialPhotoId = 101) // キャプションあり

        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertIsDisplayed()
        composeTestRule.onNodeWithText("キャプション1").assertIsDisplayed()
    }

    @Test
    fun cp04_caption_is_hidden_when_absent() {
        photosFlow.value = mockPhotos
        setContent(initialPhotoId = 102) // キャプションなし

        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertDoesNotExist()
    }

    @Test
    fun cp05_loading_is_displayed_when_empty() {
        photosFlow.value = emptyList()
        setContent()

        composeTestRule.onNodeWithTag("PhotoFullScreen_Loading").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (ConditionPhotoFullScreen)
    // ======================================================================================

    @Test
    fun bh01_swipe_switching_works() {
        photosFlow.value = mockPhotos
        setContent(initialPhotoId = 101)

        // 右から左へスワイプして次の写真へ
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        // 2枚目(102)が表示されていること
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_102").assertIsDisplayed()
    }

    @Test
    fun bh02_double_tap_zoom_works() {
        photosFlow.value = mockPhotos
        setContent(initialPhotoId = 101)

        // ダブルタップ実行（これ自体でクラッシュしないことの確認が主目的となるが、
        // 内部で onZoomStateChanged が呼ばれることを、次の BH-03 で間接的に検証する）
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_101").performTouchInput {
            doubleClick()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh03_swipe_disabled_when_zoomed() {
        photosFlow.value = mockPhotos
        setContent(initialPhotoId = 101)

        // 1. ダブルタップしてズーム状態にする
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_101").performTouchInput {
            doubleClick()
        }
        composeTestRule.waitForIdle()

        // 2. スワイプを試みる
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        // 3. ズーム中は Pager のスクロールが無効化されているため、ページが変わっていない（101のまま）こと
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_102").assertIsNotDisplayed()
        
        // 4. 再度ダブルタップで等倍に戻す
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_101").performTouchInput {
            doubleClick()
        }
        composeTestRule.waitForIdle()
        
        // 5. 等倍ならスワイプができるはず
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_102").assertIsDisplayed()
    }

    @Test
    fun bh04_back_operation_calls_callback() {
        var backCalled = false
        photosFlow.value = mockPhotos
        setContent(onBack = { backCalled = true })

        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").performClick()
        assert(backCalled)
    }
}
