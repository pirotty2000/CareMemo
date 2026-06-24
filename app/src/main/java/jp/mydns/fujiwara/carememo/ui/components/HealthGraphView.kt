package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthGraphView(
    records: List<Any>,
    categoryType: Category,
    onExpandGraph: ((Int) -> Unit)? = null
) {
    var showHelpDialog by remember { mutableStateOf<String?>(null) }
    
    // 全データを通じた共通のX軸（時間軸）の範囲を計算
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (categoryType) {
            Category.BP_AND_PULSE -> {
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_BP, HealthThresholds.BP_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(0) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    val sysPoints = data.filter { it.bpSystolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpSystolic!!.toDouble() }
                    val diaPoints = data.filter { it.bpDiastolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpDiastolic!!.toDouble() }
                    val chartDataList = listOf(ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(上)", sysPoints, Color.Red), ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(下)", diaPoints, Color.Blue))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.BP_LOW_SYSTOLIC, HealthThresholds.BP_HIGH_SYSTOLIC, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BP_LOW_DIASTOLIC, HealthThresholds.BP_HIGH_DIASTOLIC, Color(0xFFE8F5E9)))
                    if (chartDataList.any { it.points.isNotEmpty() }) LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 70.0, maxYConstraint = 160.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX) else Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
                Spacer(modifier = Modifier.height(12.dp))
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_PULSE, HealthThresholds.PULSE_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(1) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    val pulsePoints = data.filter { it.pulse != null }.map { it.recordTime.toEpochMilli().toDouble() to it.pulse!!.toDouble() }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_PULSE, pulsePoints, Color(0xFF4CAF50)))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.PULSE_LOW, HealthThresholds.PULSE_HIGH, Color(0xFFE8F5E9)))
                    if (chartDataList.any { it.points.isNotEmpty() }) LineChart(chartDataList, stepY = 10.0, ranges = ranges, minYConstraint = 40.0, maxYConstraint = 110.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX) else Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
                Spacer(modifier = Modifier.height(12.dp))
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_BODY_TEMP, HealthThresholds.TEMP_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(2) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                    val tempPoints = data.filter { it.bodyTemperature != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bodyTemperature!! }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_BODY_TEMP, tempPoints, Color(0xFFFF9800)))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.TEMP_LOW, HealthThresholds.TEMP_HIGH, Color(0xFFE8F5E9)))
                    if (chartDataList.any { it.points.isNotEmpty() }) LineChart(chartDataList, stepY = 0.5, ranges = ranges, minYConstraint = 35.0, maxYConstraint = 39.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX) else Text("データがありません", modifier = Modifier.align(Alignment.Center))
                }
            }
            Category.GLUCOSE_AND_HBA1C -> {
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_GLUCOSE, HealthThresholds.GLUCOSE_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(0) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                    val glucoses = data.mapNotNull { it.glucose?.toDouble() }
                    val glucosePoints = data.filter { it.glucose != null }.map { it.recordTime.toEpochMilli().toDouble() to it.glucose!!.toDouble() }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_GLUCOSE, glucosePoints, Color.Magenta))
                    val ranges = listOf(ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_LOW, HealthThresholds.GLUCOSE_NORMAL_HIGH, Color(0xFFE8F5E9)))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && glucoses.isNotEmpty()) {
                        val minG = glucoses.minOrNull() ?: 70.0
                        val maxG = glucoses.maxOrNull() ?: 110.0
                        LineChart(dataList = chartDataList, stepY = 50.0, ranges = ranges, minYConstraint = minG - 10.0, maxYConstraint = maxG + 10.0, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_HBA1C, HealthThresholds.HBA1C_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(1) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                    val hba1cs = data.mapNotNull { it.hba1c }
                    val hba1cPoints = data.filter { it.hba1c != null }.map { it.recordTime.toEpochMilli().toDouble() to it.hba1c!! }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_HBA1C, hba1cPoints, Color.Red))
                    val ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, HealthThresholds.HBA1C_DIABETES, Color(0xFFFFFDE7)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
                    )
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && hba1cs.isNotEmpty()) {
                        val minH = hba1cs.minOrNull() ?: 5.0
                        val maxH = hba1cs.maxOrNull() ?: 6.0
                        LineChart(dataList = chartDataList, stepY = 0.5, ranges = ranges, minYConstraint = minH - 0.5, maxYConstraint = maxH + 0.5, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            Category.HEIGHT_AND_WEIGHT -> {
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_WEIGHT, "", onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(0) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    val weights = data.mapNotNull { it.weight }
                    val weightPoints = data.filter { it.weight != null }.map { it.recordTime.toEpochMilli().toDouble() to it.weight!! }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_WEIGHT, weightPoints, Color.Blue))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && weights.isNotEmpty()) {
                        val minW = weights.minOf { it }
                        val maxW = weights.maxOf { it }
                        LineChart(dataList = chartDataList, stepY = 5.0, minYConstraint = minW - 2.0, maxYConstraint = maxW + 2.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                GraphTitleWithHelp(HealthThresholds.HEALTH_LABEL_BMI, HealthThresholds.BMI_EXPLANATION, onShowHelp = { showHelpDialog = it }, onExpand = onExpandGraph?.let { { it(1) } })
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    val data = records.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                    val bmis = data.map { it.calculateBMI() }.filter { it > 0.0 }
                    val bmiPoints = data.map { it.recordTime.toEpochMilli().toDouble() to it.calculateBMI() }.filter { it.second > 0.0 }
                    val chartDataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_BMI, bmiPoints, Color.Red))
                    val ranges = listOf(ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)), ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)), ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE)))
                    
                    if (chartDataList.any { it.points.isNotEmpty() } && bmis.isNotEmpty()) {
                        val minB = bmis.minOf { it }
                        val maxB = bmis.maxOf { it }
                        LineChart(dataList = chartDataList, stepY = 2.0, ranges = ranges, minYConstraint = minB - 1.0, maxYConstraint = maxB + 1.0, showDecimal = true, fixedMinX = globalMinX, fixedMaxX = globalMaxX)
                    } else {
                        Text("データがありません", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun GraphTitleWithHelp(
    title: String,
    helpContent: String,
    onShowHelp: (String) -> Unit,
    onExpand: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (helpContent.isNotBlank()) {
            IconButton(onClick = { onShowHelp(helpContent) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "目安の表示", modifier = Modifier.size(18.dp), tint = Color.Gray)
            }
        }
        if (onExpand != null) {
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ZoomOutMap, contentDescription = "拡大表示", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
