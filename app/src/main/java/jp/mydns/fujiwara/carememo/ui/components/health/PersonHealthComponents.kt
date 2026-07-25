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
 * ・詳細編集（HealthRecordDetailPane）：新規登録および既存記録の編集用フォームの提供。
 *
 * 【想定する利用場所】：
 * ・PersonHealthScreenContent（健康記録のメインコンテンツ領域）
 */

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.common.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthLogic
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import jp.mydns.fujiwara.carememo.ui.theme.getDisplayColor
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils


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
    val bmi = record.calculateBMI()
    val textStyle = MaterialTheme.typography.labelMedium
    val bmiLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        // --- 身長セクション ---
        Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.height?.let { "${HealthLogic.formatHeight(it)}${AppSpecifications.Health.Height.UNIT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        // --- 体重セクション ---
        Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.weight?.let { "${HealthLogic.formatWeight(it)}${AppSpecifications.Health.Weight.UNIT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        // --- BMIセクション ---
        Text(text = "${stringResource(R.string.health_label_bmi)}: ${HealthLogic.formatBmi(bmi)}", style = textStyle)
        if (bmi > 0) {
            val (status, alertLevel) = HealthLogic.evaluateBMI(bmi)
            val bmiLabel = status?.let { stringResource(HealthDisplayMapper.getBmiLabel(it)!!) } ?: "---"
            Spacer(modifier = Modifier.width(2.dp))
            val bmiColor = alertLevel.getDisplayColor()
            
            Text(text = "($bmiLabel)", style = bmiLabelStyle, color = bmiColor, fontWeight = if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${stringResource(R.string.health_label_glucose)}: ${record.glucose?.let { "${HealthLogic.formatGlucose(it)} ${AppSpecifications.Health.BloodGlucose.UNIT}" } ?: "---"}", style = textStyle)
        if (record.glucose != null) {
            val (status, alertLevel) = HealthLogic.evaluateGlucose(record.glucose)
            val gColor = alertLevel.getDisplayColor()
            val gLabel = status?.let { stringResource(HealthDisplayMapper.getGlucoseLabel(it)!!) } ?: ""
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($gLabel)", style = statusLabelStyle, color = gColor, fontWeight = if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${stringResource(R.string.health_label_hba1c)}: ${record.hba1c?.let { "${HealthLogic.formatHbA1c(it)}${AppSpecifications.Health.HbA1c.UNIT}" } ?: "---"}", style = textStyle)
        if (record.hba1c != null) {
            val (status, alertLevel) = HealthLogic.evaluateHbA1c(record.hba1c)
            val hColor = alertLevel.getDisplayColor()
            val hLabel = status?.let { stringResource(HealthDisplayMapper.getHbA1cLabel(it)!!) } ?: ""
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($hLabel)", style = statusLabelStyle, color = hColor, fontWeight = if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun VitalRecordItemContent(record: BpAndPulse) {
    val results = HealthLogic.evaluateVitalItems(record.bpSystolic, record.bpDiastolic, record.sat, record.pulse, record.bodyTemperature)
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    val highBpLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.HIGH_BP))
    val lowBpLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.LOW_BP))
    val tachycardiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.TACHYCARDIA))
    val bradycardiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.BRADYCARDIA))
    val respiratoryFailureLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.LOW_SAT))
    val feverLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.FEVER))
    val hypothermiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.HYPOTHERMIA))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatBpValue(record.bpSystolic)}/${HealthLogic.formatBpValue(record.bpDiastolic)} ${AppSpecifications.Health.BloodPressure.UNIT}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            
            // SATセクション（アイコンなし）
            Text(text = "${HealthLogic.formatSat(record.sat)} ${AppSpecifications.Health.OxygenSaturation.UNIT}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))

            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatPulse(record.pulse)} ${AppSpecifications.Health.Pulse.UNIT}", style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatBodyTemp(record.bodyTemperature)} ${AppSpecifications.Health.BodyTemperature.UNIT}", style = textStyle)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            VitalStatusIndicator(label = highBpLabel, isActive = results.any { it.first == VitalStatus.HIGH_BP }, style = statusLabelStyle)
            VitalStatusIndicator(label = lowBpLabel, isActive = results.any { it.first == VitalStatus.LOW_BP }, style = statusLabelStyle)
            VitalStatusIndicator(label = respiratoryFailureLabel, isActive = results.any { it.first == VitalStatus.LOW_SAT }, style = statusLabelStyle)
            VitalStatusIndicator(label = tachycardiaLabel, isActive = results.any { it.first == VitalStatus.TACHYCARDIA }, style = statusLabelStyle)
            VitalStatusIndicator(label = bradycardiaLabel, isActive = results.any { it.first == VitalStatus.BRADYCARDIA }, style = statusLabelStyle)
            VitalStatusIndicator(label = feverLabel, isActive = results.any { it.first == VitalStatus.FEVER }, style = statusLabelStyle)
            VitalStatusIndicator(label = hypothermiaLabel, isActive = results.any { it.first == VitalStatus.HYPOTHERMIA }, style = statusLabelStyle)
        }
    }
}

