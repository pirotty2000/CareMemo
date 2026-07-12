package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：PersonHealthComponents
 *
 * 【役割】：
 * 健康記録（身長・体重、バイタル、血糖値・HbA1c）に関連する履歴リスト、履歴アイテム、
 * および詳細表示・編集用の共通パーツ群を提供する。
 *
 * 【主な機能】：
 * ・履歴リスト（PersonHistoryList）：時系列データのグルーピング表示とスワイプ削除、選択状態の管理。
 * ・履歴内容の描画（HealthHistoryItemBody）：カテゴリに応じた表示内容の動的な切り替え。
 * ・詳細表示（HealthRecordDisplayCard）：選択した記録の全項目をカード形式で詳細表示。
 * ・詳細編集（HealthRecordDetailPane）：新規登録および既存記録の編集用フォームの提供。
 *
 * 【想定する利用場所】：
 * ・PersonHealthScreenContent（健康記録のメインコンテンツ領域）
 */

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import java.time.Instant


/**
 * 詳細表示カード内の各項目（ラベルと値のペア）を描画する補助コンポーネント。
 * ラベルを左側に控えめに、値を right 側に強調して配置する。
 */
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


/**
 * 健康記録のカテゴリに応じて、履歴リストの「中身」を出し分ける分岐用コンポーネント
 */
@Composable
fun HealthHistoryItemBody(category: Category, record: HistoryRecord) {
    when (category) {
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalRecordItemContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseRecordItemContent(it) }
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightRecordItemContent(it) }
        else -> { /* カテゴリA以外はここでは扱わない */ }
    }
}

/**
 * 身長・体重記録の履歴アイテム表示
 */
