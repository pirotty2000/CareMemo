package jp.mydns.fujiwara.carememo.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.ChartLineData
import jp.mydns.fujiwara.carememo.ui.components.ChartRangeHighlight
import jp.mydns.fujiwara.carememo.ui.components.LineChart
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExpansionScreen(
    viewModel: PersonDetailViewModel,
    personId: Int,
    category: Category,
    initialGraphIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val records by viewModel.filteredRecords.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    // 画面を横向きに固定
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    LaunchedEffect(personId, category) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, category)
    }

    val listState = rememberLazyListState()
    var highlightedIndex by remember { mutableIntStateOf(initialGraphIndex) }

    // 初期スクロールとハイライト
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            listState.scrollToItem(initialGraphIndex)
            delay(500) // 少し待ってからハイライトを消す演出
            highlightedIndex = -1
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "${currentPerson?.getMaskedName(isNameMaskingEnabled) ?: ""} 様 - ${category.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val graphCount = when (category) {
                        Category.BP_AND_PULSE -> 3
                        Category.GLUCOSE_AND_HBA1C -> 2
                        Category.HEIGHT_AND_WEIGHT -> 2
                        else -> 0
                    }

                    items(graphCount) { index ->
                        val isHighlighted = index == initialGraphIndex && highlightedIndex == index
                        val borderColor by animateColorAsState(
                            targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(durationMillis = 1000),
                            label = "Highlight"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor, MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.padding(4.dp).height(210.dp)) { // 360dp高に最適化
                                SingleGraphInLandscape(records, category, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 横画面用に特定のインデックスのグラフのみを描画する
 */
@Composable
fun SingleGraphInLandscape(records: List<Any>, category: Category, index: Int) {
    // 共通のX軸範囲計算
    val allTimes = records.mapNotNull { 
        when (it) {
            is BpAndPulse -> it.recordTime.toEpochMilli().toDouble()
            is GlucoseAndHbA1c -> it.recordTime.toEpochMilli().toDouble()
            is HeightAndWeight -> it.recordTime.toEpochMilli().toDouble()
            else -> null
        }
    }
    val globalMinX = allTimes.minOrNull()
    val globalMaxX = allTimes.maxOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        when (category) {
            Category.BP_AND_PULSE -> {
                when (index) {
                    0 -> {
                        Text(HealthThresholds.HEALTH_LABEL_BP, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                        val sysPoints = data.filter { it.bpSystolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpSystolic!!.toDouble() }
                        val diaPoints = data.filter { it.bpDiastolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpDiastolic!!.toDouble() }
                        val chartDataList = listOf(ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(上)", sysPoints, Color.Red), ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(下)", diaPoints, Color.Blue))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(ChartRangeHighlight(HealthThresholds.BP_LOW_SYSTOLIC, HealthThresholds.BP_HIGH_SYSTOLIC, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BP_LOW_DIASTOLIC, HealthThresholds.BP_HIGH_DIASTOLIC, Color(0xFFE8F5E9)))
                            LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 70.0, maxYConstraint = 160.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                    1 -> {
                        Text(HealthThresholds.HEALTH_LABEL_PULSE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                        val pulsePoints = data.filter { it.pulse != null }.map { it.recordTime.toEpochMilli().toDouble() to it.pulse!!.toDouble() }
                        val chartDataList = listOf(ChartLineData("脈拍", pulsePoints, Color(0xFF4CAF50)))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(ChartRangeHighlight(HealthThresholds.PULSE_LOW, HealthThresholds.PULSE_HIGH, Color(0xFFE8F5E9)))
                            LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 40.0, maxYConstraint = 110.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                    2 -> {
                        Text(HealthThresholds.HEALTH_LABEL_BODY_TEMP, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                        val tempPoints = data.filter { it.bodyTemperature != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bodyTemperature!! }
                        val chartDataList = listOf(ChartLineData("体温", tempPoints, Color(0xFFFF9800)))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(ChartRangeHighlight(HealthThresholds.TEMP_LOW, HealthThresholds.TEMP_HIGH, Color(0xFFE8F5E9)))
                            LineChart(chartDataList, stepY = 0.5, ranges = ranges, minYConstraint = 35.0, maxYConstraint = 39.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                }
            }
            Category.GLUCOSE_AND_HBA1C -> {
                when (index) {
                    0 -> {
                        Text(HealthThresholds.HEALTH_LABEL_GLUCOSE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                        val glucoses = data.mapNotNull { it.glucose?.toDouble() }
                        val glucosePoints = data.filter { it.glucose != null }.map { it.recordTime.toEpochMilli().toDouble() to it.glucose!!.toDouble() }
                        val chartDataList = listOf(ChartLineData("血糖値", glucosePoints, Color.Magenta))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_LOW, HealthThresholds.GLUCOSE_NORMAL_HIGH, Color(0xFFE8F5E9)))
                            val minG = glucoses.minOrNull() ?: 70.0
                            val maxG = glucoses.maxOrNull() ?: 110.0
                            LineChart(chartDataList, stepY = 50.0, ranges = ranges, minYConstraint = minG - 10.0, maxYConstraint = maxG + 10.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                    1 -> {
                        Text(HealthThresholds.HEALTH_LABEL_HBA1C, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                        val hba1cs = data.mapNotNull { it.hba1c }
                        val hba1cPoints = data.filter { it.hba1c != null }.map { it.recordTime.toEpochMilli().toDouble() to it.hba1c!! }
                        val chartDataList = listOf(ChartLineData("HbA1c", hba1cPoints, Color.Red))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(
                                ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                                ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, HealthThresholds.HBA1C_DIABETES, Color(0xFFFFFDE7)),
                                ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
                            )
                            val minH = hba1cs.minOrNull() ?: 5.0
                            val maxH = hba1cs.maxOrNull() ?: 6.0
                            LineChart(chartDataList, stepY = 0.5, ranges = ranges, minYConstraint = minH - 0.5, maxYConstraint = maxH + 0.5, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                }
            }
            Category.HEIGHT_AND_WEIGHT -> {
                when (index) {
                    0 -> {
                        Text(HealthThresholds.HEALTH_LABEL_WEIGHT, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                        val weights = data.mapNotNull { it.weight }
                        val weightPoints = data.filter { it.weight != null }.map { it.recordTime.toEpochMilli().toDouble() to it.weight!! }
                        val chartDataList = listOf(ChartLineData("体重", weightPoints, Color.Blue))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val minW = weights.minOrNull() ?: 50.0
                            val maxW = weights.maxOrNull() ?: 60.0
                            LineChart(chartDataList, stepY = 5.0, minYConstraint = minW - 2.0, maxYConstraint = maxW + 2.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                    1 -> {
                        Text(HealthThresholds.HEALTH_LABEL_BMI, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                        val bmis = data.map { it.calculateBMI() }.filter { it > 0.0 }
                        val bmiPoints = data.map { it.recordTime.toEpochMilli().toDouble() to it.calculateBMI() }.filter { it.second > 0.0 }
                        val chartDataList = listOf(ChartLineData("BMI", bmiPoints, Color.Red))
                        if (chartDataList.any { it.points.isNotEmpty() }) {
                            val ranges = listOf(ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)), ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE)))
                            val minB = bmis.minOrNull() ?: 20.0
                            val maxB = bmis.maxOrNull() ?: 25.0
                            LineChart(chartDataList, stepY = 2.0, ranges = ranges, minYConstraint = minB - 1.0, maxYConstraint = maxB + 1.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("データがありません") }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