@Composable
private fun VitalStatusIndicator(label: String, isActive: Boolean, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall) {
    val level = HealthDisplayMapper.getVitalIndicatorLevel(isActive)
    val color = level.getDisplayColor()
    Text(
        text = label, 
        style = style.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal
        ), 
        color = color
    )
}


/**
 * 健康記録の詳細・編集パネル。
 */
@Composable
fun HealthRecordDetailPane(
    isExpanded: Boolean,
    personId: String,
    category: Category,
    recordId: String,
    records: List<HistoryRecord>,
    onCancel: () -> Unit,
    onSaveRecord: (Any) -> Unit,
) {
    key(recordId) {
        val record = remember(records, recordId) {
            if (PersonHealthLogic.isNew(recordId)) null
            else when (category) {
                Category.HEIGHT_AND_WEIGHT -> records.asSequence().filterIsInstance<HeightAndWeight>().find { it.id == recordId }
                Category.BP_AND_PULSE -> records.asSequence().filterIsInstance<BpAndPulse>().find { it.id == recordId }
                Category.GLUCOSE_AND_HBA1C -> records.asSequence().filterIsInstance<GlucoseAndHbA1c>().find { it.id == recordId }
                else -> null
            }
        }

        if (record == null && !PersonHealthLogic.isNew(recordId)) {
            LoadingScreen(modifier = Modifier.testTag("HealthDetail_Loading"))
        } else {
            // Tablet モード (isExpanded=true) かつ 既存レコードの場合は閲覧モード (isEditing=false) から開始。
            // Phone モード、または新規作成 ID の場合は常に編集モードから開始。
            var isEditing by remember(recordId) { 
                mutableStateOf(!isExpanded || PersonHealthLogic.isNew(recordId)) 
            }
            val dateTimeState = rememberDateTimeInputState(initialInstant = record?.recordTime)

            var heightText by remember(recordId) {
                val initialValue = if (record is HeightAndWeight) {
                    record.height?.toString() ?: ""
                } else if (PersonHealthLogic.isNew(recordId) && category == Category.HEIGHT_AND_WEIGHT) {
                    // 身長の最新値引き継ぎロジック
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
            val initialHeightSnapshot = remember(recordId) { heightText }
            val initialWeightSnapshot = remember(recordId) { weightText }
            val initialBpSystolicSnapshot = remember(recordId) { bpSystolicText }
            val initialBpDiastolicSnapshot = remember(recordId) { bpDiastolicText }
            val initialSatSnapshot = remember(recordId) { satText }
            val initialPulseSnapshot = remember(recordId) { pulseText }
            val initialBodyTempSnapshot = remember(recordId) { bodyTemperatureText }
            val initialGlucoseSnapshot = remember(recordId) { glucoseText }
            val initialHbA1cSnapshot = remember(recordId) { hba1cText }
            val initialDateTimeSnapshot = remember(recordId) { dateTimeState.toInstant() }

            val isChanged by remember(heightText, weightText, bpSystolicText, bpDiastolicText, satText, pulseText, bodyTemperatureText, glucoseText, hba1cText, dateTimeState.year.value, dateTimeState.month.value, dateTimeState.day.value, dateTimeState.hour.value, dateTimeState.minute.value) {
                derivedStateOf {
                    heightText != initialHeightSnapshot || weightText != initialWeightSnapshot ||
                    bpSystolicText != initialBpSystolicSnapshot || bpDiastolicText != initialBpDiastolicSnapshot ||
                    satText != initialSatSnapshot || pulseText != initialPulseSnapshot || 
                    bodyTemperatureText != initialBodyTempSnapshot ||
                    glucoseText != initialGlucoseSnapshot || hba1cText != initialHbA1cSnapshot ||
                    dateTimeState.toInstant() != initialDateTimeSnapshot
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

            if (isEditing) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxSize().testTag("HealthRecordDetailPane")) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HealthRecordEditForm(
                            category = category,
                            recordId = recordId,
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
                                if (isChanged) {
                                    showDiscardDialog = true
                                } else {
                                    if (isExpanded && !PersonHealthLogic.isNew(recordId)) {
                                        isEditing = false
                                    } else {
                                        onCancel()
                                    }
                                }
                            },
                            onSave = {
                                dateTimeState.toInstant()?.let { recordTime ->
                                    val values = when (category) {
                                        Category.HEIGHT_AND_WEIGHT -> mapOf("height" to heightText.toDoubleOrNull(), "weight" to weightText.toDoubleOrNull())
                                        Category.BP_AND_PULSE -> mapOf("bpSystolic" to bpSystolicText.toIntOrNull(), "bpDiastolic" to bpDiastolicText.toIntOrNull(), "sat" to satText.toIntOrNull(), "pulse" to pulseText.toIntOrNull(), "bodyTemperature" to bodyTemperatureText.toDoubleOrNull())
                                        Category.GLUCOSE_AND_HBA1C -> mapOf("glucose" to glucoseText.toIntOrNull(), "hba1c" to hba1cText.toDoubleOrNull())
                                        else -> emptyMap()
                                    }
                                    val newRecord = PersonHealthLogic.createEntity(category, personId, recordId, recordTime, values)
                                    onSaveRecord(newRecord)
                                }
                            },
                            isChanged = isChanged
                        )
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                    VerticalScrollIndicator(scrollState = scrollState)
                }
            } else {
                HealthRecordDisplayCard(
                    category = category,
                    record = record,
                    onCancel = onCancel,
                    onEditClick = { isEditing = true }
                )
            }
        }
    }
}

