package jp.mydns.fujiwara.carememo.ui.screens

/**
 * Screen : UnifiedRecordScreen
 *
 * 【画面名】
 * 記録詳細・履歴（カテゴリ別統合画面）
 *
 * 【役割】
 * 選択された利用者の特定のカテゴリ（身長・体重、バイタル、等）に関する記録を
 * 一覧形式（履歴）またはグラフ形式で表示し、データの管理や外部出力を提供する画面。
 *
 * 【カテゴリ】
 * (A) ある利用者のある日時の、ある値を保持する。一覧とグラフを持つ
 * 1．身長・体重：身長と体重を記録、BMIを自動計算
 * 2．バイタル：血圧(上)、血圧(下)、脈拍、体温を記録
 * 3．血糖値・HbA1c：血糖値とHbA1cを記録
 * (B) ある利用者のある日時の、状況を文章で保持する。検索機能を持つ。
 * 1．所見メモ：タイトル、記録者、所見メモを保持する。写真を添付できる
 * (C) ある利用者のある日時に確認した、ある日のステータスを保持する
 * 1．服薬管理：服薬状況について、未確認・未服用・介助・服用の4つのステータスを
 *            朝・昼・夕・寝る前の4つの単位で日別に記録する
 *
 * 【主な機能】
 * ・履歴表示：日付ごとにグループ化された記録のタイムライン表示。
 * ・グラフ表示：(A)のみ。バイタルや体重等の数値データを時系列グラフで可視化（対応カテゴリのみ）。
 * ・カテゴリ切り替え：上部のセレクターバーによる表示カテゴリの動的な変更。
 * ・検索・絞り込み：(B)のみ。所見メモ等のテキスト検索機能。
 * ・データ管理：スワイプによる記録の削除、および詳細画面への遷移（新規登録・編集）。
 * ・PDF出力：表示中のデータを期間指定やパスワード保護付きでPDF化し、共有（印刷等）する機能。
 * ・アラート表示：健康指標の閾値に基づいた異常値（高血圧、発熱等）の視覚的な強調。
 *
 * 【遷移】
 * ← MainScreen（戻るボタン）
 * → ConditionDetailScreen（所見メモの新規登録・編集）
 * → HealthRecordDetailScreen（バイタル・身体測定等の新規登録・編集）
 * → GraphExpansionScreen（グラフの拡大表示）
 * → MedicationScreen（「服薬管理」カテゴリ選択時）
 *
 * 【使用するViewModel】
 * ・PersonDetailViewModel：利用者情報および各種健康記録の管理。
 * ・PersonConditionViewModel：訪問時所見および写真データの管理。
 *
 * 【備考】
 * カテゴリの特性（グラフの有無、検索可否）に応じてUIを動的に再構成する。
 * PDF出力時の外部アプリ遷移に伴い、一時的なロックバイパス（LockBypass）制御を行う。
 *
 */

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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateHeader
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecordScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    initialCategoryType: Category,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToConditionDetail: (Int, Int) -> Unit,
    onNavigateToHealthRecordDetail: (Int, Category, Int) -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToMedication: (Int) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }
    
    // タブレット用：現在選択されている所見メモのID (-1: 未選択, 0: 新規作成)
    var selectedConditionId by rememberSaveable { mutableIntStateOf(-1) }
    
    // ユーザーの表示モード設定（初期値は履歴: true）
    var preferredShowHistory by rememberSaveable { mutableStateOf(true) }
    
    val records by viewModel.filteredRecords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val conditionPhotoMap by conditionViewModel.getConditionPhotoMap(viewModel.records).collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noRecordsMsgFormat = stringResource(R.string.error_no_records_for_pdf)
    val pdfExportFailedMsg = stringResource(R.string.error_pdf_export_failed)

    var showPdfSettingsDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

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
        conditionViewModel.loadPerson(personId)
        viewModel.setCategory(currentCategory)
        // カテゴリが切り替わったら選択をリセット
        selectedConditionId = -1
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
                            defaultTitle = stringResource(R.string.app_name)
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        val categoryName = stringResource(currentCategory.displayNameRes)
                        if (isExpanded && currentCategory == Category.CONDITION_AT_VISIT) {
                            IconButton(onClick = { selectedConditionId = 0 }) {
                                Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                            }
                        }
                        IconButton(onClick = {
                            if (records.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(noRecordsMsgFormat.format(categoryName))
                                }
                            } else {
                                showPdfSettingsDialog = true
                            }
                        }) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = stringResource(R.string.pdf_export)
                            )
                        }
                    }
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
        floatingActionButton = { FloatingActionButton(onClick = { if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, 0) else onNavigateToHealthRecordDetail(personId, currentCategory, 0) }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_new)) } }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AlertDialog(onDismissRequest = { recordToDelete = null }, title = { Text(stringResource(R.string.delete_data_title)) }, text = { Text(stringResource(R.string.delete_confirm_message)) }, confirmButton = { TextButton(onClick = { recordToDelete?.let { viewModel.deleteRecord(it) }; recordToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text(stringResource(R.string.cancel)) } })
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.height(4.dp))
            
            // 検索・絞り込みバー (スマホ時、またはタブレットの非所見メモ時)
            if (currentCategory.hasSearch && !(isExpanded && currentCategory == Category.CONDITION_AT_VISIT)) { 
                val categoryName = stringResource(currentCategory.displayNameRes)
                OutlinedTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search_placeholder, categoryName)) }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear)) } } }, singleLine = true, shape = MaterialTheme.shapes.medium) 
            }
            
            // グラフを持つカテゴリのみ表示切り替えボタンを出す（スマホサイズのみ）
            if (currentCategory.hasGraph && !isExpanded) { 
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) { 
                    SegmentedButton(
                        selected = preferredShowHistory, 
                        onClick = { preferredShowHistory = true }, 
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2), 
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_history)) }
                    SegmentedButton(
                        selected = !preferredShowHistory, 
                        onClick = { preferredShowHistory = false }, 
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2), 
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_graph)) } 
                } 
            }

            Box(modifier = Modifier.weight(1f)) {
                if (records.isEmpty()) { 
                    EmptyState(message = stringResource(R.string.empty_records), description = stringResource(R.string.empty_records_description), icon = Icons.Outlined.Description) 
                } else if (isExpanded && currentCategory.hasGraph) {
                    // --- タブレット・横向き（数値データ系）: 2カラムレイアウト ---
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 左側: 履歴リスト (比率 1)
                        Box(modifier = Modifier.weight(1f)) {
                            UnifiedHistoryList(
                                records = records.filterIsInstance<HistoryRecord>(),
                                category = currentCategory,
                                conditionPhotoMap = conditionPhotoMap,
                                onItemClick = { record -> 
                                    if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, record.id) 
                                    else onNavigateToHealthRecordDetail(personId, currentCategory, record.id) 
                                },
                                onDeleteSwipe = { record -> recordToDelete = record },
                                isAnyDialogOpen = recordToDelete != null
                            )
                        }
                        // 右側: グラフ (比率 1.5)
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.weight(1.5f)) {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp)) {
                                HealthGraphView(
                                    records = records,
                                    categoryType = currentCategory,
                                    onExpandGraph = { index -> onNavigateToGraphExpansion(personId, currentCategory, index) }
                                )
                            }
                            if (scrollState.maxValue > 0) {
                                VerticalScrollIndicator(scrollState)
                            }
                        }
                    }
                } else if (isExpanded && currentCategory == Category.CONDITION_AT_VISIT) {
                    // --- タブレット・横向き（所見メモ）: リスト・詳細レイアウト ---
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 左側: 簡易履歴リスト (比率 1)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // タブレット時のみ、検索ボックスをここに配置
                            val categoryName = stringResource(currentCategory.displayNameRes)
                            OutlinedTextField(
                                value = searchQuery, 
                                onValueChange = { viewModel.updateSearchQuery(it) }, 
                                modifier = Modifier.fillMaxWidth(), 
                                placeholder = { Text(stringResource(R.string.search_placeholder, categoryName)) }, 
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, 
                                trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear)) } } }, 
                                singleLine = true, 
                                shape = MaterialTheme.shapes.medium
                            )
                            
                            Box(modifier = Modifier.weight(1f)) {
                                UnifiedHistoryList(
                                    records = records.filterIsInstance<HistoryRecord>(),
                                    category = currentCategory,
                                    conditionPhotoMap = conditionPhotoMap,
                                    selectedRecordId = selectedConditionId, // 選択状態を渡す
                                    onItemClick = { record -> selectedConditionId = record.id },
                                    onDeleteSwipe = { record -> 
                                        if (selectedConditionId == record.id) selectedConditionId = -1
                                        recordToDelete = record 
                                    },
                                    isAnyDialogOpen = recordToDelete != null
                                )
                            }
                        }
                        // 右側: 詳細表示・編集 (比率 2)
                        Box(modifier = Modifier.weight(2f)) {
                            ConditionDetailPane(
                                viewModel = viewModel,
                                conditionViewModel = conditionViewModel,
                                personId = personId,
                                conditionId = selectedConditionId,
                                onNavigateToPhotoPreview = { _, pId, cId ->
                                    onNavigateToConditionDetail(pId, cId)
                                },
                                onNavigateToFullScreen = { _, _ ->
                                    // タブレット版でのフルスクリーン表示は必要に応じて実装
                                }
                            )
                        }
                    }
                } else if (preferredShowHistory || !currentCategory.hasGraph) {
                    UnifiedHistoryList(records = records.filterIsInstance<HistoryRecord>(), category = currentCategory, conditionPhotoMap = conditionPhotoMap, onItemClick = { record -> if (currentCategory == Category.CONDITION_AT_VISIT) onNavigateToConditionDetail(personId, record.id) else onNavigateToHealthRecordDetail(personId, currentCategory, record.id) }, onDeleteSwipe = { record -> recordToDelete = record }, isAnyDialogOpen = recordToDelete != null)
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) { Column(modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 16.dp)) { HealthGraphView(records = records, categoryType = currentCategory, onExpandGraph = { index -> onNavigateToGraphExpansion(personId, currentCategory, index) }); Spacer(modifier = Modifier.height(80.dp)) }; if (scrollState.maxValue > 0) { VerticalScrollIndicator(scrollState) } }
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
                val allPhotos = if (currentCategory.hasOption && photos) conditionViewModel.getAllPhotosForPerson(personId) else emptyList()
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
                        snackbarHostState.showSnackbar(pdfExportFailedMsg)
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
    selectedRecordId: Int = -1, // 選択中IDを受け取れるように拡張
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
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatDateHeader(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isSingle) { Text(text = formatTime(items.first().recordTime), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            items(items.size) { index ->
                val record = items[index]
                val isSelected = record.id == selectedRecordId // 選択判定
                HistoryItemWrapper(
                    record = record, 
                    showTime = !isSingle, 
                    isSelected = isSelected, // ラッパーに渡す
                    onItemClick = { onItemClick(record) }, 
                    onDeleteSwipe = { onDeleteSwipe(record) }, 
                    isAnyDialogOpen = isAnyDialogOpen
                ) {
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
    isSelected: Boolean = false, // 追加
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
    
    // 選択状態に応じた色
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onItemClick)
                .padding(vertical = 1.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (showTime) {
                    Text(
                        text = formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
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
    val context = LocalContext.current
    val bmi = record.calculateBMI()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Height,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.height?.let { "${it}cm" } ?: "---",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Scale,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = record.weight?.let { "${it}kg" } ?: "---",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_BMI)}: ${if (bmi > 0) "%.1f".format(bmi) else "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (bmi > 0) {
            val (bmiLabel, alertLevel) = record.getBmiResult(context)
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($bmiLabel)",
                fontSize = 10.sp,
                color = if (alertLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red
            )
        }
    }
}

@Composable
fun GlucoseRecordItemContent(record: GlucoseAndHbA1c) {
    val context = LocalContext.current
    val (gStatus, gLevel) = record.getGlucoseResult(context)
    val (hStatus, hLevel) = record.getHbA1cResult(context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_GLUCOSE)}: ${record.glucose?.let { "$it mg/dL" } ?: "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (record.glucose != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($gStatus)",
                fontSize = 10.sp,
                color = if (gLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red,
                fontWeight = if (gLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stringResource(HealthThresholds.HEALTH_LABEL_HBA1C)}: ${record.hba1c?.let { "$it%" } ?: "---"}",
            style = MaterialTheme.typography.labelSmall
        )
        if (record.hba1c != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($hStatus)",
                fontSize = 10.sp,
                color = if (hLevel == HealthThresholds.AlertLevel.NORMAL) Color.Blue else Color.Red,
                fontWeight = if (hLevel != HealthThresholds.AlertLevel.NORMAL) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
        if (!record.title.isNullOrBlank()) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record.condition ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhoto) {
                Icon(
                    imageVector = Icons.Rounded.AddAPhoto,
                    contentDescription = "写真あり",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "記録者: ${record.author}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun BoxScope.VerticalScrollIndicator(scrollState: ScrollState) {
    val barHeight = 60.dp
    val density = LocalDensity.current
    val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
    val maxOffset = viewportHeight - barHeight

    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
        }
    }
    val isBottomSelected by remember {
        derivedStateOf { scrollState.value > (scrollState.maxValue / 2) }
    }

    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) { index ->
            val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.3f
                        )
                    )
            )
        }
    }

    Box(
        modifier = Modifier
            .width(4.dp)
            .height(barHeight)
            .align(Alignment.TopEnd)
            // ここを修正：ラムダ形式にすることでスクロール中の再構成を回避
            .offset {
                IntOffset(
                    x = 0,
                    y = (maxOffset * scrollFraction).roundToPx()
                )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    )
}

