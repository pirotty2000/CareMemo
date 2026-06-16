package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

// 健康指標の判定基準
object HealthThresholds {
    // 高血圧・低血圧・頻脈・徐脈の判定ルールとなる数値
    const val BP_HIGH_SYSTOLIC = 140.0  // 高血圧：血圧（上）
    const val BP_HIGH_DIASTOLIC = 90.0  // 高血圧：血圧（下）
    const val BP_LOW_SYSTOLIC = 100.0   // 低血圧：血圧（上）
    const val BP_LOW_DIASTOLIC = 60.0   // 低血圧：血圧（下）
    const val PULSE_HIGH = 100.0        // 頻脈
    const val PULSE_LOW = 50.0          // 徐脈

    // 血糖値・HbA1cの判定基準
    const val GLUCOSE_NORMAL_HIGH = 99.0
    const val GLUCOSE_NORMAL_LOW = 70.0
    const val HBA1C_GOOD = 5.5
    const val HBA1C_PREDIABETES = 6.0
    const val HBA1C_DIABETES = 6.5

    // BMIの判定基準
    const val BMI_NORMAL_LOW = 18.5
    const val BMI_NORMAL_HIGH = 25.0
    const val BMI_OBESITY_1 = 30.0
    const val BMI_OBESITY_2 = 35.0
    const val BMI_OBESITY_3 = 40.0

    // 指標の説明文（文字テンプレートを使用し、定数と連動）
    val BP_EXPLANATION = """
        血圧グラフの見方：
        ・赤い線（血圧・上）が、上の薄い緑色の範囲（${BP_LOW_SYSTOLIC.toInt()}〜${BP_HIGH_SYSTOLIC.toInt()}）にあれば正常です。
        ・青い線（血圧・下）が、下の薄い緑色の範囲（${BP_LOW_DIASTOLIC.toInt()}〜${BP_HIGH_DIASTOLIC.toInt()}）にあれば正常です。
        
        ※ いずれかの線が緑色の範囲より上にあれば「高血圧」、下にあれば「低血圧」の目安となります。
    """.trimIndent()

    val PULSE_EXPLANATION = """
        脈拍グラフの見方：
        ・線が薄い緑色の範囲（${PULSE_LOW.toInt()}〜${PULSE_HIGH.toInt()}）にあれば正常です。
        
        ※ 範囲より上にあれば「頻脈」、下にあれば「徐脈」の目安となります。
    """.trimIndent()

    val GLUCOSE_EXPLANATION = """
        血糖値グラフの見方：
        ・線が薄い緑色の範囲（${GLUCOSE_NORMAL_LOW.toInt()}〜${GLUCOSE_NORMAL_HIGH.toInt()}）にあれば正常範囲（空腹時などの目安）です。
    """.trimIndent()

    val HBA1C_EXPLANATION = """
        HbA1cグラフの見方：
        ・薄い緑色（$HBA1C_GOOD％以下）：良好
        ・薄い黄色（$HBA1C_PREDIABETES％〜6.4％）：糖尿病予備軍
        ・薄い赤色（$HBA1C_DIABETES％以上）：糖尿病が強く疑われる値
    """.trimIndent()

