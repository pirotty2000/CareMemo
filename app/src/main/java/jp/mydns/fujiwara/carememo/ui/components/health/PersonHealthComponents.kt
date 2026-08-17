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
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import jp.mydns.fujiwara.carememo.ui.theme.getDisplayColor
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
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
 *           │    ├─ DateTimeInputFields (日時入力：ui/components/common/DateTimeInputFields.kt)
 *           │    ├─ <カテゴリ別入力> AppCompactTextField (各項目：ui/components/base/AppCompactTextField.kt)
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
 * @param modifier 修飾子
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
 * 「身長・体重」記録の履歴アイテム表示。
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
private fun VitalRecordItemContent(
    record: BpAndPulse,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
 * 「血糖値・HbA1c」記録の履歴アイテム表示。
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
 * @param uiState UI 状態
 * @param onCancel 閲覧モードの終了（詳細ペインを閉じる）のコールバック
 * @param onEditClick 編集モードへの移行
 * @param onEditInputUpdate 入力値の更新
 * @param onSaveClick 保存の実行
 * @param onCancelEdit 編集のキャンセル（警告付き）のコールバック
 */
@Composable
fun HealthRecordDetailPane(
    uiState: PersonHealthUiState,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onEditClick: () -> Unit,
    onEditInputUpdate: ((HealthEditInput) -> HealthEditInput) -> Unit,
    onSaveClick: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val record = remember(uiState.records, uiState.selectedRecordId) {
        if (uiState.selectedRecordId == null || IdLogic.isNew(uiState.selectedRecordId)) null
        else when (uiState.currentCategory) {
            Category.HEIGHT_AND_WEIGHT -> uiState.records.filterIsInstance<HeightAndWeight>()
                .find { it.id == uiState.selectedRecordId }
            Category.BP_AND_PULSE -> uiState.records.filterIsInstance<BpAndPulse>()
                .find { it.id == uiState.selectedRecordId }
            Category.GLUCOSE_AND_HBA1C -> uiState.records.filterIsInstance<GlucoseAndHbA1c>()
                .find { it.id == uiState.selectedRecordId }
            else -> null
        }
    }

    if (record == null && uiState.selectedRecordId != null && !IdLogic.isNew(uiState.selectedRecordId)) {
        LoadingScreen(modifier = modifier.testTag("HealthDetail_Loading"))
    } else {
        var showDiscardDialog by remember { mutableStateOf(false) }

        // システム戻るボタンによる破棄保護
        androidx.activity.compose.BackHandler(enabled = uiState.isEditing && uiState.isChanged) {
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

        if (uiState.isEditing) {
            // [2-1] HealthRecordEditForm (記録の編集)
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
                        recordId = uiState.selectedRecordId ?: "",
                        editInput = uiState.editInput,
                        initialRecordTime = uiState.initialRecordTime,
                        isSaveEnabled = uiState.isSaveEnabled,
                        onEditInputUpdate = onEditInputUpdate,
                        onCancel = {
                            if (uiState.isChanged) {
                                showDiscardDialog = true
                            } else {
                                onCancelEdit()
                            }
                        },
                        onSave = onSaveClick
                    )
                    Spacer(modifier = Modifier.height(80.dp))
                }
                VerticalScrollIndicator(scrollState = scrollState)
            }
        } else {
            // [2-2] HealthRecordDisplayCard (記録の閲覧)
            HealthRecordDisplayCard(
                category = uiState.currentCategory,
                record = record,
                modifier = modifier,
                onCancel = onCancel,
                onEditClick = onEditClick
            )
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
    editInput: HealthEditInput,
    initialRecordTime: Instant?,
    isSaveEnabled: Boolean,
    onEditInputUpdate: ((HealthEditInput) -> HealthEditInput) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateTimeState = rememberDateTimeInputState(initialInstant = initialRecordTime)

    // 日時状態を ViewModel へ同期
    LaunchedEffect(
        dateTimeState.year.value,
        dateTimeState.month.value,
        dateTimeState.day.value,
        dateTimeState.hour.value,
        dateTimeState.minute.value
    ) {
        val nextTime = dateTimeState.toInstant()
        if (nextTime != editInput.recordTime) {
            onEditInputUpdate { it.copy(recordTime = nextTime) }
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
                containerColor =
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        ) {
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
                                AppCompactTextField(
                                    value = editInput.heightText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(heightText = v) } },
                                    type = AppTextFieldType.DECIMAL,
                                    label = { Text(stringResource(R.string.health_label_height)) },
                                    suffix = { Text(AppSpecifications.Health.Height.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Height")
                                )
                                AppCompactTextField(
                                    value = editInput.weightText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(weightText = v) } },
                                    type = AppTextFieldType.DECIMAL,
                                    label = { Text(stringResource(R.string.health_label_weight)) },
                                    suffix = { Text(AppSpecifications.Health.Weight.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Weight"),
                                    imeAction = ImeAction.Done
                                )
                            }
                        }
                        // バイタル
                        Category.BP_AND_PULSE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(
                                    value = editInput.bpSystolicText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(bpSystolicText = v) } },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_bp_systolic)) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_BpSystolic")
                                )
                                AppCompactTextField(
                                    value = editInput.bpDiastolicText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(bpDiastolicText = v) } },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_bp_diastolic)) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_BpDiastolic")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppCompactTextField(
                                    value = editInput.satText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(satText = v) } },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_sat)) },
                                    suffix = { Text(AppSpecifications.Health.OxygenSaturation.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Sat")
                                )
                                AppCompactTextField(
                                    value = editInput.pulseText,
                                    onValueChange = { v -> onEditInputUpdate { it.copy(pulseText = v) } },
                                    type = AppTextFieldType.INTEGER,
                                    label = { Text(stringResource(R.string.health_label_pulse)) },
                                    suffix = { Text(AppSpecifications.Health.Pulse.UNIT) },
                                    modifier = Modifier.weight(1f).testTag("HealthField_Pulse")
                                )
                            }
                            AppCompactTextField(
                                value = editInput.bodyTemperatureText,
                                onValueChange = { v -> onEditInputUpdate { it.copy(bodyTemperatureText = v) } },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_body_temp)) },
                                suffix = { Text(AppSpecifications.Health.BodyTemperature.UNIT) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_Temp"),
                                imeAction = ImeAction.Done
                            )
                        }
                        // 血糖値・HbA1c
                        Category.GLUCOSE_AND_HBA1C -> {
                            AppCompactTextField(
                                value = editInput.glucoseText,
                                onValueChange = { v -> onEditInputUpdate { it.copy(glucoseText = v) } },
                                type = AppTextFieldType.INTEGER,
                                label = { Text(stringResource(R.string.health_label_glucose)) },
                                suffix = { Text(AppSpecifications.Health.BloodGlucose.UNIT) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_Glucose")
                            )
                            AppCompactTextField(
                                value = editInput.hba1cText,
                                onValueChange = { v -> onEditInputUpdate { it.copy(hba1cText = v) } },
                                type = AppTextFieldType.DECIMAL,
                                label = { Text(stringResource(R.string.health_label_hba1c)) },
                                suffix = { Text(AppSpecifications.Health.HbA1c.UNIT) },
                                modifier = Modifier.fillMaxWidth().testTag("HealthField_HbA1c"),
                                imeAction = ImeAction.Done
                            )
                        }
                        else -> {}
                    }

                    // アクションボタン（キャンセル・保存）
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).testTag("HealthField_CancelButton")
                        ) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("HealthField_SaveButton"),
                            enabled = isSaveEnabled
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
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onEditClick: () -> Unit,
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
                        text = stringResource(R.string.common_record_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.common_edit))
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
 * 「身長・体重」記録の詳細表示。
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
 * 「バイタル」記録の詳細表示。
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
 * 「血糖値・HbA1c」記録の詳細表示。
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
 * 詳細表示画面における1行分のラベルと値のセットを描画します。
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
