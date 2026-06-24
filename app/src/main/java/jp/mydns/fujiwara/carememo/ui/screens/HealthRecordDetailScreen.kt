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

    // 編集モードの状態
    var isEditing by remember { mutableStateOf(recordId == 0) }

    // 共通の日時状態
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }

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
            else -> null
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
            val zdt = recordTime.atZone(ZoneId.systemDefault())
            year = zdt.year.toString()
            month = zdt.monthValue.toString()
            day = zdt.dayOfMonth.toString()
            hour = "%02d".format(zdt.hour)
            minute = "%02d".format(zdt.minute)

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
        } else if (recordId == 0 && year.isEmpty()) {
            val now = LocalDateTime.now()
            year = now.year.toString()
            month = now.monthValue.toString()
            day = now.dayOfMonth.toString()
            hour = "%02d".format(now.hour)
            minute = "%02d".format(now.minute)
            
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

    val monthFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }
    val firstFieldFocusRequester = remember { FocusRequester() }
    val secondFieldFocusRequester = remember { FocusRequester() }
    val thirdFieldFocusRequester = remember { FocusRequester() }
    val fourthFieldFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recordId == 0) "新規記録" else if (isEditing) "記録の編集" else "${category.displayName}詳細") },
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
                    year = year, onYearChange = { year = it },
                    month = month, onMonthChange = { month = it },
                    day = day, onDayChange = { day = it },
                    hour = hour, onHourChange = { hour = it },
                    minute = minute, onMinuteChange = { minute = it },
                    heightText = heightText, onHeightChange = { heightText = it },
                    weightText = weightText, onWeightChange = { weightText = it },
                    bpSystolicText = bpSystolicText, onBpSystolicChange = { bpSystolicText = it },
                    bpDiastolicText = bpDiastolicText, onBpDiastolicChange = { bpDiastolicText = it },
                    pulseText = pulseText, onPulseChange = { pulseText = it },
                    bodyTemperatureText = bodyTemperatureText, onBodyTemperatureChange = { bodyTemperatureText = it },
                    glucoseText = glucoseText, onGlucoseChange = { glucoseText = it },
                    hba1cText = hba1cText, onHba1cChange = { hba1cText = it },
                    monthFocusRequester = monthFocusRequester,
                    dayFocusRequester = dayFocusRequester,
                    hourFocusRequester = hourFocusRequester,
                    minuteFocusRequester = minuteFocusRequester,
                    firstFieldFocusRequester = firstFieldFocusRequester,
                    secondFieldFocusRequester = secondFieldFocusRequester,
                    thirdFieldFocusRequester = thirdFieldFocusRequester,
                    fourthFieldFocusRequester = fourthFieldFocusRequester,
                    onCancel = { if (recordId == 0) onBack() else isEditing = false },
                    onSave = {
                        val recordTime = try {
                            LocalDateTime.of(
                                year.toInt(), month.toInt(), day.toInt(),
                                hour.toInt(), minute.toInt()
                            ).atZone(ZoneId.systemDefault()).toInstant()
                        } catch (_: Exception) {
                            Instant.now()
                        }

                        val newRecord: Any = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = recordId, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull(), recordTime = recordTime)
                            Category.BP_AND_PULSE -> BpAndPulse(id = recordId, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), pulse = pulseText.toIntOrNull(), bodyTemperature = bodyTemperatureText.toDoubleOrNull(), recordTime = recordTime)
                            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = recordId, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                            else -> throw IllegalStateException("Unknown category")
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
    year: String, onYearChange: (String) -> Unit,
    month: String, onMonthChange: (String) -> Unit,
    day: String, onDayChange: (String) -> Unit,
    hour: String, onHourChange: (String) -> Unit,
    minute: String, onMinuteChange: (String) -> Unit,
    heightText: String, onHeightChange: (String) -> Unit,
    weightText: String, onWeightChange: (String) -> Unit,
    bpSystolicText: String, onBpSystolicChange: (String) -> Unit,
    bpDiastolicText: String, onBpDiastolicChange: (String) -> Unit,
    pulseText: String, onPulseChange: (String) -> Unit,
    bodyTemperatureText: String, onBodyTemperatureChange: (String) -> Unit,
    glucoseText: String, onGlucoseChange: (String) -> Unit,
    hba1cText: String, onHba1cChange: (String) -> Unit,
    monthFocusRequester: FocusRequester,
    dayFocusRequester: FocusRequester,
    hourFocusRequester: FocusRequester,
    minuteFocusRequester: FocusRequester,
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
            DateTimeInputFields(
                year = year, onYearChange = onYearChange,
                month = month, onMonthChange = onMonthChange,
                day = day, onDayChange = onDayChange,
                hour = hour, onHourChange = onHourChange,
                minute = minute, onMinuteChange = onMinuteChange,
                monthFocusRequester = monthFocusRequester,
                dayFocusRequester = dayFocusRequester,
                hourFocusRequester = hourFocusRequester,
                minuteFocusRequester = minuteFocusRequester
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (category) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { onHeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text("身長") }, suffix = { Text("cm") },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text("体重") }, suffix = { Text("kg") },
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
                                label = { Text("血圧(上)") },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = bpDiastolicText,
                                onValueChange = { onBpDiastolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text("血圧(下)") },
                                modifier = Modifier.weight(1f).focusRequester(secondFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { thirdFieldFocusRequester.requestFocus() })
                            )
                        }
                        OutlinedTextField(
                            value = pulseText,
                            onValueChange = { onPulseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text("脈拍") }, suffix = { Text("bpm") },
                            modifier = Modifier.fillMaxWidth().focusRequester(thirdFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { fourthFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = bodyTemperatureText,
                            onValueChange = { onBodyTemperatureChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("体温") }, suffix = { Text("℃") },
                            modifier = Modifier.fillMaxWidth().focusRequester(fourthFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        OutlinedTextField(
                            value = glucoseText,
                            onValueChange = { onGlucoseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text("血糖値") }, suffix = { Text("mg/dL") },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = hba1cText,
                            onValueChange = { onHba1cChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("HbA1c") }, suffix = { Text("%") },
                            modifier = Modifier.fillMaxWidth().focusRequester(secondFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    else -> {}
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
                            else -> true
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
                    DetailItem(label = "身長", value = record.height?.let { "${it} cm" } ?: "---")
                    DetailItem(label = "体重", value = record.weight?.let { "${it} kg" } ?: "---")
                    val bmi = record.calculateBMI()
                    if (bmi > 0) {
                        DetailItem(label = "BMI", value = "%.1f (${bmi.evaluateBMI()})".format(bmi))
                    }
                }
                is BpAndPulse -> {
                    DetailItem(label = "血圧", value = "${record.bpSystolic ?: "---"} / ${record.bpDiastolic ?: "---"} mmHg")
                    DetailItem(label = "脈拍", value = record.pulse?.let { "$it bpm" } ?: "---")
                    DetailItem(label = "体温", value = record.bodyTemperature?.let { "$it ℃" } ?: "---")
                    DetailItem(label = "判定", value = record.checkStatus())
                }
                is GlucoseAndHbA1c -> {
                    DetailItem(label = "血糖値", value = record.glucose?.let { "$it mg/dL" } ?: "---")
                    DetailItem(label = "HbA1c", value = record.hba1c?.let { "$it %" } ?: "---")
                    DetailItem(label = "判定", value = record.checkStatus())
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
