package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordDetailScreen(
    viewModel: PersonDetailViewModel,
    personId: Int,
    category: Category,
    recordId: Int,
    onBack: () -> Unit
) {
    val records by viewModel.records.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    // 編集モードの状態
    var isEditing by remember { mutableStateOf(recordId == 0) }

    // 日時ステート管理
    val dateTimeState = rememberDateTimeInputState()

    // カテゴリー固有の状態
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var bpSystolicText by remember { mutableStateOf("") }
    var bpDiastolicText by remember { mutableStateOf("") }
    var pulseText by remember { mutableStateOf("") }
    var bodyTemperatureText by remember { mutableStateOf("") }
    var glucoseText by remember { mutableStateOf("") }
    var hba1cText by remember { mutableStateOf("") }

    val record = remember(records, recordId) {
        when (category) {
            Category.HEIGHT_AND_WEIGHT -> records.filterIsInstance<HeightAndWeight>().find { it.id == recordId }
            Category.BP_AND_PULSE -> records.filterIsInstance<BpAndPulse>().find { it.id == recordId }
            Category.GLUCOSE_AND_HBA1C -> records.filterIsInstance<GlucoseAndHbA1c>().find { it.id == recordId }
            Category.CONDITION_AT_VISIT, Category.MEDICATION -> null
        }
    }

    // 初期値セット
    LaunchedEffect(record) {
        if (record != null) {
            val recordTime = when (record) {
                is HeightAndWeight -> record.recordTime
                is BpAndPulse -> record.recordTime
                is GlucoseAndHbA1c -> record.recordTime
                else -> Instant.now()
            }
            dateTimeState.setFromInstant(recordTime)

            when (record) {
                is HeightAndWeight -> {
                    heightText = record.height?.toString() ?: ""
                    weightText = record.weight?.toString() ?: ""
                }
                is BpAndPulse -> {
                    bpSystolicText = record.bpSystolic?.toString() ?: ""
                    bpDiastolicText = record.bpDiastolic?.toString() ?: ""
                    pulseText = record.pulse?.toString() ?: ""
                    bodyTemperatureText = record.bodyTemperature?.toString() ?: ""
                }
                is GlucoseAndHbA1c -> {
                    glucoseText = record.glucose?.toString() ?: ""
                    hba1cText = record.hba1c?.toString() ?: ""
                }
            }
        } else if (recordId == 0 && dateTimeState.year.value.isEmpty()) {
            dateTimeState.setFromInstant(Instant.now())
            
            if (category == Category.HEIGHT_AND_WEIGHT) {
                val latestHeight = records.filterIsInstance<HeightAndWeight>()
                    .filter { it.height != null }
                    .maxByOrNull { it.recordTime }?.height
                heightText = latestHeight?.toString() ?: ""
            }
        }
    }

    // 新規記録時の身長デフォルト値セット (履歴データの読み込み完了を待って実行)
    LaunchedEffect(records) {
        if (recordId == 0 && category == Category.HEIGHT_AND_WEIGHT && heightText.isEmpty()) {
            val latestHeight = records.filterIsInstance<HeightAndWeight>()
                .filter { it.height != null }
                .maxByOrNull { it.recordTime }?.height
            if (latestHeight != null) {
                heightText = latestHeight.toString()
            }
        }
    }

    LaunchedEffect(personId, category) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, category)
    }

    val firstFieldFocusRequester = remember { FocusRequester() }
    val secondFieldFocusRequester = remember { FocusRequester() }
    val thirdFieldFocusRequester = remember { FocusRequester() }
    val fourthFieldFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = currentPerson,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "${category.displayName}記録"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (isEditing && recordId != 0) isEditing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (record == null && recordId != 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditing) {
                HealthRecordEditForm(
                    category = category,
                    dateTimeState = dateTimeState,
                    heightText = heightText, onHeightChange = { heightText = it },
                    weightText = weightText, onWeightChange = { weightText = it },
                    bpSystolicText = bpSystolicText, onBpSystolicChange = { bpSystolicText = it },
                    bpDiastolicText = bpDiastolicText, onBpDiastolicChange = { bpDiastolicText = it },
                    pulseText = pulseText, onPulseChange = { pulseText = it },
                    bodyTemperatureText = bodyTemperatureText, onBodyTemperatureChange = { bodyTemperatureText = it },
                    glucoseText = glucoseText, onGlucoseChange = { glucoseText = it },
                    hba1cText = hba1cText, onHba1cChange = { hba1cText = it },
                    firstFieldFocusRequester = firstFieldFocusRequester,
                    secondFieldFocusRequester = secondFieldFocusRequester,
                    thirdFieldFocusRequester = thirdFieldFocusRequester,
                    fourthFieldFocusRequester = fourthFieldFocusRequester,
                    onCancel = { if (recordId == 0) onBack() else isEditing = false },
                    onSave = {
                        val recordTime = dateTimeState.toInstant() ?: Instant.now()

                        val newRecord: Any = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = recordId, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull(), recordTime = recordTime)
                            Category.BP_AND_PULSE -> BpAndPulse(id = recordId, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), pulse = pulseText.toIntOrNull(), bodyTemperature = bodyTemperatureText.toDoubleOrNull(), recordTime = recordTime)
                            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = recordId, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                            Category.CONDITION_AT_VISIT, Category.MEDICATION -> throw IllegalStateException("Not supported category in this screen")
                        }
                        viewModel.saveRecord(newRecord)
                        if (recordId == 0) onBack() else isEditing = false
                    }
                )
            } else {
                HealthRecordDisplayCard(record = record!!)
            }
        }
    }
}

