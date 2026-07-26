package jp.mydns.fujiwara.carememo.ui.components.medication

/**
 * Component：PersonMedicationComponents
 *
 * 【役割】：
 * 服薬管理（カテゴリC）に関連するカレンダー表示、月間履歴テーブル、および
 * 日別の服薬状況を登録するための入力ダイアログ等の共通パーツ群を提供する。
 *
 * 【主な機能】：
 * ・履歴テーブル（MedicationHistoryTable）：月間の全スロット状況を一覧形式で表示。
 * ・カレンダー（CalendarGrid）：日付ごとの服薬有無を視覚的に把握できるグリッド表示。
 * ・入力ダイアログ（MedicationInputDialog）：4つの時間枠（朝・昼・夕・寝る前）の状況を一括編集。
 *
 * 【想定する利用場所】：
 * ・PersonMedicationScreenContent（服薬管理のメインコンテンツ領域）
 *
 * 【このコンポーネントでは行わないこと】：
 * ・データベースへの直接アクセス（すべて引数またはラムダ経由で外部から操作）
 * ・「すべて未選択＝削除」等の判定ロジック（親の ViewModel 層が担当）
 *
 * 【公開composable】：
 * ・MedicationHistoryTable
 * ・CalendarGrid
 * ・MedicationInputDialog
 *
 * ---
 * 最終更新日: 2026/07/04
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

/**
 * 全体像：服薬管理（Medication）
 *
 * ■ ui/screens/medication/PersonMedicationScreenContent.kt の PersonMedicationScreenContent (画面全体の器)
 * ├── [レイアウト制御：Phone版 (カレンダー/履歴 切り替え) ・ Tablet版 (2カラム固定)]
 * ├──【カレンダー表示】
 * │  └─ [1] CalendarGrid (月間グリッド：PersonMedicationComponents.kt)
 * │       └─ [1-1] DayCell (1日分のセル：タップで入力ダイアログ起動)
 * │            └─ [1-1-1] MedicationStatusIcon (朝/昼/夕/寝る前 4スロットの状況アイコン)
 * ├──【履歴テーブル表示】
 * │  └─ [2] MedicationHistoryTable (月間一覧テーブル：PersonMedicationComponents.kt)
 * │       └─ <テーブル行> 日付ごとの服薬状況を記号（○/△/×/－）で一覧表示
 * └──【入力・編集セクション】
 *      └─ [3] MedicationInputDialog (登録・編集用ダイアログ：PersonMedicationComponents.kt)
 *           ├─ [3-1] MedicationRow (時間枠ごとの入力行：朝・昼・夕・寝る前)
 *           │    └─ [3-1-1] StatusChip (服薬状況選択：未・介助・服用)
 *           └─ [3-2] DateTimeInputFields (特定の時間枠の「記録時刻」を詳細編集)
 *                └─ <アクション> キャンセル、保存ボタン
 */

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [1] CalendarGrid (月間グリッド)
 * 1ヶ月分の日付をグリッド表示するカレンダーコンポーネント。
 */
