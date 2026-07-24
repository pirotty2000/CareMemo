package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonConditionScreen (ロジック・安全性)
 * 仕様書項目: LG-01 〜 LG-06
 */
class PersonConditionScreenTest_4_Logic {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val detailUiState = MutableStateFlow(PersonDetailUiState())
    private val conditionUiState = MutableStateFlow(PersonConditionUiState())
    private val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(extraBufferCapacity = 1)
    private val isNameMaskingEnabled = MutableStateFlow(false)
    private val defaultRecorderName = MutableStateFlow("記録者A")

    private val testPerson = Person(
        id = "1", lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { detailViewModel.uiState } returns detailUiState.asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns isNameMaskingEnabled.asStateFlow()

        every { conditionViewModel.uiState } returns conditionUiState.asStateFlow()
        every { conditionViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { conditionViewModel.viewEvent } returns MutableSharedFlow<PersonConditionViewEvent>().asSharedFlow()
        every { conditionViewModel.isNameMaskingEnabled } returns isNameMaskingEnabled.asStateFlow()
        every { conditionViewModel.defaultRecorderName } returns defaultRecorderName.asStateFlow()

        // 状態更新の stub
        every { conditionViewModel.setSelectedConditionId(any()) } answers {
            val id = it.invocation.args[0] as String?
            conditionUiState.value = conditionUiState.value.copy(selectedConditionId = id)
        }

        // 基本的な状態の初期化
        detailUiState.value = PersonDetailUiState(
            personId = "1",
            person = testPerson
        )
        conditionUiState.value = PersonConditionUiState(
            personId = "1",
            records = listOf(ConditionAtVisit(id = "1", personId = "1", title = "A", condition = "B", author = "C", recordTime = Instant.now())),
            isLoading = false
        )
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    personId = "1",
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToCategory = {},
                    onNavigateToPhotoPreview = { _, _, _ -> },
                    onNavigateToFullScreen = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun lg01_loading_state_is_respected() {
        // isLoading = true にして再描画
        conditionUiState.value = conditionUiState.value.copy(isLoading = true, personId = null) // LoadingScreen を出すために ID を null にする
        setContent()
        composeTestRule.onNodeWithTag("AppLoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun lg02_save_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("保存失敗", "データの保存中にエラーが発生しました。"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("データの保存中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg03_delete_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("削除失敗", "データの削除中にエラーが発生しました。"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("データの削除中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg04_photo_save_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("写真保存失敗", "写真の保存中にエラーが発生しました。"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("写真の保存中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg05_photo_delete_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("写真削除失敗", "写真の削除中にエラーが発生しました。"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("写真の削除中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg06_preparation_failure_shows_error_dialog() {
        val preparationErrorMessage = "カメラの起動準備に失敗しました。"
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseUiStateViewModel.UiEvent.ShowErrorDialog(title = "写真の取得に失敗", message = preparationErrorMessage))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(preparationErrorMessage).assertIsDisplayed()
    }
}
