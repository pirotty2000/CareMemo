package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import android.speech.RecognizerIntent
import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.runtime.saveable.rememberSaveable
import jp.mydns.fujiwara.carememo.ui.components.CompactTextField
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecordScreen(
    viewModel: PersonDetailViewModel,
    initialCategoryType: Category,
    personId: Int,
    onBack: () -> Unit,
    onNavigateToConditionDetail: (Int, Int) -> Unit
) {
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }
    
    val currentRecord by viewModel.currentRecordState.collectAsState()
    val records by viewModel.filteredRecords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val conditionPhotoMap by viewModel.conditionPhotoMap.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    // ダイアログ表示用の状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is PersonDetailViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PersonDetailViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
            }
        }
    }

    val categoryListState = rememberLazyListState()
    var showMemoDialog by remember { mutableStateOf<ConditionAtVisit?>(null) }
    var isMemoEditMode by remember { mutableStateOf(false) }
    var showMemoCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, currentCategory)
        viewModel.clearCurrentRecord()
        
        val index = Category.entries.indexOf(currentCategory)
        if (index >= 0) {
            categoryListState.animateScrollToItem(index)
        }
    }

    val age = remember(currentPerson) {
        currentPerson?.birthday?.let { birthdayInstant ->
            val birthDate = birthdayInstant.atZone(ZoneId.systemDefault()).toLocalDate()
            val now = LocalDate.now()
            java.time.Period.between(birthDate, now).years
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        currentPerson?.let { person ->
                            Column {
                                Text(
                                    text = person.getMaskedFurigana(isNameMaskingEnabled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = buildString {
                                        append(person.getMaskedName(isNameMaskingEnabled))
                                        append(" さん")
                                        if (age != null) append(" (${age}歳)")
                                        if (person.note.isNotBlank()) append(" [${person.note}]")
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } ?: Text("利用者記録", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("PDF出力（共有）") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (records.isEmpty()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("${currentCategory.displayName}の記録がないため出力できません")
                                        }
                                        return@DropdownMenuItem
                                    }
                                    showPdfSettingsDialog = true
                                }
                            )
                        }
                    }
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 8.dp),
                    state = categoryListState,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(Category.entries) { _, category ->
                        val hasData = when (category) {
                            Category.HEIGHT_AND_WEIGHT -> personCategorySummary?.hasHeightWeight == true
                            Category.BP_AND_PULSE -> personCategorySummary?.hasBpAndPulse == true
                            Category.GLUCOSE_AND_HBA1C -> personCategorySummary?.hasGlucoseAndHbA1c == true
                            Category.CONDITION_AT_VISIT -> personCategorySummary?.hasCondition == true
                        }

                        FilterChip(
                            selected = currentCategory == category,
                            onClick = { currentCategory = category },
                            label = { Text(category.displayName) },
                            leadingIcon = if (currentCategory == category) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = currentCategory == category,
                                borderColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                borderWidth = if (hasData) 1.5.dp else 1.0.dp,
                                selectedBorderColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentCategory == Category.CONDITION_AT_VISIT) {
                FloatingActionButton(onClick = { onNavigateToConditionDetail(personId, 0) }) {
                    Icon(Icons.Default.Add, contentDescription = "所見追加")
                }
            }
        }
    ) { paddingValues ->
        var showHistory by remember { mutableStateOf(true) }
        var recordToDelete by remember { mutableStateOf<Any?>(null) }

        if (recordToDelete != null) {
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text("データの削除") },
                text = { Text("この記録を削除してもよろしいですか？\n削除されたデータは元に戻せません。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            recordToDelete?.let { viewModel.deleteRecord(it) }
                            recordToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("削除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (currentCategory == Category.CONDITION_AT_VISIT) {
                // 所見メモ用の検索バー
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("所見メモを検索...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            } else {
                InputForm(
                    categoryType = currentCategory,
                    recordData = currentRecord,
                    personId = personId,
                    records = records,
                    defaultRecorderName = defaultRecorderName,
                    onSave = { viewModel.saveRecord(it) },
                    onClear = { viewModel.clearCurrentRecord() },
                    snackbarHostState = snackbarHostState
                )
            }

            if (currentCategory != Category.CONDITION_AT_VISIT) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = showHistory,
                        onClick = { showHistory = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(if (showHistory) Icons.Filled.History else Icons.Outlined.History, contentDescription = null) }
                    ) {
                        Text("履歴")
                    }
                    SegmentedButton(
                        selected = !showHistory,
                        onClick = { showHistory = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(if (!showHistory) Icons.Filled.Timeline else Icons.Outlined.Timeline, contentDescription = null) }
                    ) {
                        Text("グラフ")
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (currentCategory == Category.CONDITION_AT_VISIT) {
                    val memos = records.filterIsInstance<ConditionAtVisit>()
                    if (memos.isEmpty()) {
                        EmptyState(
                            message = "記録がありません",
                            description = "右下の ＋ ボタンをタップして、\n日々の変化や気づきを記録しましょう",
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        MemoHistoryList(
                            records = memos,
                            conditionPhotoMap = conditionPhotoMap,
                            onItemClick = { memo ->
                                onNavigateToConditionDetail(personId, memo.id)
                            },
                            onDeleteClick = { recordToDelete = it }
                        )
                    }
                } else if (showHistory) {
                    if (records.isEmpty()) {
                        EmptyState(
                            message = "履歴がありません",
                            icon = Icons.Outlined.Info
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = "${currentCategory.displayName}の記録: ${records.size}件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                            }
                            items(records.size, key = { index ->
                                when (val r = records[index]) {
                                    is HeightAndWeight -> r.id
                                    is BpAndPulse -> r.id
                                    is GlucoseAndHbA1c -> r.id
                                    is ConditionAtVisit -> r.id
                                    else -> index
                                }
                            }) { index ->
                                val record = records[index]
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            recordToDelete = record
                                            false
                                        } else false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        val color = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                            else -> Color.Transparent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 4.dp)
                                                .background(color, shape = CardDefaults.shape)
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "物理削除", tint = Color.White)
                                        }
                                    }
                                ) {
                                    RecordListItem(
                                        categoryType = currentCategory,
                                        record = record,
                                        onClick = { viewModel.selectRecord(record) },
                                        isEditable = currentRecord == record
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (records.isEmpty()) {
                        EmptyState(
                            message = "グラフのデータがありません",
                            icon = Icons.Outlined.Info
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 16.dp)
                            ) {
                                GraphView(records, currentCategory)
                                Spacer(modifier = Modifier.height(32.dp))
                            }

                            if (scrollState.maxValue > 0) {
                                val barHeight = 60.dp
                                val density = LocalDensity.current
                                val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
                                val maxOffset = viewportHeight - barHeight
                                val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
                                
                                // 垂直ドットインジケーター（スクロールバーの左隣）
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 14.dp), // スクロールバーのすぐ左
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // スクロール位置が半分を超えたら下のドットを強調
                                    val isBottomSelected = scrollState.value > (scrollState.maxValue / 2)
                                    
                                    repeat(2) { index ->
                                        val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                )
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(barHeight)
                                        .align(Alignment.TopEnd)
                                        .offset(y = maxOffset * scrollFraction)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogMessage = null; dialogTitle = null },
            title = { dialogTitle?.let { Text(it) } },
            text = { Text(dialogMessage!!) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null; dialogTitle = null }) {
                    Text("閉じる")
                }
            }
        )
    }

    if (showMemoCreateDialog) {
        MemoEditDialog(
            memo = null,
            personId = personId,
            isEditMode = true,
            defaultRecorderName = defaultRecorderName,
            onEditClick = {},
            onDismiss = { showMemoCreateDialog = false },
            onSave = { viewModel.saveRecord(it); showMemoCreateDialog = false }
        )
    }

    if (showMemoDialog != null) {
        MemoEditDialog(
            memo = showMemoDialog,
            personId = personId,
            isEditMode = isMemoEditMode,
            defaultRecorderName = defaultRecorderName,
            onEditClick = { isMemoEditMode = true },
            onDismiss = { showMemoDialog = null; isMemoEditMode = false },
            onSave = { viewModel.saveRecord(it); showMemoDialog = null; isMemoEditMode = false }
        )
    }

    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = currentCategory,
            onDismiss = { showPdfSettingsDialog = false },
            onExport = { range, order, startDate, endDate, includePhotos ->
                showPdfSettingsDialog = false
                scope.launch {
                    val allPhotos: List<ConditionPhoto> = if (currentCategory == Category.CONDITION_AT_VISIT && includePhotos) {
                        viewModel.getAllPhotosForPerson(personId)
                    } else emptyList()

                    currentPerson?.let { person ->
                        val success = PdfExporter.exportAndShare(
                            context = context,
                            person = person,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            category = currentCategory,
                            records = records,
                            allPhotos = allPhotos,
                            range = range,
                            order = order,
                            customStartDate = startDate,
                            customEndDate = endDate
                        )
                        if (!success) {
                            snackbarHostState.showSnackbar("指定された期間のデータがありません")
                        }
                    }
                }
            }
        )
    }
}