@Composable
private fun HeightWeightRecordItemContent(record: HeightAndWeight) {
    val context = LocalContext.current
    val bmi = record.calculateBMI()
    val textStyle = MaterialTheme.typography.labelMedium
    val bmiLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        // --- 身長セクション ---
        Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.height?.let { "${AppThresholds.formatHeight(it)}${AppThresholds.UNIT_HEIGHT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        // --- 体重セクション ---
        Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.weight?.let { "${AppThresholds.formatWeight(it)}${AppThresholds.UNIT_WEIGHT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        // --- BMIセクション ---
        Text(text = "${stringResource(AppThresholds.HEALTH_LABEL_BMI)}: ${AppThresholds.formatBmi(bmi)}", style = textStyle)
        if (bmi > 0) {
            val (bmiLabel, alertLevel) = record.getBmiResult(context)
            Spacer(modifier = Modifier.width(2.dp))
            val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)
            val bmiColor = when {
                alertLevel == AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                bmi < AppThresholds.BMI_NORMAL_LOW -> MaterialTheme.colorScheme.error
                else -> warningColor
            }
            Text(text = "($bmiLabel)", style = bmiLabelStyle, color = bmiColor, fontWeight = if (alertLevel != AppThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val context = LocalContext.current
    val (gStatus, gLevel) = record.getGlucoseResult(context)
    val (hStatus, hLevel) = record.getHbA1cResult(context)
    val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE)}: ${record.glucose?.let { "${AppThresholds.formatGlucose(it)} ${AppThresholds.UNIT_GLUCOSE}" } ?: "---"}", style = textStyle)
        if (record.glucose != null) {
            val gColor = when (gLevel) {
                AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                AppThresholds.AlertLevel.WARNING -> warningColor
                else -> MaterialTheme.colorScheme.error
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($gStatus)", style = statusLabelStyle, color = gColor, fontWeight = if (gLevel != AppThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${stringResource(AppThresholds.HEALTH_LABEL_HBA1C)}: ${record.hba1c?.let { "${AppThresholds.formatHbA1c(it)}${AppThresholds.UNIT_HBA1C}" } ?: "---"}", style = textStyle)
        if (record.hba1c != null) {
            val hColor = when (hLevel) {
                AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                AppThresholds.AlertLevel.WARNING -> warningColor
                else -> MaterialTheme.colorScheme.error
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($hStatus)", style = statusLabelStyle, color = hColor, fontWeight = if (hLevel != AppThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun VitalRecordItemContent(record: BpAndPulse) {
    val context = LocalContext.current
    val results = record.getVitalResults(context)
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    val highBpLabel = stringResource(AppThresholds.VITAL_LABEL_HIGH_BP)
    val lowBpLabel = stringResource(AppThresholds.VITAL_LABEL_LOW_BP)
    val tachycardiaLabel = stringResource(AppThresholds.VITAL_LABEL_TACHYCARDIA)
    val bradycardiaLabel = stringResource(AppThresholds.VITAL_LABEL_BRADYCARDIA)
    val respiratoryFailureLabel = stringResource(AppThresholds.VITAL_LABEL_LOW_SAT)
    val feverLabel = stringResource(AppThresholds.VITAL_LABEL_FEVER)
    val hypothermiaLabel = stringResource(AppThresholds.VITAL_LABEL_HYPOTHERMIA)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${AppThresholds.formatBpValue(record.bpSystolic)}/${AppThresholds.formatBpValue(record.bpDiastolic)} ${AppThresholds.UNIT_BP}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            
            // SATセクション（アイコンなし）
            Text(text = "${AppThresholds.formatSat(record.sat)} ${AppThresholds.UNIT_SAT}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))

            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${AppThresholds.formatPulse(record.pulse)} ${AppThresholds.UNIT_PULSE}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${AppThresholds.formatBodyTemp(record.bodyTemperature)} ${AppThresholds.UNIT_BODY_TEMP}", style = textStyle)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VitalStatusIndicator(label = highBpLabel, isActive = results.any { it.first == highBpLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = lowBpLabel, isActive = results.any { it.first == lowBpLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = respiratoryFailureLabel, isActive = results.any { it.first == respiratoryFailureLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = tachycardiaLabel, isActive = results.any { it.first == tachycardiaLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = bradycardiaLabel, isActive = results.any { it.first == bradycardiaLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = feverLabel, isActive = results.any { it.first == feverLabel }, style = statusLabelStyle)
            VitalStatusIndicator(label = hypothermiaLabel, isActive = results.any { it.first == hypothermiaLabel }, style = statusLabelStyle)
        }
    }
}

@Composable
private fun VitalStatusIndicator(label: String, isActive: Boolean, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall) {
    Text(text = label, style = style.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal), color = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}


/**
 * 健康記録の詳細・編集パネル。
 */
@Composable
fun HealthRecordDetailPane(
    personId: Int,
    category: Category,
    recordId: Int,
    records: List<HistoryRecord>,
    onCancel: () -> Unit,
    onSaveRecord: (Any) -> Unit,
) {
    val record = remember(records, recordId) {
        when (category) {
            Category.HEIGHT_AND_WEIGHT -> records.asSequence().filterIsInstance<HeightAndWeight>().find { it.id == recordId }
            Category.BP_AND_PULSE -> records.asSequence().filterIsInstance<BpAndPulse>().find { it.id == recordId }
            Category.GLUCOSE_AND_HBA1C -> records.asSequence().filterIsInstance<GlucoseAndHbA1c>().find { it.id == recordId }
            else -> null
        }
    }

    if (record == null && recordId != 0) {
        LoadingScreen(modifier = Modifier.testTag("HealthDetail_Loading"))
        return
    }

    var isEditing by remember(recordId) { mutableStateOf(recordId == 0) }
    val dateTimeState = rememberDateTimeInputState(initialInstant = record?.recordTime)

    var heightText by remember(recordId, category, records) {
        val initialValue = if (record is HeightAndWeight) {
            record.height?.toString() ?: ""
        } else if (recordId == 0 && category == Category.HEIGHT_AND_WEIGHT) {
            records.filterIsInstance<HeightAndWeight>()
                .filter { it.height != null }
                .maxByOrNull { it.recordTime }?.height?.toString() ?: ""
        } else {
            ""
        }
        mutableStateOf(initialValue)
    }
    var weightText by remember(recordId) { mutableStateOf(if (record is HeightAndWeight) record.weight?.toString() ?: "" else "") }
    var bpSystolicText by remember(recordId) { mutableStateOf(if (record is BpAndPulse) record.bpSystolic?.toString() ?: "" else "") }
    var bpDiastolicText by remember(recordId) { mutableStateOf(if (record is BpAndPulse) record.bpDiastolic?.toString() ?: "" else "") }
    var satText by remember(recordId) { mutableStateOf(if (record is BpAndPulse) record.sat?.toString() ?: "" else "") }
    var pulseText by remember(recordId) { mutableStateOf(if (record is BpAndPulse) record.pulse?.toString() ?: "" else "") }
    var bodyTemperatureText by remember(recordId) { mutableStateOf(if (record is BpAndPulse) record.bodyTemperature?.toString() ?: "" else "") }
    var glucoseText by remember(recordId) { mutableStateOf(if (record is GlucoseAndHbA1c) record.glucose?.toString() ?: "" else "") }
    var hba1cText by remember(recordId) { mutableStateOf(if (record is GlucoseAndHbA1c) record.hba1c?.toString() ?: "" else "") }

    // 変更検知用の初期状態
    val initialDateTime = remember(recordId) { record?.recordTime }
    val initialHeight = remember(recordId, category, records) {
        if (record is HeightAndWeight) {
            record.height?.toString() ?: ""
        } else if (recordId == 0 && category == Category.HEIGHT_AND_WEIGHT) {
            records.filterIsInstance<HeightAndWeight>()
                .filter { it.height != null }
                .maxByOrNull { it.recordTime }?.height?.toString() ?: ""
        } else {
            ""
        }
    }
    val initialWeight = remember(recordId) { if (record is HeightAndWeight) record.weight?.toString() ?: "" else "" }
    val initialBpSystolic = remember(recordId) { if (record is BpAndPulse) record.bpSystolic?.toString() ?: "" else "" }
    val initialBpDiastolic = remember(recordId) { if (record is BpAndPulse) record.bpDiastolic?.toString() ?: "" else "" }
    val initialSat = remember(recordId) { if (record is BpAndPulse) record.sat?.toString() ?: "" else "" }
    val initialPulse = remember(recordId) { if (record is BpAndPulse) record.pulse?.toString() ?: "" else "" }
    val initialBodyTemp = remember(recordId) { if (record is BpAndPulse) record.bodyTemperature?.toString() ?: "" else "" }
    val initialGlucose = remember(recordId) { if (record is GlucoseAndHbA1c) record.glucose?.toString() ?: "" else "" }
    val initialHbA1c = remember(recordId) { if (record is GlucoseAndHbA1c) record.hba1c?.toString() ?: "" else "" }

    val isChanged by remember(heightText, weightText, bpSystolicText, bpDiastolicText, satText, pulseText, bodyTemperatureText, glucoseText, hba1cText, dateTimeState.year.value, dateTimeState.month.value, dateTimeState.day.value, dateTimeState.hour.value, dateTimeState.minute.value) {
        derivedStateOf {
            heightText != initialHeight || weightText != initialWeight ||
            bpSystolicText != initialBpSystolic || bpDiastolicText != initialBpDiastolic ||
            satText != initialSat || pulseText != initialPulse || bodyTemperatureText != initialBodyTemp ||
            glucoseText != initialGlucose || hba1cText != initialHbA1c ||
            dateTimeState.toInstant() != initialDateTime
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    // システム戻るボタンの制御
    androidx.activity.compose.BackHandler(enabled = isEditing && isChanged) {
        showDiscardDialog = true
    }

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
                        onCancel()
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

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().testTag("HealthRecordDetailPane")) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (recordId == 0) "新規作成" else "記録の詳細", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!isEditing && recordId != 0) {
                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Rounded.EditNote, contentDescription = "編集") }
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
                    satText = satText, onSatChange = { satText = it },
                    pulseText = pulseText, onPulseChange = { pulseText = it },
                    bodyTemperatureText = bodyTemperatureText, onBodyTemperatureChange = { bodyTemperatureText = it },
                    glucoseText = glucoseText, onGlucoseChange = { glucoseText = it },
                    hba1cText = hba1cText, onHba1cChange = { hba1cText = it },
                    onCancel = {
                        if (isChanged) showDiscardDialog = true else onCancel()
                    },
                    onSave = {
                        dateTimeState.toInstant()?.let { recordTime ->
                            val newRecord: Any = when (category) {
                                Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = recordId, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull(), recordTime = recordTime)
                                Category.BP_AND_PULSE -> BpAndPulse(id = recordId, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), sat = satText.toIntOrNull(), pulse = pulseText.toIntOrNull(), bodyTemperature = bodyTemperatureText.toDoubleOrNull(), recordTime = recordTime)
                                Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = recordId, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                                else -> throw IllegalStateException("Not supported category")
                            }
                            onSaveRecord(newRecord)
                        }
                    }
                )
            } else {
                HealthRecordDisplayCard(record = record!!)
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * 各カテゴリに応じた具体的な入力項目を提供するフォーム。
 */
@Composable
private fun HealthRecordEditForm(
    category: Category,
    dateTimeState: DateTimeInputState,
    heightText: String, onHeightChange: (String) -> Unit,
    weightText: String, onWeightChange: (String) -> Unit,
    bpSystolicText: String, onBpSystolicChange: (String) -> Unit,
    bpDiastolicText: String, onBpDiastolicChange: (String) -> Unit,
    satText: String, onSatChange: (String) -> Unit,
    pulseText: String, onPulseChange: (String) -> Unit,
    bodyTemperatureText: String, onBodyTemperatureChange: (String) -> Unit,
    glucoseText: String, onGlucoseChange: (String) -> Unit,
    hba1cText: String, onHba1cChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val isDateTimeValid by remember(dateTimeState) { derivedStateOf { dateTimeState.toInstant() != null } }

    OutlinedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeInputFields(state = dateTimeState)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (category) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(value = heightText, onValueChange = onHeightChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_HEIGHT)) }, suffix = { Text(AppThresholds.UNIT_HEIGHT) }, modifier = Modifier.weight(1f))
                            AppCompactTextField(value = weightText, onValueChange = onWeightChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_WEIGHT)) }, suffix = { Text(AppThresholds.UNIT_WEIGHT) }, modifier = Modifier.weight(1f), imeAction = ImeAction.Done)
                        }
                    }
                    Category.BP_AND_PULSE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(value = bpSystolicText, onValueChange = onBpSystolicChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BP_SYSTOLIC)) }, modifier = Modifier.weight(1f))
                            AppCompactTextField(value = bpDiastolicText, onValueChange = onBpDiastolicChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BP_DIASTOLIC)) }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppCompactTextField(value = satText, onValueChange = onSatChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_SAT)) }, suffix = { Text(AppThresholds.UNIT_SAT) }, modifier = Modifier.weight(1f))
                            AppCompactTextField(value = pulseText, onValueChange = onPulseChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_PULSE)) }, suffix = { Text(AppThresholds.UNIT_PULSE) }, modifier = Modifier.weight(1f))
                        }
                        AppCompactTextField(value = bodyTemperatureText, onValueChange = onBodyTemperatureChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BODY_TEMP)) }, suffix = { Text(AppThresholds.UNIT_BODY_TEMP) }, modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done)
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        AppCompactTextField(value = glucoseText, onValueChange = onGlucoseChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE)) }, suffix = { Text(AppThresholds.UNIT_GLUCOSE) }, modifier = Modifier.fillMaxWidth())
                        AppCompactTextField(value = hba1cText, onValueChange = onHba1cChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(AppThresholds.HEALTH_LABEL_HBA1C)) }, suffix = { Text(AppThresholds.UNIT_HBA1C) }, modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done)
                    }
                    else -> {}
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = (when (category) {
                            Category.HEIGHT_AND_WEIGHT -> AppThresholds.isValidHeightAndWeight(heightText, weightText)
                            Category.BP_AND_PULSE -> AppThresholds.isValidBpAndPulse(bpSystolicText, bpDiastolicText, satText, pulseText, bodyTemperatureText)
                            Category.GLUCOSE_AND_HBA1C -> AppThresholds.isValidGlucoseAndHbA1c(glucoseText, hba1cText)
                            else -> true
                        }) && isDateTimeValid
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }
}

