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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.main.BirthdayInputState
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel

/**
 * Screen : PersonEditScreen
 *
 * 【役割】：
 * 利用者の新規登録および情報編集を行うための独立した画面。
 * 従来のダイアログ形式から移行し、入力内容の保護（破棄確認）と操作の一貫性を提供する。
 *
 * 【主な機能】：
 * ・利用者基本情報の入力（姓名、フリガナ、備考、生年月日）。
 * ・変更破棄の確認（BackHandler）：未保存の変更がある状態で戻ろうとした場合に警告を表示。
 * ・バリデーション：必須項目の入力および生年月日の妥当性チェック。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val isChanged by viewModel.isChanged.collectAsState()
    val isValid by viewModel.isValid.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

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
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isNew) stringResource(R.string.main_user_registration)
                        else stringResource(R.string.main_user_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isChanged) showDiscardDialog = true else onBack()
                    }) {
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
                            onValueChange = { viewModel.lastName.value = it },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name)) },
                            modifier = Modifier.weight(1f)
                        )
                        AppTextField(
                            value = firstName,
                            onValueChange = { viewModel.firstName.value = it },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = lastNameFurigana,
                            onValueChange = { viewModel.lastNameFurigana.value = it },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_last_name_furigana)) },
                            modifier = Modifier.weight(1f)
                        )
                        AppTextField(
                            value = firstNameFurigana,
                            onValueChange = { viewModel.firstNameFurigana.value = it },
                            type = AppTextFieldType.TEXT,
                            label = { Text(stringResource(R.string.main_label_first_name_furigana)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AppTextField(
                        value = note,
                        onValueChange = { viewModel.note.value = it },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.main_label_note)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    // 生年月日
                    BirthdayInputSection(viewModel)

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 下部アクションボタン ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isChanged) showDiscardDialog = true else onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = { viewModel.save() },
                            enabled = isValid,
                            modifier = Modifier.weight(1f)
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
private fun BirthdayInputSection(viewModel: PersonEditViewModel) {
    val era by viewModel.era.collectAsState()
    val year by viewModel.year.collectAsState()
    val month by viewModel.month.collectAsState()
    val day by viewModel.day.collectAsState()

    // BirthdayInputFields が MutableState を要求するため、橋渡し役の State を作成
    val eraState = remember { mutableStateOf(era) }
    val yearState = remember { mutableStateOf(year) }
    val monthState = remember { mutableStateOf(month) }
    val dayState = remember { mutableStateOf(day) }

    // 内部状態が変わったら ViewModel を更新
    LaunchedEffect(eraState.value) { viewModel.era.value = eraState.value }
    LaunchedEffect(yearState.value) { viewModel.year.value = yearState.value }
    LaunchedEffect(monthState.value) { viewModel.month.value = monthState.value }
    LaunchedEffect(dayState.value) { viewModel.day.value = dayState.value }

    // ViewModel側でデータがロードされた際（編集モード）に、UIの状態を同期
    LaunchedEffect(era) { eraState.value = era }
    LaunchedEffect(year) { yearState.value = year }
    LaunchedEffect(month) { monthState.value = month }
    LaunchedEffect(day) { dayState.value = day }

    val birthdayState = remember {
        BirthdayInputState(eraState, yearState, monthState, dayState)
    }

    BirthdayInputFields(state = birthdayState)
}