@Composable
fun HealthRecordEditForm(
    category: Category,
    dateTimeState: DateTimeInputState,
    heightText: String, onHeightChange: (String) -> Unit,
    weightText: String, onWeightChange: (String) -> Unit,
    bpSystolicText: String, onBpSystolicChange: (String) -> Unit,
    bpDiastolicText: String, onBpDiastolicChange: (String) -> Unit,
    pulseText: String, onPulseChange: (String) -> Unit,
    bodyTemperatureText: String, onBodyTemperatureChange: (String) -> Unit,
    glucoseText: String, onGlucoseChange: (String) -> Unit,
    hba1cText: String, onHba1cChange: (String) -> Unit,
    firstFieldFocusRequester: FocusRequester,
    secondFieldFocusRequester: FocusRequester,
    thirdFieldFocusRequester: FocusRequester,
    fourthFieldFocusRequester: FocusRequester,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeInputFields(state = dateTimeState)

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (category) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { onHeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(HealthThresholds.HEALTH_LABEL_HEIGHT) }, suffix = { Text("cm") },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(HealthThresholds.HEALTH_LABEL_WEIGHT) }, suffix = { Text("kg") },
                                modifier = Modifier.weight(1f).focusRequester(secondFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )
                        }
                    }
                    Category.BP_AND_PULSE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bpSystolicText,
                                onValueChange = { onBpSystolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(HealthThresholds.HEALTH_LABEL_BP_SYSTOLIC) },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = bpDiastolicText,
                                onValueChange = { onBpDiastolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(HealthThresholds.HEALTH_LABEL_BP_DIASTOLIC) },
                                modifier = Modifier.weight(1f).focusRequester(secondFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { thirdFieldFocusRequester.requestFocus() })
                            )
                        }
                        OutlinedTextField(
                            value = pulseText,
                            onValueChange = { onPulseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(HealthThresholds.HEALTH_LABEL_PULSE) }, suffix = { Text("bpm") },
                            modifier = Modifier.fillMaxWidth().focusRequester(thirdFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { fourthFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = bodyTemperatureText,
                            onValueChange = { onBodyTemperatureChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(HealthThresholds.HEALTH_LABEL_BODY_TEMP) }, suffix = { Text("℃") },
                            modifier = Modifier.fillMaxWidth().focusRequester(fourthFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        OutlinedTextField(
                            value = glucoseText,
                            onValueChange = { onGlucoseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(HealthThresholds.HEALTH_LABEL_GLUCOSE) }, suffix = { Text("mg/dL") },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = hba1cText,
                            onValueChange = { onHba1cChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(HealthThresholds.HEALTH_LABEL_HBA1C) }, suffix = { Text("%") },
                            modifier = Modifier.fillMaxWidth().focusRequester(secondFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    Category.CONDITION_AT_VISIT, Category.MEDICATION -> {}
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> weightText.isNotBlank()
                            Category.BP_AND_PULSE -> bpSystolicText.isNotBlank() || bpDiastolicText.isNotBlank() || pulseText.isNotBlank() || bodyTemperatureText.isNotBlank()
                            Category.GLUCOSE_AND_HBA1C -> glucoseText.isNotBlank() || hba1cText.isNotBlank()
                            Category.CONDITION_AT_VISIT, Category.MEDICATION -> true
                        }
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
fun HealthRecordDisplayCard(record: Any) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = formatRecordTime(when (record) {
                    is HeightAndWeight -> record.recordTime
                    is BpAndPulse -> record.recordTime
                    is GlucoseAndHbA1c -> record.recordTime
                    else -> Instant.now()
                }),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            when (record) {
                is HeightAndWeight -> {
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_HEIGHT, value = record.height?.let { "${it} cm" } ?: "---")
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_WEIGHT, value = record.weight?.let { "${it} kg" } ?: "---")
                    val bmi = record.calculateBMI()
                    if (bmi > 0) {
                        val (label, _) = record.getBmiResult()
                        DetailItem(label = HealthThresholds.HEALTH_LABEL_BMI, value = "%.1f ($label)".format(bmi))
                    }
                }
                is BpAndPulse -> {
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_BP, value = "${record.bpSystolic ?: "---"} / ${record.bpDiastolic ?: "---"} mmHg")
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_PULSE, value = record.pulse?.let { "$it bpm" } ?: "---")
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_BODY_TEMP, value = record.bodyTemperature?.let { "$it ℃" } ?: "---")
                    val statusText = record.getVitalResults().joinToString("・") { it.first }
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_STATUS, value = statusText)
                }
                is GlucoseAndHbA1c -> {
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_GLUCOSE, value = record.glucose?.let { "$it mg/dL" } ?: "---")
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_HBA1C, value = record.hba1c?.let { "$it %" } ?: "---")
                    val g = record.getGlucoseResult().first
                    val h = record.getHbA1cResult().first
                    val statusText = if (g != "---" && h != "---") "$g・$h" else if (g != "---") g else h
                    DetailItem(label = HealthThresholds.HEALTH_LABEL_STATUS, value = statusText)
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
