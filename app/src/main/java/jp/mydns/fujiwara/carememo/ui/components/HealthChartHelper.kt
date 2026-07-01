package jp.mydns.fujiwara.carememo.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.data.*

/**
 * グラフ描画に必要な設定情報を保持するクラス
 */
data class HealthChartConfig(
    val title: String,
    val helpContent: String = "",
    val dataList: List<ChartLineData>,
    val ranges: List<ChartRangeHighlight> = emptyList(),
    val limits: List<ChartLimitLine> = emptyList(),
    val stepY: Double = 5.0,
    val minYConstraint: Double? = null,
    val maxYConstraint: Double? = null,
    val showDecimal: Boolean = false
) {
    /**
     * サブタイトル（ヒント）を行ごとのリストとして取得する
     */
    fun getSubtitleLines(): List<String> =
        if (helpContent.isNotBlank()) {
            helpContent.split("\n").filter { it.isNotBlank() }
        } else emptyList()
}

object HealthChartHelper {

    /**
     * 全データを通じた共通のX軸（時間軸）の範囲を計算します
     */
    fun calculateGlobalXRange(records: List<Any>): Pair<Double?, Double?> {
        val allTimes = records.filterIsInstance<HistoryRecord>()
            .map { it.recordTime.toEpochMilli().toDouble() }

        return allTimes.minOrNull() to allTimes.maxOrNull()
    }

    /**
     * カテゴリーごとのグラフ数を返します
     */
    fun getGraphCount(category: Category): Int {
        return when (category) {
            Category.BP_AND_PULSE -> 3
            Category.GLUCOSE_AND_HBA1C -> 2
            Category.HEIGHT_AND_WEIGHT -> 2
            Category.CONDITION_AT_VISIT, Category.MEDICATION -> 0
        }
    }

    /**
     * 指定されたカテゴリーとインデックスに対応するグラフ設定を生成します
     */
    fun getChartConfig(
        context: Context,
        category: Category,
        index: Int,
        records: List<Any>,
        isDark: Boolean = false
    ): HealthChartConfig? {
        return when (category) {
            Category.BP_AND_PULSE -> getBpAndPulseConfig(context, index, records.filterIsInstance<BpAndPulse>(), isDark)
            Category.GLUCOSE_AND_HBA1C -> getGlucoseAndHbA1cConfig(context, index, records.filterIsInstance<GlucoseAndHbA1c>(), isDark)
            Category.HEIGHT_AND_WEIGHT -> getHeightAndWeightConfig(context, index, records.filterIsInstance<HeightAndWeight>(), isDark)
            Category.CONDITION_AT_VISIT, Category.MEDICATION -> null
        }
    }

    // --- ハイライト色の定義 ---
    private fun getWarningHighlight(isDark: Boolean) = if (isDark) Color(0xFF333112) else Color(0xFFFFFDE7)
    private fun getAlertHighlight(isDark: Boolean) = if (isDark) Color(0xFF3D1F1F) else Color(0xFFFFEBEE)
    private fun getInfoHighlight(isDark: Boolean) = if (isDark) Color(0xFF1A213D) else Color(0xFFE3F2FD)
    private fun getObesityColor2(isDark: Boolean) = if (isDark) Color(0xFF2D1A3D) else Color(0xFFF3E5F5) // 薄い紫
    private fun getObesityColor3(isDark: Boolean) = if (isDark) Color(0xFF4A148C) else Color(0xFFCE93D8) // 紫

    // --- ユーティリティ ---

    private fun mapRanges(ranges: List<HealthThresholds.VisualRange>, isDark: Boolean): List<ChartRangeHighlight> {
        return ranges.map {
            val color = when (it.level) {
                HealthThresholds.AlertLevel.ALERT -> getAlertHighlight(isDark)
                HealthThresholds.AlertLevel.WARNING -> getWarningHighlight(isDark)
                HealthThresholds.AlertLevel.INFO -> getInfoHighlight(isDark)
                HealthThresholds.AlertLevel.NORMAL -> if (isDark) Color.Transparent else Color.White
            }
            ChartRangeHighlight(it.start, it.end, color)
        }
    }

