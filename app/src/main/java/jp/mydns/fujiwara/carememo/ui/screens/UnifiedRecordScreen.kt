package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.HealthGraphView
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecordScreen(
    viewModel: PersonDetailViewModel,
    initialCategoryType: Category,
    personId: Int,
    onBack: () -> Unit,
    onNavigateToConditionDetail: (Int, Int) -> Unit,
    onNavigateToHealthRecordDetail: (Int, Category, Int) -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit
) {
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }
    var showHistory by rememberSaveable { mutableStateOf(true) }
    
    val records by viewModel.filteredRecords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val conditionPhotoMap by viewModel.conditionPhotoMap.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPdfSettingsDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is PersonDetailViewModel.UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is PersonDetailViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
            }
        }
    }

    val categoryListState = rememberLazyListState()

    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, currentCategory)
        
        val index = Category.entries.indexOf(currentCategory)
        if (index >= 0) categoryListState.animateScrollToItem(index)
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
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (records.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("${currentCategory.displayName}の記録がないため出力できません") }
                                    return@IconButton
                                }
                                showPdfSettingsDialog = true
                            }
                        ) { Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力") }
                    }
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 8.dp),
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
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
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
            FloatingActionButton(onClick = {
                if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, 0)
                else onNavigateToHealthRecordDetail(personId, currentCategory, 0)
            }) { Icon(Icons.Rounded.Add, contentDescription = "新規追加") }
        }
    ) { paddingValues ->
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
                    ) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) { Text("キャンセル") }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (currentCategory == Category.CONDITION_AT_VISIT) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("所見メモを検索...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = "クリア") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }

            if (currentCategory != Category.CONDITION_AT_VISIT) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = showHistory,
                        onClick = { showHistory = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) }
                    ) { Text("履歴") }
                    SegmentedButton(
                        selected = !showHistory,
                        onClick = { showHistory = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) }
                    ) { Text("グラフ") }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (records.isEmpty()) {
                    EmptyState(
                        message = if (currentCategory == Category.CONDITION_AT_VISIT) "記録がありません" else "履歴がありません",
                        description = if (currentCategory == Category.CONDITION_AT_VISIT) "右下の ＋ ボタンをタップして、\n日々の変化や気づきを記録しましょう" else null,
                        icon = if (currentCategory == Category.CONDITION_AT_VISIT) Icons.Outlined.Description else Icons.Outlined.Info
                    )
                } else if (currentCategory == Category.CONDITION_AT_VISIT || showHistory) {
                    UnifiedHistoryList(
                        records = records,
                        category = currentCategory,
                        conditionPhotoMap = conditionPhotoMap,
                        onItemClick = { record ->
                            val id = when (record) {
                                is HeightAndWeight -> record.id
                                is BpAndPulse -> record.id
                                is GlucoseAndHbA1c -> record.id
                                is ConditionAtVisit -> record.id
                                else -> 0
                            }
                            if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, id)
                            else onNavigateToHealthRecordDetail(personId, currentCategory, id)
                        },
                        onDeleteSwipe = { record -> recordToDelete = record },
                        isAnyDialogOpen = recordToDelete != null
                    )
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 16.dp)) {
                            HealthGraphView(
                                records = records,
                                categoryType = currentCategory,
                                onExpandGraph = { index -> onNavigateToGraphExpansion(personId, currentCategory, index) }
                            )
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                        if (scrollState.maxValue > 0) {
                            VerticalScrollIndicator(scrollState)
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
            confirmButton = { TextButton(onClick = { dialogMessage = null; dialogTitle = null }) { Text("閉じる") } }
        )
    }

    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = currentCategory,
            onDismiss = { showPdfSettingsDialog = false },
            onExport = { r, o, start, end, photos ->
                showPdfSettingsDialog = false
                scope.launch {
                    val allPhotos = if (currentCategory == Category.CONDITION_AT_VISIT && photos) viewModel.getAllPhotosForPerson(personId) else emptyList()
                    currentPerson?.let { person ->
                        val success = PdfExporter.exportAndShare(context, person, isNameMaskingEnabled, currentCategory, records, allPhotos, r, o, start, end)
                        if (!success) snackbarHostState.showSnackbar("指定された期間のデータがありません")
                    }
                }
            }
        )
    }
}

