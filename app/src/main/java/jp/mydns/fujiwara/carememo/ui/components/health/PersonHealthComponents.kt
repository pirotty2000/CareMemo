package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：PersonHealthComponents
 *
 * 【役割】
 * 健康記録（身長・体重、バイタル、血糖値・HbA1c）に関連する履歴リストのアイテム表示、
 * および詳細表示・編集用の共通パーツ群を提供します。
 *
 * 【主な機能】
 * ・履歴内容の描画（HealthHistoryItemBody）：カテゴリに応じた表示内容の動的な切り替えと異常値判定の可視化。
 * ・詳細パネル（HealthRecordDetailPane）：閲覧と編集のモード管理、変更検知による中断保護、入力値のバリデーション。
 * ・閲覧表示（HealthRecordDisplayCard）：判定結果や単位を付与したレスポンシブな詳細レイアウト。
 * ・編集フォーム（HealthRecordEditForm）：数値入力に最適化したキーボード制御とリアルタイムバリデーション。
 *
 * 【想定する利用場所】
 * ・PersonHealthScreenContent（健康記録画面のメイン領域）
 *
 * 【このコンポーネントでは行わないこと】
 * ・グラフの描画（HealthGraphView / LineChart が担当）。
 * ・データベースへの直接アクセス（ViewModel 経由でラムダとして操作を受け取る）。
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
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
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
import java.time.Instant

/**
 * 全体像：健康管理（Health）
 *
 * ■ ui/screens/health/PersonHealthScreenContent.kt の PersonHealthScreenContent (画面全体の器)
 * │
 * ├─【左側 / 上部：履歴セクション】
 * │  └─ ■ ui/components/common/HistoryComponents.kt の PersonHistoryList (共通履歴リストの枠)
 * │       └─ [1] HealthHistoryItemBody (履歴1行分の要約：PersonHealthComponents.kt)
 * │            ├─ [1-1] HeightWeightRecordItemContent (身長・体重の要約)
 * │            ├─ [1-2] VitalRecordItemContent (バイタルの要約)
 * │            │    └─ [1-2-1] VitalStatusIndicator (状態インジケーター)
 * │            └─ [1-3] GlucoseRecordItemContent (血糖値の要約)
 * │
 * └─【右側 / 詳細：詳細・編集セクション】
 *      └─ [2] HealthRecordDetailPane (詳細・編集パネル：PersonHealthComponents.kt)
 *           │
 *           ├─ [2-1] HealthRecordEditForm (【編集モード】入力フォーム)
 *           │    ├─ DateTimeInputFields (日時入力)
 *           │    ├─ <カテゴリ別入力> AppCompactTextField (各項目：数値入力)
 *           │    └─ <アクション> キャンセルボタン、保存ボタン
 *           │
 *           └─ [2-2] HealthRecordDisplayCard (【閲覧モード】詳細表示用)
 *                ├─ <ヘッダー> 戻るボタン、タイトル、編集開始ボタン
 *                └─ [2-2-1] HealthDetailContent (カテゴリ別詳細表示)
 *                     ├─ [2-2-1-1] HeightWeightDetailContent ─ DetailRow (身長/体重/BMI)
 *                     ├─ [2-2-1-2] VitalDetailContent ─ DetailRow (血圧/SAT/脈拍/体温)
 *                     └─ [2-2-1-3] GlucoseDetailContent ─ DetailRow (血糖値/HbA1c)
 */

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [1]HealthHistoryItemBody
 * 健康記録のカテゴリに応じて、履歴リストの「中身」を出し分ける分岐用コンポーネント。
 *
 * @param category 表示対象のカテゴリ
 * @param record 履歴レコード
 */
@Composable
fun HealthHistoryItemBody(category: Category, record: HistoryRecord) {
    when (category) {
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalRecordItemContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseRecordItemContent(it) }
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightRecordItemContent(it) }
        else -> { /* 健康カテゴリ以外はここでは扱わない */ }
    }
}

/**
 * [1-1]HeightWeightRecordItemContent
 * 「身長・体重」記録の履歴アイテム表示。
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

            Text(text = "($bmiLabel)", style = bmiLabelStyle, color = bmiColor, fontWeight =
                if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/**
 * [1-2]VitalRecordItemContent
 * 「バイタル」記録の履歴アイテム表示。
 * 複数の指標を1行にまとめ、下部に異常値判定インジケーターを配置します。
 */
