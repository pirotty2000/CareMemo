@file:Suppress("NonAsciiCharacters")

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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonConditionScreen (画面挙動・結合)
 */
class PersonConditionScreenTest_3_Behavior {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var detailViewModel: PersonDetailUiStateViewModel
    private lateinit var conditionViewModel: PersonConditionViewModel

    private val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(extraBufferCapacity = 1)
    private val testPerson = Person(
        id = "1", lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val detailUiStateFlow = MutableStateFlow(PersonDetailUiState(person = testPerson, personId = "1"))
    private val conditionUiStateFlow = MutableStateFlow(PersonConditionUiState(personId = "1"))

    @Before
    fun setup() {
        detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
        conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)

        every { detailViewModel.uiState } returns detailUiStateFlow
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseUiStateViewModel.UiEvent>().asSharedFlow()
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        every { conditionViewModel.uiState } returns conditionUiStateFlow
        every { conditionViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { conditionViewModel.viewEvent } returns MutableSharedFlow<PersonConditionViewEvent>().asSharedFlow()
        every { conditionViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { conditionViewModel.defaultRecorderName } returns MutableStateFlow("テスト記録者")

        // 状態更新の stub
        every { conditionViewModel.setSelectedConditionId(any()) } answers {
            val id = it.invocation.args[0] as String?
            conditionUiStateFlow.value = conditionUiStateFlow.value.copy(selectedConditionId = id)
        }
    }

    private fun setContent() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    detailViewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bh01_save_action_calls_viewmodel() {
        // 一覧画面が表示されるように、selectedConditionId を明示的に null にする
        conditionUiStateFlow.value = conditionUiStateFlow.value.copy(
            selectedConditionId = null,
            records = listOf(ConditionAtVisit(id = "1", personId = "1", title = "A", condition = "B", author = "C", recordTime = Instant.now())).toImmutableList()
        )
        setContent()

        // 新規追加ボタン（FAB）
        composeTestRule.onNodeWithContentDescription("新規追加", substring = true).performClick()
        
        // FABをクリックした結果、selectedConditionId が NEW_RECORD_ID になり、編集フォームが表示されることを待機
        composeTestRule.waitForIdle()

        // メモ入力欄を特定して入力する
        composeTestRule.onNodeWithTag("Condition_MemoInput")
            .assertIsDisplayed()
            .performTextInput("内容")
        composeTestRule.onNodeWithTag("Condition_SaveButton").performClick()
        
        verify { conditionViewModel.saveCurrentEdit(any()) }
    }

    @Test
    fun bh03_duplicate_datetime_save_guard() {
        setContent()
        composeTestRule.onNodeWithContentDescription("新規追加", substring = true).performClick()
        
        // 重複エラーイベント発生
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialog("保存エラー", "既に記録が存在します"))
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("既に記録が存在します").assertIsDisplayed()
    }
}
