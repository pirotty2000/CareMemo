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
    private fun getNormalHighlight(isDark: Boolean) = if (isDark) Color(0xFF1B2E1D) else Color(0xFFE8F5E9)
    private fun getWarningHighlight(isDark: Boolean) = if (isDark) Color(0xFF333112) else Color(0xFFFFFDE7)
    private fun getAlertHighlight(isDark: Boolean) = if (isDark) Color(0xFF3D1F1F) else Color(0xFFFFEBEE)
    private fun getInfoHighlight(isDark: Boolean) = if (isDark) Color(0xFF1A213D) else Color(0xFFE3F2FD)
    private fun getObesityColor2(isDark: Boolean) = if (isDark) Color(0xFF2D1A3D) else Color(0xFFF3E5F5) // 薄い紫
    private fun getObesityColor3(isDark: Boolean) = if (isDark) Color(0xFF4A148C) else Color(0xFFCE93D8) // 紫

    private fun getBpAndPulseConfig(context: Context, index: Int, data: List<BpAndPulse>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }
        val warningColor = getWarningHighlight(isDark)
        val alertColor = getAlertHighlight(isDark)
        val infoColor = getInfoHighlight(isDark)

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
                        ChartLineData("${context.getString(HealthThresholds.HEALTH_LABEL_BP)}(${context.getString(HealthThresholds.HEALTH_LABEL_SYSTOLIC_SHORT)})", sysPoints, Color.Red),
                        ChartLineData("${context.getString(HealthThresholds.HEALTH_LABEL_BP)}(${context.getString(HealthThresholds.HEALTH_LABEL_DIASTOLIC_SHORT)})", diaPoints, Color.Blue)
                    ),
                    ranges = listOf(
                        ChartRangeHighlight(HealthThresholds.BP_HIGH_SYSTOLIC, 300.0, alertColor), // 140〜: 高血圧(赤)
                        // 100〜140: 正常(白)
                        ChartRangeHighlight(90.0, 100.0, warningColor), // 90〜100: ボーダー(黄)
                        // 60〜90: 正常(白)
                        ChartRangeHighlight(0.0, HealthThresholds.BP_LOW_DIASTOLIC, infoColor) // 〜60: 低血圧(青)
                    ),
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
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_PULSE), pulsePoints, Color(0xFF4CAF50))),
                    ranges = listOf(
                        ChartRangeHighlight(HealthThresholds.PULSE_HIGH, 300.0, alertColor), // 100〜: 頻脈(赤)
                        ChartRangeHighlight(0.0, HealthThresholds.PULSE_LOW, infoColor)      // 〜50: 徐脈(青)
                    ),
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
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_BODY_TEMP), tempPoints, Color(0xFFFF9800))),
                    ranges = listOf(
                        ChartRangeHighlight(HealthThresholds.TEMP_HIGH, 50.0, alertColor), // 37.5〜: 発熱(赤)
                        ChartRangeHighlight(0.0, HealthThresholds.TEMP_LOW, infoColor)    // 〜35.5: 低体温(青)
                    ),
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
        val warningColor = getWarningHighlight(isDark)
        val alertColor = getAlertHighlight(isDark)
        val infoColor = getInfoHighlight(isDark)
        
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
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_GLUCOSE), glucosePoints, Color.Magenta)),
                    ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.GLUCOSE_NORMAL_LOW, infoColor), // 低血糖: 薄い青
                        // 正常 (70-99): ハイライトなし(白)
                        ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_HIGH, HealthThresholds.GLUCOSE_NORMAL_PREDIABETES, warningColor), // 予備群: 薄い黄
                        ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_PREDIABETES + 0.1, 1000.0, alertColor) // 高血糖: 薄い赤
                    ),
                    limits = listOf(
                        ChartLimitLine("正常(下限)", HealthThresholds.GLUCOSE_NORMAL_LOW, Color.Gray, isLabelAbove = false),
                        ChartLimitLine("正常(上限)", HealthThresholds.GLUCOSE_NORMAL_HIGH, Color.Gray, isLabelAbove = true)
                    ),
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
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_HBA1C), hba1cPoints, Color.Red)),
                    ranges = listOf(
                        // 正常 (<= 5.5): ハイライトなし(白)
                        ChartRangeHighlight(HealthThresholds.HBA1C_GOOD + 0.01, HealthThresholds.HBA1C_DIABETES - 0.01, warningColor), // 予備群: 薄い黄
                        ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 20.0, alertColor) // 糖尿病型: 薄い赤
                    ),
                    limits = listOf(
                        ChartLimitLine("正常(上限)", HealthThresholds.HBA1C_GOOD, Color.Gray, isLabelAbove = true)
                    ),
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
        val warningColor = getWarningHighlight(isDark)
        val alertColor = getAlertHighlight(isDark)
        val infoColor = getInfoHighlight(isDark)

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
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_WEIGHT), weightPoints, Color.Blue)),
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
                HealthChartConfig(
                    title = context.getString(HealthThresholds.HEALTH_LABEL_BMI),
                    helpContent = HealthThresholds.getBmiExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(HealthThresholds.HEALTH_LABEL_BMI), bmiPoints, Color.Red)),
                    ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, infoColor), // 低体重: 薄い青
                        // 普通体重 (18.5-25.0): ハイライトなし(白)
                        ChartRangeHighlight(HealthThresholds.BMI_NORMAL_HIGH, HealthThresholds.BMI_OBESITY_1, warningColor), // 肥満(1度): 薄い黄
                        ChartRangeHighlight(HealthThresholds.BMI_OBESITY_1, HealthThresholds.BMI_OBESITY_2, alertColor), // 肥満(2度): 薄い赤
                        ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, HealthThresholds.BMI_OBESITY_3, getObesityColor2(isDark)), // 肥満(3度): 薄い紫
                        ChartRangeHighlight(HealthThresholds.BMI_OBESITY_3, 100.0, getObesityColor3(isDark)) // 肥満(4度): 紫
                    ),
                    limits = listOf(
                        ChartLimitLine("正常(下限)", HealthThresholds.BMI_NORMAL_LOW, Color.Gray, isLabelAbove = false),
                        ChartLimitLine("正常(上限)", HealthThresholds.BMI_NORMAL_HIGH, Color.Gray, isLabelAbove = true)
                    ),
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