@Composable
fun VitalRecordItemContent(record: BpAndPulse) {
    val context = LocalContext.current
    val results = record.getVitalResults(context)

    val highBpLabel = stringResource(HealthThresholds.VITAL_LABEL_HIGH_BP)
    val lowBpLabel = stringResource(HealthThresholds.VITAL_LABEL_LOW_BP)
    val tachycardiaLabel = stringResource(HealthThresholds.VITAL_LABEL_TACHYCARDIA)
    val bradycardiaLabel = stringResource(HealthThresholds.VITAL_LABEL_BRADYCARDIA)
    val feverLabel = stringResource(HealthThresholds.VITAL_LABEL_FEVER)
    val hypothermiaLabel = stringResource(HealthThresholds.VITAL_LABEL_HYPOTHERMIA)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bpSystolic ?: "---"}/${record.bpDiastolic ?: "---"} mmHg",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${record.pulse ?: "---"} bpm", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Rounded.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${record.bodyTemperature?.let { "%.1f".format(it) } ?: "---"} ℃",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VitalStatusIndicator(
                label = highBpLabel,
                isActive = results.any { it.first == highBpLabel }
            )
            VitalStatusIndicator(
                label = lowBpLabel,
                isActive = results.any { it.first == lowBpLabel }
            )
            VitalStatusIndicator(
                label = tachycardiaLabel,
                isActive = results.any { it.first == tachycardiaLabel }
            )
            VitalStatusIndicator(
                label = bradycardiaLabel,
                isActive = results.any { it.first == bradycardiaLabel }
            )
            VitalStatusIndicator(
                label = feverLabel,
                isActive = results.any { it.first == feverLabel }
            )
            VitalStatusIndicator(
                label = hypothermiaLabel,
                isActive = results.any { it.first == hypothermiaLabel }
            )
        }
    }
}

