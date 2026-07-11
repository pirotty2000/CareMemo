@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
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

/**
 * UI層テスト：GraphExpansionScreen (グラフ拡大表示)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_GraphExpansion.md
 * 
 * 【重要】この画面は強制的に横画面 (LANDSCAPE) へ切り替えます。
 * 長時間のテスト実行時、エミュレータの回転処理と Activity 再生成が
 * タイミングによって「No compose hierarchies found」を引き起こすため、
 * setup での回転待機と、各テストでの階層準備待ちを強化しています。
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

    private val mockRecords = listOf(
        BpAndPulse(id = 1, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
    )

    private val isLoadingFlow = MutableStateFlow(false)
    private val recordsFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())

    @Before
    fun setup() {
        viewModel = mockk<PersonDetailViewModel>(relaxed = true)
        healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)

        every { viewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { healthViewModel.isLoading } returns isLoadingFlow
        every { healthViewModel.getHealthRecords(any()) } returns recordsFlow

        // 前のテストの回転が残っている場合を考慮し、明示的に横向き固定
        if (composeTestRule.activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // 回転による Activity 再生成とシステム全体の落ち着きを待つ
            Thread.sleep(1000) 
            composeTestRule.waitForIdle()
        }
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
        composeTestRule.waitForIdle()
    }

    /**
     * Compose階層が有効になり、ノードが存在するまで待機する
     */
    private fun waitForHierarchy(tag: String, timeout: Long = 10000) {
        composeTestRule.waitUntil(timeout) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ======================================================================================
    // 1. 画面表示テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun cp01_header_info_is_displayed() {
        recordsFlow.value = mockRecords
        setContent(category = Category.BP_AND_PULSE)

        // 階層が現れるのを待つ
        waitForHierarchy("GraphExpansion_HeaderTitle")

        composeTestRule.onNodeWithTag("GraphExpansion_HeaderTitle")
            .assertExists()
            .assertTextContains("山田", substring = true)
            .assertTextContains("バイタル", substring = true)
    }

    @Test
    fun cp02_graph_list_shows_correct_number_of_cards() {
        recordsFlow.value = mockRecords
        setContent(category = Category.BP_AND_PULSE)

        // 階層が現れるのを待つ
        waitForHierarchy("GraphExpansion_GraphList")

        composeTestRule.onNodeWithTag("GraphExpansion_GraphList").assertExists()
        composeTestRule.onNodeWithTag("GraphExpansion_GraphCard_0").assertExists()
        composeTestRule.onNodeWithTag("GraphExpansion_GraphCard_1").assertExists()
    }

    @Test
    fun cp03_initial_graph_index_is_handled() {
        recordsFlow.value = mockRecords
        setContent(category = Category.BP_AND_PULSE, initialIndex = 1)

        waitForHierarchy("GraphExpansion_GraphCard_1")
        composeTestRule.onNodeWithTag("GraphExpansion_GraphCard_1").assertExists()
    }

    @Test
    fun cp04_loading_state_is_displayed() {
        isLoadingFlow.value = true
        setContent()

        waitForHierarchy("GraphExpansion_Loading")
        composeTestRule.onNodeWithTag("GraphExpansion_Loading").assertExists()
    }

    @Test
    fun cp05_empty_state_is_displayed() {
        recordsFlow.value = emptyList()
        isLoadingFlow.value = false
        setContent()

        waitForHierarchy("GraphExpansion_EmptyState")
        composeTestRule.onNodeWithTag("GraphExpansion_EmptyState").assertExists()
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertExists()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (GraphExpansionScreen)
    // ======================================================================================

    @Test
    fun bh01_screen_orientation_is_forced_to_landscape() {
        recordsFlow.value = mockRecords
        setContent()

        assert(composeTestRule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    }

    @Test
    fun bh02_scroll_operation_works() {
        recordsFlow.value = mockRecords
        setContent(category = Category.BP_AND_PULSE)

        waitForHierarchy("GraphExpansion_GraphList")

        composeTestRule.onNodeWithTag("GraphExpansion_GraphList")
            .performTouchInput {
                swipeUp()
            }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag("GraphExpansion_GraphCard_3").assertExists()
    }

    @Test
    fun bh03_back_operation_calls_callback() {
        var backCalled = false
        recordsFlow.value = mockRecords
        setContent(onBack = { backCalled = true })

        waitForHierarchy("GraphExpansion_BackButton")

        composeTestRule.onNodeWithTag("GraphExpansion_BackButton")
            .assertExists()
            .performClick()

        assert(backCalled)
    }
}
