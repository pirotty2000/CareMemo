package jp.mydns.fujiwara.carememo.ui.screens.health

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
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import kotlinx.coroutines.launch

/**
 * 健康記録の一括入力画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputScreen(
    viewModel: BatchInputViewModel,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    
    val recordTime = uiState.recordTime
    val initialRecordTime = uiState.initialRecordTime
    val isChanged = uiState.isChanged
    val isValid = uiState.isValid
    val isLoading = uiState.isLoading

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 日時入力の状態管理
    val dateTimeState = rememberDateTimeInputState(initialInstant = initialRecordTime)

    // ViewModel からの recordTime 変更を dateTimeState に反映
    LaunchedEffect(recordTime) {
        dateTimeState.updateFromInstant(recordTime)
    }

    // dateTimeState の変更を ViewModel に通知
    LaunchedEffect(dateTimeState.year.value, dateTimeState.month.value, dateTimeState.day.value, dateTimeState.hour.value, dateTimeState.minute.value) {
        dateTimeState.toInstant()?.let { newTime ->
            if (newTime != recordTime) {
                viewModel.setRecordTime(newTime)
            }
        }
    }

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

    // システム戻るボタンの制御
    androidx.activity.compose.BackHandler(enabled = isChanged) {
        showDiscardDialog = true
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        viewModel.navigateBack()
                    },
                    modifier = Modifier.testTag("BatchInputScreen_DiscardConfirmButton")
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.testTag("BatchInputScreen_DiscardCancelButton")
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
                    PersonHeaderTitle(
                        person = uiState.person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = stringResource(R.string.common_category_height_weight)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        if (isChanged) showDiscardDialog = true else viewModel.navigateBack()
                    }, modifier = Modifier.testTag("BatchInputScreen_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (isLoading && uiState.personId == null) {
            LoadingScreen()
        } else {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .imePadding() // キーボード回避
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
                        state = dateTimeState,
                        modifier = Modifier.testTag("BatchInputScreen_DateTimeInput")
                    )

                    HorizontalDivider()

                    // 身長・体重
                    Text(stringResource(R.string.common_category_height_weight), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.height,
                            onValueChange = viewModel::updateHeight,
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_height)) },
                            suffix = { Text("cm") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_HeightField")
                        )
                        AppTextField(
                            value = uiState.weight,
                            onValueChange = viewModel::updateWeight,
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_weight)) },
                            suffix = { Text("kg") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_WeightField")
                        )
                    }

                    HorizontalDivider()

                    // バイタル
                    Text(stringResource(R.string.common_category_vital), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.bpSystolic,
                            onValueChange = viewModel::updateBpSystolic,
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                            suffix = { Text("mmHg") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpSystolicField")
                        )
                        AppTextField(
                            value = uiState.bpDiastolic,
                            onValueChange = viewModel::updateBpDiastolic,
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                            suffix = { Text("mmHg") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpDiastolicField")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.sat,
                            onValueChange = viewModel::updateSat,
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_sat)) },
                            suffix = { Text("%") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_SatField")
                        )
                        AppTextField(
                            value = uiState.pulse,
                            onValueChange = viewModel::updatePulse,
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_pulse)) },
                            suffix = { Text("bpm") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_PulseField")
                        )
                    }
                    AppTextField(
                        value = uiState.bodyTemperature,
                        onValueChange = viewModel::updateBodyTemp,
                        type = AppTextFieldType.DECIMAL,
                        label = { Text(stringResource(R.string.health_label_body_temp)) },
                        suffix = { Text("℃") },
                        modifier = Modifier.fillMaxWidth().testTag("BatchInputScreen_TempField")
                    )

                    HorizontalDivider()

                    // 血糖値
                    Text(stringResource(R.string.common_category_glucose), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = uiState.glucose,
                            onValueChange = viewModel::updateGlucose,
                            type = AppTextFieldType.INTEGER,
                            label = { Text(stringResource(R.string.health_label_glucose)) },
                            suffix = { Text("mg/dL") },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_GlucoseField")
                        )
                        AppTextField(
                            value = uiState.hba1c,
                            onValueChange = viewModel::updateHbA1c,
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_hba1c)) },
                            suffix = { Text("%") },
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
                            onClick = {
                                focusManager.clearFocus()
                                if (isChanged) showDiscardDialog = true else viewModel.navigateBack()
                            },
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_CancelButton")
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveBatch()
                            },
                            enabled = isValid,
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_SaveButton")
                        ) {
                            if (isLoading) {
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
}
