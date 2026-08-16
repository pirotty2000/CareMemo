package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoType
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.UnassignedPhotoUiState
import jp.mydns.fujiwara.carememo.viewmodel.UnassignedPhotoViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: UnassignedPhotoManagementScreen (SCR-S-004)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-004_UnassignedPhotoManagementScreen.md に準拠
 */
class UnassignedPhotoManagementScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<UnassignedPhotoViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val mockPhotos = listOf(
        UnassignedPhotoInfo(
            type = UnassignedPhotoType.TEMPORARY,
            photoId = null,
            personId = null,
            photoFileName = "temp_1.jpg",
            thumbnailFileName = "thumb_1.jpg",
            capturedAt = Instant.now(),
            descriptionResId = jp.mydns.fujiwara.carememo.R.string.unassigned_photo_type_temporary
        ),
        UnassignedPhotoInfo(
            type = UnassignedPhotoType.UNASSIGNED_RECORD,
            photoId = "p1",
            personId = "u1",
            photoFileName = "unassigned_1.jpg",
            thumbnailFileName = "thumb_2.jpg",
            capturedAt = Instant.now(),
            descriptionResId = jp.mydns.fujiwara.carememo.R.string.unassigned_photo_type_unassigned
        )
    )

    @Before
    fun setup() {
        every { viewModel.uiState } returns MutableStateFlow(UnassignedPhotoUiState(unassignedPhotos = mockPhotos.toImmutableList()))
        every { viewModel.viewEvent } returns MutableSharedFlow(extraBufferCapacity = 1)
    }

    private fun setContent(uiState: UnassignedPhotoUiState? = null) {
        if (uiState != null) {
            every { viewModel.uiState } returns MutableStateFlow(uiState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                UnassignedPhotoManagementScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_unassignedList_rendersItems() {
        setContent()
        composeTestRule.onNodeWithTag("UnassignedPhoto_Grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("UnassignedPhoto_Item_temp_1.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("UnassignedPhoto_Item_unassigned_1.jpg").assertIsDisplayed()
    }

    @Test
    fun DSP_03_emptyState_isDisplayed_whenNoPhotos() {
        setContent(UnassignedPhotoUiState(isLoading = false, unassignedPhotos = persistentListOf()))
        composeTestRule.onNodeWithTag("UnassignedPhoto_EmptyState").assertIsDisplayed()
        // Match string from R.string.unassigned_photo_empty_msg
        composeTestRule.onNodeWithText("見つかりませんでした", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_04_loadingIndicator_isDisplayed() {
        setContent(UnassignedPhotoUiState(isLoading = true, unassignedPhotos = persistentListOf()))
        composeTestRule.onNodeWithTag("UnassignedPhoto_Loading").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_deleteIcon_showsConfirmDialog() {
        setContent()
        // Click delete on the first item
        composeTestRule.onAllNodesWithTag("UnassignedPhoto_DeleteButton").onFirst().performClick()
        
        // Match title from R.string.p_detail_dialog_title_delete ("データの削除")
        composeTestRule.onNodeWithText("データの削除", substring = true).assertIsDisplayed()
    }

    @Test
    fun ACT_02_deleteConfirm_triggersViewModel() {
        setContent()
        composeTestRule.onAllNodesWithTag("UnassignedPhoto_DeleteButton").onFirst().performClick()
        
        // Click "Delete" in dialog
        composeTestRule.onNodeWithText("削除").performClick()
        
        verify { viewModel.deletePhoto(any()) }
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("UnassignedPhoto_BackButton").performClick()
        verify { viewModel.navigateBack() }
    }

    //endregion
}