enum class ExportRange(val displayName: String) {
    ALL("全ての記録"),
    LATEST("最新の1件のみ"),
    ONE_MONTH("直近 1ヶ月分"),
    THREE_MONTHS("直近 3ヶ月分"),
    SIX_MONTHS("直近 半年分"),
    CUSTOM("期間を指定する")
}

enum class ExportOrder(val displayName: String) {
    NEWEST_FIRST("新しい順"),
    OLDEST_FIRST("古い順")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsDialog(
    category: Category,
    onDismiss: () -> Unit,
    onExport: (ExportRange, ExportOrder, Instant?, Instant?, Boolean) -> Unit // Booleanを追加
) {
    var selectedRange by remember { mutableStateOf(ExportRange.ALL) }
    var selectedOrder by remember { mutableStateOf(ExportOrder.NEWEST_FIRST) }
    var includePhotos by remember { mutableStateOf(true) } // 写真印刷オプション（デフォルトOn）
    
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate ?: endDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = datePickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("決定") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("決定") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF出力設定 (${category.displayName})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text("抽出範囲", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportRange.entries.forEach { range ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (range == selectedRange),
                                    onClick = { selectedRange = range }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (range == selectedRange), onClick = { selectedRange = range })
                            Text(text = range.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    
                    if (selectedRange == ExportRange.CUSTOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(startDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "開始日")
                            }
                            Text("〜", style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "終了日")
                            }
                        }
                    }
                }
                
                if (category == Category.CONDITION_AT_VISIT) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { includePhotos = !includePhotos }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("写真を印刷に含める", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = includePhotos, onCheckedChange = { includePhotos = it })
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column {
                    Text("並び順", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportOrder.entries.forEach { order ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (order == selectedOrder),
                                    onClick = { selectedOrder = order }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (order == selectedOrder), onClick = { selectedOrder = order })
                            Text(text = order.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onExport(
                        selectedRange, 
                        selectedOrder, 
                        startDate?.let { Instant.ofEpochMilli(it) }, 
                        endDate?.let { Instant.ofEpochMilli(it) },
                        includePhotos
                    ) 
                },
                enabled = if (selectedRange == ExportRange.CUSTOM) startDate != null || endDate != null else true
            ) {
                Text("PDFを作成")
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
fun InputForm(
    categoryType: Category,
    recordData: Any?,
    personId: Int,
    records: List<Any>,
    defaultRecorderName: String,
    onSave: (Any?) -> Unit,
    onClear: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val isEditMode = recordData != null
    val scope = rememberCoroutineScope()

    var yearText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("") }
    var minuteText by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val yearFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }
    val categoryFirstFocusRequester = remember { FocusRequester() }

    val dataField2Requester = remember { FocusRequester() }
    val dataField3Requester = remember { FocusRequester() }

    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var bpSystolicText by remember { mutableStateOf("") }
    var bpDiastolicText by remember { mutableStateOf("") }
    var pulseText by remember { mutableStateOf("") }
    var glucoseText by remember { mutableStateOf("") }
    var hba1cText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var authorText by remember { mutableStateOf("") }
    var conditionText by remember { mutableStateOf("") }

    var isUserModifiedTime by remember { mutableStateOf(false) }

    LaunchedEffect(recordData) {
        if (recordData != null) {
            val recordTime = when (recordData) {
                is HeightAndWeight -> recordData.recordTime
                is BpAndPulse -> recordData.recordTime
                is GlucoseAndHbA1c -> recordData.recordTime
                is ConditionAtVisit -> recordData.recordTime
                else -> Instant.now()
            }
            val zonedDateTime = recordTime.atZone(ZoneId.systemDefault())
            yearText = zonedDateTime.year.toString()
            monthText = zonedDateTime.monthValue.toString()
            dayText = zonedDateTime.dayOfMonth.toString()
            hourText = "%02d".format(zonedDateTime.hour)
            minuteText = "%02d".format(zonedDateTime.minute)
            isUserModifiedTime = true

            when (recordData) {
                is HeightAndWeight -> {
                    heightText = recordData.height?.toString() ?: ""
                    weightText = recordData.weight.toString()
                }
                is BpAndPulse -> {
                    bpSystolicText = recordData.bpSystolic?.toString() ?: ""
                    bpDiastolicText = recordData.bpDiastolic?.toString() ?: ""
                    pulseText = recordData.pulse?.toString() ?: ""
                }
                is GlucoseAndHbA1c -> {
                    glucoseText = recordData.glucose?.toString() ?: ""
                    hba1cText = recordData.hba1c?.toString() ?: ""
                }
                is ConditionAtVisit -> {
                    titleText = recordData.title ?: ""
                    authorText = recordData.author
                    conditionText = recordData.condition ?: ""
                }
            }
        } else {
            if (!isUserModifiedTime) {
                val now = Instant.now().atZone(ZoneId.systemDefault())
                yearText = now.year.toString()
                monthText = now.monthValue.toString()
                dayText = now.dayOfMonth.toString()
                hourText = "00"
                minuteText = "00"
            }
            heightText = if (categoryType == Category.HEIGHT_AND_WEIGHT) {
                val latestHeight = records.filterIsInstance<HeightAndWeight>()
                    .maxByOrNull { it.recordTime }?.height
                latestHeight?.toString() ?: ""
            } else ""
            
            weightText = ""
            bpSystolicText = ""
            bpDiastolicText = ""
            pulseText = ""
            glucoseText = ""
            hba1cText = ""
            titleText = ""
            authorText = if (categoryType == Category.CONDITION_AT_VISIT) defaultRecorderName else ""
            conditionText = ""

            if (isUserModifiedTime) {
                if (categoryType == Category.CONDITION_AT_VISIT) {
                    categoryFirstFocusRequester.requestFocus()
                } else {
                    yearFocusRequester.requestFocus()
                }
            }
        }
    }

    fun filterInteger(text: String): String = text.filter { it.isDigit() }

    fun filterDecimal(text: String): String {
        val filtered = text.filter { it.isDigit() || it == '.' }
        val parts = filtered.split('.')
        return when {
            parts.size > 2 -> parts[0] + "." + parts[1]
            parts.size == 2 -> parts[0] + "." + parts[1].take(1)
            else -> filtered
        }
    }

    val y = yearText.toIntOrNull()
    val m = monthText.toIntOrNull()
    val d = dayText.toIntOrNull()
    val hh = hourText.toIntOrNull() ?: 0
    val mm = minuteText.toIntOrNull() ?: 0

    val isYearError = y == null || (y < 1900 || y > 2100)
    val isMonthError = m == null || m < 1 || m > 12
    val isDayError = try {
        if (y != null && m != null && d != null) {
            d < 1 || d > java.time.YearMonth.of(y, m).lengthOfMonth()
        } else true
    } catch (_: Exception) { true }
    val isHourError = hh !in 0..23
    val isMinuteError = mm !in 0..59

    val isDateTimeValid = !isYearError && !isMonthError && !isDayError && !isHourError && !isMinuteError

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "記録日時", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactTextField(
                        value = yearText,
                        onValueChange = { 
                            val filtered = filterInteger(it)
                            if (filtered.length <= 4) {
                                yearText = filtered
                                isUserModifiedTime = true
                                if (filtered.length == 4) monthFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier.weight(1.3f).focusRequester(yearFocusRequester),
                        onFocusChanged = { state -> if (state.isFocused) yearText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { monthFocusRequester.requestFocus() }),
                        isError = isYearError,
                        suffix = { Text("年", style = MaterialTheme.typography.bodySmall) }
                    )
                    CompactTextField(
                        value = monthText,
                        onValueChange = { 
                            val filtered = filterInteger(it)
                            if (filtered.length <= 2) {
                                monthText = filtered
                                isUserModifiedTime = true
                                if (filtered.length == 2) dayFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(monthFocusRequester),
                        onFocusChanged = { if (it.isFocused) monthText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { dayFocusRequester.requestFocus() }),
                        isError = isMonthError,
                        suffix = { Text("月", style = MaterialTheme.typography.bodySmall) }
                    )
                    CompactTextField(
                        value = dayText,
                        onValueChange = { 
                            val filtered = filterInteger(it)
                            if (filtered.length <= 2) {
                                dayText = filtered
                                isUserModifiedTime = true
                                if (filtered.length == 2) hourFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(dayFocusRequester),
                        onFocusChanged = { if (it.isFocused) dayText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { hourFocusRequester.requestFocus() }),
                        isError = isDayError,
                        suffix = { Text("日", style = MaterialTheme.typography.bodySmall) }
                    )
                    CompactTextField(
                        value = hourText,
                        onValueChange = { 
                            val filtered = filterInteger(it)
                            if (filtered.length <= 2) {
                                hourText = filtered
                                isUserModifiedTime = true
                                if (filtered.length == 2) minuteFocusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(hourFocusRequester),
                        onFocusChanged = { if (it.isFocused) hourText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { minuteFocusRequester.requestFocus() }),
                        isError = isHourError,
                        suffix = { Text("時", style = MaterialTheme.typography.bodySmall) }
                    )
                    CompactTextField(
                        value = minuteText,
                        onValueChange = { 
                            val filtered = filterInteger(it)
                            if (filtered.length <= 2) {
                                minuteText = filtered
                                isUserModifiedTime = true
                                if (filtered.length == 2) focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(minuteFocusRequester),
                        onFocusChanged = { if (it.isFocused) minuteText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        isError = isMinuteError,
                        suffix = { Text("分", style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (categoryType) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactTextField(
                                value = heightText,
                                onValueChange = { heightText = filterDecimal(it) },
                                modifier = Modifier.weight(1f).focusRequester(categoryFirstFocusRequester),
                                onFocusChanged = { if (it.isFocused) heightText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                label = { Text("身長") },
                                suffix = { Text("cm", style = MaterialTheme.typography.labelSmall) }
                            )
                            CompactTextField(
                                value = weightText,
                                onValueChange = { weightText = filterDecimal(it) },
                                modifier = Modifier.weight(1f).focusRequester(dataField2Requester),
                                onFocusChanged = { if (it.isFocused) weightText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                label = { Text("体重") },
                                suffix = { Text("kg", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Category.BP_AND_PULSE -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactTextField(
                                value = bpSystolicText,
                                onValueChange = { bpSystolicText = filterInteger(it) },
                                modifier = Modifier.weight(1f).focusRequester(categoryFirstFocusRequester),
                                onFocusChanged = { if (it.isFocused) bpSystolicText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                label = { Text("血圧(上)") }
                            )
                            CompactTextField(
                                value = bpDiastolicText,
                                onValueChange = { bpDiastolicText = filterInteger(it) },
                                modifier = Modifier.weight(1f).focusRequester(dataField2Requester),
                                onFocusChanged = { if (it.isFocused) bpDiastolicText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dataField3Requester.requestFocus() }),
                                label = { Text("血圧(下)") }
                            )
                            CompactTextField(
                                value = pulseText,
                                onValueChange = { pulseText = filterInteger(it) },
                                modifier = Modifier.weight(1f).focusRequester(dataField3Requester),
                                onFocusChanged = { if (it.isFocused) pulseText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                label = { Text("脈拍") }
                            )
                        }
                    }
                    Category.GLUCOSE_AND_HBA1C -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactTextField(
                                value = glucoseText,
                                onValueChange = { glucoseText = filterInteger(it) },
                                modifier = Modifier.weight(1f).focusRequester(categoryFirstFocusRequester),
                                onFocusChanged = { if (it.isFocused) glucoseText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                label = { Text("血糖値") }
                            )
                            CompactTextField(
                                value = hba1cText,
                                onValueChange = { hba1cText = filterDecimal(it) },
                                modifier = Modifier.weight(1f).focusRequester(dataField2Requester),
                                onFocusChanged = { if (it.isFocused) hba1cText = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                label = { Text("HbA1c") }
                            )
                        }
                    }
                    Category.CONDITION_AT_VISIT -> {
                        OutlinedTextField(value = titleText, onValueChange = { titleText = it }, label = { Text("タイトル") }, modifier = Modifier.fillMaxWidth().focusRequester(categoryFirstFocusRequester), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next), keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }))
                        OutlinedTextField(value = authorText, onValueChange = { authorText = it }, label = { Text("記録者") }, modifier = Modifier.fillMaxWidth().focusRequester(dataField2Requester), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next), keyboardActions = KeyboardActions(onNext = { dataField3Requester.requestFocus() }))
                        TextField(value = conditionText, onValueChange = { conditionText = it }, label = { Text("所見メモ") }, modifier = Modifier.fillMaxWidth().focusRequester(dataField3Requester), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
                    }
                }
            }

            val hasData = when (categoryType) {
                Category.HEIGHT_AND_WEIGHT -> heightText.isNotBlank() || weightText.isNotBlank()
                Category.BP_AND_PULSE -> bpSystolicText.isNotBlank() || bpDiastolicText.isNotBlank() || pulseText.isNotBlank()
                Category.GLUCOSE_AND_HBA1C -> glucoseText.isNotBlank() || hba1cText.isNotBlank()
                Category.CONDITION_AT_VISIT -> titleText.isNotBlank() || conditionText.isNotBlank()
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        val recordTime = try {
                            java.time.LocalDateTime.of(yearText.toInt(), monthText.toInt(), dayText.toInt(), hourText.toIntOrNull() ?: 0, minuteText.toIntOrNull() ?: 0, 0).atZone(ZoneId.systemDefault()).toInstant()
                        } catch (_: Exception) { Instant.now() }

                        if (!hasData) { scope.launch { snackbarHostState.showSnackbar("保存するデータがありません") }; return@Button }

                        val newRecord = when (categoryType) {
                            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = (recordData as? HeightAndWeight)?.id ?: 0, personId = personId, height = heightText.toDoubleOrNull(), weight = weightText.toDoubleOrNull() ?: 0.0, recordTime = recordTime)
                            Category.BP_AND_PULSE -> BpAndPulse(id = (recordData as? BpAndPulse)?.id ?: 0, personId = personId, bpSystolic = bpSystolicText.toIntOrNull(), bpDiastolic = bpDiastolicText.toIntOrNull(), pulse = pulseText.toIntOrNull(), recordTime = recordTime)
                            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = (recordData as? GlucoseAndHbA1c)?.id ?: 0, personId = personId, glucose = glucoseText.toIntOrNull(), hba1c = hba1cText.toDoubleOrNull(), recordTime = recordTime)
                            Category.CONDITION_AT_VISIT -> ConditionAtVisit(id = (recordData as? ConditionAtVisit)?.id ?: 0, personId = personId, title = titleText, condition = conditionText, author = authorText, recordTime = recordTime)
                        }
                        onSave(newRecord)
                        if (!isEditMode) { heightText = ""; weightText = ""; bpSystolicText = ""; bpDiastolicText = ""; pulseText = ""; glucoseText = ""; hba1cText = ""; titleText = ""; conditionText = ""; focusManager.clearFocus() }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDateTimeValid && hasData && when (categoryType) { Category.HEIGHT_AND_WEIGHT -> weightText.toDoubleOrNull() != null; Category.CONDITION_AT_VISIT -> authorText.isNotBlank(); else -> true }
                ) { Text(if (isEditMode) "更新" else "保存") }
                if (isEditMode) OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("キャンセル") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphView(records: List<Any>, categoryType: Category) {
    var showHelpDialog by remember { mutableStateOf<String?>(null) }
    if (showHelpDialog != null) { AlertDialog(onDismissRequest = { showHelpDialog = null }, title = { Text("数値の目安") }, text = { Text(showHelpDialog!!) }, confirmButton = { TextButton(onClick = { showHelpDialog = null }) { Text("閉じる") } }) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (categoryType) {
            Category.BP_AND_PULSE -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("血圧", style = MaterialTheme.typography.titleMedium); IconButton(onClick = { showHelpDialog = HealthThresholds.BP_EXPLANATION }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) } }
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    val sysPoints = data.filter { it.bpSystolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpSystolic!!.toDouble() }
                    val diaPoints = data.filter { it.bpDiastolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpDiastolic!!.toDouble() }
                    val chartDataList = listOf(ChartLineData("血圧(上)", sysPoints, Color.Red), ChartLineData("血圧(下)", diaPoints, Color.Blue))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.BP_LOW_SYSTOLIC, HealthThresholds.BP_HIGH_SYSTOLIC, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BP_LOW_DIASTOLIC, HealthThresholds.BP_HIGH_DIASTOLIC, Color(0xFFE8F5E9)))
                    if (chartDataList.any { it.points.isNotEmpty() }) LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 70.0, maxYConstraint = 160.0) else Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("脈拍", style = MaterialTheme.typography.titleMedium); IconButton(onClick = { showHelpDialog = HealthThresholds.PULSE_EXPLANATION }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) } }
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    val pulsePoints = data.filter { it.pulse != null }.map { it.recordTime.toEpochMilli().toDouble() to it.pulse!!.toDouble() }
                    val chartDataList = listOf(ChartLineData("脈拍", pulsePoints, Color(0xFF4CAF50)))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.PULSE_LOW, HealthThresholds.PULSE_HIGH, Color(0xFFE8F5E9)))
                    if (chartDataList.any { it.points.isNotEmpty() }) LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 40.0, maxYConstraint = 110.0) else Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }
            Category.GLUCOSE_AND_HBA1C -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("血糖値", style = MaterialTheme.typography.titleMedium); IconButton(onClick = { showHelpDialog = HealthThresholds.GLUCOSE_EXPLANATION }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) } }
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                    val glucoses = data.mapNotNull { it.glucose?.toDouble() }
                    val glucosePoints = data.filter { it.glucose != null }.map { it.recordTime.toEpochMilli().toDouble() to it.glucose!!.toDouble() }
                    val chartDataList = listOf(ChartLineData("血糖値", glucosePoints, Color.Magenta))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_LOW, HealthThresholds.GLUCOSE_NORMAL_HIGH, Color(0xFFE8F5E9)))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && glucoses.isNotEmpty()) {
                        val minG = glucoses.minOrNull() ?: 70.0
                        val maxG = glucoses.maxOrNull() ?: 110.0
                        LineChart(
                            dataList = chartDataList, 
                            stepY = 50.0,
                            ranges = ranges, 
                            minYConstraint = minG - 10.0, 
                            maxYConstraint = maxG + 10.0
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("HbA1c", style = MaterialTheme.typography.titleMedium); IconButton(onClick = { showHelpDialog = HealthThresholds.HBA1C_EXPLANATION }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) } }
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                    val hba1cs = data.mapNotNull { it.hba1c }
                    val hba1cPoints = data.filter { it.hba1c != null }.map { it.recordTime.toEpochMilli().toDouble() to it.hba1c!! }
                    val chartDataList = listOf(ChartLineData("HbA1c", hba1cPoints, Color.Red))
                    val ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, HealthThresholds.HBA1C_DIABETES, Color(0xFFFFFDE7)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
                    )
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && hba1cs.isNotEmpty()) {
                        val minH = hba1cs.minOrNull() ?: 5.0
                        val maxH = hba1cs.maxOrNull() ?: 6.0
                        LineChart(
                            dataList = chartDataList, 
                            stepY = 0.5, 
                            ranges = ranges, 
                            minYConstraint = minH - 0.5, 
                            maxYConstraint = maxH + 0.5, 
                            showDecimal = true
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            Category.HEIGHT_AND_WEIGHT -> {
                Text("体重", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 2.dp))
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    val weights = data.mapNotNull { it.weight }
                    val weightPoints = data.filter { it.weight != null }.map { it.recordTime.toEpochMilli().toDouble() to it.weight!! }
                    val chartDataList = listOf(ChartLineData("体重", weightPoints, Color.Blue))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && weights.isNotEmpty()) {
                        val minW = weights.minOrNull() ?: 40.0
                        val maxW = weights.maxOrNull() ?: 80.0
                        LineChart(
                            dataList = chartDataList, 
                            stepY = 5.0,
                            minYConstraint = minW - 2.0, 
                            maxYConstraint = maxW + 2.0,
                            showDecimal = true
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("BMI", style = MaterialTheme.typography.titleMedium); IconButton(onClick = { showHelpDialog = HealthThresholds.BMI_EXPLANATION }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) } }
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    val bmis = data.map { it.calculateBMI() }.filter { it > 0.0 }
                    val bmiPoints = data.map { it.recordTime.toEpochMilli().toDouble() to it.calculateBMI() }.filter { it.second > 0.0 }
                    val chartDataList = listOf(ChartLineData("BMI", bmiPoints, Color.Red))
                    val ranges = listOf(ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)), ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE)))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && bmis.isNotEmpty()) {
                        val minB = bmis.minOrNull() ?: 20.0
                        val maxB = bmis.maxOrNull() ?: 25.0
                        LineChart(
                            dataList = chartDataList, 
                            stepY = 2.0,
                            ranges = ranges, 
                            minYConstraint = minB - 1.0, 
                            maxYConstraint = maxB + 1.0,
                            showDecimal = true
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            else -> {}
        }
    }
}