@Composable
private fun VitalRecordItemContent(record: BpAndPulse) {
    // 全指標の判定結果を一括取得
    val results = HealthLogic.evaluateVitalItems(record.bpSystolic, record.bpDiastolic, record.sat, record.pulse, record.bodyTemperature)
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    // 各判定ラベルの取得
    val highBpLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.HIGH_BP))
    val lowBpLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.LOW_BP))
    val tachycardiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.TACHYCARDIA))
    val bradycardiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.BRADYCARDIA))
    val respiratoryFailureLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.LOW_SAT))
    val feverLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.FEVER))
    val hypothermiaLabel = stringResource(HealthDisplayMapper.getVitalLabel(VitalStatus.HYPOTHERMIA))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 血圧(上/下)
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatBpValue(record.bpSystolic)}/${HealthLogic.formatBpValue(record.bpDiastolic)} ${AppSpecifications.Health.BloodPressure.UNIT}", style = textStyle)

            Spacer(modifier = Modifier.width(8.dp))

            // 酸素飽和度（SpO2）
            Text(text = "${HealthLogic.formatSat(record.sat)} ${AppSpecifications.Health.OxygenSaturation.UNIT}", style = textStyle)

            Spacer(modifier = Modifier.width(8.dp))

            // 脈拍
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatPulse(record.pulse)} ${AppSpecifications.Health.Pulse.UNIT}", style = textStyle)

            Spacer(modifier = Modifier.width(8.dp))

            // 体温
            Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${HealthLogic.formatBodyTemp(record.bodyTemperature)} ${AppSpecifications.Health.BodyTemperature.UNIT}", style = textStyle)
        }

        // 異常値判定インジケータ（該当する項目のみ強調表示）
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
 * 「バイタル」記録の異常値判定インジケーター（各指標ごとのフラグ）。
 */
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
 * [1-3]GlucoseRecordItemContent
 * 「血糖値・HbA1c」記録の履歴アイテム表示。
 */
