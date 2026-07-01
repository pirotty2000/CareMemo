package jp.mydns.fujiwara.carememo.ui.screens

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
 * ・入力補助：数値キーボードの自動表示、IMEアクションによるフォーカス移動、入力値のバリデーション。
 * ・即時反映：保存成功時のフィードバックと自動画面遷移。
 *
 * 【遷移】
 * ← MainScreen（戻るボタンまたは保存完了時に遷移）
 *
 * 【使用するViewModel】
 * BatchInputViewModel
 *
 * 【備考】
 * 多くの項目を一度に扱うため、スクロール位置の管理や各項目のエラー状態の可視化に配慮している。
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.HealthThresholds
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchInputScreen(
    viewModel: BatchInputViewModel,
    personId: Int,
    onBack: () -> Unit
) {
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val recordTime by viewModel.recordTime.collectAsState()
    val dateTimeState = rememberDateTimeInputState(initialInstant = recordTime)

    val height by viewModel.height.collectAsState()
    val weight by viewModel.weight.collectAsState()
    val bpSystolic by viewModel.bpSystolic.collectAsState()
    val bpDiastolic by viewModel.bpDiastolic.collectAsState()
    val pulse by viewModel.pulse.collectAsState()
    val bodyTemperature by viewModel.bodyTemperature.collectAsState()
    val glucose by viewModel.glucose.collectAsState()
    val hba1c by viewModel.hba1c.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

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
                else -> {}
            }
        }
    }

    BatchInputScreenContent(
        currentPerson = currentPerson,
        isLoading = isLoading,
        isNameMaskingEnabled = isNameMaskingEnabled,
        isProcessing = isSaving,
        height = height,
        onHeightChange = { viewModel.height.value = it },
        weight = weight,
        onWeightChange = { viewModel.weight.value = it },
        bpSystolic = bpSystolic,
        onBpSystolicChange = { viewModel.bpSystolic.value = it },
        bpDiastolic = bpDiastolic,
        onBpDiastolicChange = { viewModel.bpDiastolic.value = it },
        pulse = pulse,
        onPulseChange = { viewModel.pulse.value = it },
        bodyTemperature = bodyTemperature,
        onBodyTemperatureChange = { viewModel.bodyTemperature.value = it },
        glucose = glucose,
        onGlucoseChange = { viewModel.glucose.value = it },
        hba1c = hba1c,
        onHba1cChange = { viewModel.hba1c.value = it },
        dateTimeState = dateTimeState,
        flashColor = flashColor,
        snackbarHostState = snackbarHostState,
        scrollState = scrollState,
        onSave = {
            viewModel.setRecordTime(dateTimeState.toInstant() ?: Instant.now())
            viewModel.saveBatch()
        },
        onBack = onBack
    )
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
    pulse: String,
    onPulseChange: (String) -> Unit,
    bodyTemperature: String,
    onBodyTemperatureChange: (String) -> Unit,
    glucose: String,
    onGlucoseChange: (String) -> Unit,
    hba1c: String,
    onHba1cChange: (String) -> Unit,
    dateTimeState: DateTimeInputState,
    flashColor: Color,
    snackbarHostState: SnackbarHostState,
    scrollState: ScrollState,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequesters = remember { List(8) { FocusRequester() } }

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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(flashColor)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. 記録日時 (オートフォーカスを日で止める)
                    InputSectionCard(title = "") {
                        DateTimeInputFields(state = dateTimeState, autoFocusHour = false)
                    }

                    // 2. 身長・体重
                    InputSectionCard(title = "身長・体重") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = height,
                                onValueChange = { onHeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_HEIGHT)) },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[0]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[1].requestFocus() })
                            )
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_WEIGHT)) },
                                suffix = { Text("kg") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[1]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[2].requestFocus() })
                            )
                        }
                    }

                    // 3. バイタル
                    InputSectionCard(title = "バイタル") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bpSystolic,
                                onValueChange = { onBpSystolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BP_SYSTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[2]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[3].requestFocus() })
                            )
                            OutlinedTextField(
                                value = bpDiastolic,
                                onValueChange = { onBpDiastolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BP_DIASTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[3]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[4].requestFocus() })
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pulse,
                                onValueChange = { onPulseChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_PULSE)) },
                                suffix = { Text("bpm") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[4]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[5].requestFocus() })
                            )
                            OutlinedTextField(
                                value = bodyTemperature,
                                onValueChange = { onBodyTemperatureChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BODY_TEMP)) },
                                suffix = { Text("℃") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[5]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[6].requestFocus() })
                            )
                        }
                    }

                    // 4. 血糖値・HbA1c
                    InputSectionCard(title = "血糖値・HbA1c") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = glucose,
                                onValueChange = { onGlucoseChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_GLUCOSE)) },
                                suffix = { Text("mg/dL") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[6]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusRequesters[7].requestFocus() })
                            )
                            OutlinedTextField(
                                value = hba1c,
                                onValueChange = { onHba1cChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_HBA1C)) },
                                suffix = { Text("%") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesters[7]),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !isProcessing) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.save))
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // スクロールインジケーター (上向きV, 下向きV)
            VerticalScrollIndicator(scrollState)
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
            pulse = "70",
            onPulseChange = {},
            bodyTemperature = "36.5",
            onBodyTemperatureChange = {},
            glucose = "110",
            onGlucoseChange = {},
            hba1c = "6.0",
            onHba1cChange = {},
            dateTimeState = dateTimeState,
            flashColor = Color.Transparent,
            snackbarHostState = remember { SnackbarHostState() },
            scrollState = rememberScrollState(),
            onSave = {},
            onBack = {}
        )
    }
}
