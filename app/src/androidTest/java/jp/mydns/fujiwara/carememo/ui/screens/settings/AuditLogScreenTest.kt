@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.AuditLogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * AuditLogScreen の UI テスト (System B 移行済)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-002_AuditLogScreen.md に準拠
 */
class AuditLogScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val mockLogs = listOf(
        AuditLog(id = 1, timestamp = Instant.now(), featureName = "PersonList", operation = "addPerson", tableName = "person_db", actionType = "LOGICAL_DELETE", affectedId = "1", resultType = "SUCCESS"),
        AuditLog(id = 2, timestamp = Instant.now(), featureName = "PersonHealth", operation = "saveRecord", tableName = "health_db", actionType = "UPDATE", affectedId = "10", resultType = "DB_ERROR")
    )

    private val uiStateFlow = MutableStateFlow(AuditLogUiState())

    private fun setupMockViewModel(): AuditLogViewModel {
        val viewModel = mockk<AuditLogViewModel>(relaxed = true)
        
        // System B 形式の uiState 購読を stub
        every { viewModel.uiState } returns uiStateFlow
        
        uiStateFlow.value = AuditLogUiState(
            auditLogs = mockLogs,
            isLoading = false,
            availableFeatures = listOf("PersonList", "PersonHealth"),
            availableResults = listOf("SUCCESS", "DB_ERROR")
        )
        return viewModel
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (AuditLogScreenContent)
    // ======================================================================================

    @Test
    fun cp01_logList_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreenContent(
                    auditLogs = mockLogs,
                    isLoading = false,
                    selectedFeature = null,
                    selectedResult = null,
                    isAscending = false,
                    availableFeatures = listOf("PersonList", "PersonHealth"),
                    availableResults = listOf("SUCCESS", "DB_ERROR"),
                    onFeatureSelect = {},
                    onResultSelect = {},
                    onToggleSort = {},
                    onClearFilters = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("AuditLog_LogList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_2").assertIsDisplayed()
    }

    @Test
    fun cp02_emptyState_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreenContent(
                    auditLogs = emptyList(),
                    isLoading = false,
                    selectedFeature = null,
                    selectedResult = null,
                    isAscending = false,
                    availableFeatures = emptyList(),
                    availableResults = emptyList(),
                    onFeatureSelect = {},
                    onResultSelect = {},
                    onToggleSort = {},
                    onClearFilters = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("AuditLog_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("ログはありません").assertIsDisplayed()
    }

    @Test
    fun cp03_filterChips_areDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreenContent(
                    auditLogs = mockLogs,
                    isLoading = false,
                    selectedFeature = null,
                    selectedResult = null,
                    isAscending = false,
                    availableFeatures = listOf("PersonList"),
                    availableResults = listOf("SUCCESS"),
                    onFeatureSelect = {},
                    onResultSelect = {},
                    onToggleSort = {},
                    onClearFilters = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("AuditLog_FilterChips").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLog_ResultFilter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLog_FeatureFilter").assertIsDisplayed()
    }

    @Test
    fun cp04_labelsAreCorrectlyMapped() {
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreenContent(
                    auditLogs = mockLogs,
                    isLoading = false,
                    selectedFeature = null,
                    selectedResult = null,
                    isAscending = false,
                    availableFeatures = listOf("PersonHealth"),
                    availableResults = listOf("SUCCESS"),
                    onFeatureSelect = {},
                    onResultSelect = {},
                    onToggleSort = {},
                    onClearFilters = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithText("利用終了", substring = true).assertExists()
        composeTestRule.onNodeWithText("成功", substring = true).assertExists()
        composeTestRule.onNodeWithText("健康記録", substring = true).assertExists()
    }

    @Test
    fun cp05_largeData_isScrollable() {
        val manyLogs = List(50) { i ->
            AuditLog(id = i.toLong(), timestamp = Instant.now(), featureName = "F", operation = "O", tableName = "T", actionType = "A", affectedId = "I", resultType = "R")
        }
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreenContent(
                    auditLogs = manyLogs,
                    isLoading = false,
                    selectedFeature = null,
                    selectedResult = null,
                    isAscending = false,
                    availableFeatures = emptyList(),
                    availableResults = emptyList(),
                    onFeatureSelect = {},
                    onResultSelect = {},
                    onToggleSort = {},
                    onClearFilters = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("AuditLog_LogList").performScrollToIndex(49)
        composeTestRule.onNodeWithTag("AuditLogItem_49").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (AuditLogScreen)
    // ======================================================================================

    @Test
    fun bh01_filterApplication_callsViewModel() {
        val viewModel = setupMockViewModel()

        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("AuditLog_FeatureFilter").performClick()
        composeTestRule.onNodeWithTag("FeatureFilterItem_PersonList").performClick()

        verify { viewModel.setFeatureFilter("PersonList") }
    }

    @Test
    fun bh02_backOperation_callsOnBack() {
        val viewModel = setupMockViewModel()
        uiStateFlow.value = AuditLogUiState(auditLogs = emptyList(), isLoading = false)
        
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(viewModel = viewModel, onBack = { backCalled = true })
            }
        }

        composeTestRule.onNodeWithTag("AuditLogScreen_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun bh03_concreteLabelFilter_works() {
        val viewModel = setupMockViewModel()

        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("AuditLog_ResultFilter").performClick()
        composeTestRule.onNodeWithTag("ResultFilterItem_SUCCESS").performClick()

        verify { viewModel.setResultFilter("SUCCESS") }
    }
}
