package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.ui.components.CategorySelectorBar
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel,
    personId: Int,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit
) {
    val currentPerson by viewModel.currentPerson.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val recordsByDate by viewModel.recordsByDate.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    var showDialog by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    val age = remember(currentPerson) {
        currentPerson?.birthday?.let { birthdayInstant ->
            val birthDate = birthdayInstant.atZone(ZoneId.systemDefault()).toLocalDate()
            val now = LocalDate.now()
            java.time.Period.between(birthDate, now).years
        }
    }

    val categoryListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = Category.entries.indexOf(Category.MEDICATION)
        if (index >= 0) categoryListState.scrollToItem(index)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        currentPerson?.let { person ->
                            Column {
                                Text(text = person.getMaskedFurigana(isNameMaskingEnabled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Text(text = buildString { append(person.getMaskedName(isNameMaskingEnabled)); append(" さん"); if (age != null) append(" (${age}歳)"); if (person.note.isNotBlank()) append(" [${person.note}]") }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        } ?: Text("服薬管理", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = Category.MEDICATION,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { category ->
                        if (category != Category.MEDICATION) {
                            onNavigateToCategory(category)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 月の選択
            MonthSelector(
                selectedMonth = selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // カレンダー
            Box(modifier = Modifier.weight(1f)) {
                CalendarGrid(
                    yearMonth = selectedMonth,
                    recordsByDate = recordsByDate,
                    onDayClick = { date -> showDialog = date }
                )
            }

            // 凡例セクション
            MedicationLegend()
        }
    }

    if (showDialog != null) {
        MedicationInputDialog(
            date = showDialog!!,
            personId = personId,
            records = recordsByDate[showDialog.toString()] ?: emptyList(),
            onDismiss = { showDialog = null },
            onSave = { record ->
                viewModel.saveMedicationRecord(record)
            }
        )
    }
}

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "前月")
        }
        Text(
            text = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年 MM月")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
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
    val firstDayOfMonth = yearMonth.atDay(1).dayOfWeek.value % 7 // 0:日, 1:月, ...
    
    val calendarDays = mutableListOf<LocalDate?>()
    repeat(firstDayOfMonth) { calendarDays.add(null) }
    for (day in 1..daysInMonth) {
        calendarDays.add(yearMonth.atDay(day))
    }

    Column {
        // 曜日見出し
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
            daysOfWeek.forEachIndexed { index, day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (index) {
                        0 -> Color.Red
                        6 -> Color.Blue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 日付グリッド
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
            .aspectRatio(0.8f)
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
            color = when (dayOfWeek) {
                DayOfWeek.SUNDAY -> Color.Red
                DayOfWeek.SATURDAY -> Color.Blue
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        // 日付の下の残りスペース全体を使って記号を中央配置する
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MedicationStatusIcon(status = records.find { it.timeSlot == 0 }?.status)
                    MedicationStatusIcon(status = records.find { it.timeSlot == 1 }?.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MedicationStatusIcon(status = records.find { it.timeSlot == 2 }?.status)
                    MedicationStatusIcon(status = records.find { it.timeSlot == 3 }?.status)
                }
            }
        }
    }
}

@Composable
fun MedicationStatusIcon(status: Int?) {
    val (text, color) = when (status) {
        2 -> "●" to Color(0xFF673AB7) // 濃い色（自立）
        1 -> "○" to Color(0xFFB39DDB) // 薄い色（介助）
        0 -> "×" to Color.Gray       // グレー（未服用）
        else -> "－" to Color.LightGray.copy(alpha = 0.5f) // ハイフン（未確認）
    }

    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                )
            )
        )
    }
}

@Composable
fun MedicationLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "凡例",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(symbol = "●", label = "服用自立", color = Color(0xFF673AB7))
                LegendItem(symbol = "○", label = "服薬介助", color = Color(0xFFB39DDB))
                LegendItem(symbol = "×", label = "未服用", color = Color.Gray)
                LegendItem(symbol = "－", label = "未確認", color = Color.LightGray.copy(alpha = 0.5f))
            }
            
            // 時間枠の説明
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配置: 左上=朝 / 右上=昼 / 左下=夕 / 右下=寝前",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LegendItem(symbol: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = symbol,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun MedicationInputDialog(
    date: LocalDate,
    personId: Int,
    records: List<MedicationRecord>,
    onDismiss: () -> Unit,
    onSave: (MedicationRecord) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.format(DateTimeFormatter.ofPattern("M月d日"))} の服薬状況") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MedicationRow(
                    label = "朝",
                    currentRecord = records.find { it.timeSlot == 0 },
                    onStatusChange = { status ->
                        onSave(createRecord(personId, date, 0, status))
                    }
                )
                MedicationRow(
                    label = "昼",
                    currentRecord = records.find { it.timeSlot == 1 },
                    onStatusChange = { status ->
                        onSave(createRecord(personId, date, 1, status))
                    }
                )
                MedicationRow(
                    label = "夕",
                    currentRecord = records.find { it.timeSlot == 2 },
                    onStatusChange = { status ->
                        onSave(createRecord(personId, date, 2, status))
                    }
                )
                MedicationRow(
                    label = "寝る前",
                    currentRecord = records.find { it.timeSlot == 3 },
                    onStatusChange = { status ->
                        onSave(createRecord(personId, date, 3, status))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

private fun createRecord(personId: Int, date: LocalDate, slot: Int, status: Int): MedicationRecord {
    return MedicationRecord(
        personId = personId,
        dosageDate = date.toString(),
        timeSlot = slot,
        status = status,
        recordTime = Instant.now()
    )
}

@Composable
fun MedicationRow(
    label: String,
    currentRecord: MedicationRecord?,
    onStatusChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(60.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                text = "未",
                isSelected = currentRecord?.status == 0,
                color = Color.Gray,
                onClick = { onStatusChange(0) }
            )
            StatusChip(
                text = "介助",
                isSelected = currentRecord?.status == 1,
                color = Color(0xFFB39DDB),
                onClick = { onStatusChange(1) }
            )
            StatusChip(
                text = "服用",
                isSelected = currentRecord?.status == 2,
                color = Color(0xFF673AB7),
                onClick = { onStatusChange(2) }
            )
        }
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
