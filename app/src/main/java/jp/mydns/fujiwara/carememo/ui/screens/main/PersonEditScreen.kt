package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import jp.mydns.fujiwara.carememo.ui.navigation.NavigationKeys
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputState
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel

/**
 * UI Action：利用者登録・編集画面におけるユーザー操作の集約定義
 */
sealed interface PersonEditUiAction {
    // 入力変更操作
    data class LastNameChanged(val value: String) : PersonEditUiAction
    data class FirstNameChanged(val value: String) : PersonEditUiAction
    data class LastNameFuriganaChanged(val value: String) : PersonEditUiAction
    data class FirstNameFuriganaChanged(val value: String) : PersonEditUiAction
    data class NoteChanged(val value: String) : PersonEditUiAction
    data class EraChanged(val value: BirthEra) : PersonEditUiAction
    data class YearChanged(val value: String) : PersonEditUiAction
    data class MonthChanged(val value: String) : PersonEditUiAction
    data class DayChanged(val value: String) : PersonEditUiAction

    // フィールド操作
    data class MarkFieldAsTouched(val fieldName: String) : PersonEditUiAction

    // ボタン・ナビゲーション操作
    data object Save : PersonEditUiAction
    data object Cancel : PersonEditUiAction

    // ダイアログ操作
    data object DismissDiscardDialog : PersonEditUiAction
    data object ConfirmDiscard : PersonEditUiAction
    data object DismissErrorDialog : PersonEditUiAction
}

/**
 * Screen：PersonEditScreen
 *
 * 【役割】
 * 利用者の新規登録および情報編集（SCR-M-002）を行うための独立した画面です。
 * ViewModel との接続（State 購読、イベント監視）および破棄確認ダイアログ等の「副作用」を制御します。
 *
 * 【主な機能】
 * ・状態管理：`PersonEditViewModel` からの入力状態やバリデーション結果の購読。
 * ・イベントハンドリング：保存成功時の戻り遷移（SavedStateHandle への結果セット込み）やエラー通知の制御。
 * ・破棄保護：入力中に戻る操作を行った際の、変更破棄確認ダイアログの表示制御。
 * ・重複エラー対応：同姓同名（またはアーカイブ済み）が発見された際のエラーダイアログとフォーカス制御。
 *
 * 【全体像：利用者編集構成（Person Edit Layout）】
 *
 * ■ PersonEditScreen (★本コンポーネント：制御層)
 * │
 * └─ [1] PersonEditScreenContent (表示層：内部定義)
 *      ├─ Scaffold (AppBar ＋ SnackbarHost)
 *      └─ Column (スクロールエリア)
 *           ├─ 氏名入力 (AppTextField x 4)
 *           ├─ 識別メモ (AppTextField)
 *           ├─ [2] BirthdayInputSection (生年月日入力：ブリッジ用)
 *           │    └─ BirthdayInputFields (ui/components/main/)
 *           └─ アクションボタン (保存、キャンセル)
 */
