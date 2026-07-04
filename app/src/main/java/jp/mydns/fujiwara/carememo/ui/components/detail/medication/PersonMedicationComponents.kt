package jp.mydns.fujiwara.carememo.ui.components.detail.medication

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatMedicationDialogTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.getShortDayOfWeekNames
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.detail.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.detail.common.rememberDateTimeInputState

/**
 * 月間の服薬状況を一覧表示するテーブル形式のコンポーネント。
 */
@Composable
fun MedicationHistoryTable(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val hasAnyRecord = recordsByDate.values.any { it.isNotEmpty() }

    if (!hasAnyRecord) {
        EmptyState(
            message = "記録がありません",
            icon = Icons.Outlined.Description
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("日", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("朝", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("昼", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("夕", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("寝る前", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBgColor)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${day}日($dayOfWeekText)",
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.Center,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val timeSlots = (0 until AppThresholds.MEDICATION_TIME_SLOT_COUNT).toList()
                    timeSlots.forEach { slot ->
                        val weight = if (slot == AppThresholds.TIME_SLOT_BEDTIME) 1.2f else 1f
                        val record = records.find { it.timeSlot == slot }
                        val symbol = getMedicationStatusSymbol(record?.status)
                        val symbolColor = when (record?.status) {
                            0 -> getMedicationStatusColor(0)
                            1, 2 -> getMedicationStatusColor(2)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
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

/**
 * 1ヶ月分の日付をグリッド表示するカレンダーコンポーネント。
 */
@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    onDayClick: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfMonth = yearMonth.atDay(1).dayOfWeek.value % 7 
    
    val calendarDays = mutableListOf<LocalDate?>()
    repeat(firstDayOfMonth) { calendarDays.add(null) }
    for (day in 1..daysInMonth) {
        calendarDays.add(yearMonth.atDay(day))
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = getShortDayOfWeekNames()
            daysOfWeek.forEachIndexed { index, day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (index) {
                        0 -> MaterialTheme.colorScheme.error
                        6 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(calendarDays) { date ->
                if (date != null) {
                    DayCell(
                        date = date,
                        records = recordsByDate[date.toString()] ?: emptyList(),
                        onClick = { onDayClick(date) }
                    )
                } else {
                    Box(modifier = Modifier.aspectRatio(1f))
                }
            }
        }
    }
}

/**
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
            .aspectRatio(0.9f)
            .padding(1.dp)
            .border(
                width = if (isToday) 2.dp else 0.5.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
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
                    MedicationStatusIcon(label = getTimeSlotLabel(AppThresholds.TIME_SLOT_MORNING, true), status = records.find { it.timeSlot == AppThresholds.TIME_SLOT_MORNING }?.status)
                    MedicationStatusIcon(label = getTimeSlotLabel(AppThresholds.TIME_SLOT_LUNCH, true), status = records.find { it.timeSlot == AppThresholds.TIME_SLOT_LUNCH }?.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(label = getTimeSlotLabel(AppThresholds.TIME_SLOT_DINNER, true), status = records.find { it.timeSlot == AppThresholds.TIME_SLOT_DINNER }?.status)
                    MedicationStatusIcon(label = getTimeSlotLabel(AppThresholds.TIME_SLOT_BEDTIME, true), status = records.find { it.timeSlot == AppThresholds.TIME_SLOT_BEDTIME }?.status)
                }
            }
        }
    }
}

/**
 * 服薬状況（服用・未・介助）を示す小さな円形アイコン。
 */
@Composable
private fun MedicationStatusIcon(label: String, status: Int?) {
    val bgColor = when (status) {
        2 -> getMedicationStatusColor(2)
        1 -> getMedicationStatusColor(1)
        0 -> getMedicationStatusColor(0)
        else -> Color.Transparent
    }
    val contentColor = when (status) {
        2, 0 -> Color.White
        1 -> getMedicationStatusColor(2)
        else -> Color.LightGray.copy(alpha = 0.5f)
    }
    val displayText = if (status == 0) getMedicationStatusSymbol(0) else label
    val displayLabel = if (status == null) "－" else displayText

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
            fontSize = if (status == 0) 12.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                lineHeight = if (status == 0) 12.sp else 9.sp,
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

/**
 * 日付ごとの服薬状況を登録・編集するためのダイアログ。
 */
@Composable
fun MedicationInputDialog(
    date: LocalDate,
    personId: Int,
    records: List<MedicationRecord>,
    onDismiss: () -> Unit,
    onConfirm: (List<MedicationRecord?>) -> Unit
) {
    // スロットごとの一時的な状態を保持（初期値はDBから取得した既存データ）
    var tempRecords by remember(records) { 
        mutableStateOf(
            (0 until AppThresholds.MEDICATION_TIME_SLOT_COUNT).map { slot ->
                records.find { it.timeSlot == slot }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${formatMedicationDialogTitle(date)} の服薬状況",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val timeSlots = (0 until AppThresholds.MEDICATION_TIME_SLOT_COUNT).toList()
                timeSlots.forEach { slot ->
                    MedicationRow(
                        label = getTimeSlotLabel(slot),
                        currentRecord = tempRecords[slot],
                        isSelectedForTime = editingSlot == slot,
                        onStatusToggle = { status ->
                            val current = tempRecords[slot]
                            if (current?.status == status) {
                                // 同じステータスなら解除（削除）
                                syncCurrentTimeFieldsToTemp()
                                tempRecords = tempRecords.toMutableList().apply { set(slot, null) }
                                if (editingSlot == slot) editingSlot = null
                            } else {
                                // 新規またはステータス変更
                                syncCurrentTimeFieldsToTemp()
                                val instant = current?.recordTime ?: Instant.now()
                                tempRecords = tempRecords.toMutableList().apply {
                                    set(slot, MedicationRecord(
                                        id = current?.id ?: 0,
                                        personId = personId,
                                        dosageDate = date.toString(),
                                        timeSlot = slot,
                                        status = status,
                                        recordTime = instant
                                    ))
                                }
                                startEditingSlot(slot)
                            }
                        },
                        onTimeClick = {
                            startEditingSlot(slot)
                        }
                    )
                }

                if (editingSlot != null) {
                    HorizontalDivider()
                    DateTimeInputFields(state = dateTimeState)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    syncCurrentTimeFieldsToTemp()
                    onConfirm(tempRecords)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
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
                    text = "未",
                    isSelected = currentRecord?.status == 0,
                    color = getMedicationStatusColor(0),
                    onClick = { onStatusToggle(0) }
                )
                StatusChip(
                    text = "介助",
                    isSelected = currentRecord?.status == 1,
                    color = getMedicationStatusColor(1),
                    onClick = { onStatusToggle(1) }
                )
                StatusChip(
                    text = "服用",
                    isSelected = currentRecord?.status == 2,
                    color = getMedicationStatusColor(2),
                    onClick = { onStatusToggle(2) }
                )
            }
        }

        Text(
            text = if (currentRecord != null) "確認日時: ${formatRecordTime(currentRecord.recordTime)}" else "",
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
 * ステータス（未・介助・服用）を選択するためのチップ。
 */
@Composable
private fun StatusChip(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(36.dp).width(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun getDayOfWeekColor(dayOfWeek: DayOfWeek): Color {
    return when (dayOfWeek) {
        DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

fun getMedicationStatusSymbol(status: Int?): String {
    return when (status) {
        0 -> "×"
        1 -> "△"
        2 -> "○"
        else -> "ー"
    }
}

fun getTimeSlotLabel(slot: Int, isShort: Boolean = false): String {
    return when (slot) {
        0 -> "朝"
        1 -> "昼"
        2 -> "夕"
        3 -> if (isShort) "寝" else "寝る前"
        else -> ""
    }
}

@Composable
fun getMedicationStatusColor(status: Int): Color {
    return when (status) {
        0 -> Color(0xFFD32F2F) 
        1 -> Color(0xFFD1C4E9) 
        2 -> Color(0xFF673AB7) 
        else -> Color.Transparent
    }
}