/**
 * 健康記録の詳細表示カード。
 */
@Composable
private fun HealthRecordDisplayCard(
    category: Category,
    record: HistoryRecord?,
    onCancel: () -> Unit,
    onEditClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().testTag("HealthRecordDisplayCard")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                    Text(
                        text = "記録の詳細",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    record?.let { r ->
                        Text(
                            text = DateTimeUtils.formatRecordTime(r.recordTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        HealthDetailContent(category, r)
                    }
                }
            }
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

@Composable
private fun HealthDetailContent(category: Category, record: HistoryRecord) {
    when (category) {
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightDetailContent(it) }
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalDetailContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseDetailContent(it) }
        else -> {}
    }
}

@Composable
private fun HeightWeightDetailContent(record: HeightAndWeight) {
    val bmi = record.calculateBMI()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow(
            label = stringResource(R.string.health_label_height),
            value = record.height?.let { HealthLogic.formatHeight(it) } ?: "---",
            unit = AppSpecifications.Health.Height.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_weight),
            value = record.weight?.let { HealthLogic.formatWeight(it) } ?: "---",
            unit = AppSpecifications.Health.Weight.UNIT
        )
        if (bmi > 0) {
            val (status, alertLevel) = HealthLogic.evaluateBMI(bmi)
            val bmiLabel = status?.let { stringResource(HealthDisplayMapper.getBmiLabel(it)!!) } ?: "---"
            DetailRow(
                label = stringResource(R.string.health_label_bmi),
                value = "${HealthLogic.formatBmi(bmi)} ($bmiLabel)",
                color = alertLevel.getDisplayColor(),
                isBold = alertLevel != HealthAlertLevel.NORMAL
            )
        }
    }
}

@Composable
private fun VitalDetailContent(record: BpAndPulse) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow(
            label = stringResource(R.string.health_label_bp_systolic),
            value = HealthLogic.formatBpValue(record.bpSystolic),
            unit = AppSpecifications.Health.BloodPressure.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_bp_diastolic),
            value = HealthLogic.formatBpValue(record.bpDiastolic),
            unit = AppSpecifications.Health.BloodPressure.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_sat),
            value = HealthLogic.formatSat(record.sat),
            unit = AppSpecifications.Health.OxygenSaturation.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_pulse),
            value = HealthLogic.formatPulse(record.pulse),
            unit = AppSpecifications.Health.Pulse.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_body_temp),
            value = HealthLogic.formatBodyTemp(record.bodyTemperature),
            unit = AppSpecifications.Health.BodyTemperature.UNIT
        )
    }
}

