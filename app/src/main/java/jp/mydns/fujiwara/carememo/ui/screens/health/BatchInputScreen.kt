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
 * 巡回時や定期測定時など、複数のデータを効率的に連続入力することを目的としています。
 *
 * 【主な機能】
 * ・同時入力：全健康カテゴリの入力フィールドを縦スクロール形式で一括提供。
 * ・日時一括設定：入力された全データに対して、指定された記録日時を共通で適用。
 * ・相関バリデーション：ViewModel（BatchInputViewModel）と連携した、画面全体の保存可否判定。
 * ・変更検知：初期状態からの差分をリアルタイムに計算し、未保存での離脱時に警告を表示。
 *
 * 【全体像：一括入力構成（Batch Input Layout）】
 *
 * ■ BatchInputScreen (★本コンポーネント)
 * │
 * ├─ TopAppBar (利用者情報 ＋ 戻るボタン)
 * └─ Column (縦スクロールエリア)
 *      ├─ DateTimeInputFields (日時設定：共通)
 *      ├─ カテゴリ別入力（AppTextField x N）
 *      │    ├─ 身長・体重
 *      │    ├─ バイタル（血圧、SAT、脈拍、体温）
 *      │    └─ 血糖値（血糖、HbA1c）
 *      └─ アクションボタン (キャンセル、保存)
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
                        // 引数の中に __RES__ 形式のプレースホルダがある場合は解決する
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
    val handleAction: (BatchInputUiAction) -> Unit = remember(viewModel, navController, focusManager, uiState.isChanged) {
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
                    if (uiState.isChanged) showDiscardDialog = true else viewModel.navigateBack()
                }
                BatchInputUiAction.ConfirmDiscard -> {
                    showDiscardDialog = false
                    viewModel.navigateBack()
                }
                BatchInputUiAction.Back -> {
                    focusManager.clearFocus()
                    if (uiState.isChanged) showDiscardDialog = true else viewModel.navigateBack()
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
    androidx.activity.compose.BackHandler(enabled = uiState.isChanged) {
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
 *
 * 【役割】
 * 一括入力画面のレイアウト本体 (Stateless)
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
                    IconButton(onClick = { onAction(BatchInputUiAction.Back) }, modifier = Modifier.testTag("BatchInputScreen_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.personId == null) {
            LoadingScreen()
        } else {
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
                    // 記録日時 (Stateless)
                    DateTimeInputFields(
                        year = uiState.year,
                        onYearChange = { onAction(BatchInputUiAction.UpdateYear(it)) },
                        month = uiState.month,
                        onMonthChange = { onAction(BatchInputUiAction.UpdateMonth(it)) },
                        day = uiState.day,
                        onDayChange = { onAction(BatchInputUiAction.UpdateDay(it)) },
                        hour = uiState.hour,
                        onHourChange = { onAction(BatchInputUiAction.UpdateHour(it)) },
                        minute = uiState.minute,
                        onMinuteChange = { onAction(BatchInputUiAction.UpdateMinute(it)) },
                        isError = uiState.fieldErrors["recordTime"] != null,
                        supportingText = uiState.fieldErrors["recordTime"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { field, _ -> onAction(BatchInputUiAction.MarkFieldAsTouched(field)) },
                        modifier = Modifier.testTag("BatchInputScreen_DateTimeInput")
                    )

                    HorizontalDivider()

                    // 身長・体重
                    Text(stringResource(R.string.common_category_height_weight), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.height,
                            onValueChange = { onAction(BatchInputUiAction.UpdateHeight(it)) },
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_height)) },
                            suffix = { Text("cm") },
                            isError = uiState.fieldErrors["height"] != null,
                            supportingText = uiState.fieldErrors["height"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["height"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("height")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_HeightField")
                        )
                        AppTextField(
                            value = uiState.weight,
                            onValueChange = { onAction(BatchInputUiAction.UpdateWeight(it)) },
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_weight)) },
                            suffix = { Text("kg") },
                            isError = uiState.fieldErrors["weight"] != null,
                            supportingText = uiState.fieldErrors["weight"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["weight"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("weight")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_WeightField")
                        )
                    }

                    HorizontalDivider()

                    // バイタル
                    Text(stringResource(R.string.common_category_vital), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.bpSystolic,
                            onValueChange = { onAction(BatchInputUiAction.UpdateBpSystolic(it)) },
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                            suffix = { Text("mmHg") },
                            isError = uiState.fieldErrors["bpSystolic"] != null,
                            supportingText = uiState.fieldErrors["bpSystolic"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["bpSystolic"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bpSystolic")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpSystolicField")
                        )
                        AppTextField(
                            value = uiState.bpDiastolic,
                            onValueChange = { onAction(BatchInputUiAction.UpdateBpDiastolic(it)) },
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                            suffix = { Text("mmHg") },
                            isError = uiState.fieldErrors["bpDiastolic"] != null,
                            supportingText = uiState.fieldErrors["bpDiastolic"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["bpDiastolic"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bpDiastolic")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpDiastolicField")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.sat,
                            onValueChange = { onAction(BatchInputUiAction.UpdateSat(it)) },
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_sat)) },
                            suffix = { Text("%") },
                            isError = uiState.fieldErrors["sat"] != null,
                            supportingText = uiState.fieldErrors["sat"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["sat"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("sat")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_SatField")
                        )
                        AppTextField(
                            value = uiState.pulse,
                            onValueChange = { onAction(BatchInputUiAction.UpdatePulse(it)) },
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_pulse)) },
                            suffix = { Text("bpm") },
                            isError = uiState.fieldErrors["pulse"] != null,
                            supportingText = uiState.fieldErrors["pulse"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["pulse"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("pulse")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_PulseField")
                        )
                    }
                    AppTextField(
                        value = uiState.bodyTemperature,
                        onValueChange = { onAction(BatchInputUiAction.UpdateBodyTemp(it)) },
                        type = AppTextFieldType.DECIMAL,
                        label = { Text(stringResource(R.string.health_label_body_temp)) },
                        suffix = { Text("℃") },
                        isError = uiState.fieldErrors["bodyTemperature"] != null,
                        supportingText = uiState.fieldErrors["bodyTemperature"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["bodyTemperature"]?.toTypedArray() ?: emptyArray())) } },
                        onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("bodyTemperature")) },
                        modifier = Modifier.fillMaxWidth().testTag("BatchInputScreen_TempField")
                    )

                    HorizontalDivider()

                    // 血糖値
                    Text(stringResource(R.string.common_category_glucose), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.glucose,
                            onValueChange = { onAction(BatchInputUiAction.UpdateGlucose(it)) },
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_glucose)) },
                            suffix = { Text("mg/dL") },
                            isError = uiState.fieldErrors["glucose"] != null,
                            supportingText = uiState.fieldErrors["glucose"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["glucose"]?.toTypedArray() ?: emptyArray())) } },
                            onFocusChanged = { if (!it.isFocused) onAction(BatchInputUiAction.MarkFieldAsTouched("glucose")) },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_GlucoseField")
                        )
                        AppTextField(
                            value = uiState.hba1c,
                            onValueChange = { onAction(BatchInputUiAction.UpdateHbA1c(it)) },
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_hba1c)) },
                            suffix = { Text("%") },
                            isError = uiState.fieldErrors["hba1c"] != null,
                            supportingText = uiState.fieldErrors["hba1c"]?.let { { Text(stringResource(it, *uiState.fieldErrorArgs["hba1c"]?.toTypedArray() ?: emptyArray())) } },
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
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_CancelButton")
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = { onAction(BatchInputUiAction.SaveClick) },
                            enabled = uiState.isValid,
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_SaveButton")
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
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
