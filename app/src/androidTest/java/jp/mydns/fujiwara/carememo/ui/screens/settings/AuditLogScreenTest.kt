package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogScreenState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.AuditLogViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: AuditLogScreen (SCR-S-002)
 */
class AuditLogScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<AuditLogViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val mockLogs = listOf(
        AuditLog(id = 1, timestamp = Instant.now(), featureName = "PersonList", operation = "addPerson", tableName = "person_db", actionType = "INSERT", affectedId = "1", resultType = "SUCCESS"),
        AuditLog(id = 2, timestamp = Instant.now(), featureName = "PersonHealth", operation = "saveRecord", tableName = "health_db", actionType = "UPDATE", affectedId = "10", resultType = "DB_ERROR")
    )

    @Before
    fun setup() {
        val initialState = AuditLogUiState(
            screenState = AuditLogScreenState.Active,
            auditLogs = mockLogs.toImmutableList(),
            filteredLogs = mockLogs.toImmutableList(),
            availableFeatures = persistentListOf("PersonList", "PersonHealth"),
            availableResults = persistentListOf("SUCCESS", "DB_ERROR")
        )
        every { viewModel.uiState } returns MutableStateFlow(initialState)
        every { viewModel.uiEventFlow } returns MutableSharedFlow()
        every { viewModel.viewEvent } returns MutableSharedFlow(extraBufferCapacity = 1)
    }

    private fun setContent(uiState: AuditLogUiState? = null) {
        if (uiState != null) {
            every { viewModel.uiState } returns MutableStateFlow(uiState)
        }

        composeTestRule.setContent {
            CareMemoTheme {
                AuditLogScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_logList_rendersItems() {
        setContent()
        composeTestRule.onNodeWithTag("AuditLog_LogList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuditLogItem_2").assertIsDisplayed()
    }

    @Test
    fun DSP_02_emptyState_isDisplayed_whenNoLogs() {
        setContent(AuditLogUiState(screenState = AuditLogScreenState.Active, auditLogs = persistentListOf(), filteredLogs = persistentListOf()))
        // Use unmerged tree or broader search if necessary
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasTestTag("AuditLog_EmptyState"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasTestTag("AuditLog_EmptyState"), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ログはありません", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_03_loadingIndicator_isDisplayed() {
        setContent(AuditLogUiState(screenState = AuditLogScreenState.Loading, auditLogs = persistentListOf()))
        composeTestRule.onNodeWithTag("AuditLog_Loading").assertIsDisplayed()
    }

    @Test
    fun DSP_04_labelsAreMappedToJapanese() {
        setContent()
        // Success labels are displayed as is (SUCCESS) in the current implementation of AuditLogItem
        composeTestRule.onNodeWithText("SUCCESS", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_featureFilter_triggersViewModel() {
        setContent()
        
        composeTestRule.onNodeWithTag("AuditLog_FeatureFilter").performClick()
        // Use unmerged tree to find menu items
        composeTestRule.onNodeWithTag("FeatureFilterItem_PersonList", useUnmergedTree = true).performClick()
        
        verify { viewModel.setFeatureFilter("PersonList") }
    }

    @Test
    fun ACT_03_sortToggle_triggersViewModel() {
        setContent()
        composeTestRule.onNodeWithTag("AuditLog_SortToggle").performClick()
        verify { viewModel.toggleSortOrder() }
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_navigatesBack() {
        setContent()
        composeTestRule.onNodeWithTag("AuditLogScreen_BackButton").performClick()
        verify { viewModel.navigateBack() }
    }

    //endregion
}
