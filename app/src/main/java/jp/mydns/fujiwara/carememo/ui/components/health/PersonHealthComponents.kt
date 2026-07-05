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
 *
 * 【このコンポーネントでは行わないこと】：
 * ・データベースへの直接アクセス（すべて引数またはラムダ経由で外部から操作）
 * ・画面全体のスクロール管理（個別の部品内でのスクロールや LazyColumn に限定）
 *
 * 【公開composable】：
 * ・PersonHistoryList
 * ・HealthHistoryItemBody
 * ・HealthRecordDetailPane
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.common.DetailItem
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import java.time.Instant


/**
 * 健康記録のカテゴリに応じて、履歴リストの「中身」を出し分ける分岐用コンポーネント
 *
 * 【役割】：
 * 抽象的な記録データ（HistoryRecord）を、具体的なカテゴリ（血圧、血糖値、身長体重）
 * 専用の表示コンポーネントへ安全にキャストして橋渡しを行う。
 *
 * 【描画の割り当て】：
 * ・血圧・脈拍・体温　　 -> [VitalRecordItemContent]
 * ・血糖値・HbA1c　　　　-> [GlucoseRecordItemContent]
 * ・身長・体重・BMI　　 -> [HeightWeightRecordItemContent]
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
 *
 * 【構成】：身長アイコン + 身長値 | 体重アイコン + 体重値 | BMI値 (判定ラベル)
 */
@Composable
private fun HeightWeightRecordItemContent(record: HeightAndWeight) {
    val context = LocalContext.current
    val bmi = record.calculateBMI()

    // 文字サイズの切り替え用
    // val textStyle = MaterialTheme.typography.labelSmall
    val textStyle = MaterialTheme.typography.labelMedium
    // val textStyle = MaterialTheme.typography.labelLarge

    // BMI判定ラベルのスタイル切り替え用
    // val bmiLabelStyle = MaterialTheme.typography.labelSmall
    val bmiLabelStyle = MaterialTheme.typography.labelMedium
    // val bmiLabelStyle = MaterialTheme.typography.labelLarge

    Row(verticalAlignment = Alignment.CenterVertically) {
        // --- 身長セクション ---
        Icon(
            Icons.Rounded.Height,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.height?.let { "${it}cm" } ?: "---",
            style = textStyle
        )

        Spacer(modifier = Modifier.width(8.dp))

        // --- 体重セクション ---
        Icon(
            Icons.Rounded.Scale,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.weight?.let { "${it}kg" } ?: "---",
            style = textStyle
        )

        Spacer(modifier = Modifier.width(8.dp))

        // --- BMIセクション ---
        Text(
            text = "${stringResource(AppThresholds.HEALTH_LABEL_BMI)}: ${if (bmi > 0) "%.1f".format(bmi) else "---"}",
            style = textStyle
        )
        if (bmi > 0) {
            val (bmiLabel, alertLevel) = record.getBmiResult(context)
            Spacer(modifier = Modifier.width(2.dp))
            
            // 注意（Warning）用のオレンジ色
            val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)

            // BMI固有の配色ロジック
            val bmiColor = when {
                alertLevel == AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                bmi < AppThresholds.BMI_NORMAL_LOW -> MaterialTheme.colorScheme.error // 低体重は error
                else -> warningColor // 肥満(1〜4度)は オレンジ
            }

            // 判定結果ラベル (痩せすぎ/普通/肥満など)
            Text(
                text = "($bmiLabel)",
                style = bmiLabelStyle,
                color = bmiColor
            )
        }
    }
}

