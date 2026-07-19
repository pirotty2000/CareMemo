package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * SCR-M-002 PersonEditScreen (利用者登録・編集) の UI テスト
 *
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-M-002_PersonEditScreen.md に準拠
 */
class PersonEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ======================================================================================
    // 1. コンポーネント単体テスト (PersonEditScreenContent)
    // ======================================================================================

    @Test
    fun cp01_initialDisplay_New() {
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

        // 全ての入力フィールドが空（またはデフォルト値）で表示されること
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_LastNameKana").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_FirstNameKana").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_Memo").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").assertTextContains("")
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun cp02_initialDisplay_Edit() {
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

        // 該当するデータが各フィールドに正しく反映されていること
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("山田")
        composeTestRule.onNodeWithTag("PersonEdit_FirstName").assertTextContains("太郎")
        composeTestRule.onNodeWithTag("PersonEdit_LastNameKana").assertTextContains("ヤマダ")
        composeTestRule.onNodeWithTag("PersonEdit_FirstNameKana").assertTextContains("タロウ")
        composeTestRule.onNodeWithTag("PersonEdit_Memo").assertTextContains("備考")
        composeTestRule.onNodeWithTag("PersonEdit_BirthYear").assertTextContains("10")
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp03_validation_missingRequiredField() {
        // 「姓」が空の場合
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = true,
                    isLoading = false,
                    lastName = "",
                    firstName = "太郎",
                    lastNameFurigana = "",
                    firstNameFurigana = "",
                    note = "",
                    era = BirthEra.SHOWA,
                    year = "25",
                    month = "1",
                    day = "1",
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
        // 保存ボタンが非活性であること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun cp04_validation_allRequiredFieldsPresent() {
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
        // 保存ボタンが活性化すること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp05_validation_optionalFields() {
        // 「せい」「めい」「識別メモ」が空であっても、必須項目が埋まっていれば保存可能
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
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp06_eraSelector_switch() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiStateFlow = MutableStateFlow(PersonEditUiState(era = BirthEra.SHOWA, isNew = true))
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // 元号セレクタを切り替えた際、表示が正しく更新されること
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").performClick()
        composeTestRule.onNodeWithText("西暦").performClick()
        
        // 内部状態をモックで更新して反映を確認
        uiStateFlow.value = uiStateFlow.value.copy(era = BirthEra.AD)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PersonEdit_EraSelector").assertTextContains("西暦")
    }

    @Test
    fun cp07_longStringDisplay() {
        val longText = "あ".repeat(50)
        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreenContent(
                    isNew = true,
                    isLoading = false,
                    lastName = longText,
                    firstName = longText,
                    lastNameFurigana = longText,
                    firstNameFurigana = longText,
                    note = longText,
                    era = BirthEra.SHOWA,
                    year = "25",
                    month = "1",
                    day = "1",
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
        // 長大文字列が入力されても、レイアウトが崩れない（エラーなく表示される）ことを確認
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertExists()
        composeTestRule.onNodeWithTag("PersonEdit_Memo").assertExists()
    }

    // ======================================================================================
    // 2. 画面全体の挙動・結合テスト (PersonEditScreen)
    // ======================================================================================

    @Test
    fun bh01_duplicateWarning_isDisplayed() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isValid = true, isNew = true))
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // 重複警告イベントを発生させる
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
            titleResId = R.string.main_err_title_duplicate_archived_add,
            messageResId = R.string.main_err_duplicate_active
        ))

        composeTestRule.waitForIdle()
        // 重複警告が表示されること
        composeTestRule.onNodeWithTag("PersonEdit_DuplicateDialog").assertIsDisplayed()
    }

    @Test
    fun bh02_duplicateCheck_withDifferentMemo() {
        // 同姓同名・同年月日の許容（メモが異なれば重複とみなされない）
        // これは ViewModel 側のテストで主に行うが、UI 側では重複警告が出ないことを確認する
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isValid = true, isNew = true))
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // 保存ボタンを押し、ViewModel 側で重複なしと判定され SaveSuccess が返るケース
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.SaveSuccess)
        composeTestRule.waitForIdle()

        // 警告ダイアログが出ないことを確認
        composeTestRule.onNodeWithTag("PersonEdit_DuplicateDialog").assertDoesNotExist()
    }

    @Test
    fun bh03_editMode_updateProcess() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isValid = true, isNew = false))
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").performClick()
        // ViewModel.save() が呼ばれることの確認
        io.mockk.verify { viewModel.save() }
    }

    @Test
    fun bh04_duplicateWarning_focusMemo() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isValid = true, isNew = true))
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
            titleResId = R.string.main_err_title_duplicate_archived_add,
            messageResId = R.string.main_err_duplicate_active
        ))

        composeTestRule.waitForIdle()
        // 「編集を続ける」を選択
        composeTestRule.onNodeWithText("編集を続ける").performClick()
        composeTestRule.waitForIdle()

        // 識別メモへフォーカスが移動すること
        composeTestRule.onNodeWithTag("PersonEdit_Memo").assertIsFocused()
    }

    @Test
    fun bh05_cancelOperation() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isNew = true))
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("PersonEdit_CancelButton").performClick()
        // 確認ダイアログが表示されること
        composeTestRule.onNodeWithText("変更の破棄").assertIsDisplayed()
    }

    @Test
    fun bh06_invalidDate_validation() {
        // 「2月31日」など存在しない日付が指定された場合
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
                    month = "2",
                    day = "31",
                    isValid = false, // 不正な日付なので false
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
        // 保存ボタンが非活性になること
        composeTestRule.onNodeWithTag("PersonEdit_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun bh07_saveSuccess_and_close() {
        var screenClosed = false
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(PersonEditUiState(isChanged = true, isValid = true, isNew = true))
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = { screenClosed = true })
            }
        }

        // 保存成功イベントを発生させる
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.SaveSuccess)
        composeTestRule.waitForIdle()

        // 画面が閉じる（onBack が呼ばれる）こと
        assert(screenClosed)
    }

    @Test
    fun bh08_saveFailure_behavior() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(
            PersonEditUiState(lastName = "入力中データ", isChanged = true, isValid = true, isNew = true)
        )
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // 保存失敗（エラーダイアログ）イベントを発生させる
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
            titleResId = R.string.common_error_title_save,
            messageResId = R.string.common_error_save,
            args = listOf("テストエラー詳細")
        ))

        composeTestRule.waitForIdle()
        // エラーダイアログが表示されること
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        // 入力中のデータが維持されていること
        composeTestRule.onNodeWithTag("PersonEdit_LastName").assertTextContains("入力中データ")
    }

    @Test
    fun bh09_validationError_display() {
        val viewModel = mockk<PersonEditViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseUiStateViewModel.UiEvent>(replay = 1)
        every { viewModel.uiState } returns MutableStateFlow(
            PersonEditUiState(lastName = "", isChanged = true, isValid = false, isNew = true)
        )
        every { viewModel.uiEventFlow } returns uiEventFlow

        composeTestRule.setContent {
            CareMemoTheme {
                PersonEditScreen(viewModel = viewModel, onBack = {})
            }
        }

        // バリデーションエラー（姓名未入力等）のイベントを発生させる
        uiEventFlow.tryEmit(BaseUiStateViewModel.UiEvent.ShowErrorDialogRes(
            titleResId = R.string.common_error_title_save,
            messageResId = R.string.main_err_edit_empty_last_name
        ))

        composeTestRule.waitForIdle()
        // 具体的な不備内容を示すエラーダイアログが表示されること
        composeTestRule.onNodeWithText("姓を入力してください").assertIsDisplayed()
    }
}
