@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonConditionScreen (コンポーネント単体)
 */
class PersonConditionScreenTest_2_Component {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val testPerson = Person(
        id = 1, lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )
    private val testRecords = listOf(
        ConditionAtVisit(id = 1, personId = 1, title = "記録", condition = "内容", author = "A", recordTime = Instant.now())
    )

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(person = testPerson, personId = 1))
    private val conditionUiStateFlow = MutableStateFlow(PersonConditionUiState(personId = 1))

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { conditionViewModel.uiState } returns conditionUiStateFlow
        every { conditionViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { conditionViewModel.viewEvent } returns MutableSharedFlow<PersonConditionViewEvent>().asSharedFlow()
        every { conditionViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { conditionViewModel.defaultRecorderName } returns MutableStateFlow("")

        // 状態更新の stub
        every { conditionViewModel.setSelectedConditionId(any()) } answers {
            val id = it.invocation.args[0] as Int?
            conditionUiStateFlow.value = conditionUiStateFlow.value.copy(selectedConditionId = id)
        }
    }

    private fun setContent(widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
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
        composeTestRule.waitForIdle()
    }

    @Test
    fun cp01_phone_list_display() {
        conditionUiStateFlow.value = conditionUiStateFlow.value.copy(filteredRecords = testRecords)
        setContent()
        composeTestRule.onNodeWithTag("PersonHistoryList").assertIsDisplayed()
    }

    @Test
    fun cp06_search_box_updates_viewmodel() {
        // 一覧画面が表示されるように、selectedConditionId を明示的に -1 にする
        conditionUiStateFlow.value = conditionUiStateFlow.value.copy(selectedConditionId = -1)
        setContent()
        
        // 検索ボックスを特定して入力する
        composeTestRule.onNodeWithTag("ConditionScreen_SearchBox")
            .assertIsDisplayed()
            .performTextInput("キーワード")
        verify { conditionViewModel.updateSearchQuery("キーワード") }
    }
}
