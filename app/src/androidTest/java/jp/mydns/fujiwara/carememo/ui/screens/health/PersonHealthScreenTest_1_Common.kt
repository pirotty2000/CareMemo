package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
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
 * UI層テスト：PersonHealthScreen (1. 共通コンポーネント)
 * 仕様書項目: COM-01 〜 COM-05
 */
class PersonHealthScreenTest_1_Common {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val currentPersonFlow = MutableStateFlow<Person?>(null)

    @Before
    fun setup() {
        detailViewModel = mockk(relaxed = true)
        healthViewModel = mockk(relaxed = true)

        every { healthViewModel.records } returns MutableStateFlow(emptyList<HistoryRecord>())
        every { healthViewModel.isLoading } returns MutableStateFlow(false)
        every { healthViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()

        every { detailViewModel.currentPerson } returns currentPersonFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null)

        currentPersonFlow.value = Person(
            id = 1, lastName = "山田", firstName = "太郎",
            lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
            birthday = Instant.parse("1950-01-01T00:00:00Z")
        )
    }

    private fun setContent(onBack: () -> Unit = {}, onNavigateToCategory: (Category) -> Unit = {}) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    viewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    initialCategoryType = Category.BP_AND_PULSE,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = onBack,
                    onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = onNavigateToCategory
                )
            }
        }
    }

    @Test
    fun com01_header_back_button() {
        var backCalled = false
        setContent(onBack = { backCalled = true })
        composeTestRule.onNodeWithTag("HealthScreen_BackButton").performClick()
        assert(backCalled)
    }

    @Test
    fun com02_header_person_info() {
        setContent()
        
        // 名前の一部が表示されるまで待機（伏せ字解除のタイミングを待つため onNodeWithText を使用）
        composeTestRule.onNodeWithText("山田", substring = true).assertIsDisplayed()
        
        // タグを指定して、名前と年齢が含まれていることを最終確認
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge")
            .assertTextContains("山田", substring = true)
            .assertTextContains("歳", substring = true)
    }

    @Test
    fun com03_header_pdf_button() {
        setContent()
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").assertIsDisplayed()
    }

    @Test
    fun com04_category_selector_navigation() {
        var navigatedCategory: Category? = null
        setContent(onNavigateToCategory = { navigatedCategory = it })
        
        composeTestRule.onNodeWithTag("CategoryChip_CONDITION_AT_VISIT").performScrollTo().performClick()
        assert(navigatedCategory == Category.CONDITION_AT_VISIT)
        verify { healthViewModel.loadPerson(1) }
    }

    @Test
    fun com05_header_long_name_display() {
        currentPersonFlow.value = currentPersonFlow.value?.copy(lastName = "寿限無寿限無五劫の擦り切れ海砂利水魚")
        setContent()
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").assertIsDisplayed()
    }
}
