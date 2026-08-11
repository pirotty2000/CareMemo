package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoType
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoUiState
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: OrphanedPhotoManagementScreen (SCR-S-004)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-004_OrphanedPhotoManagementScreen.md に準拠
 */
class OrphanedPhotoManagementScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<OrphanedPhotoViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val mockPhotos = listOf(
        OrphanedPhotoInfo(
            type = OrphanedPhotoType.TEMPORARY,
            photoId = null,
            personId = null,
            photoFileName = "temp_1.jpg",
            thumbnailFileName = "thumb_1.jpg",
            capturedAt = Instant.now(),
            descriptionResId = jp.mydns.fujiwara.carememo.R.string.orphaned_photo_type_temporary
        ),
        OrphanedPhotoInfo(
            type = OrphanedPhotoType.ORPHANED_RECORD,
            photoId = "p1",
            personId = "u1",
            photoFileName = "orphaned_1.jpg",
            thumbnailFileName = "thumb_2.jpg",
            capturedAt = Instant.now(),
            descriptionResId = jp.mydns.fujiwara.carememo.R.string.orphaned_photo_type_orphaned
        )
    )

    @Before
    fun setup() {
        every { viewModel.uiState } returns MutableStateFlow(OrphanedPhotoUiState(orphanedPhotos = mockPhotos.toImmutableList()))
        every { viewModel.viewEvent } returns MutableSharedFlow(extraBufferCapacity = 1)
    }

    private fun setContent(uiState: OrphanedPhotoUiState? = null) {
        if (uiState != null) {
            every { viewModel.uiState } returns MutableStateFlow(uiState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                OrphanedPhotoManagementScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_orphanedList_rendersItems() {
        setContent()
        composeTestRule.onNodeWithTag("OrphanedPhoto_Grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("OrphanedPhoto_Item_temp_1.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("OrphanedPhoto_Item_orphaned_1.jpg").assertIsDisplayed()
    }

    @Test
    fun DSP_03_emptyState_isDisplayed_whenNoPhotos() {
        setContent(OrphanedPhotoUiState(isLoading = false, orphanedPhotos = persistentListOf()))
        composeTestRule.onNodeWithTag("OrphanedPhoto_EmptyState").assertIsDisplayed()
        // Match string from R.string.orphaned_photo_empty_msg
        composeTestRule.onNodeWithText("見つかりませんでした", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_04_loadingIndicator_isDisplayed() {
        setContent(OrphanedPhotoUiState(isLoading = true, orphanedPhotos = persistentListOf()))
        composeTestRule.onNodeWithTag("OrphanedPhoto_Loading").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_deleteIcon_showsConfirmDialog() {
        setContent()
        // Click delete on the first item
        composeTestRule.onAllNodesWithTag("OrphanedPhoto_DeleteButton").onFirst().performClick()
        
        // Match title from R.string.p_detail_dialog_title_delete ("データの削除")
        composeTestRule.onNodeWithText("データの削除", substring = true).assertIsDisplayed()
    }

    @Test
    fun ACT_02_deleteConfirm_triggersViewModel() {
        setContent()
        composeTestRule.onAllNodesWithTag("OrphanedPhoto_DeleteButton").onFirst().performClick()
        
        // Click "Delete" in dialog
        composeTestRule.onNodeWithText("削除").performClick()
        
        verify { viewModel.deletePhoto(any()) }
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("OrphanedPhoto_BackButton").performClick()
        verify { viewModel.navigateBack() }
    }

    //endregion
}
