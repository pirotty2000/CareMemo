@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Test: BatchInputScreen (SCR-PH-002)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-002_BatchInputScreen.md に準拠
 */
class BatchInputScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<BatchInputViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)
    private val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(extraBufferCapacity = 1)
    private val viewEventFlow = MutableSharedFlow<BatchInputViewEvent>(extraBufferCapacity = 1)
    private val uiStateFlow = MutableStateFlow(BatchInputUiState(
        personId = "u1",
        currentPersonName = "山田 太郎"
    ))

    @Before
    fun setup() {
        every { viewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { viewModel.viewEvent } returns viewEventFlow.asSharedFlow()
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                BatchInputScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_allInputFields_areDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_DateTimeInput").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpSystolicField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_GlucoseField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun DSP_02_saveButton_reflectsValidity() {
        uiStateFlow.value = uiStateFlow.value.copy(isValid = true)
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsEnabled()

        uiStateFlow.value = uiStateFlow.value.copy(isValid = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun DSP_04_scroll_isPossible() {
        setContent()
        val scrollColumn = composeTestRule.onNodeWithTag("BatchInputScreen_InputScrollColumn")
        scrollColumn.assert(hasScrollAction())
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_numericInput_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performScrollTo().performTextInput("170")
        verify { viewModel.updateHeight("170") }
    }

    @Test
    fun ACT_03_saveButton_callsViewModel() {
        uiStateFlow.value = uiStateFlow.value.copy(isValid = true)
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()
        verify { viewModel.saveBatch() }
    }

    @Test
    fun ACT_04_cancelWithChanges_showsDiscardDialog() {
        uiStateFlow.value = uiStateFlow.value.copy(isChanged = true)
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_CancelButton").performScrollTo().performClick()
        composeTestRule.onNodeWithText("変更の破棄", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation & Side Effects)

    @Test
    fun NAV_01_navigateBack_onEvent() {
        setContent()
        viewEventFlow.tryEmit(BatchInputViewEvent.NavigateBack)
        composeTestRule.waitForIdle()
        verify { navController.popBackStack() }
    }

    @Test
    fun EVT_02_duplicateError_displaysDialog() {
        setContent()
        
        // Simulate duplicate error for Height/Weight
        val categoryName = "__RES__" + R.string.common_category_height_weight
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
            R.string.common_error_title_save,
            R.string.batch_err_duplicate_blocked,
            listOf(categoryName)
        ))
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        // Check if category name is resolved in implementation (it should be according to BatchInputScreen logic)
        val expectedCategoryName = composeTestRule.activity.getString(R.string.common_category_height_weight)
        // Use hasAnyAncestor(isDialog()) to find the text inside the error dialog
        composeTestRule.onNode(hasText(expectedCategoryName, substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    //endregion
}
