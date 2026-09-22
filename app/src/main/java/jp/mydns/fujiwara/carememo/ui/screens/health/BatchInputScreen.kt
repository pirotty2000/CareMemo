package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputOperation
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputScreenState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import kotlinx.coroutines.launch

/**
 * UI Action：一括入力画面におけるユーザー操作の集約定義
 */
sealed interface BatchInputUiAction {
    // 日時更新
    data class UpdateYear(val value: String) : BatchInputUiAction
    data class UpdateMonth(val value: String) : BatchInputUiAction
    data class UpdateDay(val value: String) : BatchInputUiAction
    data class UpdateHour(val value: String) : BatchInputUiAction
    data class UpdateMinute(val value: String) : BatchInputUiAction

    // 健康指標更新
    data class UpdateHeight(val value: String) : BatchInputUiAction
    data class UpdateWeight(val value: String) : BatchInputUiAction
    data class UpdateBpSystolic(val value: String) : BatchInputUiAction
    data class UpdateBpDiastolic(val value: String) : BatchInputUiAction
    data class UpdateSat(val value: String) : BatchInputUiAction
    data class UpdatePulse(val value: String) : BatchInputUiAction
    data class UpdateBodyTemp(val value: String) : BatchInputUiAction
    data class UpdateGlucose(val value: String) : BatchInputUiAction
    data class UpdateHbA1c(val value: String) : BatchInputUiAction
    data class MarkFieldAsTouched(val fieldName: String) : BatchInputUiAction

    // 画面操作
    data object SaveClick : BatchInputUiAction
    data object CancelClick : BatchInputUiAction
    data object ConfirmDiscard : BatchInputUiAction
    data object Back : BatchInputUiAction
    data object DismissDialog : BatchInputUiAction
}

