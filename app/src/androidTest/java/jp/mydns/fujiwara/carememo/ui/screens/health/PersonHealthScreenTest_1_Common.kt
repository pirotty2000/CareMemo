@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
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
 * UI層テスト：PersonHealthScreen (1. 共通コンポーネント)
 * 仕様書項目: COM-01 〜 COM-05
 */
class PersonHealthScreenTest_1_Common {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var healthViewModel: PersonHealthViewModel

    private val testPerson = Person(
        id = 1, lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(person = testPerson, personId = 1))
    private val healthUiStateFlow = MutableStateFlow(PersonHealthUiState(personId = 1))

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { healthViewModel.uiState } returns healthUiStateFlow
        every { healthViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
    }

    private fun setContent(
        onBack: () -> Unit = {}, 
        onNavigateToCategory: (Category) -> Unit = {},
        onShowPdfSettings: (Category) -> Unit = {},
        onNavigateToBatchInput: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = onBack,
                    onNavigateToCategory = onNavigateToCategory,
                    onShowPdfSettings = onShowPdfSettings,
                    onNavigateToBatchInput = onNavigateToBatchInput,
                    onNavigateToGraphExpansion = { _: Int, _: Category, _: Int -> }
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
        
        // 名前の一部が表示されるまで待機
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
        
        // LazyRow (CategorySelectorBar) に対して、目的のチップまでスクロールするように指示
        // これにより、画面外にあって未構成のノードを構成・表示させる
        composeTestRule.onNodeWithTag("CategorySelectorBar")
            .performScrollToNode(hasTestTag("CategoryChip_CONDITION_AT_VISIT"))
        
        // 出現したノードをクリック
        composeTestRule.onNodeWithTag("CategoryChip_CONDITION_AT_VISIT")
            .performClick()

        assert(navigatedCategory == Category.CONDITION_AT_VISIT)
    }

    @Test
    fun com05_header_long_name_display() {
        detailUiStateFlow.value = detailUiStateFlow.value.copy(person = testPerson.copy(lastName = "寿限無寿限無五劫の擦り切れ海砂利水魚"))
        setContent()
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").assertIsDisplayed()
    }
}
