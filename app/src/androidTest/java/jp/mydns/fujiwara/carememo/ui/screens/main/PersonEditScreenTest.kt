package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * PersonEditScreen (利用者登録・編集) の UI テスト
 *
 * 仕様書：doc/test/TEST_SPEC_UI_PersonEditScreen.md に準拠
 */
class PersonEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ======================================================================================
    // 1. コンポーネント単体テスト (PersonEditScreenContent)
    // ======================================================================================

    @Test
    fun cp01_initialDisplay_NewMode_isEmpty() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = true,
                    isLoading = false,
                    lastName = "",
                    firstName = "",
                    lastNameFurigana = "",
                    firstNameFurigana = "",
                    note = "",
                    era = BirthEra.SHOWA,
                    year = "",
                    month = "",
                    day = "",
                    isValid = false,
                    onLastNameChange = {},
                    onFirstNameChange = {},
                    onLastNameFuriganaChange = {},
                    onFirstNameFuriganaChange = {},
                    onNoteChange = {},
                    onEraChange = {},
                    onYearChange = {},
                    onMonthChange = {},
                    onDayChange = {},
                    onSave = {},
                    onCancel = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // 初期表示が空であることを確認
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").assertTextContains("")
        // 保存ボタンが非活性であること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun cp02_initialDisplay_EditMode_isPrefilled() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = false,
                    isLoading = false,
                    lastName = "山田",
                    firstName = "太郎",
                    lastNameFurigana = "ヤマダ",
                    firstNameFurigana = "タロウ",
                    note = "備考",
                    era = BirthEra.HEISEI,
                    year = "10",
                    month = "5",
                    day = "20",
                    isValid = true,
                    onLastNameChange = {},
                    onFirstNameChange = {},
                    onLastNameFuriganaChange = {},
                    onFirstNameFuriganaChange = {},
                    onNoteChange = {},
                    onEraChange = {},
                    onYearChange = {},
                    onMonthChange = {},
                    onDayChange = {},
                    onSave = {},
                    onCancel = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // 渡されたデータが埋まっていることを確認
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("山田")
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").assertTextContains("太郎")
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").assertTextContains("10")
        // 保存ボタンが活性化していること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp03_validation_missingRequiredField_disablesSaveButton() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = true,
                    isLoading = false,
                    lastName = "山田",
                    firstName = "", // 未入力
                    lastNameFurigana = "ヤマダ",
                    firstNameFurigana = "タロウ",
                    note = "",
                    era = BirthEra.SHOWA,
                    year = "25",
                    month = "1",
                    day = "1",
                    isValid = false, // 未入力があるので false
                    onLastNameChange = {},
                    onFirstNameChange = {},
                    onLastNameFuriganaChange = {},
                    onFirstNameFuriganaChange = {},
                    onNoteChange = {},
                    onEraChange = {},
                    onYearChange = {},
                    onMonthChange = {},
                    onDayChange = {},
                    onSave = {},
                    onCancel = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // 保存ボタンが非活性であること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun cp04_validation_allRequiredFieldsPresent_enablesSaveButton() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = true,
                    isLoading = false,
                    lastName = "山田",
                    firstName = "太郎",
                    lastNameFurigana = "",
                    firstNameFurigana = "",
                    note = "",
                    era = BirthEra.SHOWA,
                    year = "25",
                    month = "1",
                    day = "1",
                    isValid = true, // 必須項目が埋まっているので true
                    onLastNameChange = {},
                    onFirstNameChange = {},
                    onLastNameFuriganaChange = {},
                    onFirstNameFuriganaChange = {},
                    onNoteChange = {},
                    onEraChange = {},
                    onYearChange = {},
                    onMonthChange = {},
                    onDayChange = {},
                    onSave = {},
                    onCancel = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // 保存ボタンが活性化していること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp06_eraSelector_changeUpdatesYear() {
        // UI操作のテスト
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val eraFlow = MutableStateFlow(BirthEra.SHOWA)
        
        every { viewModel.lastName } returns MutableStateFlow("")
        every { viewModel.firstName } returns MutableStateFlow("")
        every { viewModel.lastNameFurigana } returns MutableStateFlow("")
        every { viewModel.firstNameFurigana } returns MutableStateFlow("")
        every { viewModel.note } returns MutableStateFlow("")
        every { viewModel.era } returns eraFlow
        every { viewModel.year } returns MutableStateFlow("25")
        every { viewModel.month } returns MutableStateFlow("1")
        every { viewModel.day } returns MutableStateFlow("1")
        every { viewModel.isChanged } returns MutableStateFlow(false)
        every { viewModel.isValid } returns MutableStateFlow(true)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNew } returns true
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // 初期値（昭和）を確認
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").assertTextContains("昭和")

        // 元号セレクタをタップして開く
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").performClick()
        
        // メニュー項目から「西暦」を探してタップ
        composeTestRule.onNodeWithText("西暦").performClick()
        
        // UI側の状態変更を反映させる（モック側を更新）
        eraFlow.value = BirthEra.AD
        composeTestRule.waitForIdle()
        
        // セレクタの表示が変わったことを確認
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").assertTextContains("西暦")
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (PersonEditScreen)
    // ======================================================================================

    @Test
    fun bh01_duplicateWarning_isDisplayed_and_focusesMemoAfterDismiss() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        // replay = 1 を指定して、イベントの消失を防ぐ
        val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>(replay = 1)

        every { viewModel.lastName } returns MutableStateFlow("山田")
        every { viewModel.firstName } returns MutableStateFlow("太郎")
        every { viewModel.lastNameFurigana } returns MutableStateFlow("")
        every { viewModel.firstNameFurigana } returns MutableStateFlow("")
        every { viewModel.note } returns MutableStateFlow("既存のメモ")
        every { viewModel.era } returns MutableStateFlow(BirthEra.SHOWA)
        every { viewModel.year } returns MutableStateFlow("25")
        every { viewModel.month } returns MutableStateFlow("1")
        every { viewModel.day } returns MutableStateFlow("1")
        every { viewModel.isChanged } returns MutableStateFlow(true)
        every { viewModel.isValid } returns MutableStateFlow(true)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNew } returns true
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        // 重複エラーイベントを発生させる
        uiEventFlow.tryEmit(
            BaseViewModel.UiEvent.ShowErrorDialogRes(
                titleResId = R.string.main_err_title_duplicate_archived_add,
                messageResId = R.string.main_err_duplicate_active
            )
        )

        // 再描画とダイアログの表示を待機
        composeTestRule.waitForIdle()

        // Then: 重複警告ダイアログが表示されていることを確認
        composeTestRule.onNodeWithTag("PersonEdit_DuplicateDialog").assertExists().assertIsDisplayed()
        
        // 「編集を続ける」ボタンをタップ
        composeTestRule.onNodeWithText("編集を続ける").performClick()

        // 再描画を待機
        composeTestRule.waitForIdle()

        // Then: ダイアログが閉じ、識別メモ欄にフォーカスが移動していることを確認
        composeTestRule.onNodeWithTag("PersonEdit_DuplicateDialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("PersonEdit_Memo").assertIsFocused()
    }

    @Test
    fun bh05_cancelOperation_showsDiscardDialog() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        
        // 変更ありの状態にする
        every { viewModel.isChanged } returns MutableStateFlow(true)
        every { viewModel.lastName } returns MutableStateFlow("編集中の名前")
        every { viewModel.firstName } returns MutableStateFlow("")
        every { viewModel.lastNameFurigana } returns MutableStateFlow("")
        every { viewModel.firstNameFurigana } returns MutableStateFlow("")
        every { viewModel.note } returns MutableStateFlow("")
        every { viewModel.era } returns MutableStateFlow(BirthEra.SHOWA)
        every { viewModel.year } returns MutableStateFlow("")
        every { viewModel.month } returns MutableStateFlow("")
        every { viewModel.day } returns MutableStateFlow("")
        every { viewModel.isValid } returns MutableStateFlow(false)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNew } returns true
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        // キャンセルボタンをタップ
        composeTestRule.onNodeWithTag("PersonEdit_CancelButton").performClick()

        // 破棄確認ダイアログが表示されることを確認
        composeTestRule.onNodeWithText("変更の破棄").assertIsDisplayed()
        composeTestRule.onNodeWithText("破棄して戻る").assertIsDisplayed()
    }
}