// --- 共通コンポーネント ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedHistoryList(
    records: List<Any>,
    category: Category,
    conditionPhotoMap: Map<Int, Boolean>,
    onItemClick: (Any) -> Unit,
    onDeleteSwipe: (Any) -> Unit,
    isAnyDialogOpen: Boolean
) {
    val groupedRecords = remember(records) {
        records.groupBy { 
            val time = when (it) {
                is HeightAndWeight -> it.recordTime
                is BpAndPulse -> it.recordTime
                is GlucoseAndHbA1c -> it.recordTime
                is ConditionAtVisit -> it.recordTime
                else -> Instant.now()
            }
            time.atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { entry ->
            // 同一日のレコードを時刻の昇順（古い順）にソート
            entry.value.sortedBy {
                when (it) {
                    is HeightAndWeight -> it.recordTime
                    is BpAndPulse -> it.recordTime
                    is GlucoseAndHbA1c -> it.recordTime
                    is ConditionAtVisit -> it.recordTime
                    else -> Instant.now()
                }
            }
        }.toSortedMap(compareByDescending { it }) // 日付は降順（新しい順）にソート
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        groupedRecords.forEach { (date, items) ->
            val isSingle = items.size == 1
            stickyHeader {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateHeader(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSingle) {
                            val time = when (val first = items.first()) {
                                is HeightAndWeight -> first.recordTime
                                is BpAndPulse -> first.recordTime
                                is GlucoseAndHbA1c -> first.recordTime
                                is ConditionAtVisit -> first.recordTime
                                else -> Instant.now()
                            }
                            Text(
                                text = formatTime(time),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            items(items.size) { index ->
                val record = items[index]
                HistoryItemWrapper(
                    record = record,
                    showTime = !isSingle, // 1件のみの場合はヘッダに時刻が出るので、データ欄では非表示
                    onItemClick = { onItemClick(record) },
                    onDeleteSwipe = { onDeleteSwipe(record) },
                    isAnyDialogOpen = isAnyDialogOpen
                ) {
                    HistoryItemBody(category, record, conditionPhotoMap)
                }
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWrapper(
    record: Any,
    showTime: Boolean,
    onItemClick: () -> Unit,
    onDeleteSwipe: () -> Unit,
    isAnyDialogOpen: Boolean,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDeleteSwipe()
    }
    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen && dismissState.currentValue != SwipeToDismissBoxValue.Settled) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else Color.Transparent
            Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onItemClick).padding(vertical = 1.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (showTime) {
                    val time = when (record) {
                        is HeightAndWeight -> record.recordTime
                        is BpAndPulse -> record.recordTime
                        is GlucoseAndHbA1c -> record.recordTime
                        is ConditionAtVisit -> record.recordTime
                        else -> Instant.now()
                    }
                    Text(text = formatTime(time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                content()
            }
        }
    }
}

@Composable
fun HistoryItemBody(category: Category, record: Any, conditionPhotoMap: Map<Int, Boolean>) {
    when (category) {
        Category.BP_AND_PULSE -> if (record is BpAndPulse) VitalRecordItemContent(record)
        Category.GLUCOSE_AND_HBA1C -> if (record is GlucoseAndHbA1c) GlucoseRecordItemContent(record)
        Category.HEIGHT_AND_WEIGHT -> if (record is HeightAndWeight) HeightWeightRecordItemContent(record)
        Category.CONDITION_AT_VISIT -> if (record is ConditionAtVisit) ConditionMemoContent(record, conditionPhotoMap[record.id] == true)
    }
}

@Composable
fun HeightWeightRecordItemContent(record: HeightAndWeight) {
    val bmi = record.calculateBMI()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.height?.let { "${it}cm" } ?: "---", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = record.weight?.let { "${it}kg" } ?: "---", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${HealthThresholds.HEALTH_LABEL_BMI}: ${if (bmi > 0) "%.1f".format(bmi) else "---"}", style = MaterialTheme.typography.labelSmall)
        if (bmi > 0) {
            val bmiLabel = bmi.evaluateBMI()
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($bmiLabel)", fontSize = 10.sp, color = if (bmiLabel == HealthThresholds.BMI_LABEL_NORMAL) Color.Blue else Color.Red)
        }
    }
}

@Composable
fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val gStatus = record.evaluateGlucose()
    val hStatus = record.evaluateHbA1c()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${HealthThresholds.HEALTH_LABEL_GLUCOSE}: ${record.glucose?.let { "$it mg/dL" } ?: "---"}", style = MaterialTheme.typography.labelSmall)
        if (gStatus != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($gStatus)", fontSize = 10.sp, color = if (gStatus == HealthThresholds.GLUCOSE_LABEL_NORMAL) Color.Blue else Color.Red)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${HealthThresholds.HEALTH_LABEL_HBA1C}: ${record.hba1c?.let { "$it%" } ?: "---"}", style = MaterialTheme.typography.labelSmall)
        if (hStatus != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "($hStatus)", fontSize = 10.sp, color = if (hStatus == HealthThresholds.HBA1C_LABEL_NORMAL) Color.Blue else Color.Red)
        }
    }
}

@Composable
fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
        if (!record.title.isNullOrBlank()) Text(text = record.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = record.condition ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (hasPhoto) {
                Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = "写真あり", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = "記録者: ${record.author}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun BoxScope.VerticalScrollIndicator(scrollState: ScrollState) {
    val barHeight = 60.dp
    val density = LocalDensity.current
    val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
    val maxOffset = viewportHeight - barHeight
    val scrollFraction by remember { derivedStateOf { if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f } }
    val isBottomSelected by remember { derivedStateOf { scrollState.value > (scrollState.maxValue / 2) } }
    
    Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(2) { index ->
            val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
        }
    }
    Box(modifier = Modifier.width(4.dp).height(barHeight).align(Alignment.TopEnd).offset(y = maxOffset * scrollFraction).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
}

@Composable
fun VitalRecordItemContent(record: BpAndPulse) {
    val status = record.getVitalStatus()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.pulse ?: "---"} bpm", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.bodyTemperature?.let { "%.1f".format(it) } ?: "---"} ℃", style = MaterialTheme.typography.labelMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_HIGH_BP, isActive = status.isHighBp)
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_LOW_BP, isActive = status.isLowBp)
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_TACHYCARDIA, isActive = status.isTachycardia)
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_BRADYCARDIA, isActive = status.isBradycardia)
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_FEVER, isActive = status.isFever)
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_HYPOTHERMIA, isActive = status.isHypothermia)
        }
    }
}