data class ChartLineData(val label: String, val points: List<Pair<Double, Double>>, val color: Color)
data class ChartLimitLine(val label: String, val value: Double, val color: Color, val isLabelAbove: Boolean)
data class ChartRangeHighlight(val startValue: Double, val endValue: Double, val color: Color)

@Composable
fun LineChart(
    dataList: List<ChartLineData>,
    stepY: Double = 5.0,
    limits: List<ChartLimitLine> = emptyList(),
    ranges: List<ChartRangeHighlight> = emptyList(),
    minYConstraint: Double? = null,
    maxYConstraint: Double? = null,
    showDecimal: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val valueLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val limitLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
    val legendStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    
    // ズームと移動の状態管理
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val allPoints = dataList.flatMap { it.points }
    if (allPoints.isEmpty()) return
    
    val minX = allPoints.minOf { it.first }
    val maxX = allPoints.maxOf { it.first }
    val duration = if (maxX - minX == 0.0) 1.0 else maxX - minX
    
    val allYValues = allPoints.map { it.second } + limits.map { it.value }
    var minYInput = allYValues.minOf { it }
    var maxYInput = allYValues.maxOf { it }
    minYConstraint?.let { minYInput = minOf(minYInput, it) }
    maxYConstraint?.let { maxYInput = maxOf(maxYInput, it) }
    val minY = floor(minYInput / stepY) * stepY
    val maxY = ceil(maxYInput / stepY) * stepY
    val yRange = if (maxY - minY == 0.0) stepY else maxY - minY
    val yStepsCount = (yRange / stepY).toInt()
    
    val density = LocalDensity.current
    val paddingLeft = 40.dp
    val paddingTop = 20.dp
    val paddingBottom = 20.dp

    Column(modifier = Modifier.fillMaxSize()) {
        if (dataList.size > 1) {
            Box(modifier = Modifier.padding(start = paddingLeft, top = 4.dp, bottom = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    dataList.forEach { lineData ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(lineData.color) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lineData.label, style = legendStyle.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.weight(1f)) {
            // Y軸ラベル（固定）
            Canvas(modifier = Modifier.width(paddingLeft).fillMaxHeight()) {
                val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                val topPx = paddingTop.toPx()
                for (i in 0..yStepsCount) {
                    val yVal = minY + stepY * i
                    val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                    val label = if (showDecimal || stepY <= 1.0) "%.1f".format(yVal) else yVal.toInt().toString()
                    val textLayout = textMeasurer.measure(label, labelStyle)
                    drawText(textLayout, topLeft = Offset(size.width - textLayout.size.width - 4.dp.toPx(), py - textLayout.size.height / 2))
                }
            }
            // グラフ本体（ズーム・パン対応）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleX = (scaleX * zoom).coerceAtLeast(1f)
                            // パンの制限（データの範囲内に収める）
                            val maxOffsetX = 0f
                            val minOffsetX = -(size.width * (scaleX - 1f))
                            offsetX = (offsetX + pan.x).coerceIn(minOffsetX, maxOffsetX)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scaleX = 1f
                            offsetX = 0f
                        })
                    }
            ) {
                val leftBufferPx = with(density) { 8.dp.toPx() } // 外側余白を戻す
                val rightBufferPx = with(density) { 8.dp.toPx() } // 外側余白を戻す
                val horizontalPaddingPx = with(density) { 20.dp.toPx() } // 枠内の遊びを追加
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartWidth = (size.width - leftBufferPx - rightBufferPx) * scaleX
                    val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                    val topPx = paddingTop.toPx()
                    val startX = leftBufferPx + offsetX
                    
                    // 枠内の有効な描画幅
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    // グラフエリアのみにクリッピング
                    clipRect(left = leftBufferPx, top = 0f, right = size.width - rightBufferPx, bottom = size.height) {
                        // 1. 範囲ハイライト
                        ranges.forEach { range ->
                            val pyStart = topPx + chartHeight - ((range.startValue - minY) / yRange).toFloat() * chartHeight
                            val pyEnd = topPx + chartHeight - ((range.endValue - minY) / yRange).toFloat() * chartHeight
                            val top = pyEnd.coerceIn(topPx, topPx + chartHeight)
                            val bottom = pyStart.coerceIn(topPx, topPx + chartHeight)
                            if (bottom > top) {
                                drawRect(
                                    color = range.color,
                                    topLeft = Offset(startX, top),
                                    size = androidx.compose.ui.geometry.Size(chartWidth, bottom - top)
                                )
                            }
                        }

                        // 2. Y軸グリッド線
                        for (i in 0..yStepsCount) {
                            val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.5f),
                                start = Offset(leftBufferPx, py),
                                end = Offset(size.width - rightBufferPx, py),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        
                        // 3. X軸グリッドとラベル
                        val baseLabelCount = 4
                        val labelCount = (baseLabelCount * scaleX).toInt().coerceAtMost(20)
                        for (i in 0 until labelCount) {
                            val currentX = minX + (duration * i / (labelCount - 1))
                            val px = startX + horizontalPaddingPx + ((currentX - minX) / duration).toFloat() * effectiveWidth
                            
                            if (px in (leftBufferPx - 100f)..(size.width - rightBufferPx + 100f)) {
                                drawLine(
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    start = Offset(px, topPx),
                                    end = Offset(px, topPx + chartHeight),
                                    strokeWidth = 1.dp.toPx()
                                )
                                val dateStr = DateTimeFormatter.ofPattern("yy/MM/dd")
                                    .withLocale(Locale.JAPAN)
                                    .format(Instant.ofEpochMilli(currentX.toLong()).atZone(ZoneId.systemDefault()))
                                val textLayout = textMeasurer.measure(dateStr, labelStyle)
                                drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, topPx + chartHeight + 4.dp.toPx()))
                            }
                        }

                        // 4. 限界線
                        limits.forEach { limit ->
                            val py = topPx + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
                            if (py in topPx..(topPx + chartHeight)) {
                                drawLine(
                                    color = limit.color.copy(alpha = 0.6f),
                                    start = Offset(startX, py),
                                    end = Offset(startX + chartWidth, py),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                                val labelLayout = textMeasurer.measure(limit.label, limitLabelStyle.copy(color = limit.color))
                                drawText(labelLayout, topLeft = Offset(startX + 4.dp.toPx(), if (limit.isLabelAbove) py - labelLayout.size.height - 2.dp.toPx() else py + 2.dp.toPx()))
                            }
                        }

                        // 5. データ折れ線
                        dataList.forEach { lineData ->
                            val path = Path()
                            val sortedPoints = lineData.points.sortedBy { it.first }
                            sortedPoints.forEachIndexed { index, (x, y) ->
                                val px = startX + horizontalPaddingPx + ((x - minX) / duration).toFloat() * effectiveWidth
                                val py = topPx + chartHeight - ((y - minY) / yRange).toFloat() * chartHeight
                                
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                
                                if (px in leftBufferPx..(size.width - rightBufferPx)) {
                                    drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(y) else y.toInt().toString()
                                    val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                                    drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLayout.size.height - 2.dp.toPx()))
                                }
                            }
                            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    
                    // 枠線
                    drawLine(Color.Gray, Offset(leftBufferPx, topPx), Offset(leftBufferPx, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                    drawLine(Color.Gray, Offset(leftBufferPx, topPx + chartHeight), Offset(size.width - rightBufferPx, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                }
            }
        }
    }
}

@Composable
fun RecordListItem(categoryType: Category, record: Any, onClick: () -> Unit, isEditable: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), colors = if (isEditable) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val instant = when (record) { is HeightAndWeight -> record.recordTime; is BpAndPulse -> record.recordTime; is GlucoseAndHbA1c -> record.recordTime; is ConditionAtVisit -> record.recordTime; else -> null }
            // 「日時：」ラベルを削除
            Text(text = instant?.let { formatRecordTime(it) } ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            when (categoryType) {
                Category.HEIGHT_AND_WEIGHT -> if (record is HeightAndWeight) { 
                    val bmi = record.calculateBMI()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Straighten, contentDescription = "身長", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = record.height?.let { "${it}cm" } ?: "---", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.MonitorWeight, contentDescription = "体重", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = record.weight?.let { "${it}kg" } ?: "---", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "BMI: ${if (bmi > 0) "%.1f".format(bmi) else "---"} (${if (bmi > 0) bmi.evaluateBMI() else "---"})", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Category.BP_AND_PULSE -> if (record is BpAndPulse) { Text(text = "血圧: ${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg, 脈拍: ${record.pulse ?: "---"} bpm (${record.checkStatus()})", style = MaterialTheme.typography.bodyMedium) }
                Category.GLUCOSE_AND_HBA1C -> if (record is GlucoseAndHbA1c) { Text(text = "血糖値: ${record.glucose?.let { "$it mg/dL" } ?: "---"}, HbA1c: ${record.hba1c?.let { "$it%" } ?: "---"} (${record.checkStatus()})", style = MaterialTheme.typography.bodyMedium) }
                Category.CONDITION_AT_VISIT -> if (record is ConditionAtVisit) { Text(text = "タイトル: ${record.title ?: "---"}, 記録者: ${record.author.ifBlank { "---" }}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

fun formatRecordTime(instant: Instant): String {
    val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val eraDate = JapaneseDate.from(localDate)
    val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
    val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
    val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "%d(%s%d)年%d月%d日 %02d:%02d".format(localDate.year, eraName, eraYear, localDate.monthValue, localDate.dayOfMonth, localDateTime.hour, localDateTime.minute)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MemoHistoryList(
    records: List<ConditionAtVisit>,
    conditionPhotoMap: Map<Int, Boolean>,
    onItemClick: (ConditionAtVisit) -> Unit,
    onDeleteClick: (ConditionAtVisit) -> Unit
) {
    val groupedRecords = remember(records) { 
        records.groupBy { it.recordTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { entry -> entry.value.sortedBy { it.recordTime } } // 同一日は時刻昇順
            .toSortedMap(compareByDescending { it }) // 日付は降順
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        groupedRecords.forEach { (date, memos) ->
            stickyHeader { Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) { Text(text = formatDateHeader(date), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(memos.size, key = { index -> memos[index].id }) { index ->
                val memo = memos[index]; val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteClick(memo); false } else false })
                SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false, backgroundContent = { val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else Color.Transparent; Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White) } }) {
                    Card(modifier = Modifier.fillMaxWidth().clickable { onItemClick(memo) }.padding(vertical = 1.dp), shape = androidx.compose.ui.graphics.RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(text = formatTime(memo.recordTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); if (!memo.title.isNullOrBlank()) Text(text = memo.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                            Spacer(modifier = Modifier.height(4.dp)); Text(text = memo.condition ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (conditionPhotoMap[memo.id] == true) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "写真あり",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(text = "記録者: ${memo.author}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

fun formatDateHeader(date: LocalDate): String { 
    val eraDate = JapaneseDate.from(date)
    val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
    val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
    return "%d(%s%d)年%d月%d日".format(date.year, eraName, eraYear, date.monthValue, date.dayOfMonth)
}
fun formatTime(instant: Instant): String { return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoEditDialog(memo: ConditionAtVisit?, personId: Int, isEditMode: Boolean, defaultRecorderName: String, onEditClick: () -> Unit, onDismiss: () -> Unit, onSave: (ConditionAtVisit) -> Unit) {
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf(memo?.title ?: "") }; var condition by remember { mutableStateOf(memo?.condition ?: "") }; var author by remember { mutableStateOf(memo?.author ?: defaultRecorderName) }
    
    val initialTime = memo?.recordTime ?: Instant.now(); val zonedDateTime = initialTime.atZone(ZoneId.systemDefault())
    
    var year by remember { mutableStateOf(zonedDateTime.year.toString()) }
    var month by remember { mutableStateOf(zonedDateTime.monthValue.toString()) }
    var day by remember { mutableStateOf(zonedDateTime.dayOfMonth.toString()) }
    // 新規作成時は 00:00、編集時は既存の値をセット
    var hour by remember { mutableStateOf(if (memo == null) "00" else "%02d".format(zonedDateTime.hour)) }
    var minute by remember { mutableStateOf(if (memo == null) "00" else "%02d".format(zonedDateTime.minute)) }

    val monthFocusRequester = remember { FocusRequester() }; val dayFocusRequester = remember { FocusRequester() }; val hourFocusRequester = remember { FocusRequester() }; val minuteFocusRequester = remember { FocusRequester() }
    val speechLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                // 句読点の自動補完と改行の挿入
                val formattedText = buildString {
                    append(spokenText.trim())
                    // 末尾が句読点等でなければ「。」を付与
                    if (!spokenText.trim().any { it in "。、？！?.!" }) {
                        append("。")
                    }
                    append("\n")
                }
                
                // 既存の文章があれば末尾に追加
                condition = if (condition.isBlank()) {
                    formattedText
                } else {
                    // 既存の末尾が改行でなければ改行を挟んでから追加
                    val separator = if (condition.endsWith("\n")) "" else "\n"
                    condition + separator + formattedText
                }
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { if (isEditMode) { Button(onClick = { val recordTime = try { java.time.LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt()).atZone(ZoneId.systemDefault()).toInstant() } catch (e: Exception) { initialTime }; onSave(ConditionAtVisit(id = memo?.id ?: 0, personId = personId, title = title, condition = condition, author = author, recordTime = recordTime)) }, enabled = author.isNotBlank() && condition.isNotBlank()) { Text("保存") } } else { Button(onClick = onEditClick) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("記録を修正") } } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (isEditMode) "キャンセル" else "閉じる") } }, title = { Text(if (memo == null) "新規記録" else if (isEditMode) "記録の編集" else "記録の参照") }, text = {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isEditMode) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("記録日時", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactTextField(
                                value = year,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    if (filtered.length <= 4) {
                                        year = filtered
                                        if (filtered.length == 4) monthFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1.3f),
                                onFocusChanged = { if (it.isFocused) year = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { monthFocusRequester.requestFocus() }),
                                suffix = { Text("年", style = MaterialTheme.typography.bodySmall) }
                            )
                            CompactTextField(
                                value = month,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    if (filtered.length <= 2) {
                                        month = filtered
                                        if (filtered.length == 2) dayFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f).focusRequester(monthFocusRequester),
                                onFocusChanged = { if (it.isFocused) month = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dayFocusRequester.requestFocus() }),
                                suffix = { Text("月", style = MaterialTheme.typography.bodySmall) }
                            )
                            CompactTextField(
                                value = day,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    if (filtered.length <= 2) {
                                        day = filtered
                                        if (filtered.length == 2) hourFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f).focusRequester(dayFocusRequester),
                                onFocusChanged = { if (it.isFocused) day = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { hourFocusRequester.requestFocus() }),
                                suffix = { Text("日", style = MaterialTheme.typography.bodySmall) }
                            )
                            CompactTextField(
                                value = hour,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    if (filtered.length <= 2) {
                                        hour = filtered
                                        if (filtered.length == 2) minuteFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f).focusRequester(hourFocusRequester),
                                onFocusChanged = { if (it.isFocused) hour = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { minuteFocusRequester.requestFocus() }),
                                suffix = { Text("時", style = MaterialTheme.typography.bodySmall) }
                            )
                            CompactTextField(
                                value = minute,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    if (filtered.length <= 2) minute = filtered
                                },
                                modifier = Modifier.weight(1f).focusRequester(minuteFocusRequester),
                                onFocusChanged = { if (it.isFocused) minute = "" },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("タイトル (任意)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("記録者") }, modifier = Modifier.fillMaxWidth())
                Box(modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("所見メモ") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), trailingIcon = { IconButton(onClick = { val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toString()); putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力してください") }; speechLauncher.launch(intent) }) { Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } }) }
            } else {
                Text(text = "日時: ${formatRecordTime(memo!!.recordTime)}", style = MaterialTheme.typography.labelLarge); Text(text = "記録者: ${memo.author}", style = MaterialTheme.typography.labelLarge); if (!memo.title.isNullOrBlank()) Text(text = "タイトル: ${memo.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); HorizontalDivider(); Text(text = memo.condition ?: "", style = MaterialTheme.typography.bodyLarge)
            }
        }
    })
}

@Composable
fun EmptyState(message: String, description: String? = null, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp)); Text(text = message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        if (description != null) { Spacer(modifier = Modifier.height(8.dp)); Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), textAlign = TextAlign.Center) }
    }
}

@Preview(showBackground = true)
@Composable
fun UnifiedRecordScreenPreview() { MaterialTheme { Text("Preview requires a ViewModel with Repository") } }
