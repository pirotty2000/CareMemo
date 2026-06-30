package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateHeader
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedHistoryList(
    records: List<HistoryRecord>,
    category: Category,
    conditionPhotoMap: Map<Int, Boolean>,
    selectedRecordId: Int = -1,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    isAnyDialogOpen: Boolean,
) {
    val groupedRecords = remember(records) {
        records.groupBy { it.recordTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { entry -> entry.value.sortedBy { it.recordTime } }
            .toSortedMap(compareByDescending { it })
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        groupedRecords.forEach { (date, items) ->
            val isSingle = items.size == 1
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateHeader(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSingle) {
                            Text(
                                text = formatTime(items.first().recordTime),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            items(items.size) { index ->
                val record = items[index]
                val isSelected = record.id == selectedRecordId
                HistoryItemWrapper(
                    record = record,
                    showTime = !isSingle,
                    isSelected = isSelected,
                    onItemClick = { onItemClick(record) },
                    onDeleteSwipe = { onDeleteSwipe(record) },
                    isAnyDialogOpen = isAnyDialogOpen
                ) {
                    val hasOptionData = (category.hasOption && conditionPhotoMap[record.id] == true)
                    HistoryItemBody(category, record, hasOptionData)
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWrapper(
    record: HistoryRecord,
    showTime: Boolean,
    isSelected: Boolean = false,
    onItemClick: () -> Unit,
    onDeleteSwipe: () -> Unit,
    isAnyDialogOpen: Boolean,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDeleteSwipe()
    }
    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen && (dismissState.currentValue != SwipeToDismissBoxValue.Settled)) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onItemClick)
                .padding(vertical = 1.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (showTime) {
                    Text(
                        text = formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                content()
            }
        }
    }
}

@Composable
fun HistoryItemBody(category: Category, record: HistoryRecord, hasOption: Boolean) {
    when (category) {
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalRecordItemContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseRecordItemContent(it) }
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightRecordItemContent(it) }
        Category.CONDITION_AT_VISIT -> (record as? ConditionAtVisit)?.let { ConditionMemoContent(it, hasOption) }
        Category.MEDICATION -> { /* 統合画面では表示しない */ }
    }
}

@Composable
fun HeightWeightRecordItemContent(record: HeightAndWeight) {
    val context = LocalContext.current
    val bmi = record.calculateBMI()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Height,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.height?.let { "${it}cm" } ?: "---",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Scale,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.weight?.let { "${it}kg" } ?: "---",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_BMI)}: ${if (bmi > 0) "%.1f".format(bmi) else "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (bmi > 0) {
            val (bmiLabel, alertLevel) = record.getBmiResult(context)
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($bmiLabel)",
                fontSize = 10.sp,
                color = if (alertLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red
            )
        }
    }
}

@Composable
fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val context = LocalContext.current
    val (gStatus, gLevel) = record.getGlucoseResult(context)
    val (hStatus, hLevel) = record.getHbA1cResult(context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_GLUCOSE)}: ${record.glucose?.let { "$it mg/dL" } ?: "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (record.glucose != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($gStatus)",
                fontSize = 10.sp,
                color = if (gLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red,
                fontWeight = if (gLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_HBA1C)}: ${record.hba1c?.let { "$it%" } ?: "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (record.hba1c != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($hStatus)",
                fontSize = 10.sp,
                color = if (hLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red,
                fontWeight = if (hLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun VitalRecordItemContent(record: BpAndPulse) {
    val context = LocalContext.current
    val results = record.getVitalResults(context)

    val highBpLabel = stringResource(HealthThresholds.VITAL_LABEL_HIGH_BP)
    val lowBpLabel = stringResource(HealthThresholds.VITAL_LABEL_LOW_BP)
    val tachycardiaLabel = stringResource(HealthThresholds.VITAL_LABEL_TACHYCARDIA)
    val bradycardiaLabel = stringResource(HealthThresholds.VITAL_LABEL_BRADYCARDIA)
    val feverLabel = stringResource(HealthThresholds.VITAL_LABEL_FEVER)
    val hypothermiaLabel = stringResource(HealthThresholds.VITAL_LABEL_HYPOTHERMIA)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.pulse ?: "---"} bpm", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Rounded.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bodyTemperature?.let { "%.1f".format(it) } ?: "---"} ℃",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VitalStatusIndicator(
                label = highBpLabel,
                isActive = results.any { it.first == highBpLabel }
            )
            VitalStatusIndicator(
                label = lowBpLabel,
                isActive = results.any { it.first == lowBpLabel }
            )
            VitalStatusIndicator(
                label = tachycardiaLabel,
                isActive = results.any { it.first == tachycardiaLabel }
            )
            VitalStatusIndicator(
                label = bradycardiaLabel,
                isActive = results.any { it.first == bradycardiaLabel }
            )
            VitalStatusIndicator(
                label = feverLabel,
                isActive = results.any { it.first == feverLabel }
            )
            VitalStatusIndicator(
                label = hypothermiaLabel,
                isActive = results.any { it.first == hypothermiaLabel }
            )
        }
    }
}

@Composable
fun VitalStatusIndicator(label: String, isActive: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal
        ),
        color = if (isActive) MaterialTheme.colorScheme.error else Color.LightGray.copy(alpha = 0.6f)
    )
}

