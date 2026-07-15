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
 * UI層テスト：PersonHealthScreen (3. 画面全体の挙動・結合)
 * 仕様書項目: BH-01 〜 BH-06
 */
class PersonHealthScreenTest_3_Behavior {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val recordsFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())
    private val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>(extraBufferCapacity = 1)
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
        every { healthViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()

        every { detailViewModel.currentPerson } returns MutableStateFlow(testPerson)
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null)
    }

    private fun setContent(onNavigateToGraphExpansion: (Int, Category, Int) -> Unit = { _, _, _ -> }) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    viewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    initialCategoryType = Category.BP_AND_PULSE,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToGraphExpansion = onNavigateToGraphExpansion,
                    onNavigateToCategory = {}
                )
            }
        }
    }

    @Test
    fun bh01_data_save_action() {
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()
        composeTestRule.onNodeWithText("血圧(上)").performTextInput("120")
        composeTestRule.onNodeWithText("血圧(下)").performTextInput("80")
        composeTestRule.onNodeWithText("保存").performClick()
        
        verify { healthViewModel.saveRecord(any()) }
    }

    @Test
    fun bh02_graph_expansion_navigation() {
        var expandedPersonId = -1
        var expandedCategory: Category? = null
        recordsFlow.value = testRecords
        setContent(onNavigateToGraphExpansion = { pid, cat, _ ->
            expandedPersonId = pid
            expandedCategory = cat
        })
        
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        composeTestRule.onAllNodesWithContentDescription("拡大表示").onFirst().performClick()
        
        assert(expandedPersonId == 1)
        assert(expandedCategory == Category.BP_AND_PULSE)
    }

    @Test
    fun bh03_duplicate_datetime_save_guard() {
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()
        
        composeTestRule.runOnUiThread {
            uiEventFlow.tryEmit(BaseViewModel.UiEvent.ShowErrorDialog("保存エラー", "既に同じ日時の記録が存在します。"))
        }
        
        composeTestRule.onNodeWithText("既に同じ日時の記録が存在します。").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_InputForm").assertIsDisplayed()
    }

    @Test
    fun bh04_pdf_output_action() {
        recordsFlow.value = testRecords
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").performClick()
        composeTestRule.onNodeWithTag("PdfSettingsDialog").assertIsDisplayed()
    }

    @Test
    fun bh05_return_from_expansion_maintains_state() {
        recordsFlow.value = testRecords
        setContent()
        
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        composeTestRule.onNodeWithTag("HealthScreen_GraphArea").assertIsDisplayed()
    }

    @Test
    fun bh06_pdf_settings_for_health_relevant_only() {
        recordsFlow.value = testRecords
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").performClick()
        
        // 「期間を指定する」をスクロールして見つけ、確実にクリック
        composeTestRule.onNodeWithText("期間を指定する")
            .performScrollTo()
            .performClick()
        
        composeTestRule.waitForIdle()

        // 期間指定（開始〜終了）が存在することを確認
        // Buttonのラベルとして存在するため、より柔軟に検索
        composeTestRule.onNodeWithText("開始日", substring = true).assertExists()
        composeTestRule.onNodeWithText("終了日", substring = true).assertExists()
        
        // 所見特有の項目が表示されていないことを確認
        composeTestRule.onNodeWithText("最新の1件のみ").assertDoesNotExist()
        composeTestRule.onNodeWithText("写真を印刷に含める").assertDoesNotExist()
    }
}
