@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * PersonMedicationScreen (服薬管理) の UI テスト
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var medicationViewModel: PersonMedicationViewModel

    private val testPerson = Person(
        id = "1", lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(person = testPerson, personId = "1"))
    private val medicationUiStateFlow = MutableStateFlow(PersonMedicationUiState(personId = "1"))

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        medicationViewModel = mockk<PersonMedicationViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { medicationViewModel.uiState } returns medicationUiStateFlow
        every { medicationViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToCategory = {}
                )
            }
        }
    }

    @Test
    fun COM_02_personInfo_isDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田", substring = true).assertExists()
        composeTestRule.onNodeWithText("歳", substring = true).assertExists()
    }

    @Test
    fun CAL_01_calendar_is_displayed() {
        setContent()
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
    }
}
