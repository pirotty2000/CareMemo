package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.ui.components.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatMedicationDialogTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = "服薬管理"
                        )
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
            },
            onDelete = { record ->
                viewModel.deleteMedicationRecord(record)
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
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(label = "朝", status = records.find { it.timeSlot == 0 }?.status)
                    MedicationStatusIcon(label = "昼", status = records.find { it.timeSlot == 1 }?.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    MedicationStatusIcon(label = "夕", status = records.find { it.timeSlot == 2 }?.status)
                    MedicationStatusIcon(label = "寝", status = records.find { it.timeSlot == 3 }?.status)
                }
            }
        }
    }
}

@Composable
fun MedicationStatusIcon(label: String, status: Int?) {
    val bgColor = when (status) {
        2 -> Color(0xFF673AB7) // 濃い色（自立）
        1 -> Color(0xFFD1C4E9) // 薄い色（介助）
        0 -> Color(0xFFD32F2F) // 赤（未服用背景）
        else -> Color.Transparent
    }
    val contentColor = when (status) {
        2 -> Color.White
        1 -> Color(0xFF673AB7)
        0 -> Color.White       // 白（未服用文字）
        else -> Color.LightGray.copy(alpha = 0.5f)
    }
    val displayText = if (status == 0) "×" else label
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
    // 閲覧モード/編集モードの状態
    var isEditing by remember { mutableStateOf(false) }
    // 編集途中の状態を保持
    var tempRecords by remember { mutableStateOf(records.associateBy { it.timeSlot }) }
    // どの日時を編集しているか
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    // 日時入力用ステート管理
    val dateTimeState = rememberDateTimeInputState()

    // 編集フィールドの内容をtempRecordsに同期する
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

    // 編集スロットを切り替える際の処理
    fun startEditingSlot(slot: Int) {
        syncCurrentTimeFieldsToTemp()
        editingSlot = slot
        val record = tempRecords[slot]
        dateTimeState.setFromInstant(record?.recordTime ?: Instant.now())
    }

    AlertDialog(
        onDismissRequest = {
            if (!isEditing) onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatMedicationDialogTitle(date)} の服薬状況",
                    style = MaterialTheme.typography.titleMedium
                )
                if (!isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "編集")
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0 to "朝", 1 to "昼", 2 to "夕", 3 to "寝る前").forEach { (slot, label) ->
                    MedicationRow(
                        label = label,
                        currentRecord = tempRecords[slot],
                        isEditing = isEditing,
                        isSelectedForTime = editingSlot == slot,
                        onStatusToggle = { status ->
                            val current = tempRecords[slot]
                            if (current?.status == status) {
                                // 既に選択されているものを再度タップした場合は未服用（削除対象）にする
                                syncCurrentTimeFieldsToTemp()
                                tempRecords = tempRecords.toMutableMap().apply { remove(slot) }
                                if (editingSlot == slot) editingSlot = null
                            } else {
                                // 新規または変更
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
                            if (isEditing) {
                                startEditingSlot(slot)
                            }
                        }
                    )
                }

                if (isEditing && editingSlot != null) {
                    HorizontalDivider()
                    DateTimeInputFields(state = dateTimeState)
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(
                    onClick = {
                        syncCurrentTimeFieldsToTemp()

                        // 差分を保存/削除
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
            } else {
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = {
                    // キャンセル：変更を破棄して閲覧モードに戻る
                    tempRecords = records.associateBy { it.timeSlot }
                    editingSlot = null
                    isEditing = false
                }) {
                    Text("キャンセル")
                }
            }
        }
    )
}

@Composable
fun MedicationRow(
    label: String,
    currentRecord: MedicationRecord?,
    isEditing: Boolean,
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
                    color = Color(0xFFD32F2F),
                    onClick = { if (isEditing) onStatusToggle(0) }
                )
                StatusChip(
                    text = "介助",
                    isSelected = currentRecord?.status == 1,
                    color = Color(0xFFB39DDB),
                    onClick = { if (isEditing) onStatusToggle(1) }
                )
                StatusChip(
                    text = "服用",
                    isSelected = currentRecord?.status == 2,
                    color = Color(0xFF673AB7),
                    onClick = { if (isEditing) onStatusToggle(2) }
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
                .heightIn(min = 16.sp.value.dp) // 1行分の高さを常に確保
                .then(if (isEditing && currentRecord != null) Modifier.clickable { onTimeClick() } else Modifier)
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
