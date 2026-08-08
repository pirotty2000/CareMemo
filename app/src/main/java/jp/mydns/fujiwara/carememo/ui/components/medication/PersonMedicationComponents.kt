package jp.mydns.fujiwara.carememo.ui.components.medication

/**
 * Component：PersonMedicationComponents
 *
 * 【役割】
 * 利用者の「服薬管理（カテゴリC）」に関連するカレンダー表示、月間履歴テーブル、および
 * 日別の服薬状況を登録・編集するための共通パーツ群を提供します。
 *
 * 【主な機能】
 * ・カレンダー（CalendarGrid）：日付ごとの服薬有無（朝・昼・夕・寝る前）を視覚的に把握できるグリッド表示。
 * ・履歴テーブル（MedicationHistoryTable）：月間の全スロット状況を一覧形式で確認できるリスト表示。
 * ・入力ダイアログ（MedicationInputDialog）：4つの時間枠の状況選択と、記録時刻の同時編集をサポート。
 * ・ステータス表示（MedicationStatusIcon / StatusChip）：服用、介助、未服用の状態を色と記号で直感的に表現。
 *
 * 【想定する利用場所】
 * ・PersonMedicationScreenContent（服薬管理画面のメイン領域）
 *
 * 【このコンポーネントでは行わないこと】
 * ・データベースへの直接アクセス（すべて引数またはラムダ経由で外部から操作）。
 * ・複雑な判定ロジック（親の ViewModel 層または MedicationLogic が担当）。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.logic.common.*
import jp.mydns.fujiwara.carememo.ui.mapping.MedicationDisplayMapper
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatMedicationDialogTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.getShortDayOfWeekNames
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * 全体像：服薬管理（Medication）
 *
 * ■ ui/screens/medication/PersonMedicationScreenContent.kt の PersonMedicationScreenContent
 * ├── [レイアウト制御：Phone版 (カレンダー/履歴 切り替え) ・ Tablet版 (2カラム固定)]
 * ├──【カレンダー表示】
 * │  └─ [1] CalendarGrid (月間グリッド)
 * │       └─ [1-1] DayCell (1日分のセル)
 * │            └─ [1-1-1] MedicationStatusIcon (朝/昼/夕/寝る前 4スロットの状況アイコン)
 * ├──【履歴テーブル表示】
 * │  └─ [2] MedicationHistoryTable (月間一覧テーブル)
 * └──【入力・編集セクション】
 *      └─ [3] MedicationInputDialog (登録・編集用ダイアログ)
 *           ├─ [3-1] MedicationRow (時間枠ごとの入力行：朝・昼・夕・寝る前)
 *           │    └─ [3-1-1] StatusChip (服薬状況選択：未・介助・服用)
 *           └─ [3-2] DateTimeInputFields (特定の時間枠の「記録時刻」を詳細編集)
 */

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [1] CalendarGrid
 * 1ヶ月分の日付と服薬状況をグリッド形式で表示するカレンダーコンポーネント。
 *
 * @param yearMonth 表示対象の年月
 * @param recordsByDate 日付（文字列）をキーとした服薬記録のマップ
 * @param onDayClick 日付セルがタップされた際のコールバック（ダイアログ起動用）
 */