/**
 * 登録済みの記録内容を表示する詳細ビュー。
 */
@Composable
private fun HealthRecordDisplayCard(record: Any) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = formatRecordTime(when (record) { is HeightAndWeight -> record.recordTime; is BpAndPulse -> record.recordTime; is GlucoseAndHbA1c -> record.recordTime; else -> Instant.now() }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            when (record) {
                is HeightAndWeight -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_HEIGHT), value = "${AppThresholds.formatHeight(record.height)} ${AppThresholds.UNIT_HEIGHT}")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_WEIGHT), value = "${AppThresholds.formatWeight(record.weight)} ${AppThresholds.UNIT_WEIGHT}")
                    val bmi = record.calculateBMI()
                    if (bmi > 0) {
                        val (resId, _) = AppThresholds.evaluateBMI(bmi)
                        val label = resId?.let { stringResource(it) } ?: "---"
                        DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BMI), value = "${AppThresholds.formatBmi(bmi)} ($label)")
                    }
                }
                is BpAndPulse -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BP), value = "${AppThresholds.formatBpValue(record.bpSystolic)} / ${AppThresholds.formatBpValue(record.bpDiastolic)} ${AppThresholds.UNIT_BP}")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_SAT), value = "${AppThresholds.formatSat(record.sat)} ${AppThresholds.UNIT_SAT}")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_PULSE), value = "${AppThresholds.formatPulse(record.pulse)} ${AppThresholds.UNIT_PULSE}")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BODY_TEMP), value = "${AppThresholds.formatBodyTemp(record.bodyTemperature)} ${AppThresholds.UNIT_BODY_TEMP}")
                    val statusText = record.getVitalResults(context).joinToString("・") { it.first }
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
                is GlucoseAndHbA1c -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE), value = "${AppThresholds.formatGlucose(record.glucose)} ${AppThresholds.UNIT_GLUCOSE}")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_HBA1C), value = "${AppThresholds.formatHbA1c(record.hba1c)} ${AppThresholds.UNIT_HBA1C}")
                    val statusText = record.getCombinedResultText(context)
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
            }
        }
    }
}
