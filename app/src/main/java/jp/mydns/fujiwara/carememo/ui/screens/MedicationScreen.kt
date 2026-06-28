package jp.mydns.fujiwara.carememo.ui.screens

/**
 * Screen : MedicationScreen
 *
 * 【画面名】
 * 服薬管理画面
 *
 * 【役割】
 * 選択された利用者の日々の服薬状況（朝・昼・夕・寝る前）をカレンダーおよびリスト形式で管理し、
 * 記録の登録・編集、および報告用データの外部出力を提供する画面。
 *
 * 【主な機能】
 * ・カレンダー表示：月単位のグリッドで服薬状況をアイコン表示し、日付タップで編集ダイアログを表示。
 * ・履歴（テーブル）表示：曜日ごとに色分けされた月間の服薬ステータス一覧を表示。
 * ・服薬ステータス管理：「未服用(×)」「介助(△)」「服用(○)」の3段階と確認日時の記録。
 * ・カテゴリ切り替え：上部のセレクターバーによる他の記録カテゴリ（バイタル等）への遷移。
 * ・月選択：前後月への移動機能による過去データの参照。
 * ・PDF出力：月間の服薬記録を期間指定・パスワード保護付きでPDF化し、共有する機能。
 *
 * 【遷移】
 * ← MainScreen / UnifiedRecordScreen（戻るボタン）
 * → UnifiedRecordScreen（上部カテゴリセレクターにて他カテゴリ選択時）
 *
 * 【使用するViewModel】
 * MedicationViewModel
 *
 * 【備考】
 * 服薬管理は他の健康記録（数値・テキスト）と異なり、日別・時間帯別のマトリックス管理が必要なため、
 * 専用の ViewModel と UI コンポーネントで構成されている。
 * PDF出力時の外部アプリ遷移に伴うロックバイパス（LockBypass）制御に対応。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatMedicationDialogTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatYearMonthHeader
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.getShortDayOfWeekNames
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

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
    val allRecords by viewModel.allRecords.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf<LocalDate?>(null) }
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    val categoryListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = Category.entries.indexOf(Category.MEDICATION)
        if (index >= 0) categoryListState.scrollToItem(index)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                if (allRecords.isEmpty()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("服薬記録がないため出力できません")
                                    }
                                    return@IconButton
                                }
                                showPdfSettingsDialog = true
                            }
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力")
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 月の選択
            MonthSelector(
                selectedMonth = selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )

            // 表示切り替え
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isHistoryMode,
                    onClick = { isHistoryMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) }
                ) {
                    Text("カレンダー")
                }
                SegmentedButton(
                    selected = isHistoryMode,
                    onClick = { isHistoryMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) }
                ) {
                    Text("履歴")
                }
            }

            if (isHistoryMode) {
                Text(
                    text = "※ ここでは記録の編集はできません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "※ 日付のセルをタップして記録を追加／編集しましょう",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // コンテンツ
            Box(modifier = Modifier.weight(1f)) {
                if (isHistoryMode) {
                    MedicationHistoryTable(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate
                    )
                } else {
                    CalendarGrid(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate,
                        onDayClick = { date -> showDialog = date }
                    )
                }
            }
        }
    }

    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = Category.MEDICATION,
            onDismiss = { showPdfSettingsDialog = false }
        ) { range, order, start, end, _, password ->
            showPdfSettingsDialog = false
            // PDF共有（外部アプリ遷移）のため、戻ってきた際のアプリロックをスキップする設定を有効化
            viewModel.setLockBypassEnabled(true)
            scope.launch {
                currentPerson?.let { person ->
                    val success = PdfExporter.exportAndShare(
                        context = context,
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        category = Category.MEDICATION,
                        records = allRecords,
                        range = range,
                        order = order,
                        customStartDate = start,
                        customEndDate = end,
                        password = password
                    )
                    if (!success) {
                        snackbarHostState.showSnackbar("PDFの作成に失敗したか、対象データがありません")
                    }
                }
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
            text = formatYearMonthHeader(selectedMonth),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
        }
    }
}

@Composable
fun MedicationHistoryTable(
    yearMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    // その月に1件でも記録があるか判定
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
        // テーブルヘッダー
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

        // スクロール可能なデータ行
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

                // 曜日による背景色の設定（平日:白、土曜:薄い青、日曜:薄い赤）
                val rowBgColor = when (dayOfWeek) {
                    DayOfWeek.SUNDAY -> Color(0xFFFFEBEE) // 薄い赤
                    DayOfWeek.SATURDAY -> Color(0xFFE3F2FD) // 薄い青
                    else -> MaterialTheme.colorScheme.surface // 白（サーフェスカラー）
                }

                // 曜日による文字色
                val textColor = getDayOfWeekColor(dayOfWeek)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBgColor)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日(曜日)
                    Text(
                        text = "${day}日(${dayOfWeekText})",
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.Center,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // 各時間帯（朝:0, 昼:1, 夕:2, 寝る前:3）
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
    val firstDayOfMonth = yearMonth.atDay(1).dayOfWeek.value % 7 // 0:日, 1:月, ...
    
    val calendarDays = mutableListOf<LocalDate?>()
    repeat(firstDayOfMonth) { calendarDays.add(null) }
    for (day in 1..daysInMonth) {
        calendarDays.add(yearMonth.atDay(day))
    }

    Column {
        // 曜日見出し
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = getShortDayOfWeekNames()
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
            color = getDayOfWeekColor(dayOfWeek)
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
        2 -> getMedicationStatusColor(2) // 濃い色（自立）
        1 -> getMedicationStatusColor(1) // 薄い色（介助）
        0 -> getMedicationStatusColor(0) // 赤（未服用背景）
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
                .heightIn(min = 16.sp.value.dp) // 1行分の高さを常に確保
                .then(if (currentRecord != null) Modifier.clickable { onTimeClick() } else Modifier)
                .padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun getDayOfWeekColor(dayOfWeek: DayOfWeek): Color {
    return when (dayOfWeek) {
        DayOfWeek.SUNDAY -> Color.Red
        DayOfWeek.SATURDAY -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurface
    }
}

private fun getMedicationStatusSymbol(status: Int?): String {
    return when (status) {
        0 -> "×"
        1 -> "△"
        2 -> "○"
        else -> "ー"
    }
}

private fun getTimeSlotLabel(slot: Int, isShort: Boolean = false): String {
    return when (slot) {
        0 -> "朝"
        1 -> "昼"
        2 -> "夕"
        3 -> if (isShort) "寝" else "寝る前"
        else -> ""
    }
}

@Composable
private fun getMedicationStatusColor(status: Int): Color {
    return when (status) {
        0 -> Color(0xFFD32F2F) // 未 (赤)
        1 -> Color(0xFFD1C4E9) // 介助 (薄紫)
        2 -> Color(0xFF673AB7) // 服用 (濃紫)
        else -> Color.Transparent
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
