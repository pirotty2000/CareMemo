package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.time.Instant
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
    @Suppress("UNUSED_PARAMETER") personId: Int,
    onBack: () -> Unit
) {
    // 現在選択されているカテゴリを管理
    var currentCategory by remember { mutableStateOf(initialCategoryType) }
    
    val currentRecord by viewModel.currentRecordState.collectAsState()
    val records by viewModel.records.collectAsState()

    // カテゴリが変更されたらデータをリロード
    LaunchedEffect(currentCategory, personId) {
        viewModel.loadRecords(personId, currentCategory)
        viewModel.clearCurrentRecord()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("利用者記録", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                )
                // カテゴリ切り替え用タブ (最新の SecondaryScrollableTabRow に修正)
                SecondaryScrollableTabRow(
                    selectedTabIndex = Category.entries.indexOf(currentCategory),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Category.entries.forEach { category ->
                        Tab(
                            selected = currentCategory == category,
                            onClick = { currentCategory = category },
                            text = { Text(category.displayName) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        var showHistory by remember { mutableStateOf(true) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- [上部] 入力フォーム ---
            InputForm(
                categoryType = currentCategory,
                recordData = currentRecord,
                personId = personId,
                records = records, // 履歴データを渡す
                onSave = { viewModel.saveRecord(it) },
                onClear = { viewModel.clearCurrentRecord() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(thickness = 1.dp)

            // --- [中央] 履歴/グラフ 切り替え (所見メモ以外) ---
            if (currentCategory != Category.CONDITION_AT_VISIT) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = showHistory,
                        onClick = { showHistory = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("履歴")
                    }
                    SegmentedButton(
                        selected = !showHistory,
                        onClick = { showHistory = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("グラフ")
                    }
                }
            }

            // --- [下部] メインコンテンツエリア ---
            Box(modifier = Modifier.weight(1f)) {
                if (currentCategory == Category.CONDITION_AT_VISIT || showHistory) {
                    // 履歴一覧を表示
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(records.size) { index ->
                            val record = records[index]
                            RecordListItem(
                                categoryType = currentCategory,
                                record = record,
                                onClick = { viewModel.selectRecord(record) },
                                isEditable = currentRecord == record
                            )
                        }
                    }
                } else {
                    // グラフを表示
                    Column(modifier = Modifier.fillMaxSize()) {
                        GraphView(records, currentCategory)
                    }
                }
            }
        }
    }
}

@Composable
fun InputForm(
    categoryType: Category,
    recordData: Any?,
    personId: Int,
    records: List<Any>,
    onSave: (Any?) -> Unit,
    onClear: () -> Unit
) {
    val isEditMode = recordData != null

    // 各入力項目の状態管理
    var yearText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("") }
    var minuteText by remember { mutableStateOf("") }

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

    // ユーザーが一度でも入力操作をしたかどうかのフラグ
    var isUserModifiedTime by remember { mutableStateOf(false) }

    // 編集対象が選択されたら値をセット、クリアされたらリセット
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
            isUserModifiedTime = true // 編集時は「ユーザー操作あり」として扱う

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
            // 新規モード時：ユーザーがまだ操作していなければ、年月日+00:00をセット
            if (!isUserModifiedTime) {
                val now = Instant.now().atZone(ZoneId.systemDefault())
                yearText = now.year.toString()
                monthText = now.monthValue.toString()
                dayText = now.dayOfMonth.toString()
                hourText = "00"
                minuteText = "00"
            }
            // データ項目のみリセット
            if (categoryType == Category.HEIGHT_AND_WEIGHT) {
                // 身長は最新の値をデフォルトセット
                val latestHeight = records.filterIsInstance<HeightAndWeight>()
                    .maxByOrNull { it.recordTime }?.height
                heightText = latestHeight?.toString() ?: ""
            } else {
                heightText = ""
            }
            weightText = ""
            bpSystolicText = ""
            bpDiastolicText = ""
            pulseText = ""
            glucoseText = ""
            hba1cText = ""
            titleText = ""
            authorText = ""
            conditionText = ""
        }
    }

    // バリデーションロジック
    val y = yearText.toIntOrNull()
    val m = monthText.toIntOrNull()
    val d = dayText.toIntOrNull()
    val hh = hourText.toIntOrNull() ?: 0
    val mm = minuteText.toIntOrNull() ?: 0

    val isYearError = y == null || y < 1900 || y > 2100
    val isMonthError = m == null || m < 1 || m > 12
    val isDayError = try {
        if (y != null && m != null && d != null) {
            d < 1 || d > java.time.YearMonth.of(y, m).lengthOfMonth()
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }
    val isHourError = hh !in 0..23
    val isMinuteError = mm !in 0..59

    val isDateTimeValid = !isYearError && !isMonthError && !isDayError && !isHourError && !isMinuteError

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 共通項目：記録日時（分割入力）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 年 (4桁)
            OutlinedTextField(
                value = yearText,
                onValueChange = { 
                    if (it.length <= 4) {
                        yearText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier
                    .weight(1.8f)
                    .onFocusChanged { if (it.isFocused) yearText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isYearError,
                suffix = { Text("年", style = MaterialTheme.typography.bodySmall) }
            )
            // 月
            OutlinedTextField(
                value = monthText,
                onValueChange = { 
                    if (it.length <= 2) {
                        monthText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .onFocusChanged { if (it.isFocused) monthText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isMonthError,
                suffix = { Text("月", style = MaterialTheme.typography.bodySmall) }
            )
            // 日
            OutlinedTextField(
                value = dayText,
                onValueChange = { 
                    if (it.length <= 2) {
                        dayText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .onFocusChanged { if (it.isFocused) dayText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isDayError,
                suffix = { Text("日", style = MaterialTheme.typography.bodySmall) }
            )
            // 時
            OutlinedTextField(
                value = hourText,
                onValueChange = { 
                    if (it.length <= 2) {
                        hourText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier
                    .weight(1.1f)
                    .onFocusChanged { if (it.isFocused) hourText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isHourError,
                suffix = { Text(":", style = MaterialTheme.typography.bodySmall) }
            )
            // 分
            OutlinedTextField(
                value = minuteText,
                onValueChange = { 
                    if (it.length <= 2) {
                        minuteText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) minuteText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isMinuteError
            )
        }

        // カテゴリ別項目
        when (categoryType) {
            Category.HEIGHT_AND_WEIGHT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("身長") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) heightText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("体重") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) weightText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            }
            Category.BP_AND_PULSE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = bpSystolicText,
                        onValueChange = { bpSystolicText = it },
                        label = { Text("血圧(上)") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) bpSystolicText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bpDiastolicText,
                        onValueChange = { bpDiastolicText = it },
                        label = { Text("血圧(下)") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) bpDiastolicText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pulseText,
                        onValueChange = { pulseText = it },
                        label = { Text("脈拍") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) pulseText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
            Category.GLUCOSE_AND_HBA1C -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = glucoseText,
                        onValueChange = { glucoseText = it },
                        label = { Text("血糖値") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) glucoseText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hba1cText,
                        onValueChange = { hba1cText = it },
                        label = { Text("HbA1c") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) hba1cText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            }
            Category.CONDITION_AT_VISIT -> {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("タイトル") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = authorText,
                    onValueChange = { authorText = it },
                    label = { Text("記録者") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = conditionText,
                    onValueChange = { conditionText = it },
                    label = { Text("所見メモ") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val recordTime = try {
                        val year = yearText.toInt()
                        val month = monthText.toInt()
                        val day = dayText.toInt()
                        val hour = hourText.toIntOrNull() ?: 0
                        val minute = minuteText.toIntOrNull() ?: 0
                        java.time.LocalDateTime.of(year, month, day, hour, minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    } catch (_: Exception) {
                        Instant.now()
                    }

                    val newRecord = when (categoryType) {
                        Category.HEIGHT_AND_WEIGHT -> {
                            val id = (recordData as? HeightAndWeight)?.id ?: 0
                            HeightAndWeight(
                                id = id,
                                personId = personId,
                                height = heightText.toDoubleOrNull(),
                                weight = weightText.toDoubleOrNull() ?: 0.0,
                                recordTime = recordTime
                            )
                        }
                        Category.BP_AND_PULSE -> {
                            val id = (recordData as? BpAndPulse)?.id ?: 0
                            BpAndPulse(
                                id = id,
                                personId = personId,
                                bpSystolic = bpSystolicText.toIntOrNull(),
                                bpDiastolic = bpDiastolicText.toIntOrNull(),
                                pulse = pulseText.toIntOrNull(),
                                recordTime = recordTime
                            )
                        }
                        Category.GLUCOSE_AND_HBA1C -> {
                            val id = (recordData as? GlucoseAndHbA1c)?.id ?: 0
                            GlucoseAndHbA1c(
                                id = id,
                                personId = personId,
                                glucose = glucoseText.toIntOrNull(),
                                hba1c = hba1cText.toDoubleOrNull(),
                                recordTime = recordTime
                            )
                        }
                        Category.CONDITION_AT_VISIT -> {
                            val id = (recordData as? ConditionAtVisit)?.id ?: 0
                            ConditionAtVisit(
                                id = id,
                                personId = personId,
                                title = titleText,
                                condition = conditionText,
                                author = authorText,
                                recordTime = recordTime
                            )
                        }
                    }
                    onSave(newRecord)
                },
                modifier = Modifier.weight(1f),
                enabled = isDateTimeValid && when (categoryType) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        weightText.toDoubleOrNull() != null
                    }
                    Category.CONDITION_AT_VISIT -> authorText.isNotBlank()
                    else -> true
                }
            ) {
                Text(if (isEditMode) "更新" else "保存")
            }

            if (isEditMode) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("キャンセル")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphView(records: List<Any>, categoryType: Category) {
    var selectedSubCategory by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (categoryType == Category.GLUCOSE_AND_HBA1C || categoryType == Category.HEIGHT_AND_WEIGHT) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                val labels = if (categoryType == Category.HEIGHT_AND_WEIGHT) {
                    listOf("体重", "BMI")
                } else {
                    listOf("血糖値", "HbA1c")
                }

                SegmentedButton(
                    selected = selectedSubCategory == 0,
                    onClick = { selectedSubCategory = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(labels[0])
                }
                SegmentedButton(
                    selected = selectedSubCategory == 1,
                    onClick = { selectedSubCategory = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(labels[1])
                }
            }
        }

        Box(
            modifier = Modifier
                .height(220.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            var yStep = 5.0
            var limits = emptyList<ChartLimitLine>()
            
            val chartDataList = when (categoryType) {
                Category.HEIGHT_AND_WEIGHT -> {
                    yStep = 1.0
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    if (selectedSubCategory == 0) {
                        listOf(ChartLineData("体重", data.map { it.recordTime.toEpochMilli().toDouble() to (it.weight ?: 0.0) }, Color.Blue))
                    } else {
                        listOf(ChartLineData("BMI", data.map { it.recordTime.toEpochMilli().toDouble() to calculateBMI(it) }, Color.Red))
                    }
                }
                Category.BP_AND_PULSE -> {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    limits = listOf(
                        ChartLimitLine("High BP ≥ 140", 140.0, Color.Gray, isLabelAbove = true),
                        ChartLimitLine("Low BP < 100", 100.0, Color.Gray, isLabelAbove = false)
                    )
                    listOf(
                        ChartLineData("血圧(上)", data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpSystolic?.toDouble() ?: 0.0) }, Color.Red),
                        ChartLineData("血圧(下)", data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpDiastolic?.toDouble() ?: 0.0) }, Color.Blue)
                    )
                }
                Category.GLUCOSE_AND_HBA1C -> {
                    val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                    if (selectedSubCategory == 0) {
                        listOf(ChartLineData("血糖値", data.map { it.recordTime.toEpochMilli().toDouble() to (it.glucose?.toDouble() ?: 0.0) }, Color.Magenta))
                    } else {
                        yStep = 0.5
                        limits = listOf(
                            ChartLimitLine("Upper Limit < 6.2", 6.2, Color.Gray, isLabelAbove = true),
                            ChartLimitLine("Lower Limit > 4.6", 4.6, Color.Gray, isLabelAbove = false)
                        )
                        listOf(ChartLineData("HbA1c", data.map { it.recordTime.toEpochMilli().toDouble() to (it.hba1c ?: 0.0) }, Color.Red))
                    }
                }
                else -> emptyList()
            }

            if (chartDataList.isNotEmpty() && chartDataList.any { it.points.isNotEmpty() }) {
                LineChart(chartDataList, stepY = yStep, limits = limits)
            } else {
                Text("データがありません", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

data class ChartLineData(
    val label: String,
    val points: List<Pair<Double, Double>>, // x: timestamp, y: value
    val color: Color
)

data class ChartLimitLine(
    val label: String,
    val value: Double,
    val color: Color,
    val isLabelAbove: Boolean
)

@Composable
fun LineChart(
    dataList: List<ChartLineData>,
    stepY: Double = 5.0,
    limits: List<ChartLimitLine> = emptyList()
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val valueLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val limitLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
    val legendStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val allPoints = dataList.flatMap { it.points }
        if (allPoints.isEmpty()) return@Canvas

        val minX = allPoints.minOf { it.first }
        val maxX = allPoints.maxOf { it.first }
        
        // データの最小最大と、Limit Line の値を考慮して軸範囲を決定
        val allYValues = allPoints.map { it.second } + limits.map { it.value }
        val minYInput = allYValues.minOf { it }
        val maxYInput = allYValues.maxOf { it }
        
        // --- 縦軸の範囲を「stepY 刻み」で計算 ---
        val minY = floor(minYInput / stepY) * stepY
        val maxY = ceil(maxYInput / stepY) * stepY
        
        val yRange = if (maxY - minY == 0.0) stepY else maxY - minY
        val yStepsCount = (yRange / stepY).toInt()

        val xRange = if (maxX - minX == 0.0) 1.0 else maxX - minX

        // パディング設定
        val paddingLeft = 50.dp.toPx()
        val paddingBottom = 40.dp.toPx()
        val paddingTop = 40.dp.toPx()
        val paddingRight = 20.dp.toPx()
        
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        // --- 1. 凡例の描画 ---
        var legendOffsetX = paddingLeft
        dataList.forEach { lineData ->
            drawCircle(lineData.color, radius = 5.dp.toPx(), center = Offset(legendOffsetX, paddingTop / 2))
            val legendLayout = textMeasurer.measure(lineData.label, legendStyle)
            drawText(legendLayout, topLeft = Offset(legendOffsetX + 8.dp.toPx(), paddingTop / 2 - legendLayout.size.height / 2))
            legendOffsetX += legendLayout.size.width + 32.dp.toPx()
        }

        // --- 2. 罫線と軸ラベルの描画 ---
        for (i in 0..yStepsCount) {
            val yVal = minY + stepY * i
            val py = paddingTop + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
            
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(paddingLeft, py),
                end = Offset(paddingLeft + chartWidth, py),
                strokeWidth = 1.dp.toPx()
            )
            
            val label = if (stepY >= 1.0) yVal.toInt().toString() else "%.1f".format(yVal)
            val textLayout = textMeasurer.measure(label, labelStyle)
            drawText(textLayout, topLeft = Offset(paddingLeft - textLayout.size.width - 4.dp.toPx(), py - textLayout.size.height / 2))
        }

        // 縦罫線 (日付軸)
        val xSteps = 3
        for (i in 0..xSteps) {
            val xVal = minX + (xRange / xSteps) * i
            val px = paddingLeft + (i.toFloat() / xSteps) * chartWidth
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(px, paddingTop),
                end = Offset(px, paddingTop + chartHeight),
                strokeWidth = 1.dp.toPx()
            )
            val dateStr = DateTimeFormatter.ofPattern("MM/dd")
                .withLocale(Locale.JAPAN)
                .format(Instant.ofEpochMilli(xVal.toLong()).atZone(ZoneId.systemDefault()))
            val textLayout = textMeasurer.measure(dateStr, labelStyle)
            drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, paddingTop + chartHeight + 4.dp.toPx()))
        }

        // --- 3. Limit Line (境界線) の描画 ---
        limits.forEach { limit ->
            val py = paddingTop + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
            if (py in paddingTop..(paddingTop + chartHeight)) {
                drawLine(
                    color = limit.color.copy(alpha = 0.6f),
                    start = Offset(paddingLeft, py),
                    end = Offset(paddingLeft + chartWidth, py),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                
                val labelLayout = textMeasurer.measure(limit.label, limitLabelStyle.copy(color = limit.color))
                val labelY = if (limit.isLabelAbove) {
                    py - labelLayout.size.height - 2.dp.toPx()
                } else {
                    py + 2.dp.toPx()
                }
                // 左端に表示
                drawText(labelLayout, topLeft = Offset(paddingLeft + 4.dp.toPx(), labelY))
            }
        }

        // --- 4. グラフ線の描画 ---
        dataList.forEach { lineData ->
            val path = Path()
            lineData.points.forEachIndexed { index, (x, y) ->
                val px = paddingLeft + ((x - minX) / xRange).toFloat() * chartWidth
                val py = paddingTop + chartHeight - ((y - minY) / yRange).toFloat() * chartHeight
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                
                // 各ポイントに値を表示
                val valueStr = if (stepY >= 1.0) y.toInt().toString() else "%.1f".format(y)
                val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLayout.size.height - 2.dp.toPx()))
            }
            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
        }
        
        drawLine(Color.Gray, Offset(paddingLeft, paddingTop), Offset(paddingLeft, paddingTop + chartHeight), strokeWidth = 1.5.dp.toPx())
        drawLine(Color.Gray, Offset(paddingLeft, paddingTop + chartHeight), Offset(paddingLeft + chartWidth, paddingTop + chartHeight), strokeWidth = 1.5.dp.toPx())
    }
}

@Composable
fun RecordListItem(categoryType: Category, record: Any, onClick: () -> Unit, isEditable: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        colors = if (isEditable) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // リストアイテムの表示内容（例：日時、値など）
            val instant = when (record) {
                is HeightAndWeight -> record.recordTime
                is BpAndPulse -> record.recordTime
                is GlucoseAndHbA1c -> record.recordTime
                is ConditionAtVisit -> record.recordTime
                else -> null
            }
            val recordTimeStr = instant?.let { formatRecordTime(it) } ?: ""
            Text(text = "日時: $recordTimeStr", style = MaterialTheme.typography.labelMedium)
            
            Spacer(modifier = Modifier.height(4.dp))
            
            when (categoryType) {
                Category.HEIGHT_AND_WEIGHT -> {
                    if (record is HeightAndWeight) {
                        val bmi = calculateBMI(record)
                        val heightStr = record.height?.let { "${it}cm" } ?: "---"
                        val weightStr = record.weight?.let { "${it}kg" } ?: "---"
                        val bmiStr = if (bmi > 0) "%.1f".format(bmi) else "---"
                        val evaluation = if (bmi > 0) evaluateBMI(bmi) else "---"
                        
                        Text(
                            text = "身長: $heightStr, 体重: $weightStr, BMI: $bmiStr ($evaluation)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Category.BP_AND_PULSE -> {
                    if (record is BpAndPulse) {
                        val systolicStr = record.bpSystolic?.toString() ?: "---"
                        val diastolicStr = record.bpDiastolic?.toString() ?: "---"
                        val pulseStr = record.pulse?.toString() ?: "---"
                        val evaluation = checkLowBloodPressureAndBradycardia(record)
                        
                        Text(
                            text = "血圧: $systolicStr/$diastolicStr mmHg, 脈拍: $pulseStr bpm ($evaluation)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Category.GLUCOSE_AND_HBA1C -> {
                    if (record is GlucoseAndHbA1c) {
                        val glucoseStr = record.glucose?.let { "${it} mg/dL" } ?: "---"
                        val hba1cStr = record.hba1c?.let { "${it}%" } ?: "---"
                        
                        Text(
                            text = "血糖値: $glucoseStr, HbA1c: $hba1cStr",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Category.CONDITION_AT_VISIT -> {
                    if (record is ConditionAtVisit) {
                        Text(
                            text = "タイトル: ${record.title ?: "---"}, 記録者: ${record.author.ifBlank { "---" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// 補助関数の実装
fun calculateBMI(record: HeightAndWeight): Double {
    val h = record.height ?: 0.0
    val w = record.weight ?: 0.0
    val heightM = h / 100.0
    if (heightM <= 0.0) return 0.0
    return w / (heightM * heightM)
}

fun evaluateBMI(bmi: Double): String {
    return when {
        bmi <= 0.0 -> "-"
        bmi < 18.5 -> "低体重"
        bmi < 25.0 -> "標準体重"
        bmi < 30.0 -> "肥満（軽度）"
        else -> "肥満（重度以上）"
    }
}

fun checkLowBloodPressureAndBradycardia(record: BpAndPulse): String {
    val systolic = record.bpSystolic ?: 100
    val diastolic = record.bpDiastolic ?: 80
    val pulse = record.pulse ?: 70

    val isHighBp = systolic >= 140 || diastolic >= 90
    val isLowBp = systolic < 100
    val isBradycardia = pulse <= 50
    val isTachycardia = pulse >= 100

    return when {
        isHighBp && isTachycardia -> "高・頻"
        isHighBp && isBradycardia -> "高・徐"
        isLowBp && isTachycardia -> "低・頻"
        isLowBp && isBradycardia -> "低・徐"
        isHighBp -> "高血圧"
        isLowBp -> "低血圧"
        isTachycardia -> "頻脈"
        isBradycardia -> "徐脈"
        else -> "正常"
    }
}

/**
 * 記録日時を和暦含みの形式（例：2026(令和8)年6月14日 17:17）に変換する
 */
fun formatRecordTime(instant: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val localDateTime = instant.atZone(zoneId).toLocalDateTime()
    val localDate = localDateTime.toLocalDate()
    
    // 西暦部分のフォーマッタ
    val eraDate = JapaneseDate.from(localDate)
    val eraYearFormatter = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
    val eraYear = eraDate.get(java.time.temporal.ChronoField.YEAR_OF_ERA)
    val eraName = eraDate.format(eraYearFormatter)
    
    val year = localDate.year
    val month = localDate.monthValue
    val day = localDate.dayOfMonth
    val hour = localDateTime.hour
    val minute = localDateTime.minute
    
    return "%d(%s%d)年%d月%d日 %02d:%02d".format(year, eraName, eraYear, month, day, hour, minute)
}

@Preview(showBackground = true)
@Composable
fun UnifiedRecordScreenPreview() {
    // プレビュー用にダミーのViewModel（またはRepositoryをMockしたもの）が必要
    // ここでは簡易的に空の状態で表示確認
    MaterialTheme {
        Text("Preview requires a ViewModel with Repository")
    }
}
