package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: DeleteOrRestorePersonScreen (SCR-S-003)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-003_DeleteOrRestorePerson.md に準拠
 */
class DeleteOrRestorePersonScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<DeleteOrRestorePersonViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val mockPersons = listOf(
        Person(id = "u1", lastName = "山田", firstName = "太郎", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()),
        Person(id = "u2", lastName = "佐藤", firstName = "花子", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now())
    )

    @Before
    fun setup() {
        every { viewModel.uiState } returns MutableStateFlow(
            DeleteOrRestorePersonUiState(
                archivedPersons = mockPersons.toImmutableList(),
                mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                isNameMaskingEnabled = false // Disable masking for testing exact name matches
            )
        )
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        every { viewModel.viewEvent } returns MutableSharedFlow(extraBufferCapacity = 1)
    }

    private fun setContent(
        uiState: DeleteOrRestorePersonUiState? = null
    ) {
        if (uiState != null) {
            every { viewModel.uiState } returns MutableStateFlow(uiState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_archivedList_rendersItems() {
        setContent()
        composeTestRule.onNodeWithTag("DeleteOrRestore_List").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DeleteOrRestore_Item_u1").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_03_actionButton_isDisplayed_whenSelected() {
        setContent(DeleteOrRestorePersonUiState(
            archivedPersons = mockPersons.toImmutableList(),
            selectedIds = persistentSetOf("u1"),
            mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE
        ))
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton")
            .assertIsDisplayed()
            .assertTextContains("1名", substring = true)
            .assertTextContains("復帰", substring = true)
    }

    @Test
    fun DSP_04_emptyState_isDisplayed() {
        setContent(DeleteOrRestorePersonUiState(archivedPersons = persistentListOf(), isLoading = false))
        composeTestRule.onNodeWithTag("DeleteOrRestore_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("終了した利用者はいません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_checkboxToggle_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("DeleteOrRestore_Checkbox_u1").performClick()
        verify { viewModel.toggleSelection("u1") }
    }

    @Test
    fun ACT_02_selectAll_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("DeleteOrRestore_SelectAllButton").performClick()
        verify { viewModel.selectAll(any()) }
    }

    @Test
    fun ACT_03_actionButton_showsConfirmDialog() {
        setContent(DeleteOrRestorePersonUiState(
            archivedPersons = mockPersons.toImmutableList(),
            selectedIds = persistentSetOf("u1"),
            mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE
        ))
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").performClick()
        // Match string from R.string.archive_dialog_restore_confirm_msg
        composeTestRule.onNodeWithText("戻します", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        verify { viewModel.navigateBack() }
    }

    @Test
    fun NAV_02_finishEvent_popsBackStack() {
        val viewEventFlow = MutableSharedFlow<DeleteOrRestorePersonViewEvent>(extraBufferCapacity = 1)
        every { viewModel.viewEvent } returns viewEventFlow
        
        setContent()

        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(DeleteOrRestorePersonViewEvent.Finish)
        }
        
        composeTestRule.waitForIdle()
        verify { navController.popBackStack() }
    }

    //endregion
}
