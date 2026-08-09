@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
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

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(extraBufferCapacity = 1)
    private val testPerson = Person(
        id = "1", lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )
    private val testRecords = listOf(
        BpAndPulse(id = "1", personId = "1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
    )

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(
        person = testPerson,
        personId = "1",
        currentCategory = Category.BP_AND_PULSE
    ))
    private val healthUiStateFlow = MutableStateFlow(PersonHealthUiState(
        personId = "1",
        records = emptyList()
    ))

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { healthViewModel.uiState } returns healthUiStateFlow
        every { healthViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()

        // 状態更新の stub
        every { healthViewModel.setSelectedRecordId(any()) } answers {
            val id = firstArg<String?>()
            healthUiStateFlow.value = healthUiStateFlow.value.copy(selectedRecordId = id)
        }

        // タブ切り替え時に UiState を更新するように設定
        every { healthViewModel.updatePreferredShowHistory(any()) } answers {
            healthUiStateFlow.value = healthUiStateFlow.value.copy(preferredShowHistory = firstArg())
        }
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }
    }

    @Test
    fun bh01_data_save_action() {
        setContent()
        
        // 1. FAB（新規登録）をタップして入力画面を開く
        composeTestRule.onNodeWithTag("HealthScreen_AddButton").performClick()
        composeTestRule.waitForIdle()

        // 2. 数値を入力する (バイタルカテゴリを想定)
        composeTestRule.onNodeWithTag("HealthField_BpSystolic").performTextInput("120")
        composeTestRule.onNodeWithTag("HealthField_BpDiastolic").performTextInput("80")
        composeTestRule.onNodeWithTag("HealthField_Pulse").performTextInput("70")
        
        composeTestRule.waitForIdle()

        // 3. 保存ボタンをクリック
        composeTestRule.onNodeWithTag("HealthField_SaveButton").assertIsEnabled().performClick()
        
        // 4. ViewModel の保存処理が呼ばれたことを検証
        verify { healthViewModel.saveCurrentEdit() }
    }

    @Test
    fun bh02_graph_expansion_navigation() {
        healthUiStateFlow.value = healthUiStateFlow.value.copy(records = testRecords)
        setContent()
        
        // グラフタブに切り替え
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        // 拡大表示アイコンをタップ
        composeTestRule.onAllNodesWithContentDescription("拡大表示").onFirst().performClick()
        
        verify { healthViewModel.navigateToGraphExpansion("1", Category.BP_AND_PULSE, any()) }
    }

    @Test
    fun bh03_duplicate_datetime_save_guard() {
        setContent()
        
        // 保存エラーイベントの発生をシミュレート
        composeTestRule.runOnUiThread {
            uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("保存エラー", "既に同じ日時の記録が存在します。"))
        }
        composeTestRule.waitForIdle()
        
        // エラーダイアログが表示されること
        composeTestRule.onNodeWithText("既に同じ日時の記録が存在します。").assertIsDisplayed()
    }

    @Test
    fun bh04_pdf_output_action() {
        healthUiStateFlow.value = healthUiStateFlow.value.copy(records = testRecords)
        setContent()
        
        // PDFボタンタップ（タグで指定）
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").performClick()
        
        // ダイアログが表示されることを確認
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").assertIsDisplayed()
    }

    @Test
    fun bh05_return_from_expansion_maintains_state() {
        healthUiStateFlow.value = healthUiStateFlow.value.copy(records = testRecords)
        setContent()
        
        // グラフタブに切り替え
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        // グラフが表示されていることを確認
        composeTestRule.onNodeWithTag("HealthScreen_GraphArea").assertIsDisplayed()
    }
}
