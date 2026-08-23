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
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import jp.mydns.fujiwara.carememo.ui.navigation.NavigationKeys
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputState
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel

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

    val isChanged = uiState.isChanged
    val isValid = uiState.isValid
    val isLoading = uiState.isLoading

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
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
    BackHandler(enabled = isChanged) {
        showDiscardDialog = true
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            modifier = Modifier.testTag("PersonEdit_DiscardConfirmDialog"),
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_discard_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        navController.popBackStack()
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDiscardDialog = false }
                )
            }
        )
    }

    // エラーダイアログ
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
                if (isDuplicateError) {
                    focusRequester.requestFocus()
                }
                isDuplicateError = false
            },
            confirmButtonText = if (isDuplicateError) stringResource(R.string.common_continue_editing)
                                else stringResource(R.string.common_close),
            modifier = Modifier.testTag("PersonEdit_DuplicateDialog")
        )
    }

    PersonEditScreenContent(
        isNew = uiState.isNew,
        isLoading = isLoading,
        lastName = uiState.lastName,
        firstName = uiState.firstName,
        lastNameFurigana = uiState.lastNameFurigana,
        firstNameFurigana = uiState.firstNameFurigana,
        note = uiState.note,
        era = uiState.era,
        year = uiState.year,
        month = uiState.month,
        day = uiState.day,
        isValid = isValid,
        onLastNameChange = { viewModel.updateLastName(it) },
        onFirstNameChange = { viewModel.updateFirstName(it) },
        onLastNameFuriganaChange = { viewModel.updateLastNameFurigana(it) },
        onFirstNameFuriganaChange = { viewModel.updateFirstNameFurigana(it) },
        onNoteChange = { viewModel.updateNote(it) },
        onEraChange = { viewModel.updateEra(it) },
        onYearChange = { viewModel.updateYear(it) },
        onMonthChange = { viewModel.updateMonth(it) },
        onDayChange = { viewModel.updateDay(it) },
        onSave = {
            focusManager.clearFocus()
            viewModel.save()
        },
        onCancel = {
            focusManager.clearFocus()
            if (isChanged) showDiscardDialog = true else navController.popBackStack()
        },
        snackbarHostState = snackbarHostState,
        noteFocusRequester = focusRequester,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreenContent(
    isNew: Boolean,
    isLoading: Boolean,
    lastName: String,
    firstName: String,
    lastNameFurigana: String,
    firstNameFurigana: String,
    note: String,
    era: BirthEra,
    year: String,
    month: String,
    day: String,
    isValid: Boolean,
    onLastNameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameFuriganaChange: (String) -> Unit,
    onFirstNameFuriganaChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onEraChange: (BirthEra) -> Unit,
    onYearChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
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
                        if (isNew) stringResource(R.string.main_user_registration)
                        else stringResource(R.string.main_user_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            LoadingScreen(modifier = Modifier.testTag("PersonEdit_Loading"))
        } else {
            val scrollState = rememberScrollState()
            // 状態復元のための Saver 対応：rememberSaveable を使用
            // ScrollState はデフォルトで Saver が提供されているため、単に rememberSaveable に変更可能
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
                            value = lastName,
                            onValueChange = onLastNameChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name)) },
                            maxLength = nameSpec.MAX_LENGTH_LAST_NAME,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastName")
                        )
                        AppTextField(
                            value = firstName,
                            onValueChange = onFirstNameChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name)) },
                            maxLength = nameSpec.MAX_LENGTH_FIRST_NAME,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstName")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val kanaSpec = AppSpecifications.Constraints.Person.Validation
                        AppTextField(
                            value = lastNameFurigana,
                            onValueChange = onLastNameFuriganaChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name_furigana)) },
                            maxLength = kanaSpec.MAX_LENGTH_LAST_NAME_FURIGANA,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastNameKana")
                        )
                        AppTextField(
                            value = firstNameFurigana,
                            onValueChange = onFirstNameFuriganaChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name_furigana)) },
                            maxLength = kanaSpec.MAX_LENGTH_FIRST_NAME_FURIGANA,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstNameKana")
                        )
                    }

                    AppTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.main_label_note)) },
                        maxLength = AppSpecifications.Constraints.Person.Validation.MAX_LENGTH_NOTE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(noteFocusRequester)
                            .testTag("PersonEdit_Memo")
                    )

                    HorizontalDivider()

                    // 生年月日
                    BirthdayInputSection(
                        era = era,
                        year = year,
                        month = month,
                        day = day,
                        onEraChange = onEraChange,
                        onYearChange = onYearChange,
                        onMonthChange = onMonthChange,
                        onDayChange = onDayChange
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 下部アクションボタン ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).testTag("PersonEdit_CancelButton")
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = onSave,
                            enabled = isValid,
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
    onEraChange: (BirthEra) -> Unit,
    onYearChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 外部（ViewModel）からの値を常に最新状態で参照するための State
    val currentEra by rememberUpdatedState(era)
    val currentYear by rememberUpdatedState(year)
    val currentMonth by rememberUpdatedState(month)
    val currentDay by rememberUpdatedState(day)

    // コールバックを常に最新状態で保持
    val currentOnEraChange by rememberUpdatedState(onEraChange)
    val currentOnYearChange by rememberUpdatedState(onYearChange)
    val currentOnMonthChange by rememberUpdatedState(onMonthChange)
    val currentOnDayChange by rememberUpdatedState(onDayChange)

    // BirthdayInputFields が要求する MutableState インターフェースをデリゲート形式で実装
    // これにより、LaunchedEffect による非同期同期を排除し、ViewModel と UI を直結（パターン①）させる。
    val birthdayState = remember {
        BirthdayInputState(
            era = object : MutableState<BirthEra> {
                override var value: BirthEra
                    get() = currentEra
                    set(v) { currentOnEraChange(v) }
                override fun component1() = value
                override fun component2(): (BirthEra) -> Unit = { value = it }
            },
            year = object : MutableState<String> {
                override var value: String
                    get() = currentYear
                    set(v) { currentOnYearChange(v) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            },
            month = object : MutableState<String> {
                override var value: String
                    get() = currentMonth
                    set(v) { currentOnMonthChange(v) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            },
            day = object : MutableState<String> {
                override var value: String
                    get() = currentDay
                    set(v) { currentOnDayChange(v) }
                override fun component1() = value
                override fun component2(): (String) -> Unit = { value = it }
            }
        )
    }

    BirthdayInputFields(state = birthdayState, modifier = modifier)
}
