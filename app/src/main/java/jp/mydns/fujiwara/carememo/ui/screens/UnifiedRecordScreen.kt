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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import jp.mydns.fujiwara.carememo.data.*
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
    onBack: () -> Unit,
) {
    // 現在選択されているカテゴリを管理
    var currentCategory by remember { mutableStateOf(initialCategoryType) }
    
    val currentRecord by viewModel.currentRecordState.collectAsState()
    val records by viewModel.records.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // カテゴリや利用者が変更されたらデータをリロード
    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, currentCategory)
        viewModel.clearCurrentRecord()
    }

    // 年齢計算
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
                        val titleText = if (currentPerson != null) {
                            "${currentPerson?.getMaskedName(isNameMaskingEnabled)}さん（${age}歳）の利用者記録"
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
                // カテゴリ切り替え用チップ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Category.entries.forEach { category ->
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
                            } else null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    ) { paddingValues ->
        var showHistory by remember { mutableStateOf(true) }
        var recordToDelete by remember { mutableStateOf<Any?>(null) }

        // 削除確認ダイアログ
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
            Spacer(modifier = Modifier.height(4.dp)) // カテゴリ選択欄との間に少し隙間を追加

            // --- [上部] 入力フォーム ---
            // 選択中のカテゴリーに応じた入力項目が表示されます。
            // 編集モード時には既存データが自動セットされます。
            InputForm(
                categoryType = currentCategory,
                recordData = currentRecord,
                personId = personId,
                records = records, // 履歴データを渡す
                onSave = { viewModel.saveRecord(it) },
                onClear = { viewModel.clearCurrentRecord() },
                snackbarHostState = snackbarHostState
            )

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
                        items(records.size, key = { index ->
                            // IDをキーにする（型に応じてキャストが必要）
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
                                        false // ダイアログで確認するため、一旦スワイプを差し戻す
                                    } else {
                                        false
                                    }
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
                } else {
                    // グラフを表示（スクロールバーとページインジケーター付き）
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
            } else {
                ""
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
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }
    val isHourError = hh !in 0..23
    val isMinuteError = mm !in 0..59

    val isDateTimeValid = !isYearError && !isMonthError && !isDayError && !isHourError && !isMinuteError

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "記録日時",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 年・月・日の各入力フィールド。入力桁数に応じて次の項目へ自動フォーカス移動するUI。
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
                        modifier = Modifier.weight(1.8f).focusRequester(yearFocusRequester),
                        onFocusChanged = { if (it.isFocused) yearText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        modifier = Modifier.weight(1.2f).focusRequester(monthFocusRequester),
                        onFocusChanged = { if (it.isFocused) monthText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        modifier = Modifier.weight(1.2f).focusRequester(dayFocusRequester),
                        onFocusChanged = { if (it.isFocused) dayText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isDayError,
                        suffix = { Text("日", style = MaterialTheme.typography.labelSmall) }
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
                        modifier = Modifier.weight(1.1f).focusRequester(hourFocusRequester),
                        onFocusChanged = { if (it.isFocused) hourText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isHourError,
                        suffix = { Text(":", style = MaterialTheme.typography.bodySmall) }
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
                        modifier = Modifier.weight(1.1f).focusRequester(minuteFocusRequester),
                        onFocusChanged = { if (it.isFocused) minuteText = "" },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isMinuteError
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (categoryType) {
                    Category.HEIGHT_AND_WEIGHT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { heightText = filterDecimal(it) },
                                label = { Text("身長") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) heightText = "" }
                                    .focusRequester(categoryFirstFocusRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = filterDecimal(it) },
                                label = { Text("体重") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) weightText = "" }
                                    .focusRequester(dataField2Requester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                                onValueChange = { bpSystolicText = filterInteger(it) },
                                label = { Text("血圧(上)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) bpSystolicText = "" }
                                    .focusRequester(categoryFirstFocusRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = bpDiastolicText,
                                onValueChange = { bpDiastolicText = filterInteger(it) },
                                label = { Text("血圧(下)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) bpDiastolicText = "" }
                                    .focusRequester(dataField2Requester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { dataField3Requester.requestFocus() }),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = pulseText,
                                onValueChange = { pulseText = filterInteger(it) },
                                label = { Text("脈拍") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) pulseText = "" }
                                    .focusRequester(dataField3Requester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                                onValueChange = { glucoseText = filterInteger(it) },
                                label = { Text("血糖値") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) glucoseText = "" }
                                    .focusRequester(categoryFirstFocusRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() }),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = hba1cText,
                                onValueChange = { hba1cText = filterDecimal(it) },
                                label = { Text("HbA1c") },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { if (it.isFocused) hba1cText = "" }
                                    .focusRequester(dataField2Requester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                singleLine = true
                            )
                        }
                    }
                    Category.CONDITION_AT_VISIT -> {
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            label = { Text("タイトル") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(categoryFirstFocusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dataField2Requester.requestFocus() })
                        )
                        OutlinedTextField(
                            value = authorText,
                            onValueChange = { authorText = it },
                            label = { Text("記録者") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(dataField2Requester),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dataField3Requester.requestFocus() })
                        )
                        TextField(
                            value = conditionText,
                            onValueChange = { conditionText = it },
                            label = { Text("所見メモ") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(dataField3Requester),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                }
            }

            val hasData = when (categoryType) {
                Category.HEIGHT_AND_WEIGHT -> heightText.isNotBlank() || weightText.isNotBlank()
                Category.BP_AND_PULSE -> bpSystolicText.isNotBlank() || bpDiastolicText.isNotBlank() || pulseText.isNotBlank()
                Category.GLUCOSE_AND_HBA1C -> glucoseText.isNotBlank() || hba1cText.isNotBlank()
                Category.CONDITION_AT_VISIT -> titleText.isNotBlank() || conditionText.isNotBlank()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        val recordTime = try {
                            val year = yearText.toInt()
                            val month = monthText.toInt()
                            val day = dayText.toInt()
                            val hour = hourText.toIntOrNull() ?: 0
                            val minute = minuteText.toIntOrNull() ?: 0
                            java.time.LocalDateTime.of(year, month, day, hour, minute, 0)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                        } catch (_: Exception) {
                            Instant.now()
                        }

                        if (!hasData) {
                            scope.launch {
                                snackbarHostState.showSnackbar("保存するデータがありません")
                            }
                            return@Button
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

                        if (!isEditMode) {
                            heightText = ""
                            weightText = ""
                            bpSystolicText = ""
                            bpDiastolicText = ""
                            pulseText = ""
                            glucoseText = ""
                            hba1cText = ""
                            titleText = ""
                            conditionText = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDateTimeValid && hasData && when (categoryType) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphView(records: List<Any>, categoryType: Category) {
    // 各健康指標の推移を表示するグラフ表示エリア。
    // カテゴリーごとに Y 軸のステップや範囲ガイド（色の背景）を個別に設定しています。
    var showHelpDialog by remember { mutableStateOf<String?>(null) }

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
        when (categoryType) {
            Category.BP_AND_PULSE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("血圧", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showHelpDialog = HealthThresholds.BP_EXPLANATION }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "説明を表示",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
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
                        ChartLineData(
                            "血圧(上)",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpSystolic?.toDouble() ?: 0.0) },
                            Color.Red
                        ),
                        ChartLineData(
                            "血圧(下)",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.bpDiastolic?.toDouble() ?: 0.0) },
                            Color.Blue
                        )
                    )
                    val ranges = listOf(
                        ChartRangeHighlight(
                            HealthThresholds.BP_LOW_SYSTOLIC,
                            HealthThresholds.BP_HIGH_SYSTOLIC,
                            Color(0xFFE8F5E9)
                        ),
                        ChartRangeHighlight(
                            HealthThresholds.BP_LOW_DIASTOLIC,
                            HealthThresholds.BP_HIGH_DIASTOLIC,
                            Color(0xFFE8F5E9)
                        )
                    )

                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            chartDataList,
                            stepY = 10.0,
                            ranges = ranges,
                            minYConstraint = 70.0,
                            maxYConstraint = 160.0
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("脈拍", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showHelpDialog = HealthThresholds.PULSE_EXPLANATION }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "説明を表示",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
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
                        ChartLineData(
                            "脈拍",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.pulse?.toDouble() ?: 0.0) },
                            Color(0xFF4CAF50)
                        )
                    )
                    val ranges = listOf(
                        ChartRangeHighlight(
                            HealthThresholds.PULSE_LOW,
                            HealthThresholds.PULSE_HIGH,
                            Color(0xFFE8F5E9)
                        )
                    )

                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            chartDataList,
                            stepY = 10.0,
                            ranges = ranges,
                            minYConstraint = 40.0,
                            maxYConstraint = 110.0
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            Category.GLUCOSE_AND_HBA1C -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("血糖値", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showHelpDialog = HealthThresholds.GLUCOSE_EXPLANATION }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "説明を表示",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
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
                        ChartLineData(
                            "血糖値",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.glucose?.toDouble() ?: 0.0) },
                            Color.Magenta
                        )
                    )
                    val ranges = listOf(
                        ChartRangeHighlight(
                            HealthThresholds.GLUCOSE_NORMAL_LOW,
                            HealthThresholds.GLUCOSE_NORMAL_HIGH,
                            Color(0xFFE8F5E9)
                        )
                    )

                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            chartDataList,
                            stepY = 20.0,
                            ranges = ranges,
                            minYConstraint = 60.0,
                            maxYConstraint = 110.0
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HbA1c", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showHelpDialog = HealthThresholds.HBA1C_EXPLANATION }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "説明を表示",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
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
                        ChartLineData(
                            "HbA1c",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.hba1c ?: 0.0) },
                            Color.Red
                        )
                    )
                    val ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, 6.4, Color(0xFFFFFDE7)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
                    )

                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            chartDataList,
                            stepY = 0.5,
                            ranges = ranges,
                            minYConstraint = 3.0,
                            maxYConstraint = 7.0
                        )
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            Category.HEIGHT_AND_WEIGHT -> {
                Text("体重", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                Box(
                    modifier = Modifier
                        .height(220.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    val chartDataList = listOf(
                        ChartLineData(
                            "体重",
                            data.map { it.recordTime.toEpochMilli().toDouble() to (it.weight ?: 0.0) },
                            Color.Blue
                        )
                    )
                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(chartDataList, stepY = 10.0, minYConstraint = 40.0, showDecimal = true)
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BMI", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showHelpDialog = HealthThresholds.BMI_EXPLANATION }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "説明を表示",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
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
                        ChartLineData(
                            "BMI",
                            data.map { it.recordTime.toEpochMilli().toDouble() to calculateBMI(it) },
                            Color.Red
                        )
                    )
                    val ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)),
                        ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE))
                    )

                    if (chartDataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            chartDataList,
                            stepY = 1.0,
                            ranges = ranges,
                            minYConstraint = 10.0,
                            maxYConstraint = 35.0
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

data class ChartLineData(
    val label: String,
    val points: List<Pair<Double, Double>>,
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

    val scrollState = rememberScrollState()
    
    val allPoints = dataList.flatMap { it.points }
    if (allPoints.isEmpty()) return
    
    val pointCount = allPoints.size
    val minX = allPoints.minOf { it.first }
    val maxX = allPoints.maxOf { it.first }

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

    LaunchedEffect(pointCount) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val leftBufferPx = with(density) { 32.dp.toPx() }
                val rightBufferPx = with(density) { 8.dp.toPx() }
                
                val availableWidthPx = with(density) { maxWidth.toPx() } - rightBufferPx
                val oneYearMillis = 365.0 * 24 * 60 * 60 * 1000.0
                
                val totalDuration = maxOf(maxX - minX, oneYearMillis)
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
                        
                        ranges.forEach { range ->
                            val pyStart = topPx + chartHeight - ((range.startValue - minY) / yRange).toFloat() * chartHeight
                            val pyEnd = topPx + chartHeight - ((range.endValue - minY) / yRange).toFloat() * chartHeight
                            val top = pyEnd.coerceIn(topPx, topPx + chartHeight)
                            val bottom = pyStart.coerceIn(topPx, topPx + chartHeight)
                            if (bottom > top) {
                                drawRect(color = range.color, topLeft = Offset(startX, top), size = androidx.compose.ui.geometry.Size(chartWidth, bottom - top))
                            }
                        }

                        for (i in 0..yStepsCount) {
                            val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(color = Color.LightGray.copy(alpha = 0.5f), start = Offset(startX, py), end = Offset(startX + chartWidth, py), strokeWidth = 1.dp.toPx())
                        }

                        val quarterMillis = oneYearMillis / 4.0
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
                            if (quarterMillis == 0.0) break
                        }

                        limits.forEach { limit ->
                            val py = topPx + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
                            if (py in topPx..(topPx + chartHeight)) {
                                drawLine(color = limit.color.copy(alpha = 0.6f), start = Offset(startX, py), end = Offset(startX + chartWidth, py), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                                val labelLayout = textMeasurer.measure(limit.label, limitLabelStyle.copy(color = limit.color))
                                drawText(labelLayout, topLeft = Offset(startX + 4.dp.toPx(), if (limit.isLabelAbove) py - labelLayout.size.height - 2.dp.toPx() else py + 2.dp.toPx()))
                            }
                        }

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
                        val glucoseStr = record.glucose?.let { "$it mg/dL" } ?: "---"
                        val hba1cStr = record.hba1c?.let { "$it%" } ?: "---"
                        
                        Text(
                            text = "血糖値: $glucoseStr, HbA1c: $hba1cStr",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Category.CONDITION_AT_VISIT -> {
                    (record as? ConditionAtVisit)?.let { 
                        Text(
                            text = "タイトル: ${it.title ?: "---"}, 記録者: ${it.author.ifBlank { "---" }}",
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
        keyboardActions = keyboardActions,
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
                    OutlinedTextFieldDefaults.Container(
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

fun formatRecordTime(instant: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val localDateTime = instant.atZone(zoneId).toLocalDateTime()
    val localDate = localDateTime.toLocalDate()
    
    val eraDate = JapaneseDate.from(localDate)
    val eraYearFormatter = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
    val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
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
    MaterialTheme {
        Text("Preview requires a ViewModel with Repository")
    }
}
