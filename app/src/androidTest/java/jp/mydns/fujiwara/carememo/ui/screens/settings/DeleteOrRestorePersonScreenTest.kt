@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * SCR-S-003 DeleteOrRestorePerson の UI テスト (System B 移行済)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-003_DeleteOrRestorePerson.md に準拠
 */
class DeleteOrRestorePersonScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val mockPersons = listOf(
        Person(id = "1", lastName = "利用終了", firstName = "太郎", lastNameFurigana = "りようしゅうりょう", firstNameFurigana = "たろう", birthday = Instant.now()),
        Person(id = "2", lastName = "アーカイブ", firstName = "次郎", lastNameFurigana = "あーかいぶ", firstNameFurigana = "じろう", birthday = Instant.now())
    )

    private val uiStateFlow = MutableStateFlow(DeleteOrRestorePersonUiState())

    private fun setupMockViewModel(): DeleteOrRestorePersonViewModel {
        val viewModel = mockk<DeleteOrRestorePersonViewModel>(relaxed = true)
        
        // System B 形式の uiState 購読を stub
        every { viewModel.uiState } returns uiStateFlow.asStateFlow()
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        
        // 初期状態のセット
        uiStateFlow.value = DeleteOrRestorePersonUiState(
            archivedPersons = mockPersons,
            selectedIds = emptySet(),
            isNameMaskingEnabled = false,
            isLoading = false
        )
        return viewModel
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (DeleteOrRestorePersonContent)
    // ======================================================================================

    @Test
    fun cp01_archivedUserList_isDisplayed() {
        val viewModel = setupMockViewModel()
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // リストが存在することを確認
        composeTestRule.onNodeWithTag("DeleteOrRestore_List").assertExists()
        
        // 項目が表示されていること（スペースの差異を許容）
        composeTestRule.onNodeWithText("利用終了", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("アーカイブ", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp02_emptyState_isDisplayed() {
        val viewModel = setupMockViewModel()
        uiStateFlow.value = uiStateFlow.value.copy(archivedPersons = emptyList())
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 空状態のメッセージが表示されること
        composeTestRule.onNodeWithTag("DeleteOrRestore_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("終了した利用者はいません").assertIsDisplayed()
    }

    @Test
    fun cp03_veryLongName_doesNotBreakLayout() {
        val longNamePerson = Person(
            id = "99", 
            lastName = "寿限無寿限無五劫の擦り切れ海砂利水魚の水行末雲来末風来末食う寝る処に住む処", 
            firstName = "太郎", 
            lastNameFurigana = "じゅげむじゅげむ", 
            firstNameFurigana = "たろう", 
            birthday = Instant.now()
        )
        val viewModel = setupMockViewModel()
        uiStateFlow.value = uiStateFlow.value.copy(archivedPersons = listOf(longNamePerson))
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 長大な名前でもチェックボックスが表示され、操作可能であること
        composeTestRule.onNodeWithTag("DeleteOrRestore_Checkbox_99").assertIsDisplayed().performClick()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (DeleteOrRestorePerson)
    // ======================================================================================

    @Test
    fun bh01_restoreOperation_showsConfirmDialog() {
        val viewModel = setupMockViewModel()
        // 項目1を選択済みにする
        uiStateFlow.value = uiStateFlow.value.copy(selectedIds = setOf("1"))
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 実行ボタンを押す
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").performClick()
        composeTestRule.waitForIdle()

        // 復帰確認ダイアログの固有ボタンが表示されること
        composeTestRule.onNodeWithText("復帰を実行する").assertIsDisplayed()
    }

    @Test
    fun bh02_deleteOperation_showsWarningDialog() {
        val viewModel = setupMockViewModel()
        uiStateFlow.value = uiStateFlow.value.copy(selectedIds = setOf("1"), mode = DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.DELETE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 実行ボタンを押す
        composeTestRule.onNodeWithTag("DeleteOrRestore_ActionButton").performClick()
        composeTestRule.waitForIdle()

        // 警告ダイアログ（抹消）の固有タグが表示されること
        composeTestRule.onNodeWithTag("DeleteOrRestore_ConfirmDialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("抹消を実行する").assertIsDisplayed()
    }

    @Test
    fun bh03_successOperation_preparesRefreshSignal() {
        val viewModel = setupMockViewModel()
        val navController = mockk<NavController>(relaxed = true)
        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        
        // previousBackStackEntry の SavedStateHandle をモック
        val previousEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { previousEntry.savedStateHandle } returns savedStateHandle
        every { navController.previousBackStackEntry } returns previousEntry
        
        // currentBackStackEntry
        val currentEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { currentEntry.savedStateHandle } returns androidx.lifecycle.SavedStateHandle()
        every { navController.currentBackStackEntry } returns currentEntry

        // 成功イベントを発行するための SharedFlow
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 成功イベントを発行
        composeTestRule.runOnIdle {
            uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowSnackbarRes(jp.mydns.fujiwara.carememo.R.string.archive_msg_restored, listOf(1)))
        }
        composeTestRule.waitForIdle()

        // 戻るボタンを押す
        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        composeTestRule.waitForIdle()

        // 検証: SavedStateHandle にリフレッシュ要求がセットされていること
        assert(savedStateHandle.get<Boolean>("refresh_needed") == true)
    }

    @Test
    fun bh04_backOperation_callsOnBack() {
        val viewModel = setupMockViewModel()
        val navController = mockk<NavController>(relaxed = true)
        var backCalled = false

        composeTestRule.setContent {
            CareMemoTheme {
                DeleteOrRestorePersonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
                    onBack = { backCalled = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("DeleteOrRestore_BackButton").performClick()
        assert(backCalled)
    }
}
