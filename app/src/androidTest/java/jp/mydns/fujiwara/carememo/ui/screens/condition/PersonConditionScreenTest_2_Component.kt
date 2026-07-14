package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonConditionScreen (コンポーネント単体)
 * 仕様書項目: CP-01 〜 CP-06
 */
class PersonConditionScreenTest_2_Component {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val filteredRecords = MutableStateFlow<List<ConditionAtVisit>>(emptyList())
    private val currentPhotos = MutableStateFlow<List<ConditionPhoto>>(emptyList())

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        every { conditionViewModel.isLoading } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.isProcessing } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.errorMessage } returns MutableStateFlow<String?>(null).asStateFlow()
        every { conditionViewModel.filteredRecords } returns filteredRecords.asStateFlow()
        every { conditionViewModel.uiEventFlow } returns MutableSharedFlow()
        every { conditionViewModel.currentConditionPhotos } returns currentPhotos.asStateFlow()
        every { conditionViewModel.searchQuery } returns MutableStateFlow("").asStateFlow()
        every { conditionViewModel.conditionPhotoMap } returns MutableStateFlow(emptyMap<Int, Boolean>()).asStateFlow()
        
        every { detailViewModel.currentPerson } returns MutableStateFlow<Person?>(null).asStateFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow()
        every { detailViewModel.personCategorySummary } returns MutableStateFlow<PersonCategorySummary?>(null).asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false).asStateFlow()
        every { detailViewModel.defaultRecorderName } returns MutableStateFlow("記録者A").asStateFlow()
    }

    private fun setContent(widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    viewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    personId = 1,
                    widthSizeClass = widthSizeClass,
                    onBack = {},
                    onNavigateToCategory = {},
                    onNavigateToPhotoPreview = { _, _, _ -> },
                    onNavigateToFullScreen = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun cp01_selected_date_is_displayed() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.parse("2024-07-01T10:00:00Z"))
        filteredRecords.value = listOf(record)
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        // 西暦、和暦（令和）、月、日などが含まれる表示を確認
        composeTestRule.onNodeWithText("2024", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("令和", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp02_memo_input_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()
        composeTestRule.onNodeWithTag("Condition_MemoInput").assertIsDisplayed()
    }

    @Test
    fun cp03_photo_list_is_displayed() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        currentPhotos.value = listOf(ConditionPhoto(id = 1, conditionId = 1, personId = 1, photoFileName = "p.jpg", thumbnailFileName = "t.jpg", capturedAt = Instant.now(), caption = "写真A"))
        
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        composeTestRule.onNodeWithTag("Condition_PhotoList").assertIsDisplayed()
    }

    @Test
    fun cp04_empty_photo_state_shows_message() {
        val record = ConditionAtVisit(id = 1, personId = 1, title = "テスト", condition = "内容", author = "A", recordTime = Instant.now())
        filteredRecords.value = listOf(record)
        setContent()
        composeTestRule.onNodeWithText("テスト").performClick()
        composeTestRule.onNodeWithText("写真がありません", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp05_tablet_shows_two_columns() {
        setContent(WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("Condition_TabletLayout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Condition_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Condition_DetailPane").assertIsDisplayed()
    }

    @Test
    fun cp06_memo_input_is_scrollable() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_AddButton").performClick()
        val longText = "長いテキスト\n".repeat(50)
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput(longText)
        composeTestRule.onNodeWithTag("Condition_MemoInput").assertIsDisplayed()
    }
}
