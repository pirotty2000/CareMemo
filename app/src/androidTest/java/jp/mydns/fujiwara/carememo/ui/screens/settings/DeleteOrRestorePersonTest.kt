@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：DeleteOrRestorePerson (利用者復帰・完全抹消)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_DeleteOrRestorePerson.md
 */
class DeleteOrRestorePersonTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: DeleteOrRestorePersonViewModel
    
    // 状態を動的に変更するための Flow
    private val archivedPersonListFlow = MutableStateFlow<List<Person>>(emptyList())
    private val selectedIdsFlow = MutableStateFlow<Set<Int>>(emptySet())
    private val isNameMaskingEnabledFlow = MutableStateFlow(false)

    private val mockArchivedPersons = listOf(
        Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.parse("1950-01-01T00:00:00Z"), note = "識別メモ1"),
        Person(id = 2, lastName = "佐藤", firstName = "花子", lastNameFurigana = "サトウ", firstNameFurigana = "ハナコ", birthday = Instant.parse("1960-05-10T00:00:00Z"), note = "")
    )

    @Before
    fun setup() {
        viewModel = mockk<DeleteOrRestorePersonViewModel>(relaxed = true)
        
        // デフォルトの状態を設定
        archivedPersonListFlow.value = mockArchivedPersons
        selectedIdsFlow.value = emptySet()
        isNameMaskingEnabledFlow.value = false
        
        every { viewModel.archivedPersonList } returns archivedPersonListFlow
        every { viewModel.selectedIds } returns selectedIdsFlow
        every { viewModel.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
    }

    private fun setContent(mode: DeleteOrRestorePersonViewModel.OperationMode, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    mode = mode,
                    onBack = onBack
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. 画面表示テスト (DeleteOrRestorePersonScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_display_restore_mode() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        composeTestRule.onNodeWithText("利用者の復帰").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DeleteOrRestore_SelectAllButton").assertIsDisplayed()
    }

    @Test
    fun cp02_cp03_basic_display_delete_mode_and_warning() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        
        composeTestRule.onNodeWithText("利用者の完全抹消").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DeleteOrRestore_WarningBanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("二度と復元できません", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp04_empty_state_is_displayed() {
        archivedPersonListFlow.value = emptyList()
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("終了した利用者はいません").assertIsDisplayed()
    }

    @Test
    fun cp05_person_list_is_displayed() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        // リスト自体の存在確認
        composeTestRule.onNodeWithTag("DeleteOrRestore_List").assertExists()
        
        // 山田さんの項目を確認（スクロールして可視化）
        val item = composeTestRule.onNodeWithTag("DeleteOrRestore_Item_1")
        item.performScrollTo().assertIsDisplayed()
        
        composeTestRule.onNodeWithText("山田", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("76歳", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("識別メモ1", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp06_select_all_button_hidden_in_delete_mode() {
        // 抹消モードでは非表示であることを確認
        setContent(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        composeTestRule.onNodeWithTag("DeleteOrRestore_SelectAllButton").assertDoesNotExist()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (DeleteOrRestorePersonScreen)
    // ======================================================================================

    @Test
    fun bh01_checkbox_selection_calls_viewmodel() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_Checkbox_2").performScrollTo().performClick()
        verify { viewModel.toggleSelection(2) }
    }

    @Test
    fun bh02_select_all_operation_calls_viewmodel() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_SelectAllButton").performClick()
        verify { viewModel.selectAll(any()) }
    }

    @Test
    fun bh03_action_button_visibility_reactive() {
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        // 最初は非表示
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").assertDoesNotExist()
        
        // 状態を更新して再描画を待つ
        selectedIdsFlow.value = setOf(1)
        composeTestRule.waitForIdle()
        
        // 表示されることを確認
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").assertIsDisplayed()
        
        // 再度空にして非表示を確認
        selectedIdsFlow.value = emptySet()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").assertDoesNotExist()
    }

    @Test
    fun bh04_restore_action_calls_viewmodel() {
        selectedIdsFlow.value = setOf(1, 2)
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").performClick()
        verify { viewModel.restoreSelectedPersons(any()) }
    }

    @Test
    fun bh05_bh06_delete_action_shows_dialog_and_calls_viewmodel() {
        selectedIdsFlow.value = setOf(1)
        setContent(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").performClick()
        
        // 最終確認ダイアログ
        composeTestRule.onNodeWithTag("DeleteOrRestore_ConfirmDialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("抹消を実行する").performClick()
        
        verify { viewModel.deleteSelectedPersons(any()) }
    }

    @Test
    fun bh07_back_operation_calls_callback() {
        var backCalled = false
        setContent(DeleteOrRestorePersonViewModel.OperationMode.RESTORE, onBack = { backCalled = true })
        
        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        assert(backCalled)
    }
}