@Composable
fun PersonEditScreen(
    viewModel: PersonEditViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // UI 制御用の内部状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isDuplicateError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // ViewModel からの通知イベント監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                    // 重複エラーかどうかをタイトルリソースIDで判定
                    isDuplicateError = (event.titleResId == R.string.main_err_title_duplicate_archived_add ||
                            event.titleResId == R.string.main_err_title_duplicate_archived_update)
                }
                else -> {}
            }
        }
    }

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is PersonEditViewEvent.NavigateBack -> {
                    if (event.result != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                            handle[NavigationKeys.PERSON_EDIT_RESULT] = event.result.name
                            handle[NavigationKeys.PERSON_EDIT_NAME] = event.personName
                        }
                    }
                    navController.popBackStack()
                }
            }
        }
    }

    // システムの戻るボタン制御
    BackHandler(enabled = uiState.isChanged) {
        showDiscardDialog = true
    }

    // アクションハンドラ：UI からの通知を ViewModel やナビゲーションへ橋渡しする
    // remember を使用してラムダインスタンスを固定し、下位コンポーネントの不要な再描画を抑制する
    val handleAction: (PersonEditUiAction) -> Unit = remember(viewModel, navController, focusManager, uiState.isChanged, isDuplicateError) {
        { action ->
            when (action) {
                is PersonEditUiAction.LastNameChanged -> viewModel.updateLastName(action.value)
                is PersonEditUiAction.FirstNameChanged -> viewModel.updateFirstName(action.value)
                is PersonEditUiAction.LastNameFuriganaChanged -> viewModel.updateLastNameFurigana(action.value)
                is PersonEditUiAction.FirstNameFuriganaChanged -> viewModel.updateFirstNameFurigana(action.value)
                is PersonEditUiAction.NoteChanged -> viewModel.updateNote(action.value)
                is PersonEditUiAction.EraChanged -> viewModel.updateEra(action.value)
                is PersonEditUiAction.YearChanged -> viewModel.updateYear(action.value)
                is PersonEditUiAction.MonthChanged -> viewModel.updateMonth(action.value)
                is PersonEditUiAction.DayChanged -> viewModel.updateDay(action.value)
                is PersonEditUiAction.MarkFieldAsTouched -> viewModel.markFieldAsTouched(action.fieldName)
                
                PersonEditUiAction.Save -> {
                    focusManager.clearFocus()
                    viewModel.save()
                }
                PersonEditUiAction.Cancel -> {
                    focusManager.clearFocus()
                    if (uiState.isChanged) showDiscardDialog = true else navController.popBackStack()
                }
                PersonEditUiAction.DismissDiscardDialog -> {
                    showDiscardDialog = false
                }
                PersonEditUiAction.ConfirmDiscard -> {
                    showDiscardDialog = false
                    navController.popBackStack()
                }
                PersonEditUiAction.DismissErrorDialog -> {
                    dialogMessage = null
                    dialogTitle = null
                    if (isDuplicateError) {
                        focusRequester.requestFocus()
                    }
                    isDuplicateError = false
                }
            }
        }
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { handleAction(PersonEditUiAction.DismissDiscardDialog) },
            modifier = Modifier.testTag("PersonEdit_DiscardConfirmDialog"),
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_discard_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = { handleAction(PersonEditUiAction.ConfirmDiscard) }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { handleAction(PersonEditUiAction.DismissDiscardDialog) }
                )
            }
        )
    }

    // エラーダイアログ
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = { handleAction(PersonEditUiAction.DismissErrorDialog) },
            confirmButtonText = if (isDuplicateError) stringResource(R.string.common_continue_editing)
                                else stringResource(R.string.common_close),
            modifier = Modifier.testTag("PersonEdit_DuplicateDialog")
        )
    }

    PersonEditScreenContent(
        uiState = uiState,
        onAction = handleAction,
        snackbarHostState = snackbarHostState,
        noteFocusRequester = focusRequester,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreenContent(
    uiState: PersonEditUiState,
    onAction: (PersonEditUiAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    noteFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isNew) stringResource(R.string.main_user_registration)
                        else stringResource(R.string.main_user_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(PersonEditUiAction.Cancel) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingScreen(modifier = Modifier.testTag("PersonEdit_Loading"))
        } else {
            val scrollState = rememberScrollState()
            // 状態復元のための Saver 対応：rememberSaveable を使用
            val scrollStateRestorable = rememberSaveable(saver = ScrollState.Saver) {
                scrollState
            }
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .imePadding() // キーボード回避
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollStateRestorable)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- 入力フィールド群 ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val nameSpec = AppSpecifications.Constraints.Person.Validation
                        AppTextField(
                            value = uiState.lastName,
                            onValueChange = { onAction(PersonEditUiAction.LastNameChanged(it)) },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name)) },
                            maxLength = nameSpec.MAX_LENGTH_LAST_NAME,
                            isError = uiState.fieldErrors["lastName"] != null,
                            supportingText = uiState.fieldErrors["lastName"]?.let { { Text(stringResource(it)) } },
                            onFocusChanged = { if (!it.isFocused) onAction(PersonEditUiAction.MarkFieldAsTouched("lastName")) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastName")
                        )
                        AppTextField(
                            value = uiState.firstName,
                            onValueChange = { onAction(PersonEditUiAction.FirstNameChanged(it)) },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name)) },
                            maxLength = nameSpec.MAX_LENGTH_FIRST_NAME,
                            isError = uiState.fieldErrors["firstName"] != null,
                            supportingText = uiState.fieldErrors["firstName"]?.let { { Text(stringResource(it)) } },
                            onFocusChanged = { if (!it.isFocused) onAction(PersonEditUiAction.MarkFieldAsTouched("firstName")) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstName")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val kanaSpec = AppSpecifications.Constraints.Person.Validation
                        AppTextField(
                            value = uiState.lastNameFurigana,
                            onValueChange = { onAction(PersonEditUiAction.LastNameFuriganaChanged(it)) },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name_furigana)) },
                            maxLength = kanaSpec.MAX_LENGTH_LAST_NAME_FURIGANA,
                            isError = uiState.fieldErrors["lastNameFurigana"] != null,
                            supportingText = uiState.fieldErrors["lastNameFurigana"]?.let { { Text(stringResource(it)) } },
                            onFocusChanged = { if (!it.isFocused) onAction(PersonEditUiAction.MarkFieldAsTouched("lastNameFurigana")) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastNameKana")
                        )
                        AppTextField(
                            value = uiState.firstNameFurigana,
                            onValueChange = { onAction(PersonEditUiAction.FirstNameFuriganaChanged(it)) },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name_furigana)) },
                            maxLength = kanaSpec.MAX_LENGTH_FIRST_NAME_FURIGANA,
                            isError = uiState.fieldErrors["firstNameFurigana"] != null,
                            supportingText = uiState.fieldErrors["firstNameFurigana"]?.let { { Text(stringResource(it)) } },
                            onFocusChanged = { if (!it.isFocused) onAction(PersonEditUiAction.MarkFieldAsTouched("firstNameFurigana")) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstNameKana")
                        )
                    }

                    AppTextField(
                        value = uiState.note,
                        onValueChange = { onAction(PersonEditUiAction.NoteChanged(it)) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.main_label_note)) },
                        maxLength = AppSpecifications.Constraints.Person.Validation.MAX_LENGTH_NOTE,
                        isError = uiState.fieldErrors["note"] != null,
                        supportingText = uiState.fieldErrors["note"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonEditUiAction.MarkFieldAsTouched("note")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(noteFocusRequester)
                            .testTag("PersonEdit_Memo")
                    )

                    HorizontalDivider()

                    // 生年月日
                    BirthdayInputSection(
                        era = uiState.era,
                        year = uiState.year,
                        month = uiState.month,
                        day = uiState.day,
                        isError = uiState.fieldErrors["birthday"] != null,
                        supportingText = uiState.fieldErrors["birthday"]?.let { { Text(stringResource(it)) } },
                        onAction = onAction
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 下部アクションボタン ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAction(PersonEditUiAction.Cancel) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_CancelButton")
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = { onAction(PersonEditUiAction.Save) },
                            enabled = uiState.isValid,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_SaveButton")
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
                VerticalScrollIndicator(scrollState = scrollState)
            }
        }
    }
}

