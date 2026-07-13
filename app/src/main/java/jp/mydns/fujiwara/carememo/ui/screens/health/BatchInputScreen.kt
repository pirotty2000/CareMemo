package jp.mydns.fujiwara.carememo.ui.screens.health

/**
 * Screen : BatchInputScreen
 *
 * 【画面名】
 * 健康記録の一括入力画面
 *
 * 【役割】
 * バイタル（血圧・体温）、血糖値、身体計測（体重）など、複数の健康指標を
 * 一つの画面で効率的に同時入力・登録するための画面。
 *
 * 【主な機能】
 * ・記録日時設定：カレンダーと時刻選択による記録日時の指定。
 * ・バイタル入力：最高血圧、最低血圧、脈拍、体温の入力。
 * ・血糖値入力：血糖値、HbA1cの入力。
 * ・身体計測入力：身長、体重の入力。
 * ・入力補助：数値キーボードの自動表示、最大桁数到達やIMEアクションによる自動フォーカス移動。
 * ・即時反映：保存成功時のフィードバックと自動画面遷移。
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.text.input.ImeAction
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputScreen(
    viewModel: BatchInputViewModel,
    personId: Int,
    onBack: () -> Unit
) {
    val currentPerson by viewModel.currentPerson.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isInputValid by viewModel.isInputValid.collectAsStateWithLifecycle()

    val recordTime by viewModel.recordTime.collectAsStateWithLifecycle()
    val dateTimeState = rememberDateTimeInputState(initialInstant = recordTime)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // ダイアログ表示用の状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // 成功時のフラッシュ演出用
    var showSuccessEffect by remember { mutableStateOf(false) }
    val flashColor by animateColorAsState(
        targetValue = if (showSuccessEffect) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "SuccessFlash"
    )

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.SaveSuccess -> {
                    showSuccessEffect = true
                    scope.launch {
                        // 一番上までスクロールし、フォーカスを解除してキーボードを閉じる
                        launch { scrollState.animateScrollTo(0) }
                        focusManager.clearFocus()
                        
                        delay(400.milliseconds)
                        showSuccessEffect = false
                    }
                }
                is BaseViewModel.UiEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                    }
                }
                is BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = context.getString(R.string.common_error_title_error)
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    val processedArgs = event.args.map { arg ->
                        if (arg is String) {
                            // 文字列内の "__RES__12345" のようなパターンをすべて探し、実際のリソース文字列に置換する
                            val regex = "__RES__(\\d+)".toRegex()
                            regex.replace(arg) { matchResult ->
                                val resId = matchResult.groupValues[1].toIntOrNull()
                                if (resId != null) context.getString(resId) else matchResult.value
                            }
                        } else {
                            arg
                        }
                    }
                    dialogMessage = context.getString(event.messageResId, *processedArgs.toTypedArray())
                }
                else -> {}
            }
        }
    }

    BatchInputScreenContent(
        currentPerson = currentPerson,
        isLoading = isLoading,
        isNameMaskingEnabled = isNameMaskingEnabled,
        isProcessing = isSaving,
        height = uiState.height,
        onHeightChange = { viewModel.updateHeight(it) },
        weight = uiState.weight,
        onWeightChange = { viewModel.updateWeight(it) },
        bpSystolic = uiState.bpSystolic,
        onBpSystolicChange = { viewModel.updateBpSystolic(it) },
        bpDiastolic = uiState.bpDiastolic,
        onBpDiastolicChange = { viewModel.updateBpDiastolic(it) },
        sat = uiState.sat,
        onSatChange = { viewModel.updateSat(it) },
        pulse = uiState.pulse,
        onPulseChange = { viewModel.updatePulse(it) },
        bodyTemperature = uiState.bodyTemperature,
        onBodyTemperatureChange = { viewModel.updateBodyTemp(it) },
        glucose = uiState.glucose,
        onGlucoseChange = { viewModel.updateGlucose(it) },
        hba1c = uiState.hba1c,
        onHba1cChange = { viewModel.updateHbA1c(it) },
        dateTimeState = dateTimeState,
        isInputValid = isInputValid,
        flashColor = flashColor,
        snackbarHostState = snackbarHostState,
        scrollState = scrollState,
        onSave = {
            dateTimeState.toInstant()?.let { instant ->
                viewModel.setRecordTime(instant)
                viewModel.saveBatch()
            }
        },
        onBack = onBack
    )

    // 通知ダイアログの表示
    if (dialogMessage != null) {
        jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputScreenContent(
    currentPerson: Person?,
    isLoading: Boolean = false,
    isNameMaskingEnabled: Boolean,
    isProcessing: Boolean,
    height: String,
    onHeightChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    bpSystolic: String,
    onBpSystolicChange: (String) -> Unit,
    bpDiastolic: String,
    onBpDiastolicChange: (String) -> Unit,
    sat: String,
    onSatChange: (String) -> Unit,
    pulse: String,
    onPulseChange: (String) -> Unit,
    bodyTemperature: String,
    onBodyTemperatureChange: (String) -> Unit,
    glucose: String,
    onGlucoseChange: (String) -> Unit,
    hba1c: String,
    onHba1cChange: (String) -> Unit,
    dateTimeState: DateTimeInputState,
    isInputValid: Boolean,
    flashColor: Color,
    snackbarHostState: SnackbarHostState,
    scrollState: ScrollState,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val isDateTimeValid by remember(dateTimeState) {
        derivedStateOf { dateTimeState.toInstant() != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = currentPerson,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "健康記録の一括入力"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("BatchInputScreen_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {},
                colors = appTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(flashColor)) {
            if (isLoading) {
                LoadingScreen()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                        .testTag("BatchInputScreen_InputScrollColumn"),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. 記録日時
                    InputSectionCard(title = "") {
                        DateTimeInputFields(
                            state = dateTimeState,
                            autoFocusHour = true,
                            modifier = Modifier.testTag("BatchInputScreen_DateTimeInput")
                        )
                    }

                    // 2. 身長・体重
                    InputSectionCard(title = "身長・体重") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(
                                value = height,
                                onValueChange = onHeightChange,
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_height)) },
                                suffix = { Text(AppThresholds.UNIT_HEIGHT) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_HeightField")
                            )
                            AppCompactTextField(
                                value = weight,
                                onValueChange = onWeightChange,
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_weight)) },
                                suffix = { Text(AppThresholds.UNIT_WEIGHT) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_WeightField")
                            )
                        }
                    }

                    // 3. バイタル
                    InputSectionCard(title = "バイタル") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(
                                value = bpSystolic,
                                onValueChange = onBpSystolicChange,
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpSystolicField")
                            )
                            AppCompactTextField(
                                value = bpDiastolic,
                                onValueChange = onBpDiastolicChange,
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_BpDiastolicField")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(
                                value = sat,
                                onValueChange = onSatChange,
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_sat)) },
                                suffix = { Text(AppThresholds.UNIT_SAT) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_SatField")
                            )
                            AppCompactTextField(
                                value = pulse,
                                onValueChange = onPulseChange,
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_pulse)) },
                                suffix = { Text(AppThresholds.UNIT_PULSE) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_PulseField")
                            )
                        }
                        AppCompactTextField(
                            value = bodyTemperature,
                            onValueChange = onBodyTemperatureChange,
                            type = AppTextFieldType.DECIMAL,
                            label = { Text(stringResource(R.string.health_label_body_temp)) },
                            suffix = { Text(AppThresholds.UNIT_BODY_TEMP) },
                            modifier = Modifier.fillMaxWidth().testTag("BatchInputScreen_TempField")
                        )
                    }

                    // 4. 血糖値・HbA1c
                    InputSectionCard(title = "血糖値・HbA1c") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(
                                value = glucose,
                                onValueChange = onGlucoseChange,
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_glucose)) },
                                suffix = { Text(AppThresholds.UNIT_GLUCOSE) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_GlucoseField")
                            )
                            AppCompactTextField(
                                value = hba1c,
                                onValueChange = onHba1cChange,
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_hba1c)) },
                                suffix = { Text(AppThresholds.UNIT_HBA1C) },
                                modifier = Modifier.weight(1f).testTag("BatchInputScreen_Hba1cField"),
                                imeAction = ImeAction.Done
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_CancelButton"),
                            enabled = !isProcessing
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("BatchInputScreen_SaveButton"),
                            enabled = !isProcessing && isDateTimeValid && isInputValid
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.common_save))
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // スクロールインジケーター (上向きV, 下向きV)
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }
}

@Composable
private fun InputSectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatchInputScreenPreview() {
    val mockPerson = Person(
        id = 1,
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        note = "テストメモ"
    )
    val dateTimeState = rememberDateTimeInputState(initialInstant = Instant.now())
    CareMemoTheme {
        BatchInputScreenContent(
            currentPerson = mockPerson,
            isLoading = false,
            isNameMaskingEnabled = false,
            isProcessing = false,
            height = "165.5",
            onHeightChange = {},
            weight = "60.0",
            onWeightChange = {},
            bpSystolic = "120",
            onBpSystolicChange = {},
            bpDiastolic = "80",
            onBpDiastolicChange = {},
            sat = "98",
            onSatChange = {},
            pulse = "70",
            onPulseChange = {},
            bodyTemperature = "36.5",
            onBodyTemperatureChange = {},
            glucose = "110",
            onGlucoseChange = {},
            hba1c = "6.0",
            onHba1cChange = {},
            dateTimeState = dateTimeState,
            isInputValid = true,
            flashColor = Color.Transparent,
            snackbarHostState = remember { SnackbarHostState() },
            scrollState = rememberScrollState(),
            onSave = {},
            onBack = {}
        )
    }
}
