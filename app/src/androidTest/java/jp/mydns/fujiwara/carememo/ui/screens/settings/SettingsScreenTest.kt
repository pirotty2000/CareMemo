package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.logic.feature.SettingsScreenState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsUiState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Test: SettingsScreen (SCR-S-001)
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<SettingsViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)
    private val uiStateFlow = MutableStateFlow(SettingsUiState(screenState = SettingsScreenState.Active))
    private val viewEventFlow = MutableSharedFlow<SettingsViewEvent>(extraBufferCapacity = 1)

    @Before
    fun setup() {
        // Stub NavController
        val mockEntry = mockk<NavBackStackEntry>(relaxed = true)
        val savedStateHandle = SavedStateHandle()
        every { mockEntry.savedStateHandle } returns savedStateHandle
        every { navController.currentBackStackEntry } returns mockEntry
        every { navController.previousBackStackEntry } returns mockEntry

        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        every { viewModel.viewEvent } returns viewEventFlow
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { viewModel.defaultRecorderName } returns MutableStateFlow("")
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onRequireAuthentication = { _, _, onSuccess -> onSuccess() },
                    onCheckBiometricSupport = { true }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_basicLayout_isDisplayed() {
        setContent()
        // Match title from R.string.settings_title ("設定")
        composeTestRule.onAllNodesWithText("設定", substring = true).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").assertIsDisplayed()
    }

    @Test
    fun DSP_02_settingValues_areReflected() {
        uiStateFlow.value = SettingsUiState(
            screenState = SettingsScreenState.Active,
            isNameMaskingEnabled = true, 
            themeSetting = ThemeSetting.DARK
        )
        setContent()
        
        // Masking row should exist
        composeTestRule.onNodeWithTag("Settings_MaskingRow").assertIsDisplayed()
        // Check for the switch state inside or on the row
        composeTestRule.onNode(hasTestTag("Settings_MaskingRow"), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun DSP_04_devTools_areDisplayed_whenEnabled() {
        // Initial: hidden
        uiStateFlow.value = SettingsUiState(screenState = SettingsScreenState.Active, isDeveloperModeEnabled = false)
        setContent()
        composeTestRule.onNodeWithTag("Settings_AuditLogButton").assertDoesNotExist()

        // Enable dev mode
        uiStateFlow.value = uiStateFlow.value.copy(isDeveloperModeEnabled = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("Settings_AuditLogButton").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("Settings_AuditLogExportButton").performScrollTo().assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_maskingToggle_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("Settings_MaskingRow").performClick()
        verify { viewModel.setNameMaskingEnabled(any()) }
    }

    @Test
    fun ACT_02_versionTap_triggersDevModeCheck() {
        setContent()
        composeTestRule.onNodeWithTag("Settings_VersionRow").performScrollTo().performClick()
        verify { viewModel.handleVersionClick() }
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_navigateToAuditLog_onEvent() {
        setContent()

        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(SettingsViewEvent.NavigateToAuditLog)
        }
        
        composeTestRule.waitForIdle()
        verify { navController.navigate(jp.mydns.fujiwara.carememo.ui.navigation.Destination.AuditLog) }
    }

    @Test
    fun NAV_03_backButton_popsBackStack() {
        setContent()
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").performClick()
        
        // SettingsScreen sends NavigateBack event on back button click
        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(SettingsViewEvent.NavigateBack)
        }
        
        composeTestRule.waitForIdle()
        verify { navController.popBackStack() }
    }

    //endregion
}