@Composable
fun EmptyState(message: String, description: String? = null, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BoxScope.VerticalScrollIndicator(scrollState: ScrollState) {
    val barHeight = 60.dp
    val density = LocalDensity.current
    val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
    val maxOffset = viewportHeight - barHeight

    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
        }
    }
    val isBottomSelected by remember {
        derivedStateOf { scrollState.value > (scrollState.maxValue / 2) }
    }

    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) { index ->
            val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.3f
                        )
                    )
            )
        }
    }

    Box(
        modifier = Modifier
            .width(4.dp)
            .height(barHeight)
            .align(Alignment.TopEnd)
            .offset {
                IntOffset(
                    x = 0,
                    y = (maxOffset * scrollFraction).roundToPx()
                )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    )
}

/**
 * 数値系健康記録詳細ペイン
 */
@Composable
fun HealthRecordDetailPane(
    healthViewModel: HealthRecordViewModel,
    personId: Int,
    category: Category,
    recordId: Int,
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val records by healthViewModel.getHealthRecords(category).collectAsState()
    var isEditing by remember { mutableStateOf(recordId == 0) }
    val dateTimeState = rememberDateTimeInputState()

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
            Category.HEIGHT_AND_WEIGHT -> records.asSequence().filterIsInstance<HeightAndWeight>().find { it.id == recordId }
            Category.BP_AND_PULSE -> records.asSequence().filterIsInstance<BpAndPulse>().find { it.id == recordId }
            Category.GLUCOSE_AND_HBA1C -> records.asSequence().filterIsInstance<GlucoseAndHbA1c>().find { it.id == recordId }
            else -> null
        }
    }

    LaunchedEffect(recordId) { isEditing = (recordId == 0) }

    LaunchedEffect(record, category, recordId) {
        if (record != null) {
            dateTimeState.setFromInstant(record.recordTime)
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
        } else if (recordId == 0) {
            dateTimeState.setFromInstant(Instant.now())
            if (category == Category.HEIGHT_AND_WEIGHT) {
                val latestHeight = records.filterIsInstance<HeightAndWeight>()
                    .filter { it.height != null }
                    .maxByOrNull { it.recordTime }?.height
                heightText = latestHeight?.toString() ?: ""
                weightText = ""
            } else {
                bpSystolicText = ""; bpDiastolicText = ""; pulseText = ""; bodyTemperatureText = ""
                glucoseText = ""; hba1cText = ""
            }
        }
    }

    if (record == null && recordId != 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (recordId == 0) "新規作成" else "記録の詳細",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isEditing && recordId != 0) {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }
        }

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
                onCancel = onCancel,
                onSave = {
                    val recordTime = dateTimeState.toInstant() ?: Instant.now()
                    val newRecord: Any = when (category) {
                        Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = recordId, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull(), recordTime = recordTime)
                        Category.BP_AND_PULSE -> BpAndPulse(id = recordId, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), pulse = pulseText.toIntOrNull(), bodyTemperature = bodyTemperatureText.toDoubleOrNull(), recordTime = recordTime)
                        Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = recordId, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                        else -> throw IllegalStateException("Not supported category")
                    }
                    healthViewModel.saveRecord(newRecord)
                    onSaveSuccess()
                }
            )
        } else {
            HealthRecordDisplayCard(record = record!!)
        }
    }
}

