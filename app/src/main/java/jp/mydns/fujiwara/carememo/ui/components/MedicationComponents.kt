package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatMedicationDialogTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.getShortDayOfWeekNames
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MedicationHistoryTable(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val hasAnyRecord = recordsByDate.values.any { it.isNotEmpty() }

    if (!hasAnyRecord) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "記録がありません",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            modifier = Modifier.fillMaxSize()
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

                    listOf(0, 1, 2, 3).forEach { slot ->
                        val weight = if (slot == 3) 1.2f else 1f
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

@Composable
fun DayCell(
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
                    MedicationStatusIcon(label = getTimeSlotLabel(0, true), status = records.find { it.timeSlot == 0 }?.status)
                    MedicationStatusIcon(label = getTimeSlotLabel(1, true), status = records.find { it.timeSlot == 1 }?.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(label = getTimeSlotLabel(2, true), status = records.find { it.timeSlot == 2 }?.status)
                    MedicationStatusIcon(label = getTimeSlotLabel(3, true), status = records.find { it.timeSlot == 3 }?.status)
                }
            }
        }
    }
}

@Composable
fun MedicationStatusIcon(label: String, status: Int?) {
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

@Composable
fun MedicationInputDialog(
    date: LocalDate,
    personId: Int,
    records: List<MedicationRecord>,
    onDismiss: () -> Unit,
    onSave: (MedicationRecord) -> Unit,
    onDelete: (MedicationRecord) -> Unit
) {
    var tempRecords by remember { mutableStateOf(records.associateBy { it.timeSlot }) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    val dateTimeState = rememberDateTimeInputState()

    fun syncCurrentTimeFieldsToTemp() {
        if (editingSlot != null) {
            val instant = dateTimeState.toInstant()
            if (instant != null) {
                tempRecords[editingSlot!!]?.let { record ->
                    tempRecords = tempRecords.toMutableMap().apply {
                        put(editingSlot!!, record.copy(recordTime = instant))
                    }
                }
            }
        }
    }

    fun startEditingSlot(slot: Int) {
        syncCurrentTimeFieldsToTemp()
        editingSlot = slot
        val record = tempRecords[slot]
        dateTimeState.setFromInstant(record?.recordTime ?: Instant.now())
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
                listOf(0, 1, 2, 3).forEach { slot ->
                    MedicationRow(
                        label = getTimeSlotLabel(slot),
                        currentRecord = tempRecords[slot],
                        isSelectedForTime = editingSlot == slot,
                        onStatusToggle = { status ->
                            val current = tempRecords[slot]
                            if (current?.status == status) {
                                syncCurrentTimeFieldsToTemp()
                                tempRecords = tempRecords.toMutableMap().apply { remove(slot) }
                                if (editingSlot == slot) editingSlot = null
                            } else {
                                syncCurrentTimeFieldsToTemp()
                                val instant = current?.recordTime ?: Instant.now()
                                tempRecords = tempRecords.toMutableMap().apply {
                                    put(slot, MedicationRecord(
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
                    val initialMap = records.associateBy { it.timeSlot }
                    initialMap.keys.forEach { slot ->
                        if (!tempRecords.containsKey(slot)) {
                            onDelete(initialMap[slot]!!)
                        }
                    }
                    tempRecords.forEach { (slot, record) ->
                        if (record != initialMap[slot]) {
                            onSave(record)
                        }
                    }
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun MedicationRow(
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

@Composable
fun StatusChip(
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
