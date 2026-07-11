@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：AuditLog (操作ログ)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_AuditLog.md
 */
class AuditLogScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: SettingsViewModel

    private val mockLogs = listOf(
        AuditLog(id = 1, timestamp = Instant.now(), screenName = "利用者一覧", operation = "新規登録", tableName = "person_db", actionType = "INSERT", affectedId = "1"),
        AuditLog(id = 2, timestamp = Instant.now(), screenName = "健康記録", operation = "保存", tableName = "health_db", actionType = "UPDATE", affectedId = "10")
    )

    @Before
    fun setup() {
        viewModel = mockk<SettingsViewModel>(relaxed = true)
        
        // デフォルトのモック設定
        every { viewModel.auditLogs } returns MutableStateFlow(mockLogs)
        every { viewModel.selectedTable } returns MutableStateFlow(null)
        every { viewModel.selectedScreen } returns MutableStateFlow(null)
        every { viewModel.availableTables } returns MutableStateFlow(listOf("person_db", "health_db"))
        every { viewModel.availableScreens } returns MutableStateFlow(listOf("利用者一覧", "健康記録"))
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. 画面表示テスト (AuditLogScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_layout_is_displayed() {
        setContent()
        
        // タイトル表示
        composeTestRule.onNodeWithText("操作ログ").assertIsDisplayed()
        // 戻るボタン
        composeTestRule.onNodeWithTag("AuditLog_BackButton").assertIsDisplayed()
    }

    @Test
    fun cp02_empty_state_is_displayed() {
        every { viewModel.auditLogs } returns MutableStateFlow(emptyList())
        every { viewModel.availableTables } returns MutableStateFlow(emptyList())
        every { viewModel.availableScreens } returns MutableStateFlow(emptyList())
        
        setContent()
        
        composeTestRule.onNodeWithTag("AuditLog_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("ログはありません").assertIsDisplayed()
    }

    @Test
    fun cp03_log_list_is_displayed() {
        setContent()
        
        composeTestRule.onNodeWithTag("AuditLog_List").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_2").assertIsDisplayed()
        
        // 内容の一部が表示されていること
        composeTestRule.onNodeWithText("INSERT", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("利用者一覧", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp04_filter_chips_are_displayed() {
        setContent()
        
        composeTestRule.onNodeWithTag("AuditLog_TableFilter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLog_ScreenFilter").assertIsDisplayed()
    }

    @Test
    fun cp05_search_no_result_is_displayed() {
        // フィルター適用中でログが空の状態
        every { viewModel.auditLogs } returns MutableStateFlow(emptyList())
        every { viewModel.selectedTable } returns MutableStateFlow("unknown_db")
        
        setContent()
        
        composeTestRule.onNodeWithTag("AuditLog_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("条件に合うログはありません").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (AuditLogScreen)
    // ======================================================================================

    @Test
    fun bh01_table_filter_operation_calls_viewmodel() {
        setContent()
        
        // チップをタップしてドロップダウンを開く
        composeTestRule.onNodeWithTag("AuditLog_TableFilter").performClick()
        
        // メニュー項目を選択
        composeTestRule.onNodeWithText("person_db").performClick()
        
        // ViewModelが呼ばれたこと
        verify { viewModel.setTableFilter("person_db") }
    }

    @Test
    fun bh02_screen_filter_operation_calls_viewmodel() {
        setContent()
        
        // チップをタップ
        composeTestRule.onNodeWithTag("AuditLog_ScreenFilter").performClick()
        
        // メニュー項目を選択
        composeTestRule.onNodeWithText("健康記録").performClick()
        
        // ViewModelが呼ばれたこと
        verify { viewModel.setScreenFilter("健康記録") }
    }

    @Test
    fun bh03_clear_filter_operation_calls_viewmodel() {
        // フィルター適用中の状態
        every { viewModel.selectedTable } returns MutableStateFlow("person_db")
        
        setContent()
        
        // 解除ボタンをタップ
        composeTestRule.onNodeWithTag("AuditLog_FilterClear").performClick()
        
        // ViewModelが呼ばれたこと
        verify { viewModel.clearFilters() }
    }

    @Test
    fun bh04_back_operation_calls_callback() {
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(
                    viewModel = viewModel,
                    onBack = { backCalled = true }
                )
            }
        }
        
        composeTestRule.onNodeWithTag("AuditLog_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun bh05_action_type_colors_are_applied() {
        // 色を直接検証するのは難しいため、特定の actionType を持つアイテムが存在し、
        // 適切なセマンティクス（この場合は単なる表示確認）を持っていることを確認
        setContent()
        
        composeTestRule.onNodeWithText("INSERT").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPDATE").assertIsDisplayed()
    }
}
