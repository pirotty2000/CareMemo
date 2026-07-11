@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：BatchInputScreen (健康記録一括入力)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_BatchInput.md
 */
class BatchInputScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val mockPerson = Person(
        id = 1, 
        lastName = "山田", 
        firstName = "太郎", 
        lastNameFurigana = "ヤマダ", 
        firstNameFurigana = "タロウ", 
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private lateinit var viewModel: BatchInputViewModel
    private lateinit var uiEventFlow: MutableSharedFlow<BaseViewModel.UiEvent>

    @Before
    fun setup() {
        viewModel = mockk<BatchInputViewModel>(relaxed = true)
        uiEventFlow = MutableSharedFlow()
        
        every { viewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { viewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { viewModel.isSaving } returns MutableStateFlow(false)
        every { viewModel.isInputValid } returns MutableStateFlow(true)
        every { viewModel.recordTime } returns MutableStateFlow(Instant.now())
        
        // 各入力項目の初期値
        every { viewModel.height } returns MutableStateFlow("")
        every { viewModel.weight } returns MutableStateFlow("")
        every { viewModel.bpSystolic } returns MutableStateFlow("")
        every { viewModel.bpDiastolic } returns MutableStateFlow("")
        every { viewModel.sat } returns MutableStateFlow("")
        every { viewModel.pulse } returns MutableStateFlow("")
        every { viewModel.bodyTemperature } returns MutableStateFlow("")
        every { viewModel.glucose } returns MutableStateFlow("")
        every { viewModel.hba1c } returns MutableStateFlow("")
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                BatchInputScreen(
                    viewModel = viewModel,
                    personId = 1,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. 画面表示テスト (BatchInputScreenContent)
    // ======================================================================================

    @Test
    fun cp01_dateTimeInput_isDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_DateTimeInput").assertIsDisplayed()
    }

    @Test
    fun cp02_heightWeightFields_areDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp03_vitalFields_areDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpSystolicField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpDiastolicField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_SatField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_PulseField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_TempField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp04_glucoseFields_areDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_GlucoseField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_Hba1cField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp05_actionButtons_areDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_CancelButton").performScrollTo().assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (BatchInputScreen)
    // ======================================================================================

    @Test
    fun bh01_save_operation_calls_viewmodel() {
        // バリデーションが確実に通る状態にする
        every { viewModel.isInputValid } returns MutableStateFlow(true)
        
        setContent()

        // BH-01: データ保存の動作
        // 確実にボタンが見える位置までスクロール
        val saveButton = composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton")
        saveButton.performScrollTo()
        
        // ボタンが有効であることを確認してからクリック
        saveButton.assertIsEnabled().performClick()

        // ViewModelの保存処理が呼ばれることを確認
        verify { viewModel.saveBatch() }
    }

    @Test
    fun bh02_invalid_date_disables_save_button() {
        setContent()

        // 月に不正な値を入力 (例: 13)
        // DateTimeInputFields 内のフィールドを特定。ラベルが「月」のものを探す
        composeTestRule.onAllNodesWithText("月", substring = true).onFirst().performTextReplacement("13")
        
        // 保存ボタンまでスクロールして無効化されていることを確認
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun bh03_success_effect_triggered() {
        setContent()

        // 保存成功イベントを発生させる
        composeTestRule.runOnUiThread {
            runBlocking {
                uiEventFlow.emit(BaseViewModel.UiEvent.SaveSuccess)
            }
        }
        composeTestRule.waitForIdle()

        // 成功時の演出中にエラーなどが発生しないことを確認
        composeTestRule.onNodeWithTag("BatchInputScreen_InputScrollColumn").assertExists()
    }

    @Test
    fun bh04_duplicate_categories_show_error_dialog() {
        setContent()

        // 重複カテゴリ（身長・体重）を含むエラーイベントを発生させる
        val categoryNameRes = "__RES__${R.string.common_category_height_weight}"
        
        composeTestRule.runOnUiThread {
            runBlocking {
                uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialogRes(
                    R.string.common_error_title_save,
                    R.string.batch_err_duplicate_blocked,
                    listOf(categoryNameRes)
                ))
            }
        }

        // ダイアログが表示されるまで待機
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("保存エラー").fetchSemanticsNodes().isNotEmpty()
        }

        // ダイアログの表示確認
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        composeTestRule.onNodeWithText("既に以下のデータが登録されています", substring = true).assertIsDisplayed()

        // 置換されたカテゴリ名が含まれていること
        val expectedCategoryName = composeTestRule.activity.getString(R.string.common_category_height_weight)
        composeTestRule.onAllNodesWithText(expectedCategoryName, substring = true)
            .assertCountEquals(2) // タイトルとダイアログ
        
        composeTestRule.onNodeWithText("閉じる").performClick()
        composeTestRule.onNodeWithText("閉じる").assertDoesNotExist()
    }
}