@Composable
fun VitalStatusIndicator(label: String, isActive: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal
        ),
        color = if (isActive) MaterialTheme.colorScheme.error else Color.LightGray.copy(alpha = 0.6f)
    )
}

@Composable
fun EmptyState(message: String, description: String? = null, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * タブレット・横向き時に右側に表示する所見メモ詳細ペイン
 */
@Composable
fun ConditionDetailPane(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    conditionId: Int,
    onNavigateToPhotoPreview: (android.net.Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val photos by conditionViewModel.currentConditionPhotos.collectAsState()
    val isProcessing by conditionViewModel.isProcessing.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()

    val dateTimeState = rememberDateTimeInputState()

    // 編集モードの状態
    var isEditing by remember { mutableStateOf(false) }

    // 選択されたIDが変わったら編集モードを解除し、初期化をリセット
    LaunchedEffect(conditionId) {
        isEditing = conditionId == 0
    }

    val memo = remember(records, conditionId) { 
        records.asSequence().filterIsInstance<ConditionAtVisit>().find { it.id == conditionId }
    }

    // 入力用状態
    var title by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    // 初期値セット
    LaunchedEffect(memo, defaultRecorderName, conditionId) {
        if (memo != null) {
            title = memo.title ?: ""
            condition = memo.condition ?: ""
            author = memo.author
            dateTimeState.setFromInstant(memo.recordTime)
        } else if (conditionId == 0) {
            title = ""
            condition = ""
            author = defaultRecorderName
            dateTimeState.setFromInstant(java.time.Instant.now())
        }
    }

    // データのロード
    LaunchedEffect(personId, conditionId) {
        conditionViewModel.loadPerson(personId)
        conditionViewModel.setSelectedConditionId(if (conditionId != 0) conditionId else null)
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoUri != null) {
            onNavigateToPhotoPreview(tempPhotoUri!!, personId, conditionId)
        }
    }

    if (conditionId == -1) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("左のリストから記録を選択してください", color = MaterialTheme.colorScheme.outline)
                Text("右上の「＋」から新しい記録を追加できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (conditionId == 0) "新規作成" else "記録の詳細",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isEditing && conditionId != 0) {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }
        }

        if (isEditing) {
            // 編集モード
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(state = dateTimeState)
                    HorizontalDivider(thickness = 0.5.dp)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("タイトル (任意)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("記録者") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("所見メモ") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (conditionId == 0) { /* nop */ } else isEditing = false }, modifier = Modifier.weight(1f)) { Text("キャンセル") }
                        Button(
                            onClick = {
                                val recordTime = dateTimeState.toInstant() ?: java.time.Instant.now()
                                val newMemo = ConditionAtVisit(id = conditionId, personId = personId, title = title, condition = condition, author = author, recordTime = recordTime)
                                viewModel.saveRecord(newMemo)
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = author.isNotBlank() && condition.isNotBlank()
                        ) { Text("保存") }
                    }
                }
            }
        } else {
            // 参照モード
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    memo?.let { m ->
                        Text(text = formatTime(m.recordTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!m.title.isNullOrBlank()) {
                            Text(text = m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "記録者: ${m.author}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // 写真セクション
        Text(text = "写真 (${photos.size}/3)", style = MaterialTheme.typography.titleMedium)
        if (photos.isEmpty()) {
            Text("写真はありません", color = MaterialTheme.colorScheme.outline)
        } else {
            PhotoGrid(photos = photos, isEditable = isEditing, onPhotoClick = { onNavigateToFullScreen(it.photoFileName, it.caption) }, onDeletePhoto = { photoToDelete = it })
        }
        
        if (isEditing && photos.size < 3 && conditionId != 0) {
            Button(onClick = {
                val tempFile = java.io.File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            }, enabled = !isProcessing) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("撮影")
            }
        }
    }

    if (photoToDelete != null) {
        AlertDialog(onDismissRequest = { photoToDelete = null }, title = { Text("写真の削除") }, text = { Text("この写真を削除してもよろしいですか？") }, confirmButton = { TextButton(onClick = { photoToDelete?.let { conditionViewModel.deletePhoto(context, it) }; photoToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("削除") } }, dismissButton = { TextButton(onClick = { photoToDelete = null }) { Text("キャンセル") } })
    }
}