@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    onDayClick: (LocalDate) -> Unit
) {
    val calendarDays = remember(yearMonth) { MedicationLogic.getCalendarDays(yearMonth) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val daysOfWeek = getShortDayOfWeekNames()
            daysOfWeek.forEachIndexed { index, day ->
                val (bgColor, textColor) = when (index) {
                    0 -> MaterialTheme.colorScheme.error to Color.White
                    6 -> MaterialTheme.colorScheme.primary to Color.White
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

        // スクロール可能な親要素の中で正しく高さを計算させるため、
        // LazyVerticalGrid ではなく Column と Row を使用してカレンダーを構成します。
        val rows = remember(calendarDays) { calendarDays.chunked(7) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("Medication_Calendar"),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            rows.forEach { rowDays ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 常に7列分の領域を確保する
                    for (i in 0 until 7) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (i < rowDays.size) {
                                val date = rowDays[i]
                                if (date != null) {
                                    // 日付があるマス（1日〜31日）
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
 * [1-1] DayCell (1日分のセル：タップで入力ダイアログ起動)
 * カレンダー内の1日分を表示するセル。
 */
@Composable
private fun DayCell(
    date: LocalDate,
    records: List<MedicationRecord>,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()
    val dayOfWeek = date.dayOfWeek

    Column(
        modifier = Modifier
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
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = getDayOfWeekColor(dayOfWeek)
        )

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
 * [1-1-1] MedicationStatusIcon (朝/昼/夕/寝る前 4スロットの状況アイコン)
 * 服薬状況（服用・未・介助）を示す小さな円形アイコン。
 */
@Composable
private fun MedicationStatusIcon(slot: MedicationTimeSlot, status: MedicationStatus?) {
    val bgColor = MedicationDisplayMapper.getStatusColor(status)
    val contentColor = when (status) {
        MedicationStatus.TAKEN -> MaterialTheme.colorScheme.onPrimary
        MedicationStatus.ASSIST -> Color.White
        MedicationStatus.NONE -> MaterialTheme.colorScheme.onError
        null -> Color.LightGray.copy(alpha = 0.5f)
    }
    
    val displayLabel = when (status) {
        null -> "－"
        MedicationStatus.NONE -> MedicationDisplayMapper.getStatusSymbol(status)
        else -> stringResource(MedicationDisplayMapper.getTimeSlotLabelRes(slot, true))
    }

    Box(
        modifier = Modifier
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
 * [2] MedicationHistoryTable (月間一覧テーブル)
 * 月間の服薬状況を一覧表示するテーブル形式のコンポーネント。
 */
@Composable
fun MedicationHistoryTable(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val slotLabels = AppSpecifications.Medication.TimeSlot.LABELS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .testTag("Medication_HistoryTable")
    ) {
        // 履歴一覧・ヘッダー部分（日・朝・昼・夕・寝る前）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日
            Text(stringResource(R.string.p_med_history_table_day), modifier =
                Modifier
                    .weight(1.0f)
                    .padding(end = 8.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            // 朝・昼・夕・寝る前 （TimeSlot）
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

        // 履歴一覧・本体
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

                val rowBgColor = when (dayOfWeek) {
                    DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }

                val textColor = getDayOfWeekColor(dayOfWeek)
                // val daySuffix = stringResource(R.string.common_day_suffix)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBgColor)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日 (例：2(木))
                    Text(
                        //text = "${day}${daySuffix}($dayOfWeekText)",
                        text = "${day}($dayOfWeekText)",
                        modifier = Modifier
                            .weight(1.0f)
                            .padding(end = 8.dp),
                        textAlign = TextAlign.End,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

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
 * [3] MedicationInputDialog (登録・編集用ダイアログ)
 * 日付ごとの服薬状況を登録・編集するためのダイアログ。
 */
@Composable
fun MedicationInputDialog(
    date: LocalDate,
    personId: String,
    records: List<MedicationRecord>,
    onDismiss: () -> Unit,
    onConfirm: (List<MedicationRecord?>) -> Unit
) {
    // スロットごとの一時的な状態を保持（初期値はDBから取得した既存データ）
    var tempRecords by remember(records) { 
        mutableStateOf(
            MedicationTimeSlot.entries.map { slot ->
                records.find { it.timeSlot == slot.index }
            }
        )
    }
    
    // 現在時刻編集中のスロット
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    
    // 編集中のスロットが変わるたびに dateTimeState をリセット
    val dateTimeState = rememberDateTimeInputState(
        initialInstant = editingSlot?.let { tempRecords[it]?.recordTime }
    )

    /**
     * 入力中の日時を一時的なリストに反映する
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
     * 特定のスロットの時刻編集を開始する
     */
    fun startEditingSlot(slot: Int) {
        syncCurrentTimeFieldsToTemp()
        editingSlot = slot
    }

    AppDialog(
        onDismissRequest = onDismiss,
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
                    MedicationTimeSlot.entries.forEach { slot ->
                        MedicationRow(
                            label = stringResource(MedicationDisplayMapper.getTimeSlotLabelRes(slot)),
                            currentRecord = tempRecords[slot.index],
                            isSelectedForTime = editingSlot == slot.index,
                            onStatusToggle = { code ->
                                val status = MedicationStatus.fromCode(code)!!
                                val current = tempRecords[slot.index]
                                if (current?.status == status.code) {
                                    // 同じステータスなら解除（削除）
                                    syncCurrentTimeFieldsToTemp()
                                    tempRecords = tempRecords.toMutableList().apply { set(slot.index, null) }
                                    if (editingSlot == slot.index) editingSlot = null
                                } else {
                                    // 新規またはステータス変更
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
 * [3-1] MedicationRow (時間枠ごとの入力行：朝・昼・夕・寝る前)
 * ダイアログ内の1つの時間枠（朝など）の行。
 */
@Composable
private fun MedicationRow(
    label: String,
    currentRecord: MedicationRecord?,
    isSelectedForTime: Boolean,
    onStatusToggle: (Int) -> Unit,
    onTimeClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(60.dp))

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
 *  [3-1-1] StatusChip (服薬状況選択：未・介助・服用)
 * ステータス（未・介助・服用）を選択するためのチップ。
 */
@Composable
private fun StatusChip(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    // 選択時の背景色に合わせて最適な文字色を選択
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
        modifier = Modifier
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

@Composable
fun getDayOfWeekColor(dayOfWeek: DayOfWeek): Color {
    return when (dayOfWeek) {
        DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
