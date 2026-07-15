package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonHealthScreen (2. コンポーネント単体)
 * 仕様書項目: CP-01 〜 CP-06
 */
class PersonHealthScreenTest_2_Component {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val recordsFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())
    private val testPerson = Person(
        id = 1, lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )
    private val testRecords = listOf(
        BpAndPulse(id = 1, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
    )

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        healthViewModel = mockk(relaxed = true)

        every { healthViewModel.records } returns recordsFlow
        every { healthViewModel.isLoading } returns MutableStateFlow(false)
        every { healthViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()

        every { detailViewModel.currentPerson } returns MutableStateFlow(testPerson)
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null)
    }

    private fun setContent(widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    viewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    initialCategoryType = Category.BP_AND_PULSE,
                    personId = 1,
                    widthSizeClass = widthSizeClass,
                    onBack = {},
                    onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}
                )
            }
        }
    }

    @Test
    fun cp01_phone_input_form_display() {
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()
        composeTestRule.onNodeWithTag("HealthScreen_InputForm").assertIsDisplayed()
        composeTestRule.onNodeWithText("保存").assertIsDisplayed()
    }

    @Test
    fun cp02_phone_history_graph_switch() {
        recordsFlow.value = testRecords
        setContent()
        
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        composeTestRule.onNodeWithTag("HealthScreen_GraphArea").assertIsDisplayed()
        
        composeTestRule.onNodeWithTag("HealthScreen_Tab_History").performClick()
        composeTestRule.onNodeWithTag("HealthScreen_HistoryList").assertIsDisplayed()
    }

    @Test
    fun cp03_phone_graph_empty_state() {
        // タブが表示されるように、一旦レコードがある状態で開始
        recordsFlow.value = testRecords
        setContent()
        
        // グラフタブに切り替え
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        
        // データを空にする（あるいは、データがないカテゴリに切り替える等の操作が必要）
        // ここでは、データがある状態でも「記録がありません」が出ないことを確認するか、
        // もしくは実装に合わせて、EmptyStateが表示されていることを確認する。
        // 仕様書に基づき、データがない状態でグラフモードの時の文言を確認
        recordsFlow.value = emptyList()
        composeTestRule.waitForIdle()
        
        // 記録がない場合は EmptyState が優先される実装なら、その文言を確認
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp04_tablet_two_column_display() {
        recordsFlow.value = testRecords
        setContent(widthSizeClass = WindowWidthSizeClass.Expanded)
        
        composeTestRule.onNodeWithTag("HealthScreen_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_GraphArea").assertIsDisplayed()
    }

    @Test
    fun cp05_tablet_selection_sync() {
        recordsFlow.value = testRecords
        setContent(widthSizeClass = WindowWidthSizeClass.Expanded)
        
        // 表示されるまで待機してクリック
        composeTestRule.onNodeWithText("120/80", substring = true).performClick()
        composeTestRule.onNodeWithTag("HealthScreen_InputForm").assertIsDisplayed()
    }

    @Test
    fun cp06_category_switch_maintains_input() {
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()
        
        composeTestRule.onNodeWithText("血圧(上)").performTextInput("130")
        
        composeTestRule.onNodeWithTag("CategoryChip_HEIGHT_AND_WEIGHT").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("130").assertExists()
    }
}