@Composable
private fun GlucoseDetailContent(record: GlucoseAndHbA1c) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow(
            label = stringResource(R.string.health_label_glucose),
            value = record.glucose?.let { HealthLogic.formatGlucose(it) } ?: "---",
            unit = AppSpecifications.Health.BloodGlucose.UNIT
        )
        DetailRow(
            label = stringResource(R.string.health_label_hba1c),
            value = record.hba1c?.let { HealthLogic.formatHbA1c(it) } ?: "---",
            unit = AppSpecifications.Health.HbA1c.UNIT
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, unit: String = "", color: Color = Color.Unspecified, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = color
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 各カテゴリに応じた具体的な入力項目を提供するフォーム。
 */
@Composable
private fun HealthRecordEditForm(
    category: Category,
    recordId: String,
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
    onSave: () -> Unit,
    isChanged: Boolean
) {
    val isDateTimeValid by remember(dateTimeState) { derivedStateOf { dateTimeState.toInstant() != null } }
    val isNew = remember(recordId) { PersonHealthLogic.isNew(recordId) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = if (PersonHealthLogic.isNew(recordId)) "新規作成" else "記録の編集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeInputFields(state = dateTimeState)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (category) {
                        Category.HEIGHT_AND_WEIGHT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = heightText, onValueChange = onHeightChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(R.string.health_label_height)) }, suffix = { Text(AppSpecifications.Health.Height.UNIT) }, modifier = Modifier.weight(1f))
                                AppCompactTextField(value = weightText, onValueChange = onWeightChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(R.string.health_label_weight)) }, suffix = { Text(AppSpecifications.Health.Weight.UNIT) }, modifier = Modifier.weight(1f), imeAction = ImeAction.Done)
                            }
                        }
                        Category.BP_AND_PULSE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = bpSystolicText, onValueChange = onBpSystolicChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(R.string.health_label_bp_systolic)) }, modifier = Modifier.weight(1f).testTag("HealthField_BpSystolic"))
                                AppCompactTextField(value = bpDiastolicText, onValueChange = onBpDiastolicChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(R.string.health_label_bp_diastolic)) }, modifier = Modifier.weight(1f).testTag("HealthField_BpDiastolic"))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = satText, onValueChange = onSatChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(R.string.health_label_sat)) }, suffix = { Text(AppSpecifications.Health.OxygenSaturation.UNIT) }, modifier = Modifier.weight(1f).testTag("HealthField_Sat"))
                                AppCompactTextField(value = pulseText, onValueChange = onPulseChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(R.string.health_label_pulse)) }, suffix = { Text(AppSpecifications.Health.Pulse.UNIT) }, modifier = Modifier.weight(1f).testTag("HealthField_Pulse"))
                            }
                            AppCompactTextField(value = bodyTemperatureText, onValueChange = onBodyTemperatureChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(R.string.health_label_body_temp)) }, suffix = { Text(AppSpecifications.Health.BodyTemperature.UNIT) }, modifier = Modifier.fillMaxWidth().testTag("HealthField_Temp"), imeAction = ImeAction.Done)
                        }
                        Category.GLUCOSE_AND_HBA1C -> {
                            AppCompactTextField(value = glucoseText, onValueChange = onGlucoseChange, type = AppTextFieldType.INTEGER, label = { Text(stringResource(R.string.health_label_glucose)) }, suffix = { Text(AppSpecifications.Health.BloodGlucose.UNIT) }, modifier = Modifier.fillMaxWidth())
                            AppCompactTextField(value = hba1cText, onValueChange = onHba1cChange, type = AppTextFieldType.DECIMAL, label = { Text(stringResource(R.string.health_label_hba1c)) }, suffix = { Text(AppSpecifications.Health.HbA1c.UNIT) }, modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done)
                        }
                        else -> {}
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).testTag("HealthField_CancelButton")) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("HealthField_SaveButton"),
                            enabled = (when (category) {
                                Category.HEIGHT_AND_WEIGHT -> HealthLogic.isValidHeightAndWeight(heightText, weightText)
                                Category.BP_AND_PULSE -> HealthLogic.isValidBpAndPulse(bpSystolicText, bpDiastolicText, satText, pulseText, bodyTemperatureText)
                                Category.GLUCOSE_AND_HBA1C -> HealthLogic.isValidGlucoseAndHbA1c(glucoseText, hba1cText)
                                else -> true
                            }) && isDateTimeValid && (isNew || isChanged)
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }
    }
}


