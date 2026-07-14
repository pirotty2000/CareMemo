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
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
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
 * UI層テスト：PersonConditionScreen (共通コンポーネント)
 * 仕様書項目: COM-01 〜 COM-04
 */
class PersonConditionScreenTest_1_Common {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val currentPerson = MutableStateFlow<Person?>(null)
    private var backCalled = false

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        conditionViewModel = mockk(relaxed = true)

        // Screenの描画に必要な全プロパティをモックする
        every { conditionViewModel.isLoading } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.isProcessing } returns MutableStateFlow(false).asStateFlow()
        every { conditionViewModel.errorMessage } returns MutableStateFlow<String?>(null).asStateFlow()
        every { conditionViewModel.filteredRecords } returns MutableStateFlow<List<ConditionAtVisit>>(emptyList()).asStateFlow()
        every { conditionViewModel.uiEventFlow } returns MutableSharedFlow()
        every { conditionViewModel.currentConditionPhotos } returns MutableStateFlow<List<ConditionPhoto>>(emptyList()).asStateFlow()
        every { conditionViewModel.searchQuery } returns MutableStateFlow("").asStateFlow()
        every { conditionViewModel.conditionPhotoMap } returns MutableStateFlow(emptyMap<Int, Boolean>()).asStateFlow()
        
        every { detailViewModel.currentPerson } returns currentPerson.asStateFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow()
        every { detailViewModel.personCategorySummary } returns MutableStateFlow<PersonCategorySummary?>(null).asStateFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false).asStateFlow()
        every { detailViewModel.defaultRecorderName } returns MutableStateFlow("記録者A").asStateFlow()

        currentPerson.value = Person(
            id = 1, lastName = "山田", firstName = "太郎",
            lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
            birthday = Instant.parse("1950-01-01T00:00:00Z")
        )
        backCalled = false
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    viewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = { backCalled = true },
                    onNavigateToCategory = {},
                    onNavigateToPhotoPreview = { _, _, _ -> },
                    onNavigateToFullScreen = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun com01_header_back_button_works() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun com02_header_displays_person_info() {
        setContent()
        composeTestRule.onNodeWithTag("PersonHeader_Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田　太郎", substring = true).assertExists()
    }

    @Test
    fun com03_header_pdf_button_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("ConditionScreen_PdfButton").assertIsDisplayed()
    }

    @Test
    fun com04_category_bar_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
    }
}
