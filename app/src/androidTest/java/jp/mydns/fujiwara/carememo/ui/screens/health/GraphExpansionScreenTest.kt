@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
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
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-003_GraphExpansionScreen.md に準拠
 */
class GraphExpansionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
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

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(person = mockPerson, personId = 1, currentCategory = Category.BP_AND_PULSE))
    private val healthUiStateFlow = MutableStateFlow(PersonHealthUiState(personId = 1))
    private val recordsFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { healthViewModel.uiState } returns healthUiStateFlow
        every { healthViewModel.getHealthRecords(any()) } returns recordsFlow
    }

    private fun setContent(category: Category = Category.BP_AND_PULSE, initialIndex: Int = 0, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            val context = LocalContext.current
            // applicationContext を提供することで、本番コード内での Activity へのキャストを失敗させ、
            // テスト中の画面回転（およびそれに伴う Activity 再生成）を抑制する。
            CompositionLocalProvider(LocalContext provides context.applicationContext) {
                CareMemoTheme {
                    GraphExpansionScreen(
                        detailViewModel = detailViewModel,
                        healthViewModel = healthViewModel,
                        personId = 1,
                        category = category,
                        initialGraphIndex = initialIndex,
                        onBack = onBack
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * 指定したグラフが画面に表示され、特定の数値が含まれていることを検証する
     */
    private fun assertChartContainsValue(index: Int, value: String) {
        val tag = "GraphExpansion_ChartView_$index"
        
        // グラフが表示されるまで待機（最長10秒）
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }

        // 階層をマージせずに、子ノードも含めて contentDescription を検索する
        composeTestRule.onNodeWithTag(tag)
            .assertExists()
            .onChildren()
            .assertAny(hasContentDescription(value, substring = true))
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun cp01_basic_layout_display() {
        recordsFlow.value = mockRecords
        setContent()
        
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").assertIsDisplayed()
        // 1番目のグラフ(血圧)が表示されていること
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").assertIsDisplayed()
    }

    @Test
    fun cp02_chart_drawing_content() {
        recordsFlow.value = mockRecords
        setContent()
        
        // 血圧の最高値(120, 130)が描画されていることを確認
        assertChartContainsValue(0, "120")
        assertChartContainsValue(0, "130")
    }

    @Test
    fun cp03_axis_labels_display() {
        recordsFlow.value = mockRecords
        setContent()
        
        // 軸ラベルや目盛りの一部として、期待される値が表示されていること
        assertChartContainsValue(0, "120")
    }

    @Test
    fun cp04_received_data_reflection() {
        recordsFlow.value = mockRecords
        // 血糖値カテゴリで開始
        setContent(category = Category.GLUCOSE_AND_HBA1C)
        
        // ヘッダーに「血糖値」が含まれていること
        composeTestRule.onNodeWithTag("GraphExpansion_HeaderTitle")
            .assertTextContains("血糖値", substring = true)
    }

    @Test
    fun cp05_boundary_data_points() {
        // データが1点のみの場合
        recordsFlow.value = listOf(mockRecords[0])
        setContent()
        
        // エラーにならずに描画されていること
        assertChartContainsValue(0, "120")
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun bh01_pinch_zoom_operation() {
        recordsFlow.value = mockRecords
        setContent()
        
        // ピンチ操作のシミュレーション
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").performTouchInput {
            pinch(
                start0 = center + Offset(-20f, 0f),
                end0 = center + Offset(-100f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(100f, 0f)
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh02_scroll_operation() {
        recordsFlow.value = mockRecords
        setContent()
        
        // スワップ操作のシミュレーション（グラフ内スクロール）
        composeTestRule.onNodeWithTag("GraphExpansion_ChartView_0").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh03_back_button_callback() {
        var backCalled = false
        recordsFlow.value = mockRecords
        setContent(onBack = { backCalled = true })
        
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        
        composeTestRule.runOnIdle {
            assert(backCalled)
        }
    }

    @Test
    fun bh04_screen_exit_and_return() {
        var backCalled = false
        recordsFlow.value = mockRecords
        setContent(onBack = { backCalled = true })
        
        // 戻るボタンタップによりコールバックが呼ばれることを確認（bh03と同様の検証）
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        
        composeTestRule.runOnIdle {
            assert(backCalled)
        }
    }
}