@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    recordsByDate: ImmutableMap<String, ImmutableList<MedicationRecord>>,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit
) {
    // 表示用の日付リスト（月初の空白を含む）を取得
    val calendarDays = remember(yearMonth) { MedicationLogic.getCalendarDays(yearMonth) }

    Column(modifier = modifier) {
        // 曜日ヘッダー（日・月・火...）
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val daysOfWeek = getShortDayOfWeekNames()
            daysOfWeek.forEachIndexed { index, day ->
                val (bgColor, textColor) = when (index) {
                    0 -> MaterialTheme.colorScheme.error to Color.White // 日曜
                    6 -> MaterialTheme.colorScheme.primary to Color.White // 土曜
                    else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .wrapContentHeight(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = if (index == 0 || index == 6) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // カレンダー本体のグリッド
        // スクロール可能な親要素の中での高さ計算を安定させるため、LazyVerticalGrid ではなく Column + Row で構成
        val rows = remember(calendarDays) { calendarDays.chunked(7) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("Medication_Calendar"),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            rows.forEach { rowDays ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 常に7列分の領域を確保
                    for (i in 0 until 7) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (i < rowDays.size) {
                                val date = rowDays[i]
                                if (date != null) {
                                    // [1-1] DayCell: 日付があるマス（1日〜末日）
                                    DayCell(
                                        date = date,
                                        records = recordsByDate[date.toString()] ?: emptyList(),
                                        onClick = { onDayClick(date) }
                                    )
                                } else {
                                    // 月初の余白
                                    Box(modifier = Modifier.aspectRatio(0.8f))
                                }
                            } else {
                                // 月末の余白（最終週が7日に満たない場合）
                                Box(modifier = Modifier.aspectRatio(0.8f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [1-1] DayCell
 * カレンダー内の1日分を表示するセル。
 *
 * @param date 対象の日付
 * @param records その日の服薬記録リスト
 * @param onClick タップ時のコールバック
 */
@Composable
private fun DayCell(
    date: LocalDate,
    records: List<MedicationRecord>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()
    val dayOfWeek = date.dayOfWeek

    Column(
        modifier = modifier
            .aspectRatio(0.8f)
            .padding(1.dp)
            .border(
                width = if (isToday) 2.dp else 0.5.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
            .testTag("Medication_DayCell_${date}")
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 日付ラベル
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = getDayOfWeekColor(dayOfWeek)
        )

        // 服薬ステータス（4スロット分）を 2x2 で配置
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(slot = MedicationTimeSlot.MORNING, status = MedicationStatus.fromCode(records.find { it.timeSlot == MedicationTimeSlot.MORNING.index }?.status))
                    MedicationStatusIcon(slot = MedicationTimeSlot.LUNCH, status = MedicationStatus.fromCode(records.find { it.timeSlot == MedicationTimeSlot.LUNCH.index }?.status))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(slot = MedicationTimeSlot.DINNER, status = MedicationStatus.fromCode(records.find { it.timeSlot == MedicationTimeSlot.DINNER.index }?.status))
                    MedicationStatusIcon(slot = MedicationTimeSlot.BEDTIME, status = MedicationStatus.fromCode(records.find { it.timeSlot == MedicationTimeSlot.BEDTIME.index }?.status))
                }
            }
        }
    }
}

/**
 * [1-1-1] MedicationStatusIcon
 * 朝/昼/夕/寝る前の各スロットの状況を示す小さな円形アイコン。
 *
 * @param slot 時間枠（MORNING, LUNCH 等）
 * @param status 服薬状況（TAKEN, ASSIST, NONE または null）
 */
@Composable
private fun MedicationStatusIcon(
    slot: MedicationTimeSlot,
    status: MedicationStatus?,
    modifier: Modifier = Modifier
) {
    // ステータスに応じた配色の決定
    val bgColor = MedicationDisplayMapper.getStatusColor(status)
    val contentColor = when (status) {
        MedicationStatus.TAKEN -> MaterialTheme.colorScheme.onPrimary
        MedicationStatus.ASSIST -> Color.White
        MedicationStatus.NONE -> MaterialTheme.colorScheme.onError
        null -> Color.LightGray.copy(alpha = 0.5f)
    }
    
    // 表示文字（記号または時間枠の頭文字）
    val displayLabel = when (status) {
        null -> "－"
        MedicationStatus.NONE -> MedicationDisplayMapper.getStatusSymbol(status)
        else -> stringResource(MedicationDisplayMapper.getTimeSlotLabelRes(slot, true))
    }

    Box(
        modifier = modifier
            .size(17.dp)
            .background(bgColor, CircleShape)
            .let { 
                if (status == null) it.border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                else it
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            color = contentColor,
            fontSize = if (status == MedicationStatus.NONE) 12.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                lineHeight = if (status == MedicationStatus.NONE) 12.sp else 9.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                ),
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                )
            )
        )
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [2] MedicationHistoryTable
 * 月間の服薬状況を記号（○/△/×）で一覧表示するテーブルコンポーネント。
 *
 * @param yearMonth 表示対象の年月
 * @param recordsByDate 日付（文字列）をキーとした服薬記録のマップ
 * @param lazyListState スクロール状態
 */
@Composable
fun MedicationHistoryTable(
    yearMonth: YearMonth,
    recordsByDate: ImmutableMap<String, ImmutableList<MedicationRecord>>,
    modifier: Modifier = Modifier,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val slotLabels = AppSpecifications.Medication.TimeSlot.LABELS

    Column(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .testTag("Medication_HistoryTable")
    ) {
        // ヘッダー部分（日・朝・昼・夕・寝る前）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.p_med_history_table_day), modifier =
                Modifier
                    .weight(1.0f)
                    .padding(end = 8.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            
            slotLabels.forEachIndexed { index, label ->
                val weight = if (index == AppSpecifications.Medication.TimeSlot.INDEX_BEDTIME) 1.2f else 1f
                Text(
                    text = label,
                    modifier = Modifier.weight(weight),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 履歴本体
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState
        ) {
            items(count = daysInMonth) { index ->
                val day = index + 1
                val date = yearMonth.atDay(day)
                val dateStr = date.toString()
                val records = recordsByDate[dateStr] ?: emptyList()

                val dayOfWeek = date.dayOfWeek
                val dayOfWeekText = formatShortDayOfWeek(date)

                // 曜日による背景色の出し分け（土日は薄く着色）
                val rowBgColor = when (dayOfWeek) {
                    DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }

                val textColor = getDayOfWeekColor(dayOfWeek)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBgColor)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日付列 (例：2(木))
                    Text(
                        text = stringResource(R.string.medication_history_day_format, day, dayOfWeekText),
                        modifier = Modifier
                            .weight(1.0f)
                            .padding(end = 8.dp),
                        textAlign = TextAlign.End,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // 各スロットの記号表示
                    val timeSlots = MedicationTimeSlot.entries
                    timeSlots.forEach { slot ->
                        val weight = if (slot == MedicationTimeSlot.BEDTIME) 1.2f else 1f
                        val record = records.find { it.timeSlot == slot.index }
                        val status = MedicationStatus.fromCode(record?.status)

                        val symbol = MedicationDisplayMapper.getStatusSymbol(status)
                        val symbolColor = MedicationDisplayMapper.getStatusColor(status)

                        Text(
                            text = symbol,
                            modifier = Modifier.weight(weight),
                            textAlign = TextAlign.Center,
                            color = symbolColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (day < daysInMonth) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [3] MedicationInputDialog
 * 特定の日の服薬状況を登録・編集するためのダイアログ。
 *
 * @param date 対象の日付
 * @param personId 利用者ID
 * @param records 現在保存されている記録リスト
 * @param onDismiss ダイアログを閉じる際のコールバック
 * @param onConfirm 保存が確定した際のコールバック（全スロットの最新状態を渡す）
 */
@Composable
fun MedicationInputDialog(
    date: LocalDate,
    personId: String,
    records: ImmutableList<MedicationRecord>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: (List<MedicationRecord?>) -> Unit
) {
    // スロットごとの一時的な状態を保持。外部からの records 変更に追従。
    var tempRecords by remember(records) { 
        mutableStateOf(
            MedicationTimeSlot.entries.map { slot ->
                records.find { it.timeSlot == slot.index }
            }
        )
    }
    
    // 現在時刻編集（DateTimeInputFields）の対象となっているスロット
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    
    // 特定のスロットの時刻を編集するためのステート
    val dateTimeState = rememberDateTimeInputState(
        initialInstant = editingSlot?.let { tempRecords[it]?.recordTime }
    )

    /**
     * 現在 DateTimeInputFields で入力中の日時を一時的なリスト（tempRecords）に同期反映。
     */
    fun syncCurrentTimeFieldsToTemp() {
        editingSlot?.let { slot ->
            val instant = dateTimeState.toInstant()
            if (instant != null) {
                tempRecords[slot]?.let { record ->
                    tempRecords = tempRecords.toMutableList().apply {
                        set(slot, record.copy(recordTime = instant))
                    }
                }
            }
        }
    }

    /**
     * 特定のスロットを選択し、時刻編集を開始。
     */
    fun startEditingSlot(slot: Int) {
        syncCurrentTimeFieldsToTemp()
        editingSlot = slot
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.p_med_dialog_title, formatMedicationDialogTitle(date)),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            AppDialogContent {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 朝・昼・夕・寝る前 の各入力行を表示
                    MedicationTimeSlot.entries.forEach { slot ->
                        MedicationRow(
                            label = stringResource(MedicationDisplayMapper.getTimeSlotLabelRes(slot)),
                            currentRecord = tempRecords[slot.index],
                            isSelectedForTime = editingSlot == slot.index,
                            onStatusToggle = { code ->
                                val status = MedicationStatus.fromCode(code)!!
                                val current = tempRecords[slot.index]
                                if (current?.status == status.code) {
                                    // 【トグル動作】既に同じステータスなら解除（削除対象）
                                    syncCurrentTimeFieldsToTemp()
                                    tempRecords = tempRecords.toMutableList().apply { set(slot.index, null) }
                                    if (editingSlot == slot.index) editingSlot = null
                                } else {
                                    // ステータス変更または新規作成
                                    syncCurrentTimeFieldsToTemp()
                                    val instant = current?.recordTime ?: Instant.now()
                                    val newRecord = current?.copy(status = status.code, recordTime = instant)
                                        ?: MedicationRecord(
                                            id = AppSpecifications.Id.NEW_RECORD_ID,
                                            personId = personId,
                                            dosageDate = date.toString(),
                                            timeSlot = slot.index,
                                            status = status.code,
                                            recordTime = instant
                                        )
                                    tempRecords = tempRecords.toMutableList().apply {
                                        set(slot.index, newRecord)
                                    }
                                    startEditingSlot(slot.index)
                                }
                            },
                            onTimeClick = {
                                startEditingSlot(slot.index)
                            }
                        )
                    }

                    // いずれかのスロットが選択されている場合、時刻編集フィールドを表示
                    if (editingSlot != null) {
                        HorizontalDivider()
                        DateTimeInputFields(state = dateTimeState)
                    }
                }
            }
        },
        confirmButton = {
            AppDialogConfirmButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    syncCurrentTimeFieldsToTemp()
                    onConfirm(tempRecords)
                    onDismiss()
                },
                modifier = Modifier.testTag("Medication_SaveButton")
            )
        },
        dismissButton = {
            AppDialogDismissButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("Medication_CancelButton")
            )
        }
    )
}

/**
 * [3-1] MedicationRow
 * ダイアログ内の1つの時間枠（朝など）の入力行。
 *
 * @param label スロット名（朝 等）
 * @param currentRecord 現在選択されている記録（null なら未入力）
 * @param isSelectedForTime この行が時刻編集の対象として選択されているか
 * @param onStatusToggle ステータスチップがタップされた際のコールバック
 * @param onTimeClick 時刻ラベルがタップされた際のコールバック
 */
@Composable
private fun MedicationRow(
    label: String,
    currentRecord: MedicationRecord?,
    isSelectedForTime: Boolean,
    modifier: Modifier = Modifier,
    onStatusToggle: (Int) -> Unit,
    onTimeClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(60.dp))

            // ステータス選択チップ（未・介助・服用）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = stringResource(R.string.p_med_status_none),
                    isSelected = currentRecord?.status == MedicationStatus.NONE.code,
                    color = MedicationDisplayMapper.getStatusColor(MedicationStatus.NONE),
                    onClick = { onStatusToggle(MedicationStatus.NONE.code) }
                )
                StatusChip(
                    text = stringResource(R.string.p_med_status_assist),
                    isSelected = currentRecord?.status == MedicationStatus.ASSIST.code,
                    color = MedicationDisplayMapper.getStatusColor(MedicationStatus.ASSIST),
                    onClick = { onStatusToggle(MedicationStatus.ASSIST.code) }
                )
                StatusChip(
                    text = stringResource(R.string.p_med_status_taken),
                    isSelected = currentRecord?.status == MedicationStatus.TAKEN.code,
                    color = MedicationDisplayMapper.getStatusColor(MedicationStatus.TAKEN),
                    onClick = { onStatusToggle(MedicationStatus.TAKEN.code) }
                )
            }
        }

        // 記録時刻の表示ラベル（タップで編集対象に指定）
        Text(
            text = if (currentRecord != null) stringResource(R.string.p_med_label_check_time, formatRecordTime(currentRecord.recordTime)) else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelectedForTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 16.sp.value.dp)
                .then(if (currentRecord != null) Modifier.clickable { onTimeClick() } else Modifier)
                .padding(vertical = 2.dp)
        )
    }
}

/**
 *  [3-1-1] StatusChip
 * 服薬状況（未・介助・服用）を選択するための、視覚的に分かりやすいチップ。
 */
@Composable
private fun StatusChip(
    text: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 選択時の背景色に合わせて最適な文字色（コントラスト）を選択
    val selectedContentColor = when (color) {
        MaterialTheme.colorScheme.primaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer
        MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimary
        MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.onError
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(36.dp)
            .width(56.dp)
            .testTag("Medication_StatusChip_${text}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * 曜日に基づく適切なテキスト色を取得します。
 */
@Composable
fun getDayOfWeekColor(dayOfWeek: DayOfWeek): Color {
    return when (dayOfWeek) {
        DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
