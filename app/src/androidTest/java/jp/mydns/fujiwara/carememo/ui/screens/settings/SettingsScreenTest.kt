package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.logic.feature.SettingsUiState
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * SCR-S-001 SettingsScreen (設定・管理) の UI テスト (System B 移行済)
 *
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-S-001_SettingsScreen.md に準拠
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val uiStateFlow = MutableStateFlow(SettingsUiState())

    private fun setupViewModelMock(): SettingsViewModel {
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        
        // System B 形式の uiState 購読を stub
        // collectAsStateWithLifecycle や collect がクラッシュしないよう、有効な Flow を返すようにする
        every { viewModel.uiState } returns uiStateFlow.asStateFlow()
        every { viewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { viewModel.viewEvent } returns MutableSharedFlow<SettingsViewEvent>().asSharedFlow()
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(true).asStateFlow()
        every { viewModel.defaultRecorderName } returns MutableStateFlow("").asStateFlow()

        every { viewModel.canAuthenticate(any()) } returns true
        
        // 初期状態のセット
        uiStateFlow.value = SettingsUiState(
            isNameMaskingEnabled = true,
            isBiometricEnabled = true,
            lockTimeoutMinutes = 5,
            defaultRecorderName = "テスト記録者",
            isBackupPasswordEnabled = true,
            backupPassword = "123456",
            themeSetting = ThemeSetting.SYSTEM,
            auditLogRetentionDays = 30,
            auditLogCount = 10,
            endedUserCount = 0,
            isProcessing = false,
            processingProgress = 0,
            isDeveloperModeEnabled = false
        )
        
        // 互換性維持のための個別の StateFlow mock (もし View 側が直接参照している場合)
        // 今回の移行で View 側は uiState を見るように変更しているため、本来は不要。
        
        return viewModel
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (SettingsScreenContent)
    // ======================================================================================

    @Test
    fun cp01_basicLayout_isDisplayed() {
        val viewModel = setupViewModelMock()
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 画面タイトル「設定・管理」が表示されていること
        composeTestRule.onNodeWithText("設定・管理").assertIsDisplayed()
        // 戻るボタンが存在すること
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").assertIsDisplayed()
    }

    @Test
    fun cp02_variousItems_areDisplayed() {
        val viewModel = setupViewModelMock()
        uiStateFlow.value = uiStateFlow.value.copy(isDeveloperModeEnabled = false)
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 氏名伏せ字設定項目
        composeTestRule.onNodeWithTag("Settings_MaskingRow").performScrollTo().assertIsDisplayed()
        // バックアップ実行ボタン
        composeTestRule.onNodeWithTag("Settings_BackupButton").performScrollTo().assertIsDisplayed()
        
        // 開発者モードを有効にする（UiState経由）
        uiStateFlow.value = uiStateFlow.value.copy(isDeveloperModeEnabled = true)
        composeTestRule.waitForIdle()
        
        // 監査ログ遷移ボタンが表示されること
        composeTestRule.onNodeWithTag("Settings_AuditLogButton").performScrollTo().assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (SettingsScreen)
    // ======================================================================================

    @Test
    fun bh01_nameMaskingChange_preparesRefresh() {
        val viewModel = setupViewModelMock()
        val navController = mockk<NavController>(relaxed = true)
        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        
        val previousEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { previousEntry.savedStateHandle } returns savedStateHandle
        every { navController.previousBackStackEntry } returns previousEntry
        
        val currentEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { currentEntry.savedStateHandle } returns androidx.lifecycle.SavedStateHandle()
        every { navController.currentBackStackEntry } returns currentEntry

        var backCalled = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = { backCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("Settings_MaskingRow").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").performClick()
        
        assert(savedStateHandle.get<Boolean>("refresh_needed") == true)
        assert(backCalled)
    }

    @Test
    fun bh02_backupExecution_isClickable() {
        val viewModel = setupViewModelMock()
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // バックアップボタンが存在し、有効であることを確認
        composeTestRule.onNodeWithTag("Settings_BackupButton")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun bh03_auditLogNavigation_isCalled() {
        val viewModel = setupViewModelMock()
        // 最初から開発者モードをONにする
        uiStateFlow.value = uiStateFlow.value.copy(isDeveloperModeEnabled = true)
        val navController = mockk<NavController>(relaxed = true)
        var navigated = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = { navigated = true },
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 監査ログボタンが表示されているはずなので直接押す
        composeTestRule.onNodeWithTag("Settings_AuditLogButton")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        
        assert(navigated)
    }

    @Test
    fun bh04_scrollPosition_isMaintained() {
        val viewModel = setupViewModelMock()
        // 最初から開発者モードをONにして項目を増やす
        uiStateFlow.value = uiStateFlow.value.copy(isDeveloperModeEnabled = true)
        val navController = mockk<NavController>(relaxed = true)

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 1. 下の方にある要素（管理者向けセクションのボタンなど）までスクロール
        composeTestRule.onNodeWithTag("Settings_IntegrityCheckButton").performScrollTo()
        composeTestRule.onNodeWithTag("Settings_IntegrityCheckButton").assertIsDisplayed()
        
        // 2. その位置にあるボタンをクリック
        composeTestRule.onNodeWithTag("Settings_IntegrityCheckButton").performClick()
        composeTestRule.waitForIdle()
        
        // 3. 依然として同じ場所の要素が見えることを確認
        composeTestRule.onNodeWithTag("Settings_IntegrityCheckButton").assertIsDisplayed()
    }

    @Test
    fun bh05_userManagementNavigation_isCalled() {
        val viewModel = setupViewModelMock()
        val navController = mockk<NavController>(relaxed = true)
        var navigated = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = { navigated = true },
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("利用終了者の復帰").performScrollTo().performClick()
        
        assert(navigated)
    }

    @Test
    fun bh06_userRestoration_propagatesRefresh() {
        val viewModel = setupViewModelMock()
        val navController = mockk<NavController>(relaxed = true)
        
        val currentSavedStateHandle = androidx.lifecycle.SavedStateHandle()
        val previousSavedStateHandle = androidx.lifecycle.SavedStateHandle()
        
        val currentEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { currentEntry.savedStateHandle } returns currentSavedStateHandle
        every { navController.currentBackStackEntry } returns currentEntry
        
        val previousEntry = mockk<androidx.navigation.NavBackStackEntry>()
        every { previousEntry.savedStateHandle } returns previousSavedStateHandle
        every { navController.previousBackStackEntry } returns previousEntry

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        currentSavedStateHandle.set("refresh_needed", true)
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("SettingsScreen_BackButton").performClick()
        
        assert(previousSavedStateHandle.get<Boolean>("refresh_needed") == true)
    }
}
