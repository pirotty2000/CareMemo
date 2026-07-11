package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * SettingsScreen (設定・管理) の UI テスト
 *
 * 仕様書：doc/test/TEST_SPEC_UI_SettingsScreen.md に準拠
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setupViewModelMock(): SettingsViewModel {
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(true)
        every { viewModel.isBiometricEnabled } returns MutableStateFlow(true)
        every { viewModel.lockTimeoutMinutes } returns MutableStateFlow(5)
        every { viewModel.defaultRecorderName } returns MutableStateFlow("テスト記録者")
        every { viewModel.isBackupPasswordEnabled } returns MutableStateFlow(true)
        every { viewModel.backupPassword } returns MutableStateFlow("123456")
        every { viewModel.themeSetting } returns MutableStateFlow(ThemeSetting.SYSTEM)
        every { viewModel.auditLogRetentionDays } returns MutableStateFlow(30)
        every { viewModel.auditLogCount } returns MutableStateFlow(10)
        every { viewModel.deletedUserList } returns MutableStateFlow(emptyList())
        every { viewModel.isProcessing } returns MutableStateFlow(false)
        every { viewModel.processingProgress } returns MutableStateFlow(0)
        every { viewModel.inconsistencies } returns MutableStateFlow(emptyList())
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        every { viewModel.canAuthenticate(any()) } returns true
        return viewModel
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (SettingsScreenContent)
    // ======================================================================================

    @Test
    fun cp01_initialValues_areDisplayed() {
        val viewModel = setupViewModelMock()

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 各項目の初期値が正しく表示されているか（すべてスクロールしてから検証）
        composeTestRule.onNodeWithTag("Settings_MaskingSwitch").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("Settings_RecorderName").performScrollTo().assertTextContains("テスト記録者")
        composeTestRule.onNodeWithTag("Settings_BackupPasswordSwitch").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("Settings_BiometricSwitch").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithText("5分").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp02_maskingToggle_callsViewModel() {
        val viewModel = setupViewModelMock()

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // スイッチを切り替える
        composeTestRule.onNodeWithTag("Settings_MaskingSwitch").performScrollTo().performClick()

        // ViewModel のメソッドが呼ばれること
        verify { viewModel.setNameMaskingEnabled(any()) }
    }

    @Test
    fun cp04_backupPassword_visibilityControl() {
        val viewModel = setupViewModelMock()
        val isBackupPasswordEnabled = MutableStateFlow(true)
        every { viewModel.isBackupPasswordEnabled } returns isBackupPasswordEnabled

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, onSuccess -> onSuccess() }, // 認証即時成功
                    onBack = {}
                )
            }
        }

        // 最初は表示されている
        composeTestRule.onNodeWithTag("Settings_BackupPasswordInput").performScrollTo().assertExists()

        // スイッチをOFFにする
        composeTestRule.onNodeWithTag("Settings_BackupPasswordSwitch").performScrollTo().performClick()
        isBackupPasswordEnabled.value = false
        composeTestRule.waitForIdle()

        // 入力欄が消えることを確認
        composeTestRule.onNodeWithTag("Settings_BackupPasswordInput").assertDoesNotExist()
    }

    @Test
    fun cp05_passwordValidation_disablesExportButton() {
        val viewModel = setupViewModelMock()
        val backupPassword = MutableStateFlow("123") // 6文字未満
        every { viewModel.backupPassword } returns backupPassword

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // エラーメッセージが表示されていること
        composeTestRule.onNodeWithText("6文字以上で入力してください").performScrollTo().assertIsDisplayed()

        // エクスポートボタンが非活性であること
        composeTestRule.onNodeWithTag("Settings_ExportButton").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun cp08_processingDialog_isDisplayed() {
        val viewModel = setupViewModelMock()
        val isProcessing = MutableStateFlow(true)
        val progress = MutableStateFlow(45)
        every { viewModel.isProcessing } returns isProcessing
        every { viewModel.processingProgress } returns progress

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 処理中ダイアログと進捗率が表示されていること（ダイアログはスクロール不要）
        composeTestRule.onNodeWithTag("Settings_ProcessingDialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("45%").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (SettingsScreen)
    // ======================================================================================

    @Test
    fun bh01_timeoutDialog_opensOnRowClick() {
        val viewModel = setupViewModelMock()

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 再ロック待機時間の行をタップ
        composeTestRule.onNodeWithTag("Settings_TimeoutRow").performScrollTo().performClick()

        // 選択ダイアログが表示されること
        // 「再ロックまでの時間」は背景とダイアログの2箇所にあるため、onAllNodes を使用
        composeTestRule.onAllNodesWithText("再ロックまでの時間").onFirst().assertIsDisplayed()
        // ダイアログ固有の選択肢が表示されていることを確認
        composeTestRule.onNodeWithText("即時").assertIsDisplayed()
        composeTestRule.onNodeWithText("10分").assertExists()
    }

    @Test
    fun bh02_themeDialog_opensOnRowClick() {
        val viewModel = setupViewModelMock()

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // テーマ設定の行をタップ
        composeTestRule.onNodeWithTag("Settings_ThemeRow").performScrollTo().performClick()

        // 選択ダイアログが表示されること
        composeTestRule.onNodeWithText("配色とモードの選択").assertIsDisplayed()
    }

    @Test
    fun bh03_developerMode_activation() {
        val viewModel = setupViewModelMock()

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // 最初は管理者セクションが存在しない
        composeTestRule.onNodeWithTag("Settings_DevSection").assertDoesNotExist()

        // バージョン情報を7回タップする
        val versionRow = composeTestRule.onNodeWithTag("Settings_VersionRow").performScrollTo()
        repeat(7) {
            versionRow.performClick()
        }

        // Then: 管理者セクションが表示されること
        composeTestRule.onNodeWithTag("Settings_DevSection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bh04a_disableBackupPassword_requiresAuthentication() {
        val viewModel = setupViewModelMock()
        var authRequested = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> authRequested = true },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("Settings_BackupPasswordSwitch").performScrollTo().performClick()
        assert(authRequested)
    }

    @Test
    fun bh04b_showPassword_requiresAuthentication() {
        val viewModel = setupViewModelMock()
        var authRequested = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> authRequested = true },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("Settings_PasswordVisibilityToggle").performScrollTo().performClick()
        assert(authRequested)
    }

    @Test
    fun bh04c_disableAppLock_requiresAuthentication() {
        val viewModel = setupViewModelMock()
        var authRequested = false

        composeTestRule.setContent {
            CareMemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToArchiveManagement = {},
                    onNavigateToAuditLog = {},
                    onRequireAuthentication = { _, _, _ -> authRequested = true },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("Settings_BiometricSwitch").performScrollTo().performClick()
        assert(authRequested)
    }
}