@Composable
private fun HealthRecordEditForm(
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
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val firstFieldFocusRequester = remember { FocusRequester() }
    val secondFieldFocusRequester = remember { FocusRequester() }
    val thirdFieldFocusRequester = remember { FocusRequester() }
    val fourthFieldFocusRequester = remember { FocusRequester() }

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
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_HEIGHT)) }, suffix = { Text("cm") },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_WEIGHT)) }, suffix = { Text("kg") },
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
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BP_SYSTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = bpDiastolicText,
                                onValueChange = { onBpDiastolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BP_DIASTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(secondFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { thirdFieldFocusRequester.requestFocus() })
                            )
                        }
                        OutlinedTextField(
                            value = pulseText,
                            onValueChange = { onPulseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_PULSE)) }, suffix = { Text("bpm") },
                            modifier = Modifier.fillMaxWidth().focusRequester(thirdFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { fourthFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = bodyTemperatureText,
                            onValueChange = { onBodyTemperatureChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_BODY_TEMP)) }, suffix = { Text("℃") },
                            modifier = Modifier.fillMaxWidth().focusRequester(fourthFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        OutlinedTextField(
                            value = glucoseText,
                            onValueChange = { onGlucoseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_GLUCOSE)) }, suffix = { Text("mg/dL") },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = hba1cText,
                            onValueChange = { onHba1cChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(stringResource(HealthThresholds.HEALTH_LABEL_HBA1C)) }, suffix = { Text("%") },
                            modifier = Modifier.fillMaxWidth().focusRequester(secondFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    else -> {}
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> weightText.isNotBlank()
                            Category.BP_AND_PULSE -> bpSystolicText.isNotBlank() || bpDiastolicText.isNotBlank() || pulseText.isNotBlank() || bodyTemperatureText.isNotBlank()
                            Category.GLUCOSE_AND_HBA1C -> glucoseText.isNotBlank() || hba1cText.isNotBlank()
                            else -> true
                        }
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun HealthRecordDisplayCard(record: Any) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = formatRecordTime(
                    when (record) {
                        is HeightAndWeight -> record.recordTime
                        is BpAndPulse -> record.recordTime
                        is GlucoseAndHbA1c -> record.recordTime
                        else -> Instant.now()
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            when (record) {
                is HeightAndWeight -> {
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_HEIGHT), value = record.height?.let { "$it cm" } ?: "---")
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_WEIGHT), value = record.weight?.let { "$it kg" } ?: "---")
                    val bmi = record.calculateBMI()
                    if (bmi > 0) {
                        val (resId, _) = HealthThresholds.evaluateBMI(bmi)
                        val label = resId?.let { stringResource(it) } ?: "---"
                        DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_BMI), value = "%.1f ($label)".format(bmi))
                    }
                }
                is BpAndPulse -> {
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_BP), value = "${record.bpSystolic ?: "---"} / ${record.bpDiastolic ?: "---"} mmHg")
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_PULSE), value = record.pulse?.let { "$it bpm" } ?: "---")
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_BODY_TEMP), value = record.bodyTemperature?.let { "$it ℃" } ?: "---")
                    val statusText = record.getVitalResults(context).joinToString("・") { it.first }
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
                is GlucoseAndHbA1c -> {
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_GLUCOSE), value = record.glucose?.let { "$it mg/dL" } ?: "---")
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_HBA1C), value = record.hba1c?.let { "$it %" } ?: "---")
                    val statusText = record.getCombinedResultText(context)
                    DetailItem(label = stringResource(HealthThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