    val BMI_EXPLANATION = """
        BMIグラフの見方：
        ・薄い緑色（${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満）：普通体重
        ・薄い青色（${BMI_NORMAL_LOW}未満）：低体重（低栄養への注意）
        ・薄い赤色（${BMI_OBESITY_2}以上）：高度な肥満（３度以上）
        
        【判定基準（WHO）】
        ・${BMI_NORMAL_LOW}未満：低体重
        ・${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満：普通体重
        ・${BMI_NORMAL_HIGH}〜${BMI_OBESITY_1}未満：肥満(１度)
        ・${BMI_OBESITY_1}〜${BMI_OBESITY_2}未満：肥満(２度)
        ・${BMI_OBESITY_2}〜${BMI_OBESITY_3}未満：肥満(３度)
        ・${BMI_OBESITY_3}以上：肥満(４度)
    """.trimIndent()
}

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
    val person by viewModel.currentPerson.collectAsState()

    // カテゴリや利用者が変更されたらデータをリロード
    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, currentCategory)
        viewModel.clearCurrentRecord()
    }

    // 年齢計算
    val age = remember(person) {
        person?.birthday?.let { birthdayInstant ->
            val birthDate = birthdayInstant.atZone(ZoneId.systemDefault()).toLocalDate()
            val now = LocalDate.now()
            java.time.Period.between(birthDate, now).years
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        val titleText = if (person != null) {
                            "${person?.name}さん（${age}歳）の利用者記録"
                        } else {
                            "利用者記録"
                        }
                        Text(titleText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    },
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
                    // グラフを表示（スクロールバーとページインジケーター付き）
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 16.dp) // インジケーター用の余白
                        ) {
                            GraphView(records, currentCategory)
                            Spacer(modifier = Modifier.height(32.dp)) // 下部の余白
                        }

                        // 簡易スクロールバー
                        if (scrollState.maxValue > 0) {
                            val barHeight = 60.dp
                            val density = LocalDensity.current
                            val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
                            val maxOffset = viewportHeight - barHeight
                            val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
                            
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

                        // ページインジケーター（右側中央）
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isPage2 = scrollState.value > scrollState.maxValue / 2 && scrollState.maxValue > 0
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (!isPage2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isPage2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
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
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 年 (4桁)
            CompactTextField(
                value = yearText,
                onValueChange = { 
                    if (it.length <= 4) {
                        yearText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier.weight(1.8f),
                onFocusChanged = { if (it.isFocused) yearText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isYearError,
                suffix = { Text("年", style = MaterialTheme.typography.bodySmall) }
            )
            // 月
            CompactTextField(
                value = monthText,
                onValueChange = { 
                    if (it.length <= 2) {
                        monthText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier.weight(1.2f),
                onFocusChanged = { if (it.isFocused) monthText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isMonthError,
                suffix = { Text("月", style = MaterialTheme.typography.bodySmall) }
            )
            // 日
            CompactTextField(
                value = dayText,
                onValueChange = { 
                    if (it.length <= 2) {
                        dayText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier.weight(1.2f),
                onFocusChanged = { if (it.isFocused) dayText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isDayError,
                suffix = { Text("日", style = MaterialTheme.typography.bodySmall) }
            )
            // 時
            CompactTextField(
                value = hourText,
                onValueChange = { 
                    if (it.length <= 2) {
                        hourText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier.weight(1.1f),
                onFocusChanged = { if (it.isFocused) hourText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isHourError,
                suffix = { Text(":", style = MaterialTheme.typography.bodySmall) }
            )
            // 分
            CompactTextField(
                value = minuteText,
                onValueChange = { 
                    if (it.length <= 2) {
                        minuteText = it
                        isUserModifiedTime = true
                    }
                },
                modifier = Modifier.weight(1f),
                onFocusChanged = { if (it.isFocused) minuteText = "" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
    var showHelpDialog by remember { mutableStateOf<String?>(null) }

    // ヘルプダイアログ
    if (showHelpDialog != null) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = null },
            title = { Text("数値の目安") },
            text = { Text(showHelpDialog!!) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = null }) {
                    Text("閉じる")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (categoryType == Category.BP_AND_PULSE) {
            // 血圧グラフ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("血圧", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showHelpDialog = HealthThresholds.BP_EXPLANATION }) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "説明を表示", modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("血圧(上)", data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpSystolic?.toDouble() ?: 0.0) }, Color.Red),
                    ChartLineData("血圧(下)", data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpDiastolic?.toDouble() ?: 0.0) }, Color.Blue)
                )
                // 2つの独立した正常値バンドを表示
                val ranges = listOf(
                    ChartRangeHighlight(HealthThresholds.BP_LOW_SYSTOLIC, HealthThresholds.BP_HIGH_SYSTOLIC, Color(0xFFE8F5E9)), // 上の正常範囲
                    ChartRangeHighlight(HealthThresholds.BP_LOW_DIASTOLIC, HealthThresholds.BP_HIGH_DIASTOLIC, Color(0xFFE8F5E9)) // 下の正常範囲
                )
                
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 70.0, maxYConstraint = 160.0)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 脈拍グラフ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("脈拍", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showHelpDialog = HealthThresholds.PULSE_EXPLANATION }) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "説明を表示", modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("脈拍", data.map { it.recordTime.toEpochMilli().toDouble() to (it.pulse?.toDouble() ?: 0.0) }, Color(0xFF4CAF50))
                )
                val ranges = listOf(ChartRangeHighlight(HealthThresholds.PULSE_LOW, HealthThresholds.PULSE_HIGH, Color(0xFFE8F5E9)))
                
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 40.0, maxYConstraint = 110.0)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }
        } else if (categoryType == Category.GLUCOSE_AND_HBA1C) {
            // 血糖値グラフ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("血糖値", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showHelpDialog = HealthThresholds.GLUCOSE_EXPLANATION }) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "説明を表示", modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("血糖値", data.map { it.recordTime.toEpochMilli().toDouble() to (it.glucose?.toDouble() ?: 0.0) }, Color.Magenta)
                )
                val ranges = listOf(ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_LOW, HealthThresholds.GLUCOSE_NORMAL_HIGH, Color(0xFFE8F5E9)))
                
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 20.0, ranges = ranges, minYConstraint = 60.0, maxYConstraint = 110.0)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // HbA1cグラフ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HbA1c", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showHelpDialog = HealthThresholds.HBA1C_EXPLANATION }) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "説明を表示", modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("HbA1c", data.map { it.recordTime.toEpochMilli().toDouble() to (it.hba1c ?: 0.0) }, Color.Red)
                )
                val ranges = listOf(
                    ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                    ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, 6.4, Color(0xFFFFFDE7)),
                    ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
                )
                
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 0.5, ranges = ranges, minYConstraint = 3.0, maxYConstraint = 7.0)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }
        } else if (categoryType == Category.HEIGHT_AND_WEIGHT) {
            // 体重グラフ
            Text("体重", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("体重", data.map { it.recordTime.toEpochMilli().toDouble() to (it.weight ?: 0.0) }, Color.Blue)
                )
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 10.0, minYConstraint = 40.0, showDecimal = true)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BMIグラフ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BMI", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showHelpDialog = HealthThresholds.BMI_EXPLANATION }) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "説明を表示", modifier = Modifier.size(20.dp), tint = Color.Gray)
                }
            }
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                val chartDataList = listOf(
                    ChartLineData("BMI", data.map { it.recordTime.toEpochMilli().toDouble() to calculateBMI(it) }, Color.Red)
                )
                // 3段構えのハイライト
                val ranges = listOf(
                    ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)), // 低体重: 薄い青
                    ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)), // 普通体重: 薄い緑
                    ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE)) // 肥満3度以上: 薄い赤
                )
                
                if (chartDataList.any { it.points.isNotEmpty() }) {
                    LineChart(chartDataList, stepY = 1.0, ranges = ranges, minYConstraint = 10.0, maxYConstraint = 35.0)
                } else {
                    Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
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

data class ChartRangeHighlight(
    val startValue: Double,
    val endValue: Double,
    val color: Color
)

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

    // スクロール状態の管理
    val scrollState = rememberScrollState()
    
    // データ点数に基づいて必要な幅を計算
    val allPoints = dataList.flatMap { it.points }
    if (allPoints.isEmpty()) return
    
    val pointCount = allPoints.size
    val minX = allPoints.minOf { it.first }
    val maxX = allPoints.maxOf { it.first }
    val xRange = if (maxX - minX == 0.0) 1.0 else maxX - minX

    // 軸範囲の計算
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
    val paddingLeft = 50.dp
    val paddingTop = 40.dp
    val paddingBottom = 40.dp
    val paddingRight = 20.dp

    // 最新データへ自動スクロール
    LaunchedEffect(pointCount) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 凡例 (固定)
        Box(modifier = Modifier.padding(start = paddingLeft, top = 4.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                dataList.forEach { lineData ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(lineData.color)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lineData.label, style = legendStyle.copy(fontSize = 11.sp))
                    }
                }
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            // Y軸ラベル (左側に固定)
            Canvas(modifier = Modifier
                .width(paddingLeft)
                .fillMaxHeight()) {
                val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                val topPx = paddingTop.toPx()
                
                for (i in 0..yStepsCount) {
                    val yVal = minY + stepY * i
                    val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                    val label = if (showDecimal || stepY <= 1.0) "%.1f".format(yVal) else yVal.toInt().toString()
                    val textLayout = textMeasurer.measure(label, labelStyle)
                    drawText(textLayout, topLeft = Offset(size.width - textLayout.size.width - 8.dp.toPx(), py - textLayout.size.height / 2))
                }
            }

            // グラフ本体 (横スクロール可能)
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val leftBufferPx = with(density) { 32.dp.toPx() }
                val rightBufferPx = with(density) { 8.dp.toPx() } // 4dpから8dpに微調整
                
                // 1年（365日）を画面幅（availableWidth）とする
                val availableWidthPx = with(density) { maxWidth.toPx() } - rightBufferPx
                val oneYearMillis = 365.0 * 24 * 60 * 60 * 1000.0
                
                // 全体の期間（最低でも1年分は確保）
                val totalDuration = maxOf(maxX - minX, oneYearMillis)
                // コンテンツ幅 = (期間比率 * 表示幅) + 左右のパディング
                val graphContentWidthPx = (totalDuration / oneYearMillis) * availableWidthPx + leftBufferPx + rightBufferPx
                
                Box(modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)) {
                    
                    val graphWidthDp = with(density) { graphContentWidthPx.toFloat().toDp() }
                    Canvas(modifier = Modifier
                        .width(graphWidthDp)
                        .fillMaxHeight()) {
                        val chartWidth = size.width - leftBufferPx - rightBufferPx
                        val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                        val topPx = paddingTop.toPx()
                        val startX = leftBufferPx
                        
                        // --- 0. Range Highlights ---
                        ranges.forEach { range ->
                            val pyStart = topPx + chartHeight - ((range.startValue - minY) / yRange).toFloat() * chartHeight
                            val pyEnd = topPx + chartHeight - ((range.endValue - minY) / yRange).toFloat() * chartHeight
                            val top = pyEnd.coerceIn(topPx, topPx + chartHeight)
                            val bottom = pyStart.coerceIn(topPx, topPx + chartHeight)
                            if (bottom > top) {
                                drawRect(color = range.color, topLeft = Offset(startX, top), size = androidx.compose.ui.geometry.Size(chartWidth, bottom - top))
                            }
                        }

                        // --- 2. 罫線 (水平) ---
                        for (i in 0..yStepsCount) {
                            val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(color = Color.LightGray.copy(alpha = 0.5f), start = Offset(startX, py), end = Offset(startX + chartWidth, py), strokeWidth = 1.dp.toPx())
                        }

                        // --- 罫線 (垂直: 四半期ごと) と X軸ラベル ---
                        val quarterMillis = oneYearMillis / 4.0 // 約91.25日
                        var currentX = minX
                        while (currentX <= maxX || currentX <= minX + totalDuration) {
                            val px = startX + ((currentX - minX) / totalDuration).toFloat() * chartWidth
                            
                            if (px <= startX + chartWidth) {
                                drawLine(color = Color.LightGray.copy(alpha = 0.3f), start = Offset(px, topPx), end = Offset(px, topPx + chartHeight), strokeWidth = 1.dp.toPx())
                                
                                val dateStr = DateTimeFormatter.ofPattern("yy/MM/dd")
                                    .withLocale(Locale.JAPAN)
                                    .format(Instant.ofEpochMilli(currentX.toLong()).atZone(ZoneId.systemDefault()))
                                val textLayout = textMeasurer.measure(dateStr, labelStyle)
                                drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, topPx + chartHeight + 4.dp.toPx()))
                            }
                            currentX += quarterMillis
                            if (quarterMillis == 0.0) break // 安全策
                        }

                        // --- 3. Limit Line ---
                        limits.forEach { limit ->
                            val py = topPx + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
                            if (py in topPx..(topPx + chartHeight)) {
                                drawLine(color = limit.color.copy(alpha = 0.6f), start = Offset(startX, py), end = Offset(startX + chartWidth, py), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                                val labelLayout = textMeasurer.measure(limit.label, limitLabelStyle.copy(color = limit.color))
                                drawText(labelLayout, topLeft = Offset(startX + 4.dp.toPx(), if (limit.isLabelAbove) py - labelLayout.size.height - 2.dp.toPx() else py + 2.dp.toPx()))
                            }
                        }

                        // --- 4. グラフ線 ---
                        dataList.forEach { lineData ->
                            val path = Path()
                            val sortedPoints = lineData.points.sortedBy { it.first }
                            sortedPoints.forEachIndexed { index, (x, y) ->
                                val px = startX + ((x - minX) / totalDuration).toFloat() * chartWidth
                                val py = topPx + chartHeight - ((y - minY) / yRange).toFloat() * chartHeight
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                                val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(y) else y.toInt().toString()
                                val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                                drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLayout.size.height - 2.dp.toPx()))
                            }
                            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
                        }
                        
                        // 軸の線
                        drawLine(Color.Gray, Offset(startX, topPx), Offset(startX, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                        drawLine(Color.Gray, Offset(startX, topPx + chartHeight), Offset(startX + chartWidth, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                    }
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFocusChanged: (FocusState) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged(onFocusChanged),
        interactionSource = interactionSource,
        enabled = true,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                suffix = suffix,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape
                    )
                }
            )
        }
    )
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
        bmi < HealthThresholds.BMI_NORMAL_LOW -> "低体重"
        bmi < HealthThresholds.BMI_NORMAL_HIGH -> "普通体重"
        bmi < HealthThresholds.BMI_OBESITY_1 -> "肥満(１度)"
        bmi < HealthThresholds.BMI_OBESITY_2 -> "肥満(２度)"
        bmi < HealthThresholds.BMI_OBESITY_3 -> "肥満(３度)"
        else -> "肥満(４度)"
    }
}

fun checkLowBloodPressureAndBradycardia(record: BpAndPulse): String {
    val systolic = record.bpSystolic ?: 120
    val diastolic = record.bpDiastolic ?: 80
    val pulse = record.pulse ?: 70

    val isHighBp = systolic >= HealthThresholds.BP_HIGH_SYSTOLIC || diastolic >= HealthThresholds.BP_HIGH_DIASTOLIC
    val isLowBp = systolic < HealthThresholds.BP_LOW_SYSTOLIC || diastolic < HealthThresholds.BP_LOW_DIASTOLIC
    val isBradycardia = pulse <= HealthThresholds.PULSE_LOW
    val isTachycardia = pulse >= HealthThresholds.PULSE_HIGH

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