    private fun mapLimits(limits: List<HealthThresholds.VisualLimit>): List<ChartLimitLine> {
        return limits.map {
            ChartLimitLine(it.label, it.value, Color.Gray, it.isAbove)
        }
    }

    private fun getBpAndPulseConfig(context: Context, index: Int, data: List<BpAndPulse>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }

        return when (index) {
            0 -> { // 血圧
                val sysPoints = sortedData.filter { it.bpSystolic != null }.map { 
                    val noteId = HealthThresholds.evaluateVital(it.bpSystolic, null, null, null)
                        .firstOrNull { r -> r.second != HealthThresholds.AlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bpSystolic!!.toDouble(),
                        noteId?.let { id -> context.getString(id) }
                    )
                }
                val diaPoints = sortedData.filter { it.bpDiastolic != null }.map { 
                    val noteId = HealthThresholds.evaluateVital(null, it.bpDiastolic, null, null)
                        .firstOrNull { r -> r.second != HealthThresholds.AlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bpDiastolic!!.toDouble(),
                        noteId?.let { id -> context.getString(id) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_BP),
                    helpContent = HealthThresholds.getBpExplanation(context),
                    dataList = listOf(
                        ChartLineData("${context.getString(HealthThresholds.HEALTH_LABEL_BP)}(${context.getString(HealthThresholds.HEALTH_LABEL_SYSTOLIC_SHORT)})", sysPoints, Color.Red, "mmHg"),
                        ChartLineData("${context.getString(HealthThresholds.HEALTH_LABEL_BP)}(${context.getString(HealthThresholds.HEALTH_LABEL_DIASTOLIC_SHORT)})", diaPoints, Color.Blue, "mmHg")
                    ),
                    ranges = mapRanges(HealthThresholds.getBpRanges(), isDark),
                    stepY = 10.0,
                    minYConstraint = 70.0,
                    maxYConstraint = 160.0
                )
            }
            1 -> { // 脈拍
                val pulsePoints = sortedData.filter { it.pulse != null }.map { 
                    val noteId = HealthThresholds.evaluateVital(null, null, it.pulse, null)
                        .firstOrNull { r -> r.second != HealthThresholds.AlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.pulse!!.toDouble(),
                        noteId?.let { id -> context.getString(id) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_PULSE),
                    helpContent = HealthThresholds.getPulseExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_PULSE), pulsePoints, Color(0xFF4CAF50), "bpm")),
                    ranges = mapRanges(HealthThresholds.getPulseRanges(), isDark),
                    stepY = 10.0,
                    minYConstraint = 40.0,
                    maxYConstraint = 110.0
                )
            }
            2 -> { // 体温
                val tempPoints = sortedData.filter { it.bodyTemperature != null }.map { 
                    val noteId = HealthThresholds.evaluateVital(null, null, null, it.bodyTemperature)
                        .firstOrNull { r -> r.second != HealthThresholds.AlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bodyTemperature!!,
                        noteId?.let { id -> context.getString(id) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_BODY_TEMP),
                    helpContent = HealthThresholds.getTempExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_BODY_TEMP), tempPoints, Color(0xFFFF9800), "℃")),
                    ranges = mapRanges(HealthThresholds.getTempRanges(), isDark),
                    stepY = 0.5,
                    minYConstraint = 35.0,
                    maxYConstraint = 39.0,
                    showDecimal = true
                )
            }
            else -> null
        }
    }

    private fun getGlucoseAndHbA1cConfig(context: Context, index: Int, data: List<GlucoseAndHbA1c>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }
        
        return when (index) {
            0 -> { // 血糖値
                val glucoses = sortedData.mapNotNull { it.glucose?.toDouble() }
                val glucosePoints = sortedData.filter { it.glucose != null }.map { 
                    val (noteId, level) = HealthThresholds.evaluateGlucose(it.glucose)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.glucose!!.toDouble(),
                        if (level != HealthThresholds.AlertLevel.NORMAL) noteId?.let { id -> context.getString(id) } else null
                    )
                }
                val minG = glucoses.minOrNull() ?: 70.0
                val maxG = glucoses.maxOrNull() ?: 110.0
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_GLUCOSE),
                    helpContent = HealthThresholds.getGlucoseExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_GLUCOSE), glucosePoints, Color.Magenta, "mg/dL")),
                    ranges = mapRanges(HealthThresholds.getGlucoseRanges(), isDark),
                    limits = mapLimits(HealthThresholds.getGlucoseLimits()),
                    stepY = 50.0,
                    minYConstraint = minG - 10.0,
                    maxYConstraint = maxG + 10.0
                )
            }
            1 -> { // HbA1c
                val hba1cs = sortedData.mapNotNull { it.hba1c }
                val hba1cPoints = sortedData.filter { it.hba1c != null }.map { 
                    val (noteId, level) = HealthThresholds.evaluateHbA1c(it.hba1c)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.hba1c!!,
                        if (level != HealthThresholds.AlertLevel.NORMAL) noteId?.let { id -> context.getString(id) } else null
                    )
                }
                val minH = hba1cs.minOrNull() ?: 5.0
                val maxH = hba1cs.maxOrNull() ?: 6.0
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_HBA1C),
                    helpContent = HealthThresholds.getHbA1cExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_HBA1C), hba1cPoints, Color.Red, "%")),
                    ranges = mapRanges(HealthThresholds.getHbA1cRanges(), isDark),
                    limits = mapLimits(HealthThresholds.getHbA1cLimits()),
                    stepY = 0.5,
                    minYConstraint = minH - 0.5,
                    maxYConstraint = maxH + 0.5,
                    showDecimal = true
                )
            }
            else -> null
        }
    }

    private fun getHeightAndWeightConfig(context: Context, index: Int, data: List<HeightAndWeight>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }

        return when (index) {
            0 -> { // 体重
                val weights = sortedData.mapNotNull { it.weight }
                val weightPoints = sortedData.filter { it.weight != null }.map { 
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.weight!!,
                        null
                    )
                }
                val minW = weights.minOrNull() ?: 50.0
                val maxW = weights.maxOrNull() ?: 60.0
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_WEIGHT),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_WEIGHT), weightPoints, Color.Blue, "kg")),
                    stepY = 5.0,
                    minYConstraint = minW - 2.0,
                    maxYConstraint = maxW + 2.0,
                    showDecimal = true
                )
            }
            1 -> { // BMI
                val bmis = sortedData.map { it.calculateBMI() }.filter { it > 0.0 }
                val bmiPoints = sortedData.map { 
                    val bmi = it.calculateBMI()
                    val (noteId, level) = HealthThresholds.evaluateBMI(bmi)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        bmi,
                        if (level != HealthThresholds.AlertLevel.NORMAL) noteId?.let { id -> context.getString(id) } else null
                    )
                }.filter { it.y > 0.0 }
                val minB = bmis.minOrNull() ?: 20.0
                val maxB = bmis.maxOrNull() ?: 25.0

                // BMIの肥満度に応じた特殊なハイライト処理
                val baseRanges = HealthThresholds.getBmiRanges()
                val mappedRanges = baseRanges.map {
                    val color = when {
                        it.start == HealthThresholds.BMI_OBESITY_1 && it.end == HealthThresholds.BMI_OBESITY_2 -> getAlertHighlight(isDark)
                        it.start == HealthThresholds.BMI_OBESITY_2 && it.end == HealthThresholds.BMI_OBESITY_3 -> getObesityColor2(isDark)
                        it.start == HealthThresholds.BMI_OBESITY_3 -> getObesityColor3(isDark)
                        else -> when (it.level) {
                            HealthThresholds.AlertLevel.ALERT -> getAlertHighlight(isDark)
                            HealthThresholds.AlertLevel.WARNING -> getWarningHighlight(isDark)
                            HealthThresholds.AlertLevel.INFO -> getInfoHighlight(isDark)
                            else -> Color.Transparent
                        }
                    }
                    ChartRangeHighlight(it.start, it.end, color)
                }

                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_BMI),
                    helpContent = HealthThresholds.getBmiExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_BMI), bmiPoints, Color.Red, "")),
                    ranges = mappedRanges,
                    limits = mapLimits(HealthThresholds.getBmiLimits()),
                    stepY = 2.0,
                    minYConstraint = minB - 1.0,
                    maxYConstraint = maxB + 1.0,
                    showDecimal = true
                )
            }
            else -> null
        }
    }
}
