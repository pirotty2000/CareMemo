package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI層テスト：PersonConditionScreen (ロジック・安全性)
 * 仕様書項目: LG-01 〜 LG-06
 */
class PersonConditionScreenTest_4_Logic {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val isLoading = MutableStateFlow(false)
    private val isProcessing = MutableStateFlow(false)
    private val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>(extraBufferCapacity = 1)

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { conditionViewModel.isLoading } returns isLoading.asStateFlow()
        every { conditionViewModel.isProcessing } returns isProcessing.asStateFlow()
        every { conditionViewModel.errorMessage } returns MutableStateFlow<String?>(null).asStateFlow()
        every { conditionViewModel.filteredRecords } returns MutableStateFlow<List<ConditionAtVisit>>(emptyList()).asStateFlow()
        every { conditionViewModel.uiEventFlow } returns uiEventFlow
        every { conditionViewModel.currentConditionPhotos } returns MutableStateFlow<List<ConditionPhoto>>(emptyList()).asStateFlow()
        every { conditionViewModel.searchQuery } returns MutableStateFlow("").asStateFlow()
        every { conditionViewModel.conditionPhotoMap } returns MutableStateFlow(emptyMap<Int, Boolean>()).asStateFlow()
        
        every { detailViewModel.currentPerson } returns MutableStateFlow<Person?>(null).asStateFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow()
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null).asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false).asStateFlow()
        every { detailViewModel.defaultRecorderName } returns MutableStateFlow("記録者A").asStateFlow()
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    viewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToCategory = {},
                    onNavigateToPhotoPreview = { _, _, _ -> },
                    onNavigateToFullScreen = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun lg01_loading_state_is_respected() {
        isLoading.value = true
        setContent()
        composeTestRule.onNodeWithTag("AppLoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun lg02_save_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog("保存失敗", "データの保存中にエラーが発生しました。"))
        }
        composeTestRule.onNodeWithText("データの保存中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg03_delete_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog("削除失敗", "データの削除中にエラーが発生しました。"))
        }
        composeTestRule.onNodeWithText("データの削除中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg04_photo_save_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog("写真保存失敗", "写真の保存中にエラーが発生しました。"))
        }
        composeTestRule.onNodeWithText("写真の保存中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg05_photo_delete_failure_shows_error_dialog() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog("写真削除失敗", "写真の削除中にエラーが発生しました。"))
        }
        composeTestRule.onNodeWithText("写真の削除中にエラーが発生しました。").assertIsDisplayed()
    }

    @Test
    fun lg06_preparation_failure_shows_error_dialog() {
        val preparationErrorMessage = "カメラの起動準備に失敗しました。"
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog(title = "写真の取得に失敗", message = preparationErrorMessage))
        }
        composeTestRule.onNodeWithText(preparationErrorMessage).assertIsDisplayed()
    }
}
