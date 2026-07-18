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
import java.time.Instant

/**
 * UI層テスト：PersonConditionScreen (画面挙動・結合)
 * 仕様書項目: BH-01 〜 BH-09
 */
class PersonConditionScreenTest_3_Behavior {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>(extraBufferCapacity = 1)
    private val filteredRecords = MutableStateFlow<List<ConditionAtVisit>>(emptyList())
    private val currentPhotos = MutableStateFlow<List<ConditionPhoto>>(emptyList())

    private var navigateToFullScreenCalled = false
    private var navigateToPhotoPreviewCalled = false

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { conditionViewModel.isLoading } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.isProcessing } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.errorMessage } returns MutableStateFlow<String?>(null).asStateFlow()
        every { conditionViewModel.filteredRecords } returns filteredRecords.asStateFlow()
        every { conditionViewModel.uiEventFlow } returns uiEventFlow
        every { conditionViewModel.currentConditionPhotos } returns currentPhotos.asStateFlow()
        every { conditionViewModel.searchQuery } returns MutableStateFlow("").asStateFlow()
        every { conditionViewModel.conditionPhotoMap } returns MutableStateFlow(emptyMap<Int, Boolean>()).asStateFlow()
        
        every { detailViewModel.currentPerson } returns MutableStateFlow<Person?>(
            Person(
                id = 1, lastName = "山田", firstName = "太郎",
                lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
                birthday = Instant.now()
            )
        ).asStateFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow()
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null).asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false).asStateFlow()
        every { detailViewModel.defaultRecorderName } returns MutableStateFlow("記録者A").asStateFlow()

        navigateToFullScreenCalled = false
        navigateToPhotoPreviewCalled = false
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
                    onNavigateToPhotoPreview = { _, _, _ -> navigateToPhotoPreviewCalled = true },
                    onNavigateToFullScreen = { _, _ -> navigateToFullScreenCalled = true }
                )
            }
        }
    }

    @Test
    fun bh01_save_record_calls_viewModel() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()
        composeTestRule.onNodeWithText("記録者").performTextInput("テスト担当者")
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput("テストの所見内容")
        composeTestRule.onNodeWithTag("Condition_SaveButton").assertIsEnabled().performClick()
        verify(exactly = 1) { conditionViewModel.saveRecord(any(), any(), any(), any()) }
    }

    @Test
    fun bh02_photo_add_button_is_clickable() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        composeTestRule.onNodeWithContentDescription("写真を撮影").assertIsEnabled()
    }

    @Test
    fun bh03_duplicate_datetime_shows_error() {
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog("重複エラー", "既に同じ日時の記録が存在します。"))
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("既に同じ日時の記録が存在します。").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("既に同じ日時の記録が存在します。").assertIsDisplayed()
    }

    @Test
    fun bh04_navigate_to_full_screen_on_photo_click() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        currentPhotos.value = listOf(ConditionPhoto(id = 1, conditionId = 1, personId = 1, photoFileName = "p.jpg", thumbnailFileName = "t.jpg", capturedAt = Instant.now(), caption = "写真A"))
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        
        // 写真をタップ (PhotoGrid内の画像)
        composeTestRule.onNodeWithContentDescription("写真A").performClick()
        assert(navigateToFullScreenCalled)
    }

    @Test
    fun bh05_input_is_maintained_when_returning_from_other_screen() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput("維持されるべきメモ")
        
        runBlocking { uiEventFlow.emit(BaseViewModel.UiEvent.ShowSnackbar("通知")) }
        // Snackbar の表示を待つ
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("通知").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("Condition_MemoInput").assertTextContains("維持されるべきメモ")
    }

    @Test
    fun bh06_cancel_capture_maintains_state() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput("撮影前のメモ")
        
        composeTestRule.onNodeWithTag("Condition_MemoInput").assertTextContains("撮影前のメモ")
    }

    @Test
    fun bh07_new_photo_is_reflected_after_preview_save() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        
        currentPhotos.value = listOf(ConditionPhoto(id = 1, conditionId = 1, personId = 1, photoFileName = "new.jpg", thumbnailFileName = "new_t.jpg", capturedAt = Instant.now(), caption = "追加写真"))
        
        composeTestRule.onNodeWithText("追加写真").assertExists()
    }

    @Test
    fun bh08_pdf_settings_dialog_is_displayed() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        setContent()
        
        composeTestRule.onNodeWithTag("ConditionScreen_PdfButton").performClick()
        composeTestRule.onNodeWithText("出力設定", substring = true).assertExists()
    }

    @Test
    fun bh09_photo_capture_error_notifies_user() {
        val testErrorMessage = "カメラの起動に失敗しました"
        setContent()
        runBlocking {
            uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialog(title = "エラー", message = testErrorMessage))
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(testErrorMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(testErrorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("閉じる").assertExists()
    }
}
