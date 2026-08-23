package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Test: PersonEditScreen (SCR-M-002)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-M-002_PersonEditScreen.md に準拠
 */
class PersonEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_initialDisplay_newMode() {
        setContent {
            PersonEditScreenContentWrapper(isNew = true, isValid = false)
        }
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun DSP_02_initialDisplay_editMode() {
        setContent {
            PersonEditScreenContentWrapper(
                isNew = false,
                lastName = "山田",
                firstName = "太郎",
                isValid = true
            )
        }
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("山田")
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").assertTextContains("太郎")
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun DSP_03_loadingIndicator_isDisplayed() {
        setContent {
            PersonEditScreenContentWrapper(isLoading = true)
        }
        composeTestRule.onNodeWithTag("PersonEdit_Loading").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_nameInput_callsViewModel() {
        var capturedLastName = ""
        setContent {
            PersonEditScreenContentWrapper(onLastNameChange = { capturedLastName = it })
        }
        composeTestRule.onNodeWithTag("PersonEdit_LastName").performTextInput("佐藤")
        assert(capturedLastName == "佐藤")
    }

    @Test
    fun ACT_03_saveButton_callsViewModel() {
        var saveCalled = false
        setContent {
            PersonEditScreenContentWrapper(isValid = true, onSave = { saveCalled = true })
        }
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performClick()
        assert(saveCalled)
    }

    @Test
    fun ACT_04_cancelWithChanges_showsConfirmDialog() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        // Simulate changed state
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isNew = true))
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        every { viewModel.viewEvent } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, navController = mockk(relaxed = true))
            }
        }

        composeTestRule.onNodeWithTag("PersonEdit_CancelButton").performClick()
        composeTestRule.onNodeWithTag("PersonEdit_DiscardConfirmDialog").assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・イベント実行テスト (Navigation & Side Effects)

    @Test
    fun NAV_01_navigateBack_onSuccessEvent() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val navController = mockk<NavHostController>(relaxed = true)
        val viewEventFlow = MutableSharedFlow<PersonEditViewEvent>(extraBufferCapacity = 1)
        
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState())
        every { viewModel.viewEvent } returns viewEventFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, navController = navController)
            }
        }

        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(PersonEditViewEvent.NavigateBack(jp.mydns.fujiwara.carememo.ui.navigation.EditResult.ADDED))
        }
        
        composeTestRule.waitForIdle()
        verify { navController.popBackStack() }
    }

    @Test
    fun EVT_01_duplicateWarning_isDisplayed() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(extraBufferCapacity = 1)
        
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState())
        every { viewModel.uiEventFlow } returns uiEventFlow
        every { viewModel.viewEvent } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, navController = mockk(relaxed = true))
            }
        }

        composeTestRule.runOnIdle {
            uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
                titleResId = R.string.main_err_title_duplicate_archived_add,
                messageResId = R.string.main_err_duplicate_active
            ))
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PersonEdit_DuplicateDialog").assertIsDisplayed()
    }

    //endregion

    //region 6. 状態復元テスト (State Restoration)

    @Test
    fun RST_02_scrollPosition_isMaintained_onConfigurationChange() {
        val restorationTester = StateRestorationTester(composeTestRule)
        
        restorationTester.setContent {
            CareMemoTheme {
                // Content 層での内部状態 (ScrollState) の復元を検証
                PersonEditScreenContentWrapper(isNew = true)
            }
        }

        // 下方へスクロール (保存ボタンが見える位置まで)
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performScrollTo()
        composeTestRule.waitForIdle()

        // 構成変更をエミュレート
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // スクロール位置が維持されていること (保存ボタンが引き続き表示されていること)
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsDisplayed()
    }

    //endregion

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CareMemoTheme {
                content()
            }
        }
    }

    @Composable
    private fun PersonEditScreenContentWrapper(
        isNew: Boolean = true,
        isLoading: Boolean = false,
        lastName: String = "",
        firstName: String = "",
        isValid: Boolean = false,
        onLastNameChange: (String) -> Unit = {},
        onSave: () -> Unit = {}
    ) {
        PersonEditScreenContent(
            isNew = isNew,
            isLoading = isLoading,
            lastName = lastName,
            firstName = firstName,
            lastNameFurigana = "",
            firstNameFurigana = "",
            note = "",
            era = BirthEra.SHOWA,
            year = "",
            month = "",
            day = "",
            isValid = isValid,
            onLastNameChange = onLastNameChange,
            onFirstNameChange = {},
            onLastNameFuriganaChange = {},
            onFirstNameFuriganaChange = {},
            onNoteChange = {},
            onEraChange = {},
            onYearChange = {},
            onMonthChange = {},
            onDayChange = {},
            onSave = onSave,
            onCancel = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
