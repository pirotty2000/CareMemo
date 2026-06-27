package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Description
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
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateHeader
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecordScreen(
    viewModel: PersonDetailViewModel,
    initialCategoryType: Category,
    personId: Int,
    onBack: () -> Unit,
    onNavigateToConditionDetail: (Int, Int) -> Unit,
    onNavigateToHealthRecordDetail: (Int, Category, Int) -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToMedication: (Int) -> Unit,
) {
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }
    
    // ユーザーの表示モード設定（初期値は履歴: true）
    var preferredShowHistory by rememberSaveable { mutableStateOf(value = true) }
    
    // 実際の表示判定：カテゴリがグラフを持たない場合は強制的に履歴。そうでなければユーザーの好みに従う
    val isEffectivelyShowingHistory = preferredShowHistory || !currentCategory.hasGraph
    
    val records by viewModel.filteredRecords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val conditionPhotoMap by viewModel.conditionPhotoMap.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPdfSettingsDialog by remember { mutableStateOf(value = false) }
    var dialogTitle by remember { mutableStateOf<String?>(value = null) }
    var dialogMessage by remember { mutableStateOf<String?>(value = null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                else -> {}
            }
        }
    }

    val categoryListState = rememberLazyListState()

    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        viewModel.setCategory(currentCategory)
        val index = Category.entries.indexOf(currentCategory)
        if (index >= 0) categoryListState.animateScrollToItem(index)
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
                            defaultTitle = "利用者記録"
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") } },
                    actions = { IconButton(onClick = { if (records.isEmpty()) { scope.launch { snackbarHostState.showSnackbar("${currentCategory.displayName}の記録がないため出力できません") }; return@IconButton }; showPdfSettingsDialog = true }) { Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力") } }
                )
                CategorySelectorBar(
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { category ->
                        if (category == Category.MEDICATION) {
                            onNavigateToMedication(personId)
                        } else {
                            currentCategory = category
                        }
                    }
                )
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, 0) else onNavigateToHealthRecordDetail(personId, currentCategory, 0) }) { Icon(Icons.Rounded.Add, contentDescription = "新規追加") } }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AlertDialog(onDismissRequest = { recordToDelete = null }, title = { Text("データの削除") }, text = { Text("この記録を削除してもよろしいですか？\n削除されたデータは元に戻せません。") }, confirmButton = { TextButton(onClick = { recordToDelete?.let { viewModel.deleteRecord(it) }; recordToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("削除") } }, dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("キャンセル") } })
        }
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.height(4.dp))
            if (currentCategory.hasSearch) { OutlinedTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("${currentCategory.displayName}を検索...") }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = "クリア") } } }, singleLine = true, shape = MaterialTheme.shapes.medium) }
            
            // グラフを持つカテゴリのみ表示切り替えボタンを出す
            if (currentCategory.hasGraph) { 
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) { 
                    SegmentedButton(
                        selected = preferredShowHistory, 
                        onClick = { preferredShowHistory = true }, 
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2), 
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) }
                    ) { Text("履歴") }
                    SegmentedButton(
                        selected = !preferredShowHistory, 
                        onClick = { preferredShowHistory = false }, 
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2), 
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) }
                    ) { Text("グラフ") } 
                } 
            }

            Box(modifier = Modifier.weight(1f)) {
                if (records.isEmpty()) { EmptyState(message = "記録がありません", description = "右下の ＋ ボタンをタップして、\n記録を追加しましょう", icon = Icons.Outlined.Description) }
                else if (isEffectivelyShowingHistory) {
                    UnifiedHistoryList(records = records.filterIsInstance<HistoryRecord>(), category = currentCategory, conditionPhotoMap = conditionPhotoMap, onItemClick = { record -> if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, record.id) else onNavigateToHealthRecordDetail(personId, currentCategory, record.id) }, onDeleteSwipe = { record -> recordToDelete = record }, isAnyDialogOpen = recordToDelete != null)
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) { Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 16.dp)) { HealthGraphView(records = records, categoryType = currentCategory, onExpandGraph = { index -> onNavigateToGraphExpansion(personId, currentCategory, index) }); Spacer(modifier = Modifier.height(80.dp)) }; if (scrollState.maxValue > 0) { VerticalScrollIndicator(scrollState) } }
                }
            }
        }
    }
    if (dialogMessage != null) { AlertDialog(onDismissRequest = { dialogMessage = null; dialogTitle = null }, title = { dialogTitle?.let { Text(it) } }, text = { Text(dialogMessage!!) }, confirmButton = { TextButton(onClick = { dialogMessage = null; dialogTitle = null }) { Text("閉じる") } }) }
    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = currentCategory,
            onDismiss = { showPdfSettingsDialog = false }
        ) { r, o, start, end, photos, password ->
            showPdfSettingsDialog = false
            // PDF共有（外部アプリ遷移）のため、戻ってきた際のアプリロックをスキップする設定を有効化
            viewModel.setLockBypassEnabled(true)
            scope.launch {
                val allPhotos = if (currentCategory.hasOption && photos) viewModel.getAllPhotosForPerson(personId) else emptyList()
                currentPerson?.let { person ->
                    val success = PdfExporter.exportAndShare(
                        context = context,
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        category = currentCategory,
                        records = records,
                        allPhotos = allPhotos,
                        range = r,
                        order = o,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedHistoryList(
    records: List<HistoryRecord>,
    category: Category,
    conditionPhotoMap: Map<Int, Boolean>,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    isAnyDialogOpen: Boolean,
) {
    val groupedRecords = remember(records) { records.groupBy { it.recordTime.atZone(ZoneId.systemDefault()).toLocalDate() }.mapValues { entry -> entry.value.sortedBy { it.recordTime } }.toSortedMap(compareByDescending { it }) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        groupedRecords.forEach { (date, items) ->
            val isSingle = items.size == 1
            stickyHeader {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatDateHeader(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isSingle) { Text(text = formatTime(items.first().recordTime), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            items(items.size) { index ->
                val record = items[index]
                HistoryItemWrapper(record = record, showTime = !isSingle, onItemClick = { onItemClick(record) }, onDeleteSwipe = { onDeleteSwipe(record) }, isAnyDialogOpen = isAnyDialogOpen) {
                    val hasOptionData = (category.hasOption && conditionPhotoMap[record.id] == true)
                    HistoryItemBody(category, record, hasOptionData)
                }
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWrapper(
    record: HistoryRecord,
    showTime: Boolean,
    onItemClick: () -> Unit,
    onDeleteSwipe: () -> Unit,
    isAnyDialogOpen: Boolean,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) { if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDeleteSwipe() }
    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen && (dismissState.currentValue != SwipeToDismissBoxValue.Settled)) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false, backgroundContent = { val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else Color.Transparent; Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White) } }) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onItemClick).padding(vertical = 1.dp), shape = androidx.compose.ui.graphics.RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (showTime) { Text(text = formatTime(record.recordTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(4.dp)) }
                content()
            }
        }
    }
}

@Composable
fun HistoryItemBody(category: Category, record: HistoryRecord, hasOption: Boolean) {
    when (category) {
        Category.BP_AND_PULSE -> (record as? BpAndPulse)?.let { VitalRecordItemContent(it) }
        Category.GLUCOSE_AND_HBA1C -> (record as? GlucoseAndHbA1c)?.let { GlucoseRecordItemContent(it) }
        Category.HEIGHT_AND_WEIGHT -> (record as? HeightAndWeight)?.let { HeightWeightRecordItemContent(it) }
        Category.CONDITION_AT_VISIT -> (record as? ConditionAtVisit)?.let { ConditionMemoContent(it, hasOption) }
        Category.MEDICATION -> { /* 統合画面では表示しない */ }
    }
}

@Composable
fun HeightWeightRecordItemContent(record: HeightAndWeight) {
    val bmi = record.calculateBMI()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp)); Text(text = record.height?.let { "${it}cm" } ?: "---", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp)); Text(text = record.weight?.let { "${it}kg" } ?: "---", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(8.dp)); Text(text = "${HealthThresholds.HEALTH_LABEL_BMI}: ${if (bmi > 0) "%.1f".format(bmi) else "---"}", style = MaterialTheme.typography.labelSmall)
        if (bmi > 0) { val (bmiLabel, alertLevel) = record.getBmiResult(); Spacer(modifier = Modifier.width(2.dp)); Text(text = "($bmiLabel)", fontSize = 10.sp, color = if (alertLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red) }
    }
}