@Composable
private fun BirthdayInputSection(
    era: BirthEra,
    year: String,
    month: String,
    day: String,
    onAction: (PersonEditUiAction) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    // 外部からの値を MutableState インターフェース経由で BirthdayInputFields へ橋渡しする
    // remember でラップしつつ、各セッターで onAction を呼び出す
    val birthdayState = remember(onAction) {
        BirthdayInputState(
            era = object : MutableState<BirthEra> {
                override var value: BirthEra
                    get() = era // note: composition will update this value directly
                    set(v) { onAction(PersonEditUiAction.EraChanged(v)) }
                override fun component1() = value
                override fun component2(): (BirthEra) -> Unit = { value = it }
            },
            year = object : MutableState<String> {
                override var value: String
                    get() = year
                    set(v) { onAction(PersonEditUiAction.YearChanged(v)) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            },
            month = object : MutableState<String> {
                override var value: String
                    get() = month
                    set(v) { onAction(PersonEditUiAction.MonthChanged(v)) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            },
            day = object : MutableState<String> {
                override var value: String
                    get() = day
                    set(v) { onAction(PersonEditUiAction.DayChanged(v)) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            }
        )
    }

    // 値が更新された際に MutableState 内の getter が参照する値も最新になるよう、
    // BirthdayInputState 内の getter は引数の era, year 等を直接参照する。
    // (コンポーザブルが再描画される際、引数は最新の値になっているため)

    BirthdayInputFields(
        state = birthdayState,
        isError = isError,
        supportingText = supportingText,
        onFocusChanged = { field, _ -> onAction(PersonEditUiAction.MarkFieldAsTouched(field)) },
        modifier = modifier
    )
}