@Composable
private fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 血糖値
        Text(text = "${stringResource(R.string.health_label_glucose)}: ${record.glucose?.let { "${HealthLogic.formatGlucose(it)} ${AppSpecifications.Health.BloodGlucose.UNIT}" } ?: "---"}", style = textStyle)
        if (record.glucose != null) {
            val (status, alertLevel) = HealthLogic.evaluateGlucose(record.glucose)
            val gColor = alertLevel.getDisplayColor()
            val gLabel = status?.let { stringResource(HealthDisplayMapper.getGlucoseLabel(it)!!) } ?: ""
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($gLabel)", style = statusLabelStyle, color = gColor, fontWeight = if (alertLevel != HealthAlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(modifier = Modifier.width(8.dp))
        // HbA1c
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
 * @param category 対象のカテゴリ
 * @param recordId 対象のレコードID（新規なら IdLogic.isNew が true）
 * @param records 履歴リスト（新規作成時の最新値引き継ぎ等に使用）
 * @param onCancel 閲覧モードの終了、または編集のキャンセル時のコールバック
 * @param onSaveRecord 保存実行時のコールバック
 */
@Composable
fun HealthRecordDetailPane(
    category: Category,
    recordId: String?,
    records: List<HistoryRecord>,
    onCancel: () -> Unit,
    onSaveRecord: (Category, String, Instant, Map<String, Any?>) -> Unit,
) {
    // recordId ごとにキーを振り、ID変更時に状態をリセットする
    key(recordId) {
        val record = remember(records, recordId) {
            if (recordId == null || IdLogic.isNew(recordId)) null
            else when (category) {
                Category.HEIGHT_AND_WEIGHT -> records.asSequence().filterIsInstance<HeightAndWeight>().find { it.id == recordId }
                Category.BP_AND_PULSE -> records.asSequence().filterIsInstance<BpAndPulse>().find { it.id == recordId }
                Category.GLUCOSE_AND_HBA1C -> records.asSequence().filterIsInstance<GlucoseAndHbA1c>().find { it.id == recordId }
                else -> null
            }
        }

        if (record == null && recordId != null && !IdLogic.isNew(recordId)) {
            LoadingScreen(modifier = Modifier.testTag("HealthDetail_Loading"))
        } else {
            // 新規作成時は編集モードから開始
            var isEditing by remember(recordId) {
                mutableStateOf(recordId != null && IdLogic.isNew(recordId))
            }
            val dateTimeState = rememberDateTimeInputState(initialInstant = record?.recordTime)

            // 各入力項目の状態管理
            var heightText by remember(recordId, record) {
                val initialValue = if (record is HeightAndWeight) {
                    record.height?.toString() ?: ""
                } else if (recordId != null && IdLogic.isNew(recordId) && category == Category.HEIGHT_AND_WEIGHT) {
                    // 【UX向上】身長の最新値がある場合は、それをデフォルト値として引き継ぐ
                    records.filterIsInstance<HeightAndWeight>()
                        .filter { it.height != null }
                        .maxByOrNull { it.recordTime }?.height?.toString() ?: ""
                } else {
                    ""
                }
                mutableStateOf(initialValue)
            }
            var weightText by remember(recordId, record) { mutableStateOf(if (record is HeightAndWeight) record.weight?.toString() ?: "" else "") }
            var bpSystolicText by remember(recordId, record) { mutableStateOf(if (record is BpAndPulse) record.bpSystolic?.toString() ?: "" else "") }
            var bpDiastolicText by remember(recordId, record) { mutableStateOf(if (record is BpAndPulse) record.bpDiastolic?.toString() ?: "" else "") }
            var satText by remember(recordId, record) { mutableStateOf(if (record is BpAndPulse) record.sat?.toString() ?: "" else "") }
            var pulseText by remember(recordId, record) { mutableStateOf(if (record is BpAndPulse) record.pulse?.toString() ?: "" else "") }
            var bodyTemperatureText by remember(recordId, record) { mutableStateOf(if (record is BpAndPulse) record.bodyTemperature?.toString() ?: "" else "") }
            var glucoseText by remember(recordId, record) { mutableStateOf(if (record is GlucoseAndHbA1c) record.glucose?.toString() ?: "" else "") }
            var hba1cText by remember(recordId, record) { mutableStateOf(if (record is GlucoseAndHbA1c) record.hba1c?.toString() ?: "" else "") }

            // 【重要】変更検知用の初期スナップショット
            // 入力を途中で破棄しようとした際の警告ダイアログ判定に使用
            val initialHeightSnapshot = remember(recordId, record, isEditing) { heightText }
            val initialWeightSnapshot = remember(recordId, record, isEditing) { weightText }
            val initialBpSystolicSnapshot = remember(recordId, record, isEditing) { bpSystolicText }
            val initialBpDiastolicSnapshot = remember(recordId, record, isEditing) { bpDiastolicText }
            val initialSatSnapshot = remember(recordId, record, isEditing) { satText }
            val initialPulseSnapshot = remember(recordId, record, isEditing) { pulseText }
            val initialBodyTempSnapshot = remember(recordId, record, isEditing) { bodyTemperatureText }
            val initialGlucoseSnapshot = remember(recordId, record, isEditing) { glucoseText }
            val initialHbA1cSnapshot = remember(recordId, record, isEditing) { hba1cText }
            val initialDateTimeSnapshot = remember(recordId, record, isEditing) { dateTimeState.toInstant() }

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

            // システム戻るボタンによる破棄保護
            androidx.activity.compose.BackHandler(enabled = isEditing && isChanged) {
                showDiscardDialog = true
            }

            // 変更破棄の最終確認ダイアログ
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
                // [2-1] HealthRecordEditForm (記録の編集)
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
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
                            category = category,
                            recordId = recordId ?: "",
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
                                    if (recordId != null && !IdLogic.isNew(recordId)) isEditing = false else onCancel()
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
                                    onSaveRecord(category, recordId ?: "", recordTime, values)
                                    isEditing = false
                                }
                            },
                            isChanged = isChanged
                        )
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                    VerticalScrollIndicator(scrollState = scrollState)
                }
            } else {
                // [2-2] HealthRecordDisplayCard (記録の閲覧)
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
 * [2-1] HealthRecordEditForm
 * 健康記録の入力フォーム。
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

    // 入力値のリアルタイム検証（保存ボタンの活性制御に使用）
    val validationResult = remember(
        category, heightText, weightText, bpSystolicText, bpDiastolicText, satText, pulseText, bodyTemperatureText, glucoseText, hba1cText) {
        val values = when (category) {
            Category.HEIGHT_AND_WEIGHT -> mapOf("height" to heightText, "weight" to weightText)
            Category.BP_AND_PULSE -> mapOf(
                "bpSystolic" to bpSystolicText,
                "bpDiastolic" to bpDiastolicText,
                "sat" to satText,
                "pulse" to pulseText,
                "bodyTemperature" to bodyTemperatureText
            )
            Category.GLUCOSE_AND_HBA1C -> mapOf("glucose" to glucoseText, "hba1c" to hba1cText)
            else -> emptyMap()
        }
        PersonHealthLogic.validateInputs(category, values)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = if (IdLogic.isNew(recordId)) "新規作成" else "記録の編集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor =
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 入力フォームの記録日時
                DateTimeInputFields(state = dateTimeState)
                
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                
                // カテゴリ別の入力フィールド
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (category) {
                        // 身長・体重
                        Category.HEIGHT_AND_WEIGHT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = heightText, onValueChange = onHeightChange, type = AppTextFieldType.DECIMAL, label =
                                    { Text(stringResource(R.string.health_label_height)) }, suffix = { Text(AppSpecifications.Health.Height.UNIT) }, modifier = Modifier.weight(1f))
                                AppCompactTextField(value = weightText, onValueChange = onWeightChange, type = AppTextFieldType.DECIMAL, label =
                                    { Text(stringResource(R.string.health_label_weight)) }, suffix = { Text(AppSpecifications.Health.Weight.UNIT) }, modifier = Modifier.weight(1f),
                                    imeAction = ImeAction.Done)
                            }
                        }
                        // バイタル
                        Category.BP_AND_PULSE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = bpSystolicText, onValueChange = onBpSystolicChange, type = AppTextFieldType.INTEGER, label =
                                    { Text(stringResource(R.string.health_label_bp_systolic)) }, modifier = Modifier.weight(1f).testTag("HealthField_BpSystolic"))
                                AppCompactTextField(value = bpDiastolicText, onValueChange = onBpDiastolicChange, type = AppTextFieldType.INTEGER, label =
                                    { Text(stringResource(R.string.health_label_bp_diastolic)) }, modifier = Modifier.weight(1f).testTag("HealthField_BpDiastolic"))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(value = satText, onValueChange = onSatChange, type = AppTextFieldType.INTEGER, label =
                                    { Text(stringResource(R.string.health_label_sat)) }, suffix = { Text(AppSpecifications.Health.OxygenSaturation.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Sat"))
                                AppCompactTextField(value = pulseText, onValueChange = onPulseChange, type = AppTextFieldType.INTEGER, label =
                                    { Text(stringResource(R.string.health_label_pulse)) }, suffix = { Text(AppSpecifications.Health.Pulse.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Pulse"))
                            }
                            AppCompactTextField(value = bodyTemperatureText, onValueChange = onBodyTemperatureChange, type = AppTextFieldType.DECIMAL, label =
                                { Text(stringResource(R.string.health_label_body_temp)) }, suffix = { Text(AppSpecifications.Health.BodyTemperature.UNIT) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_Temp"), imeAction = ImeAction.Done)
                        }
                        // 血糖値・HbA1c
                        Category.GLUCOSE_AND_HBA1C -> {
                            AppCompactTextField(value = glucoseText, onValueChange = onGlucoseChange, type = AppTextFieldType.INTEGER, label =
                                { Text(stringResource(R.string.health_label_glucose)) }, suffix = { Text(AppSpecifications.Health.BloodGlucose.UNIT) },
                                modifier = Modifier.fillMaxWidth())
                            AppCompactTextField(value = hba1cText, onValueChange = onHba1cChange, type = AppTextFieldType.DECIMAL, label =
                                { Text(stringResource(R.string.health_label_hba1c)) }, suffix = { Text(AppSpecifications.Health.HbA1c.UNIT) },
                                modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done)
                        }
                        else -> {}
                    }
                    
                    // アクションボタン（キャンセル・保存）
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).testTag("HealthField_CancelButton")) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("HealthField_SaveButton"),
                            enabled = (validationResult == HealthInputValidationResult.SUCCESS) && isDateTimeValid && isChanged
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }
    }
}

/**
 * [2-2] HealthRecordDisplayCard
 * 健康記録の詳細閲覧用カード。
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
            // ヘッダー部：戻るボタン、タイトル、編集ボタン
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

            // 内容カード
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    record?.let { r ->
                        Text(
                            text = DateTimeUtils.formatRecordTime(r.recordTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // [2-2-1] HealthDetailContent
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
 * カテゴリに応じた詳細表示の分岐用コンポーネント。
 */
@Composable
private fun HealthDetailContent(category: Category, record: HistoryRecord) {
    when (category) {
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightDetailContent(it) }
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalDetailContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseDetailContent(it) }
        else -> {}
    }
}

/**
 * [2-2-1-1] HeightWeightDetailContent
 * 「身長・体重」記録の詳細表示。
 */
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

/**
 * [2-2-1-2] VitalDetailContent
 * 「バイタル」記録の詳細表示。
 */
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

/**
 * [2-2-1-3] GlucoseDetailContent
 * 「血糖値・HbA1c」記録の詳細表示。
 */
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

/**
 * [2-2-1-*-1] DetailRow
 * 詳細表示画面における1行分のラベルと値のセットを描画します。
 */
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
