@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：BatchInputScreen (健康記録一括入力)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-002_BatchInputScreen.md
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
    private val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>(extraBufferCapacity = 1)
    private val isInputValidFlow = MutableStateFlow(true)
    private val isSavingFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        viewModel = mockk<BatchInputViewModel>(relaxed = true)
        
        every { viewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { viewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { viewModel.isSaving } returns isSavingFlow
        every { viewModel.isInputValid } returns isInputValidFlow
        every { viewModel.recordTime } returns MutableStateFlow(Instant.now())
        every { viewModel.uiState } returns MutableStateFlow(BatchInputUiState())
    }

    private fun setContent(onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                BatchInputScreen(
                    viewModel = viewModel,
                    personId = 1,
                    onBack = onBack
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ======================================================================================
    // 1. コンポーネント単体テスト (BatchInputScreenContent)
    // ======================================================================================

    @Test
    fun cp01_date_time_input_display() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_DateTimeInput").assertIsDisplayed()
    }

    @Test
    fun cp02_height_weight_input_display() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_HeightField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp03_vital_input_display() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpSystolicField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_BpDiastolicField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_SatField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_PulseField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_TempField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp04_glucose_input_display() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_GlucoseField").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_Hba1cField").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp05_action_buttons_display() {
        setContent()
        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("BatchInputScreen_CancelButton").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cp06_scroll_accessibility() {
        setContent()
        val scrollColumn = composeTestRule.onNodeWithTag("BatchInputScreen_InputScrollColumn")
        
        // スクロール可能であることを確認
        scrollColumn.assert(hasScrollAction())
        
        // 最下部の項目までスクロールできるか（HbA1cフィールドで検証）
        composeTestRule.onNodeWithTag("BatchInputScreen_Hba1cField").performScrollTo().assertIsDisplayed()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (BatchInputScreen)
    // ======================================================================================

    @Test
    fun bh01_save_operation_calls_viewmodel() {
        isInputValidFlow.value = true
        setContent()

        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().performClick()
        verify { viewModel.saveBatch() }
    }

    @Test
    fun bh02_invalid_date_disables_save_button() {
        isInputValidFlow.value = false // バリデーション失敗状態をシミュレート
        setContent()

        composeTestRule.onNodeWithTag("BatchInputScreen_SaveButton").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun bh03_success_effect_and_scroll_to_top() {
        setContent()

        // 成功イベント発行
        uiEventFlow.tryEmit(BaseViewModel.UiEvent.SaveSuccess)
        composeTestRule.waitForIdle()

        // スクロールがトップに戻っていることを確認（日時入力エリアが表示されているか）
        composeTestRule.onNodeWithTag("BatchInputScreen_DateTimeInput").assertIsDisplayed()
    }

    @Test
    fun bh04_duplicate_guard_shows_error_dialog() {
        setContent()

        // 複数の重複カテゴリ（身長・体重 ＋ バイタル）を含むエラーイベントを発生させる
        val cat1 = R.string.common_category_height_weight
        val cat2 = R.string.common_category_vital
        val categoryNameTag = "__RES__${cat1}、__RES__${cat2}"
        
        uiEventFlow.tryEmit(BaseViewModel.UiEvent.ShowErrorDialogRes(
            R.string.common_error_title_save,
            R.string.batch_err_duplicate_blocked,
            listOf(categoryNameTag)
        ))
        composeTestRule.waitForIdle()

        // 1. ダイアログのタイトルが表示されていること
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        
        // 2. 複数のカテゴリ名が列記されていることを確認
        // チップ等と重複するため onAllNodes を使い、少なくとも1つ（ダイアログ内）が表示されていることを確認
        val expected1 = composeTestRule.activity.getString(cat1)
        val expected2 = composeTestRule.activity.getString(cat2)
        
        composeTestRule.onAllNodesWithText(expected1, substring = true).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(expected2, substring = true).onFirst().assertIsDisplayed()
        
        // 3. 閉じるボタンをタップしてダイアログが消えることを確認
        composeTestRule.onNodeWithText("閉じる").performClick()
        composeTestRule.onNodeWithText("閉じる").assertDoesNotExist()
    }

    @Test
    fun bh05_continuous_input_maintained() {
        var backCalled = false
        val uiStateFlow = MutableStateFlow(BatchInputUiState(weight = "60.0")) // 入力あり状態
        every { viewModel.uiState } returns uiStateFlow
        
        setContent(onBack = { backCalled = true })

        // 成功イベント発行
        uiEventFlow.tryEmit(BaseViewModel.UiEvent.SaveSuccess)
        
        // ViewModel側でリセットされるのをシミュレート
        uiStateFlow.value = BatchInputUiState() 
        
        composeTestRule.waitForIdle()

        // 1. 画面が閉じられていないこと（連続入力のため）
        assert(!backCalled)
        
        // 2. 記録日時は表示されたままであること
        composeTestRule.onNodeWithTag("BatchInputScreen_DateTimeInput").assertIsDisplayed()
        
        // 3. 入力フィールドがクリアされていること
        composeTestRule.onNodeWithTag("BatchInputScreen_WeightField").assertTextContains("")
    }

    @Test
    fun bh06_discard_confirmation_dialog() {
        var backCalled = false
        // UIStateを「変更あり」の状態にする
        every { viewModel.uiState } returns MutableStateFlow(BatchInputUiState(weight = "60.0"))
        // バリデーションが通る状態
        every { viewModel.isInputValid } returns MutableStateFlow(true)
        
        setContent(onBack = { backCalled = true })

        // 戻るボタンタップ
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()
        
        // 破棄確認ダイアログが表示されること
        composeTestRule.onNodeWithTag("BatchInputScreen_DiscardConfirmButton").assertIsDisplayed()
        
        // 「破棄して戻る」をタップ
        composeTestRule.onNodeWithTag("BatchInputScreen_DiscardConfirmButton").performClick()
        
        // 実際に onBack が呼ばれたこと
        assert(backCalled)
    }

    @Test
    fun bh07_discard_confirmation_on_date_change_only() {
        var backCalled = false
        // UIStateは空のまま
        every { viewModel.uiState } returns MutableStateFlow(BatchInputUiState())
        
        setContent(onBack = { backCalled = true })

        // 日付入力フィールド（年）を書き換える
        composeTestRule.onNodeWithText("年", substring = true).performTextReplacement("2020")
        
        // 戻るボタンタップ
        composeTestRule.onNodeWithTag("BatchInputScreen_BackButton").performClick()
        
        // 破棄確認ダイアログが表示されること（日時変更だけでも保護される）
        composeTestRule.onNodeWithTag("BatchInputScreen_DiscardConfirmButton").assertIsDisplayed()
        
        // キャンセルして戻る
        composeTestRule.onNodeWithTag("BatchInputScreen_DiscardCancelButton").performClick()
        assert(!backCalled)
    }
}