@Composable
fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val (gStatus, gLevel) = record.getGlucoseResult()
    val (hStatus, hLevel) = record.getHbA1cResult()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${HealthThresholds.HEALTH_LABEL_GLUCOSE}: ${record.glucose?.let { "$it mg/dL" } ?: "---"}", style = MaterialTheme.typography.labelSmall)
        if (record.glucose != null) { Spacer(modifier = Modifier.width(2.dp)); Text(text = "($gStatus)", fontSize = 10.sp, color = if (gLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red, fontWeight = if (gLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal) }
        Spacer(modifier = Modifier.width(8.dp)); Text(text = "${HealthThresholds.HEALTH_LABEL_HBA1C}: ${record.hba1c?.let { "$it%" } ?: "---"}", style = MaterialTheme.typography.labelSmall)
        if (record.hba1c != null) { Spacer(modifier = Modifier.width(2.dp)); Text(text = "($hStatus)", fontSize = 10.sp, color = if (hLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red, fontWeight = if (hLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal) }
    }
}

@Composable
fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
        if (!record.title.isNullOrBlank()) Text(text = record.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp)); Text(text = record.condition ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (hasPhoto) { Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = "写真あり", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)); Spacer(modifier = Modifier.width(4.dp)) }
            Text(text = "記録者: ${record.author}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun BoxScope.VerticalScrollIndicator(scrollState: ScrollState) {
    val barHeight = 60.dp; val density = LocalDensity.current; val viewportHeight = with(density) { scrollState.viewportSize.toDp() }; val maxOffset = viewportHeight - barHeight; val scrollFraction by remember { derivedStateOf { if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f } }; val isBottomSelected by remember { derivedStateOf { scrollState.value > (scrollState.maxValue / 2) } }
    Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { repeat(2) { index -> val isSelected = if (index == 0) !isBottomSelected else isBottomSelected; Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))) } }; Box(modifier = Modifier.width(4.dp).height(barHeight).align(Alignment.TopEnd).offset(y = maxOffset * scrollFraction).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
}