@Composable
private fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val context = LocalContext.current
    val (gStatus, gLevel) = record.getGlucoseResult(context)
    val (hStatus, hLevel) = record.getHbA1cResult(context)

    // 注意（Warning）用のオレンジ色
    val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)

    // 文字サイズの切り替え用
    val textStyle = MaterialTheme.typography.labelMedium
    val statusLabelStyle = MaterialTheme.typography.labelMedium

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE)}: ${record.glucose?.let { "$it mg/dL" } ?: "---"}",
            style = textStyle
        )
        if (record.glucose != null) {
            val gColor = when (gLevel) {
                AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                AppThresholds.AlertLevel.WARNING -> warningColor
                else -> MaterialTheme.colorScheme.error
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($gStatus)",
                style = statusLabelStyle,
                color = gColor,
                fontWeight = if (gLevel != AppThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stringResource(AppThresholds.HEALTH_LABEL_HBA1C)}: ${record.hba1c?.let { "$it%" } ?: "---"}",
            style = textStyle
        )
        if (record.hba1c != null) {
            val hColor = when (hLevel) {
                AppThresholds.AlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                AppThresholds.AlertLevel.WARNING -> warningColor
                else -> MaterialTheme.colorScheme.error
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($hStatus)",
                style = statusLabelStyle,
                color = hColor,
                fontWeight = if (hLevel != AppThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun VitalRecordItemContent(record: BpAndPulse) {
    val context = LocalContext.current
    val results = record.getVitalResults(context)

    // 文字サイズの切り替え用
    // val textStyle = MaterialTheme.typography.labelSmall
    val textStyle = MaterialTheme.typography.labelMedium
    // val textStyle = MaterialTheme.typography.labelLarge

    // 判定ラベルのスタイル切り替え用
    // val statusLabelStyle = MaterialTheme.typography.labelSmall
    val statusLabelStyle = MaterialTheme.typography.labelMedium
    // val statusLabelStyle = MaterialTheme.typography.labelLarge

    val highBpLabel = stringResource(AppThresholds.VITAL_LABEL_HIGH_BP)
    val lowBpLabel = stringResource(AppThresholds.VITAL_LABEL_LOW_BP)
    val tachycardiaLabel = stringResource(AppThresholds.VITAL_LABEL_TACHYCARDIA)
    val bradycardiaLabel = stringResource(AppThresholds.VITAL_LABEL_BRADYCARDIA)
    val feverLabel = stringResource(AppThresholds.VITAL_LABEL_FEVER)
    val hypothermiaLabel = stringResource(AppThresholds.VITAL_LABEL_HYPOTHERMIA)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ---------- 実データ ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            // --- 血圧セクション ---
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg",
                style = textStyle
            )
            // --------------------
            Spacer(modifier = Modifier.width(12.dp))
            // --- 脈拍セクション ---
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.pulse ?: "---"} bpm", style = textStyle)
            // --------------------
            Spacer(modifier = Modifier.width(12.dp))
            // --- 体温セクション ---
            Icon(
                Icons.Rounded.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bodyTemperature?.let { "%.1f".format(it) } ?: "---"} ℃",
                style = textStyle
            )
        }
        // ---------- 判定結果 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VitalStatusIndicator(
                label = highBpLabel,
                isActive = results.any { it.first == highBpLabel },
                style = statusLabelStyle
            )
            VitalStatusIndicator(
                label = lowBpLabel,
                isActive = results.any { it.first == lowBpLabel },
                style = statusLabelStyle
            )
            VitalStatusIndicator(
                label = tachycardiaLabel,
                isActive = results.any { it.first == tachycardiaLabel },
                style = statusLabelStyle
            )
            VitalStatusIndicator(
                label = bradycardiaLabel,
                isActive = results.any { it.first == bradycardiaLabel },
                style = statusLabelStyle
            )
            VitalStatusIndicator(
                label = feverLabel,
                isActive = results.any { it.first == feverLabel },
                style = statusLabelStyle
            )
            VitalStatusIndicator(
                label = hypothermiaLabel,
                isActive = results.any { it.first == hypothermiaLabel },
                style = statusLabelStyle
            )
        }
    }
}

@Composable
private fun VitalStatusIndicator(
    label: String,
    isActive: Boolean,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall
) {
    Text(
        text = label,
        style = style.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal
        ),
        color = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
}


