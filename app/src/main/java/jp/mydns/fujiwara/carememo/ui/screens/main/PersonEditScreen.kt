package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputState
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel

/**
 * Screen : PersonEditScreen
 *
 * 【役割】：
 * 利用者の新規登録および情報編集を行うための独立した画面。
 */
@Composable
fun PersonEditScreen(
    viewModel: PersonEditViewModel,
    onBack: () -> Unit
) {
    val lastName by viewModel.lastName.collectAsState()
    val firstName by viewModel.firstName.collectAsState()
    val lastNameFurigana by viewModel.lastNameFurigana.collectAsState()
    val firstNameFurigana by viewModel.firstNameFurigana.collectAsState()
    val note by viewModel.note.collectAsState()

    val era by viewModel.era.collectAsState()
    val year by viewModel.year.collectAsState()
    val month by viewModel.month.collectAsState()
    val day by viewModel.day.collectAsState()

    val isChanged by viewModel.isChanged.collectAsState()
    val isValid by viewModel.isValid.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isDuplicateError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // ViewModel からのイベント監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.SaveSuccess -> {
                    onBack()
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseViewModel.UiEvent.ShowErrorDialogRes -> {
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

    // システムの戻るボタン制御
    BackHandler(enabled = isChanged) {
        showDiscardDialog = true
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
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
                        onBack()
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
        isNew = viewModel.isNew,
        isLoading = isLoading,
        lastName = lastName,
        firstName = firstName,
        lastNameFurigana = lastNameFurigana,
        firstNameFurigana = firstNameFurigana,
        note = note,
        era = era,
        year = year,
        month = month,
        day = day,
        isValid = isValid,
        onLastNameChange = { viewModel.lastName.value = it },
        onFirstNameChange = { viewModel.firstName.value = it },
        onLastNameFuriganaChange = { viewModel.lastNameFurigana.value = it },
        onFirstNameFuriganaChange = { viewModel.firstNameFurigana.value = it },
        onNoteChange = { viewModel.note.value = it },
        onEraChange = { viewModel.era.value = it },
        onYearChange = { viewModel.year.value = it },
        onMonthChange = { viewModel.month.value = it },
        onDayChange = { viewModel.day.value = it },
        onSave = { viewModel.save() },
        onCancel = { if (isChanged) showDiscardDialog = true else onBack() },
        snackbarHostState = snackbarHostState,
        noteFocusRequester = focusRequester
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
    noteFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    Scaffold(
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
            LoadingScreen()
        } else {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- 入力フィールド群 ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = lastName,
                            onValueChange = onLastNameChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name)) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastName")
                        )
                        AppTextField(
                            value = firstName,
                            onValueChange = onFirstNameChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name)) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstName")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = lastNameFurigana,
                            onValueChange = onLastNameFuriganaChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name_furigana)) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_LastNameKana")
                        )
                        AppTextField(
                            value = firstNameFurigana,
                            onValueChange = onFirstNameFuriganaChange,
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name_furigana)) },
                            modifier = Modifier.weight(1f).testTag("PersonEdit_FirstNameKana")
                        )
                    }

                    AppTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.main_label_note)) },
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
    onDayChange: (String) -> Unit
) {
    // BirthdayInputFields が MutableState を要求するため、橋渡し役の State を作成
    val eraState = remember { mutableStateOf(era) }
    val yearState = remember { mutableStateOf(year) }
    val monthState = remember { mutableStateOf(month) }
    val dayState = remember { mutableStateOf(day) }

    // 内部状態が変わったら コールバック を呼ぶ
    LaunchedEffect(eraState.value) { onEraChange(eraState.value) }
    LaunchedEffect(yearState.value) { onYearChange(yearState.value) }
    LaunchedEffect(monthState.value) { onMonthChange(monthState.value) }
    LaunchedEffect(dayState.value) { onDayChange(dayState.value) }

    // 外部からデータがロードされた際に、UIの状態を同期
    LaunchedEffect(era) { eraState.value = era }
    LaunchedEffect(year) { yearState.value = year }
    LaunchedEffect(month) { monthState.value = month }
    LaunchedEffect(day) { dayState.value = day }

    val birthdayState = remember {
        BirthdayInputState(eraState, yearState, monthState, dayState)
    }

    BirthdayInputFields(state = birthdayState)
}
