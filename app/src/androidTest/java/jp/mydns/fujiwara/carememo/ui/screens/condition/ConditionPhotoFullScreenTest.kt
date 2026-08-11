package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: ConditionPhotoFullScreen (SCR-PC-003)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-003_ConditionPhotoFullScreen.md に準拠
 */
class ConditionPhotoFullScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val testPhotos = listOf(
        ConditionPhoto(id = "1", conditionId = "c1", personId = "u1", photoFileName = "p1.jpg", thumbnailFileName = "t1.jpg", capturedAt = Instant.now(), caption = "Caption 1"),
        ConditionPhoto(id = "2", conditionId = "c1", personId = "u1", photoFileName = "p2.jpg", thumbnailFileName = "t2.jpg", capturedAt = Instant.now(), caption = ""),
        ConditionPhoto(id = "3", conditionId = "c1", personId = "u1", photoFileName = "p3.jpg", thumbnailFileName = "t3.jpg", capturedAt = Instant.now(), caption = "Caption 3")
    )

    @Before
    fun setup() {
        every { conditionViewModel.uiEventFlow } returns MutableSharedFlow()
        every { conditionViewModel.viewEvent } returns MutableSharedFlow()
    }

    private fun setContent(
        photos: List<ConditionPhoto> = testPhotos,
        initialPhotoId: String? = "1",
        isLoading: Boolean = false
    ) {
        every { conditionViewModel.uiState } returns MutableStateFlow(
            PersonConditionUiState(
                currentConditionPhotos = photos.toImmutableList(),
                initialPhotoId = initialPhotoId,
                isLoading = isLoading
            )
        )

        composeTestRule.setContent {
            CareMemoTheme {
                ConditionPhotoFullScreen(
                    viewModel = conditionViewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_basicLayout_isDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").assertIsDisplayed()
    }

    @Test
    fun DSP_02_initialPhoto_isCorrect() {
        setContent(initialPhotoId = "3")
        // Pager should be on the 3rd photo
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Caption 3").assertIsDisplayed()
    }

    @Test
    fun DSP_03_caption_isDisplayed_whenAvailable() {
        setContent(initialPhotoId = "1")
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertIsDisplayed()
        composeTestRule.onNodeWithText("Caption 1").assertIsDisplayed()
    }

    @Test
    fun DSP_04_caption_isNotDisplayed_whenEmpty() {
        setContent(initialPhotoId = "2")
        composeTestRule.onNodeWithTag("PhotoFullScreen_Caption").assertDoesNotExist()
    }

    @Test
    fun DSP_05_loadingIndicator_isDisplayed_whenLoadingAndEmpty() {
        setContent(photos = emptyList(), isLoading = true)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Spinner").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_swipe_switchesPhotos() {
        setContent(initialPhotoId = "1")
        
        // Swipe left to go to next photo
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_2").assertIsDisplayed()
    }

    @Test
    fun ACT_02_ACT_03_zoom_restrictsAndEnablesSwipe() {
        setContent(initialPhotoId = "1")
        val imageNode = composeTestRule.onNodeWithTag("PhotoFullScreen_Image_1")
        
        // Double tap to zoom
        imageNode.performTouchInput { doubleClick() }
        
        // Try to swipe while zoomed (userScrollEnabled should be false)
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        
        // Still on photo 1
        imageNode.assertIsDisplayed()
        
        // Double tap again to zoom out
        imageNode.performTouchInput { doubleClick() }
        
        // Now swipe should work
        composeTestRule.onNodeWithTag("PhotoFullScreen_Pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("PhotoFullScreen_Image_2").assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_popsBackStack() {
        setContent()
        composeTestRule.onNodeWithTag("PhotoFullScreen_BackButton").performClick()
        verify { navController.popBackStack() }
    }

    //endregion
}