/**
 * 健康記録の詳細・編集パネル。
 *
 * 【役割】：
 * 選択された記録の「詳細表示」と、新規登録・修正のための「入力フォーム」を
 * 状態（isEditing）に応じて切り替えて提供する。
 *
 * 【表示モードの切り替え】：
 * ・新規作成時（recordId == 0）：最初から入力フォームを表示。
 * ・既存表示時（recordId != 0）：最初は詳細カードを表示し、編集ボタンでフォームへ移行。
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

    // 記録が見つからない場合の待機画面（新規作成時は除く）
    if (record == null && recordId != 0) {
        LoadingScreen()
        return
    }

    var isEditing by remember(recordId) { mutableStateOf(recordId == 0) }
    
    // 状態の初期化を LaunchedEffect から remember(recordId) に移動してブランキングを抑制
    val dateTimeState = rememberDateTimeInputState(initialInstant = record?.recordTime)

    var heightText by remember(recordId) {
        mutableStateOf(if (record is HeightAndWeight) record.height?.toString() ?: "" else "")
    }
    var weightText by remember(recordId) {
        mutableStateOf(if (record is HeightAndWeight) record.weight?.toString() ?: "" else "")
    }
    var bpSystolicText by remember(recordId) {
        mutableStateOf(if (record is BpAndPulse) record.bpSystolic?.toString() ?: "" else "")
    }
    var bpDiastolicText by remember(recordId) {
        mutableStateOf(if (record is BpAndPulse) record.bpDiastolic?.toString() ?: "" else "")
    }
    var pulseText by remember(recordId) {
        mutableStateOf(if (record is BpAndPulse) record.pulse?.toString() ?: "" else "")
    }
    var bodyTemperatureText by remember(recordId) {
        mutableStateOf(if (record is BpAndPulse) record.bodyTemperature?.toString() ?: "" else "")
    }
    var glucoseText by remember(recordId) {
        mutableStateOf(if (record is GlucoseAndHbA1c) record.glucose?.toString() ?: "" else "")
    }
    var hba1cText by remember(recordId) {
        mutableStateOf(if (record is GlucoseAndHbA1c) record.hba1c?.toString() ?: "" else "")
    }

    // 新規作成時の前回値補完ロジックのみ LaunchedEffect で継続（副作用のため）
    LaunchedEffect(recordId, category) {
        if (recordId == 0 && category == Category.HEIGHT_AND_WEIGHT) {
            val latestHeight = records.filterIsInstance<HeightAndWeight>()
                .filter { it.height != null }
                .maxByOrNull { it.recordTime }?.height
            if (latestHeight != null) {
                heightText = latestHeight.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 記録の詳細／新規作成・・・のタイトル
            Text(
                text = if (recordId == 0) "新規作成" else "記録の詳細",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            // 鉛筆アイコン
            if (!isEditing && recordId != 0) {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }
        }

        if (isEditing) {
            // ---------- 新規記録の入力 / 既存記録の編集 の画面 ----------
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
                    dateTimeState.toInstant()?.let { recordTime ->
                        val newRecord: Any = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = recordId, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull(), recordTime = recordTime)
                            Category.BP_AND_PULSE -> BpAndPulse(id = recordId, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), pulse = pulseText.toIntOrNull(), bodyTemperature = bodyTemperatureText.toDoubleOrNull(), recordTime = recordTime)
                            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = recordId, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                            else -> throw IllegalStateException("Not supported category")
                        }
                        onSaveRecord(newRecord)
                    }
                }
            )
        } else {
            // ---------- 記録の詳細 ----------
            HealthRecordDisplayCard(record = record!!)
        }
    }
}

/**
 * 各カテゴリ（身長・体重、バイタル、血糖値・HbA1c）に応じた具体的な入力項目を提供するフォーム。
 *
 * 【補足】：
 * 新規追加（recordId == 0）と
 * 既存データの編集（recordId != 0）の両方で使用される。
 * 親コンポーネント（HealthRecordDetailPane）の isEditing フラグが true の場合に呼び出される。
 */
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
    val isDateTimeValid by remember(dateTimeState) {
        derivedStateOf { dateTimeState.toInstant() != null }
    }

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
                                label = { Text(stringResource(AppThresholds.HEALTH_LABEL_HEIGHT)) }, suffix = { Text("cm") },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text(stringResource(AppThresholds.HEALTH_LABEL_WEIGHT)) }, suffix = { Text("kg") },
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
                                label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BP_SYSTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(firstFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                            )
                            OutlinedTextField(
                                value = bpDiastolicText,
                                onValueChange = { onBpDiastolicChange(it.filter { c -> c.isDigit() }) },
                                label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BP_DIASTOLIC)) },
                                modifier = Modifier.weight(1f).focusRequester(secondFieldFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { thirdFieldFocusRequester.requestFocus() })
                            )
                        }
                        OutlinedTextField(
                            value = pulseText,
                            onValueChange = { onPulseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(stringResource(AppThresholds.HEALTH_LABEL_PULSE)) }, suffix = { Text("bpm") },
                            modifier = Modifier.fillMaxWidth().focusRequester(thirdFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { fourthFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = bodyTemperatureText,
                            onValueChange = { onBodyTemperatureChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(stringResource(AppThresholds.HEALTH_LABEL_BODY_TEMP)) }, suffix = { Text("℃") },
                            modifier = Modifier.fillMaxWidth().focusRequester(fourthFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        OutlinedTextField(
                            value = glucoseText,
                            onValueChange = { onGlucoseChange(it.filter { c -> c.isDigit() }) },
                            label = { Text(stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE)) }, suffix = { Text("mg/dL") },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocusRequester),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondFieldFocusRequester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = hba1cText,
                            onValueChange = { onHba1cChange(it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text(stringResource(AppThresholds.HEALTH_LABEL_HBA1C)) }, suffix = { Text("%") },
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
                        enabled = (when (category) {
                            Category.HEIGHT_AND_WEIGHT -> weightText.isNotBlank()
                            Category.BP_AND_PULSE -> bpSystolicText.isNotBlank() || bpDiastolicText.isNotBlank() || pulseText.isNotBlank() || bodyTemperatureText.isNotBlank()
                            Category.GLUCOSE_AND_HBA1C -> glucoseText.isNotBlank() || hba1cText.isNotBlank()
                            else -> true
                        }) && isDateTimeValid
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/**
 * 登録済みの記録内容を、見やすいカード形式で一覧表示する詳細ビュー。
 */
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            when (record) {
                is HeightAndWeight -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_HEIGHT), value = record.height?.let { "$it cm" } ?: "---")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_WEIGHT), value = record.weight?.let { "$it kg" } ?: "---")
                    val bmi = record.calculateBMI()
                    if (bmi > 0) {
                        val (resId, _) = AppThresholds.evaluateBMI(bmi)
                        val label = resId?.let { stringResource(it) } ?: "---"
                        DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BMI), value = "%.1f ($label)".format(bmi))
                    }
                }
                is BpAndPulse -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BP), value = "${record.bpSystolic ?: "---"} / ${record.bpDiastolic ?: "---"} mmHg")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_PULSE), value = record.pulse?.let { "$it bpm" } ?: "---")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_BODY_TEMP), value = record.bodyTemperature?.let { "$it ℃" } ?: "---")
                    val statusText = record.getVitalResults(context).joinToString("・") { it.first }
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
                is GlucoseAndHbA1c -> {
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_GLUCOSE), value = record.glucose?.let { "$it mg/dL" } ?: "---")
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_HBA1C), value = record.hba1c?.let { "$it %" } ?: "---")
                    val statusText = record.getCombinedResultText(context)
                    DetailItem(label = stringResource(AppThresholds.HEALTH_LABEL_STATUS), value = statusText)
                }
            }
        }
    }
}