@Composable
fun VitalRecordItemContent(record: BpAndPulse) {
    val results = record.getVitalResults()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp)); Text(text = "${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Rounded.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp)); Text(text = "${record.pulse ?: "---"} bpm", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp)); Text(text = "${record.bodyTemperature?.let { "%.1f".format(it) } ?: "---"} ℃", style = MaterialTheme.typography.labelMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_HIGH_BP, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_HIGH_BP }); VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_LOW_BP, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_LOW_BP }); VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_TACHYCARDIA, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_TACHYCARDIA }); VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_BRADYCARDIA, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_BRADYCARDIA }); VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_FEVER, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_FEVER }); VitalStatusIndicator(label = HealthThresholds.VITAL_LABEL_HYPOTHERMIA, isActive = results.any { it.first == HealthThresholds.VITAL_LABEL_HYPOTHERMIA })
        }
    }
}

@Composable
fun VitalStatusIndicator(label: String, isActive: Boolean) { Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal), color = if (isActive) MaterialTheme.colorScheme.error else Color.LightGray.copy(alpha = 0.6f)) }

@Composable
fun EmptyState(message: String, description: String? = null, icon: ImageVector) { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)); Spacer(modifier = Modifier.height(16.dp)); Text(text = message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline); if (description != null) { Spacer(modifier = Modifier.height(8.dp)); Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), textAlign = TextAlign.Center) } } }

@Preview(showBackground = true)
@Composable
fun UnifiedRecordScreenPreview() { MaterialTheme { Text("Preview") } }
