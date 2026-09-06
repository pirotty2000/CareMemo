package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：PersonHealthComponents
 *
 * 【役割】
 * 健康記録（身長・体重、バイタル、血糖値・HbA1c）に関連する履歴リストのアイテム表示、
 * および詳細表示・編集用の共通パーツ群を提供します。
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
import androidx.compose.ui.draw.alpha
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
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthOperation
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import jp.mydns.fujiwara.carememo.ui.theme.getDisplayColor
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.ui.screens.health.PersonHealthUiAction
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import java.time.Instant

/**
 * [1]HealthHistoryItemBody
 * 健康記録のカテゴリに応じて、履歴リストの「中身」を出し分ける分岐用コンポーネント。
 */
@Composable
fun HealthHistoryItemBody(
    category: Category,
    record: HistoryRecord,
    modifier: Modifier = Modifier
) {
    when (category) {
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalRecordItemContent(it, modifier) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseRecordItemContent(it, modifier) }
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightRecordItemContent(it, modifier) }
        else -> { /* 健康カテゴリ以外はここでは扱わない */ }
    }
}

/**
 * [1-1]HeightWeightRecordItemContent
 */
@Composable
private fun HeightWeightRecordItemContent(
    record: HeightAndWeight,
    modifier: Modifier = Modifier
) {
    val bmi = record.calculateBMI()
    val textStyle = MaterialTheme.typography.labelMedium
    val bmiLabelStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.height?.let { "${HealthLogic.formatHeight(it)}${AppSpecifications.Health.Height.UNIT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.weight?.let { "${HealthLogic.formatWeight(it)}${AppSpecifications.Health.Weight.UNIT}" } ?: "---", style = textStyle)
        Spacer(modifier = Modifier.width(8.dp))

        Text(text = "${stringResource(R.string.health_label_bmi)}: ${HealthLogic.formatBmi(bmi)}", style = textStyle)
        if (bmi > 0) {
            val (status, alertLevel) = HealthLogic.evaluateBMI(bmi)
            val bmiLabel = status?.let { stringResource(HealthDisplayMapper.getBmiLabel(it)!!) } ?: "---"
            Spacer(modifier = Modifier.width(2.dp))
            val bmiColor = alertLevel.getDisplayColor()

            Text(text = "($bmiLabel)", style = bmiLabelStyle, color = bmiColor, fontWeight =
                if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/**
 * [1-2]VitalRecordItemContent
 */
@Composable
private fun VitalRecordItemContent(
    record: BpAndPulse,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatBpValue(record.bpSystolic)}/${HealthLogic.formatBpValue(record.bpDiastolic)} ${AppSpecifications.Health.BloodPressure.UNIT}", style = textStyle)

            Spacer(modifier = Modifier.width(8.dp))
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

/**
 * [1-2-1]VitalStatusIndicator
 */
@Composable
private fun VitalStatusIndicator(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall
) {
    val level = HealthDisplayMapper.getVitalIndicatorLevel(isActive)
    val color = level.getDisplayColor()
    Text(
        text = label,
        modifier = modifier,
        style = style.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal
        ),
        color = color
    )
}

/**
 * [1-3]GlucoseRecordItemContent
 */
@Composable
private fun GlucoseRecordItemContent(
    record: GlucoseAndHbA1c,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [2]HealthRecordDetailPane
 * 健康記録の詳細表示と編集モードを管理する最上位コンポーネント。
 *
 * @param uiState UI 状態
 * @param onAction アクションハンドラ
 * @param modifier 修飾子
 */
@Composable
fun HealthRecordDetailPane(
    uiState: PersonHealthUiState,
    onAction: (PersonHealthUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = uiState.editSession
    val record = remember(uiState.records, session.selectedRecordId) {
        if (session.selectedRecordId == null || IdLogic.isNew(session.selectedRecordId)) null
        else when (uiState.currentCategory) {
            Category.HEIGHT_AND_WEIGHT -> uiState.records.filterIsInstance<HeightAndWeight>()
                .find { it.id == session.selectedRecordId }
            Category.BP_AND_PULSE -> uiState.records.filterIsInstance<BpAndPulse>()
                .find { it.id == session.selectedRecordId }
            Category.GLUCOSE_AND_HBA1C -> uiState.records.filterIsInstance<GlucoseAndHbA1c>()
                .find { it.id == session.selectedRecordId }
            else -> null
        }
    }

    if (record == null && session.selectedRecordId != null && !IdLogic.isNew(session.selectedRecordId)) {
        LoadingScreen(modifier = modifier.testTag("HealthDetail_Loading"))
    } else {
        var showDiscardDialog by remember { mutableStateOf(false) }

        androidx.activity.compose.BackHandler(enabled = session.isEditing && session.isChanged) {
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
                            onAction(PersonHealthUiAction.SelectedRecordIdChanged(null))
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

        if (session.isEditing) {
            val scrollState = rememberScrollState()
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
                    .testTag("HealthRecordDetailPane")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HealthRecordEditForm(
                        category = uiState.currentCategory,
                        operation = uiState.operation,
                        recordId = session.selectedRecordId ?: "",
                        editInput = session.editInput,
                        initialRecordTime = session.initialRecordTime,
                        isSaveEnabled = session.isSaveEnabled,
                        fieldErrors = session.fieldErrors,
                        fieldErrorArgs = session.fieldErrorArgs,
                        onAction = onAction,
                        onCancelRequest = {
                            if (session.isChanged) {
                                showDiscardDialog = true
                            } else {
                                onAction(PersonHealthUiAction.CancelEdit)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(80.dp))
                }
                VerticalScrollIndicator(scrollState = scrollState)
            }
        } else {
            HealthRecordDisplayCard(
                category = uiState.currentCategory,
                operation = uiState.operation,
                record = record,
                onAction = onAction,
                modifier = modifier
            )
        }
    }
}

/**
 * [2-1] HealthRecordEditForm
 */
@Composable
private fun HealthRecordEditForm(
    category: Category,
    operation: PersonHealthOperation,
    recordId: String,
    editInput: HealthEditInput,
    initialRecordTime: Instant?,
    isSaveEnabled: Boolean,
    fieldErrors: Map<String, Int?>,
    fieldErrorArgs: Map<String, List<String>>,
    onAction: (PersonHealthUiAction) -> Unit,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateTimeState = rememberDateTimeInputState(initialInstant = initialRecordTime)
    val isOperating = operation != PersonHealthOperation.Idle

    LaunchedEffect(
        dateTimeState.year.value,
        dateTimeState.month.value,
        dateTimeState.day.value,
        dateTimeState.hour.value,
        dateTimeState.minute.value
    ) {
        val nextTime = dateTimeState.toInstant()
        if (nextTime != editInput.recordTime) {
            onAction(PersonHealthUiAction.EditInputUpdate { it.copy(recordTime = nextTime) })
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (IdLogic.isNew(recordId)) stringResource(R.string.common_create_new) else stringResource(R.string.common_edit_record),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val recordTimeError = fieldErrors["recordTime"]
                DateTimeInputFields(
                    state = dateTimeState,
                    enabled = !isOperating,
                    isError = recordTimeError != null,
                    supportingText = if (recordTimeError != null) {
                        { Text(stringResource(recordTimeError)) }
                    } else null,
                    onFocusChanged = { _: String, _: Boolean -> onAction(PersonHealthUiAction.MarkFieldAsTouched("recordTime")) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (category) {
                        Category.HEIGHT_AND_WEIGHT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(
                                    value = editInput.heightText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(heightText = v) }) },
                                    type = AppTextFieldType.DECIMAL,
                                    label = { Text(stringResource(R.string.health_label_height)) },
                                    suffix = { Text(AppSpecifications.Health.Height.UNIT) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["height"] != null,
                                    supportingText = fieldErrors["height"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["height"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("height")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Height")
                                )
                                AppCompactTextField(
                                    value = editInput.weightText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(weightText = v) }) },
                                    type = AppTextFieldType.DECIMAL,
                                    label = { Text(stringResource(R.string.health_label_weight)) },
                                    suffix = { Text(AppSpecifications.Health.Weight.UNIT) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["weight"] != null,
                                    supportingText = fieldErrors["weight"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["weight"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("weight")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Weight"),
                                    imeAction = ImeAction.Done
                                )
                            }
                        }
                        Category.BP_AND_PULSE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(
                                    value = editInput.bpSystolicText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(bpSystolicText = v) }) },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["bpSystolic"] != null,
                                    supportingText = fieldErrors["bpSystolic"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["bpSystolic"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("bpSystolic")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_BpSystolic")
                                )
                                AppCompactTextField(
                                    value = editInput.bpDiastolicText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(bpDiastolicText = v) }) },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["bpDiastolic"] != null,
                                    supportingText = fieldErrors["bpDiastolic"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["bpDiastolic"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("bpDiastolic")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_BpDiastolic")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(
                                    value = editInput.satText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(satText = v) }) },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_sat)) },
                                    suffix = { Text(AppSpecifications.Health.OxygenSaturation.UNIT) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["sat"] != null,
                                    supportingText = fieldErrors["sat"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["sat"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("sat")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Sat")
                                )
                                AppCompactTextField(
                                    value = editInput.pulseText,
                                    onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(pulseText = v) }) },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_pulse)) },
                                    suffix = { Text(AppSpecifications.Health.Pulse.UNIT) },
                                    enabled = !isOperating,
                                    isError = fieldErrors["pulse"] != null,
                                    supportingText = fieldErrors["pulse"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["pulse"]?.toTypedArray() ?: emptyArray())) } },
                                    onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("pulse")) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Pulse")
                                )
                            }
                            AppCompactTextField(
                                value = editInput.bodyTemperatureText,
                                onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(bodyTemperatureText = v) }) },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_body_temp)) },
                                suffix = { Text(AppSpecifications.Health.BodyTemperature.UNIT) },
                                enabled = !isOperating,
                                isError = fieldErrors["bodyTemperature"] != null,
                                supportingText = fieldErrors["bodyTemperature"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["bodyTemperature"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("bodyTemperature")) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_Temp"),
                                imeAction = ImeAction.Done
                            )
                        }
                        Category.GLUCOSE_AND_HBA1C -> {
                            AppCompactTextField(
                                value = editInput.glucoseText,
                                onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(glucoseText = v) }) },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_glucose)) },
                                suffix = { Text(AppSpecifications.Health.BloodGlucose.UNIT) },
                                enabled = !isOperating,
                                isError = fieldErrors["glucose"] != null,
                                supportingText = fieldErrors["glucose"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["glucose"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("glucose")) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_Glucose")
                            )
                            AppCompactTextField(
                                value = editInput.hba1cText,
                                onValueChange = { v -> onAction(PersonHealthUiAction.EditInputUpdate { it.copy(hba1cText = v) }) },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_hba1c)) },
                                suffix = { Text(AppSpecifications.Health.HbA1c.UNIT) },
                                enabled = !isOperating,
                                isError = fieldErrors["hba1c"] != null,
                                supportingText = fieldErrors["hba1c"]?.let { resId -> { Text(stringResource(resId, *fieldErrorArgs["hba1c"]?.toTypedArray() ?: emptyArray())) } },
                                onFocusChanged = { if (!it.isFocused) onAction(PersonHealthUiAction.MarkFieldAsTouched("hba1c")) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_HbA1c"),
                                imeAction = ImeAction.Done
                            )
                        }
                        else -> {}
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancelRequest,
                            enabled = !isOperating,
                            modifier = Modifier.weight(1f).testTag("HealthField_CancelButton")
                        ) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = { onAction(PersonHealthUiAction.SaveClick) },
                            modifier = Modifier.weight(1f).testTag("HealthField_SaveButton"),
                            enabled = isSaveEnabled && !isOperating
                        ) {
                            if (operation is PersonHealthOperation.Saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.common_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [2-2] HealthRecordDisplayCard
 */
@Composable
private fun HealthRecordDisplayCard(
    category: Category,
    operation: PersonHealthOperation,
    record: HistoryRecord?,
    onAction: (PersonHealthUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("HealthRecordDisplayCard")
    ) {
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
                        onClick = { onAction(PersonHealthUiAction.SelectedRecordIdChanged(null)) },
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                    Text(
                        text = stringResource(R.string.common_record_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }
                IconButton(
                    onClick = { onAction(PersonHealthUiAction.EditClick) },
                    enabled = operation == PersonHealthOperation.Idle
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.common_edit))
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

/**
 * [2-2-1] HealthDetailContent
 */
@Composable
private fun HealthDetailContent(
    category: Category,
    record: HistoryRecord,
    modifier: Modifier = Modifier
) {
    when (category) {
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightDetailContent(it, modifier) }
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalDetailContent(it, modifier) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseDetailContent(it, modifier) }
        else -> {}
    }
}

/**
 * [2-2-1-1] HeightWeightDetailContent
 */
@Composable
private fun HeightWeightDetailContent(
    record: HeightAndWeight,
    modifier: Modifier = Modifier
) {
    val bmi = record.calculateBMI()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                value = stringResource(R.string.health_item_bmi_format, HealthLogic.formatBmi(bmi), bmiLabel),
                color = alertLevel.getDisplayColor(),
                isBold = alertLevel != HealthAlertLevel.NORMAL
            )
        }
    }
}

/**
 * [2-2-1-2] VitalDetailContent
 */
@Composable
private fun VitalDetailContent(
    record: BpAndPulse,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

/**
 * [2-2-1-3] GlucoseDetailContent
 */
@Composable
private fun GlucoseDetailContent(
    record: GlucoseAndHbA1c,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

/**
 * [2-2-1-*-1] DetailRow
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    color: Color = Color.Unspecified,
    isBold: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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
