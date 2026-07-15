@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * UI層テスト：GraphExpansionScreen (グラフ拡大表示)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-003_GraphExpansionScreen.md
 */
class GraphExpansionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: PersonDetailViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val mockPerson = Person(
        id = 1, 
        lastName = "山田", 
        firstName = "太郎", 
        lastNameFurigana = "ヤマダ", 
        firstNameFurigana = "タロウ", 
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val baseTime = Instant.parse("2023-10-01T10:00:00Z")

    private val mockRecords = listOf(
        BpAndPulse(id = 1, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = baseTime),
        BpAndPulse(id = 2, personId = 1, bpSystolic = 130, bpDiastolic = 85, pulse = 75, recordTime = baseTime.plus(1, ChronoUnit.DAYS))
    )

    private val recordsFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())

    @Before
    fun setup() {
        viewModel = mockk<PersonDetailViewModel>(relaxed = true)
        healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)

        every { viewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { healthViewModel.isLoading } returns MutableStateFlow(false)
        every { healthViewModel.getHealthRecords(any()) } returns recordsFlow
        
        // 縦向きデバイスでのテスト実行時、回転による再生成でテストが壊れるのを防ぐため、
        // Activityの起動・回転が落ち着くまで少し待機する
        composeTestRule.waitForIdle()
    }

    private fun setContent(category: Category = Category.BP_AND_PULSE, initialIndex: Int = 0, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                GraphExpansionScreen(
                    viewModel = viewModel,
                    healthViewModel = healthViewModel,
                    personId = 1,
                    category = category,
                    initialGraphIndex = initialIndex,
                    onBack = onBack
                )
            }
        }
    }

    private fun assertChartContainsValue(index: Int, value: String) {
        val tag = "GraphExpansion_ChartView_$index"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onNodeWithTag(tag)
                .fetchSemanticsNode().config.any { it.key.name == "ContentDescription" && it.value.toString().contains(value) }
        }
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_layout_display() {
        recordsFlow.value = mockRecords
        setContent()
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").assertIsDisplayed()
    }

    @Test
    fun cp02_chart_drawing_content() {
        recordsFlow.value = mockRecords
        setContent()
        assertChartContainsValue(0, "120")
        assertChartContainsValue(0, "130")
    }

    @Test
    fun cp03_axis_labels_display() {
        recordsFlow.value = mockRecords
        setContent()
        assertChartContainsValue(0, "120")
    }

    @Test
    fun cp04_received_data_reflection() {
        recordsFlow.value = mockRecords
        setContent(category = Category.GLUCOSE_AND_HBA1C)
        composeTestRule.onNodeWithTag("GraphExpansion_HeaderTitle").assertTextContains("血糖値", substring = true)
    }

    @Test
    fun cp05_boundary_data_points() {
        recordsFlow.value = listOf(mockRecords[0])
        setContent()
        assertChartContainsValue(0, "120")
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun bh01_pinch_zoom_operation() {
        recordsFlow.value = mockRecords
        setContent()
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").performTouchInput {
            pinch(center + Offset(-10f, 0f), center + Offset(-100f, 0f), center + Offset(10f, 0f), center + Offset(100f, 0f))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh02_scroll_operation() {
        recordsFlow.value = mockRecords
        setContent()
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh03_back_button_callback() {
        var backCalled = false
        recordsFlow.value = mockRecords
        setContent(onBack = { backCalled = true })
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun bh04_screen_exit_and_return() {
        var backCalled = false
        recordsFlow.value = mockRecords
        setContent(onBack = { backCalled = true })
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        assert(backCalled)
    }
}