/**
 * Screen：BatchInputScreen
 *
 * 【役割】
 * 健康記録の各指標（身長体重、バイタル、血糖値）を一画面で同時に記録するための「一括入力」画面です。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputScreen(
    viewModel: BatchInputViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val input = uiState.input

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // イベント監視
    LaunchedEffect(Unit) {
        launch {
            viewModel.uiEventFlow.collect { event ->
                when (event) {
                    is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                        snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                    }
                    is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes -> {
                        dialogTitle = context.getString(event.titleResId)
                        val resolvedArgs = event.args.map { arg ->
                            if (arg is String && arg.contains("__RES__")) {
                                arg.split("、").joinToString("、") { part ->
                                    if (part.startsWith("__RES__")) {
                                        val resId = part.removePrefix("__RES__").toIntOrNull()
                                        if (resId != null) context.getString(resId) else part
                                    } else part
                                }
                            } else arg
                        }
                        dialogMessage = context.getString(event.messageResId, *resolvedArgs.toTypedArray())
                    }
                    else -> {}
                }
            }
        }
        launch {
            viewModel.viewEvent.collect { event ->
                when (event) {
                    BatchInputViewEvent.SaveSuccessEffects -> {
                        scope.launch {
                            scrollState.animateScrollTo(0)
                        }
                    }
                    BatchInputViewEvent.NavigateBack -> {
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    // アクションハンドラ
    val handleAction: (BatchInputUiAction) -> Unit = remember(viewModel, navController, focusManager, input.isChanged) {
        { action ->
            when (action) {
                is BatchInputUiAction.UpdateYear -> viewModel.updateYear(action.value)
                is BatchInputUiAction.UpdateMonth -> viewModel.updateMonth(action.value)
                is BatchInputUiAction.UpdateDay -> viewModel.updateDay(action.value)
                is BatchInputUiAction.UpdateHour -> viewModel.updateHour(action.value)
                is BatchInputUiAction.UpdateMinute -> viewModel.updateMinute(action.value)
                is BatchInputUiAction.UpdateHeight -> viewModel.updateHeight(action.value)
                is BatchInputUiAction.UpdateWeight -> viewModel.updateWeight(action.value)
                is BatchInputUiAction.UpdateBpSystolic -> viewModel.updateBpSystolic(action.value)
                is BatchInputUiAction.UpdateBpDiastolic -> viewModel.updateBpDiastolic(action.value)
                is BatchInputUiAction.UpdateSat -> viewModel.updateSat(action.value)
                is BatchInputUiAction.UpdatePulse -> viewModel.updatePulse(action.value)
                is BatchInputUiAction.UpdateBodyTemp -> viewModel.updateBodyTemp(action.value)
                is BatchInputUiAction.UpdateGlucose -> viewModel.updateGlucose(action.value)
                is BatchInputUiAction.UpdateHbA1c -> viewModel.updateHbA1c(action.value)
                is BatchInputUiAction.MarkFieldAsTouched -> viewModel.markFieldAsTouched(action.fieldName)
                BatchInputUiAction.SaveClick -> {
                    focusManager.clearFocus()
                    viewModel.saveBatch()
                }
                BatchInputUiAction.CancelClick -> {
                    focusManager.clearFocus()
                    if (input.isChanged) showDiscardDialog = true else viewModel.navigateBack()
                }
                BatchInputUiAction.ConfirmDiscard -> {
                    showDiscardDialog = false
                    viewModel.navigateBack()
                }
                BatchInputUiAction.Back -> {
                    focusManager.clearFocus()
                    if (input.isChanged) showDiscardDialog = true else viewModel.navigateBack()
                }
                BatchInputUiAction.DismissDialog -> {
                    showDiscardDialog = false
                    dialogMessage = null
                    dialogTitle = null
                }
            }
        }
    }

    // システム戻るボタンの制御
    androidx.activity.compose.BackHandler(enabled = input.isChanged) {
        showDiscardDialog = true
    }

    BatchInputContent(
        uiState = uiState,
        isNameMaskingEnabled = isNameMaskingEnabled,
        onAction = handleAction,
        showDiscardDialog = showDiscardDialog,
        dialogTitle = dialogTitle,
        dialogMessage = dialogMessage,
        snackbarHostState = snackbarHostState,
        scrollState = scrollState,
        modifier = modifier
    )
}

/**
 * Screen：BatchInputContent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputContent(
    uiState: BatchInputUiState,
    isNameMaskingEnabled: Boolean,
    onAction: (BatchInputUiAction) -> Unit,
    showDiscardDialog: Boolean,
    dialogTitle: String?,
    dialogMessage: String?,
    snackbarHostState: SnackbarHostState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val input = uiState.input
    val isOperating = uiState.operation != BatchInputOperation.Idle

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { 
                    PersonHeaderTitle(
                        person = uiState.person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = stringResource(R.string.health_batch_input_title)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(BatchInputUiAction.Back) },
                        enabled = !isOperating,
                        modifier = Modifier.testTag("BatchInputScreen_BackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when (uiState.screenState) {
            is BatchInputScreenState.Loading -> {
                if (uiState.personId == null) {
                    LoadingScreen()
                }
            }
            is BatchInputScreenState.Error -> {
                ErrorState(
                    message = stringResource(R.string.common_error_load_failed),
                    onRetry = { /* viewModel.loadPerson() */ }
                )
            }
            is BatchInputScreenState.Active -> {
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .imePadding()
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                            .testTag("BatchInputScreen_InputScrollColumn"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 記録日時
                        DateTimeInputFields(
                            year = input.year,
                            onYearChange = { onAction(BatchInputUiAction.UpdateYear(it)) },
                            month = input.month,
                            onMonthChange = { onAction(BatchInputUiAction.UpdateMonth(it)) },
                            day = input.day,
                            onDayChange = { onAction(BatchInputUiAction.UpdateDay(it)) },
                            hour = input.hour,
                            onHourChange = { onAction(BatchInputUiAction.UpdateHour(it)) },
                            minute = input.minute,
                            onMinuteChange = { onAction(BatchInputUiAction.UpdateMinute(it)) },
                            enabled = !isOperating,
                            isError = input.fieldErrors["recordTime"] != null,
                            supportingText = input.fieldErrors["recordTime"]?.let { resId -> { Text(stringResource(resId)) } },
                            onFocusChanged = { field, _ -> onAction(BatchInputUiAction.MarkFieldAsTouched(field)) },
                            modifier = Modifier.testTag("BatchInputScreen_DateTimeInput")
                        )

                        HorizontalDivider()

                        // 身長・体重
                        Text(stringResource(R.string.common_category_height_weight), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = input.height,
                                onValueChange = { onAction(BatchInputUiAction.UpdateHeight(it)) },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_height)) },
                                suffix = { Text("cm") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["height"] != null,
                                supportingText = input.fieldErrors["height"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["height"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("height")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_HeightField")
                            )
                            AppTextField(
                                value = input.weight,
                                onValueChange = { onAction(BatchInputUiAction.UpdateWeight(it)) },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_weight)) },
                                suffix = { Text("kg") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["weight"] != null,
                                supportingText = input.fieldErrors["weight"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["weight"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("weight")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_WeightField")
                            )
                        }

                        HorizontalDivider()

                        // バイタル
                        Text(stringResource(R.string.common_category_vital), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = input.bpSystolic,
                                onValueChange = { onAction(BatchInputUiAction.UpdateBpSystolic(it)) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                                suffix = { Text("mmHg") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["bpSystolic"] != null,
                                supportingText = input.fieldErrors["bpSystolic"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["bpSystolic"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bpSystolic")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpSystolicField")
                            )
                            AppTextField(
                                value = input.bpDiastolic,
                                onValueChange = { onAction(BatchInputUiAction.UpdateBpDiastolic(it)) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                                suffix = { Text("mmHg") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["bpDiastolic"] != null,
                                supportingText = input.fieldErrors["bpDiastolic"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["bpDiastolic"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bpDiastolic")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpDiastolicField")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = input.sat,
                                onValueChange = { onAction(BatchInputUiAction.UpdateSat(it)) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_sat)) },
                                suffix = { Text("%") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["sat"] != null,
                                supportingText = input.fieldErrors["sat"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["sat"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("sat")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_SatField")
                            )
                            AppTextField(
                                value = input.pulse,
                                onValueChange = { onAction(BatchInputUiAction.UpdatePulse(it)) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_pulse)) },
                                suffix = { Text("bpm") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["pulse"] != null,
                                supportingText = input.fieldErrors["pulse"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["pulse"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("pulse")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_PulseField")
                            )
                        }
                        AppTextField(
                            value = input.bodyTemperature,
                            onValueChange = { onAction(BatchInputUiAction.UpdateBodyTemp(it)) },
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_body_temp)) },
                            suffix = { Text("℃") },
                            enabled = !isOperating,
                            isError = input.fieldErrors["bodyTemperature"] != null,
                            supportingText = input.fieldErrors["bodyTemperature"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["bodyTemperature"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bodyTemperature")) },
                            modifier = Modifier.fillMaxWidth().testTag("BatchInputScreen_TempField")
                        )

                        HorizontalDivider()

                        // 血糖値
                        Text(stringResource(R.string.common_category_glucose), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = input.glucose,
                                onValueChange = { onAction(BatchInputUiAction.UpdateGlucose(it)) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_glucose)) },
                                suffix = { Text("mg/dL") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["glucose"] != null,
                                supportingText = input.fieldErrors["glucose"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["glucose"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("glucose")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_GlucoseField")
                            )
                            AppTextField(
                                value = input.hba1c,
                                onValueChange = { onAction(BatchInputUiAction.UpdateHbA1c(it)) },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_hba1c)) },
                                suffix = { Text("%") },
                                enabled = !isOperating,
                                isError = input.fieldErrors["hba1c"] != null,
                                supportingText = input.fieldErrors["hba1c"]?.let { resId -> { Text(stringResource(resId, *input.fieldErrorArgs["hba1c"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("hba1c")) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_Hba1cField")
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // アクションボタン
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAction(BatchInputUiAction.CancelClick) },
                                enabled = !isOperating,
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_CancelButton")
                            ) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            Button(
                                onClick = { onAction(BatchInputUiAction.SaveClick) },
                                enabled = input.isValid && !isOperating,
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_SaveButton")
                            ) {
                                if (uiState.operation is BatchInputOperation.Saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.common_save))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                    VerticalScrollIndicator(scrollState = scrollState)
                }
            }
        }
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { onAction(BatchInputUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = { onAction(BatchInputUiAction.ConfirmDiscard) },
                    modifier = Modifier.testTag("BatchInputScreen_DiscardConfirmButton")
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onAction(BatchInputUiAction.DismissDialog) },
                    modifier = Modifier.testTag("BatchInputScreen_DiscardCancelButton")
                )
            }
        )
    }

    // エラーダイアログ
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage,
            onDismiss = { onAction(BatchInputUiAction.DismissDialog) }
        )
    }
}