@Composable
fun VitalStatusIndicator(label: String, isActive: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal),
        color = if (isActive) MaterialTheme.colorScheme.error else Color.LightGray.copy(alpha = 0.6f)
    )
}

@Composable
fun EmptyState(message: String, description: String? = null, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp)); Text(text = message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        if (description != null) { Spacer(modifier = Modifier.height(8.dp)); Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), textAlign = TextAlign.Center) }
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
    onExport: (ExportRange, ExportOrder, Instant?, Instant?, Boolean) -> Unit
) {
    var selectedRange by remember { mutableStateOf(ExportRange.ALL) }
    var selectedOrder by remember { mutableStateOf(ExportOrder.NEWEST_FIRST) }
    var includePhotos by remember { mutableStateOf(true) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate ?: endDate ?: System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = { TextButton(onClick = { startDate = datePickerState.selectedDateMillis; showStartDatePicker = false }) { Text("決定") } }) { DatePicker(state = datePickerState) }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = { TextButton(onClick = { endDate = datePickerState.selectedDateMillis; showEndDatePicker = false }) { Text("決定") } }) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF出力設定 (${category.displayName})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("抽出範囲", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ExportRange.entries.forEach { range ->
                    Row(Modifier.fillMaxWidth().selectable(selected = (range == selectedRange), onClick = { selectedRange = range }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = (range == selectedRange), onClick = { selectedRange = range })
                        Text(text = range.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (selectedRange == ExportRange.CUSTOM) {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) { Text(startDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "開始日") }
                        Text("〜")
                        OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) { Text(endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "終了日") }
                    }
                }
                if (category == Category.CONDITION_AT_VISIT) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().clickable { includePhotos = !includePhotos }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("写真を印刷に含める")
                        Switch(checked = includePhotos, onCheckedChange = { includePhotos = it })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("並び順", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ExportOrder.entries.forEach { order ->
                    Row(Modifier.fillMaxWidth().selectable(selected = (order == selectedOrder), onClick = { selectedOrder = order }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = (order == selectedOrder), onClick = { selectedOrder = order })
                        Text(text = order.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onExport(selectedRange, selectedOrder, startDate?.let { Instant.ofEpochMilli(it) }, endDate?.let { Instant.ofEpochMilli(it) }, includePhotos) }, enabled = if (selectedRange == ExportRange.CUSTOM) startDate != null || endDate != null else true) { Text("PDFを作成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

fun formatDateHeader(date: LocalDate): String {
    val eraDate = JapaneseDate.from(date)
    val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
    val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
    return "%d(%s%d)年%d月%d日".format(date.year, eraName, eraYear, date.monthValue, date.dayOfMonth)
}

fun formatTime(instant: Instant): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)

fun formatRecordTime(instant: Instant): String {
    val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val eraDate = JapaneseDate.from(localDate)
    val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
    val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
    val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "%d(%s%d)年%d月%d日 %02d:%02d".format(localDate.year, eraName, eraYear, localDate.monthValue, localDate.dayOfMonth, localDateTime.hour, localDateTime.minute)
}

@Preview(showBackground = true)
@Composable
fun UnifiedRecordScreenPreview() { MaterialTheme { Text("Preview") } }
