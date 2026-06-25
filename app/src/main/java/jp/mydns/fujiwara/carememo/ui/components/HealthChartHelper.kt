package jp.mydns.fujiwara.carememo.ui.components

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
    val stepY: Double = 5.0,
    val minYConstraint: Double? = null,
    val maxYConstraint: Double? = null,
    val showDecimal: Boolean = false
)

object HealthChartHelper {

    /**
     * 全データを通じた共通のX軸（時間軸）の範囲を計算します
     */
    fun calculateGlobalXRange(records: List<Any>): Pair<Double?, Double?> {
        val allTimes = records.mapNotNull {
            when (it) {
                is BpAndPulse -> it.recordTime.toEpochMilli().toDouble()
                is GlucoseAndHbA1c -> it.recordTime.toEpochMilli().toDouble()
                is HeightAndWeight -> it.recordTime.toEpochMilli().toDouble()
                else -> null
            }
        }
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
            else -> 0
        }
    }

    /**
     * 指定されたカテゴリーとインデックスに対応するグラフ設定を生成します
     */
    fun getChartConfig(category: Category, index: Int, records: List<Any>): HealthChartConfig? {
        return when (category) {
            Category.BP_AND_PULSE -> getBpAndPulseConfig(index, records.filterIsInstance<BpAndPulse>())
            Category.GLUCOSE_AND_HBA1C -> getGlucoseAndHbA1cConfig(index, records.filterIsInstance<GlucoseAndHbA1c>())
            Category.HEIGHT_AND_WEIGHT -> getHeightAndWeightConfig(index, records.filterIsInstance<HeightAndWeight>())
            else -> null
        }
    }

    private fun getBpAndPulseConfig(index: Int, data: List<BpAndPulse>): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }
        return when (index) {
            0 -> { // 血圧
                val sysPoints = sortedData.filter { it.bpSystolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpSystolic!!.toDouble() }
                val diaPoints = sortedData.filter { it.bpDiastolic != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bpDiastolic!!.toDouble() }
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_BP,
                    helpContent = HealthThresholds.BP_EXPLANATION,
                    dataList = listOf(
                        ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(上)", sysPoints, Color.Red),
                        ChartLineData("${HealthThresholds.HEALTH_LABEL_BP}(下)", diaPoints, Color.Blue)
                    ),
                    ranges = listOf(
                        ChartRangeHighlight(HealthThresholds.BP_LOW_SYSTOLIC, HealthThresholds.BP_HIGH_SYSTOLIC, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.BP_LOW_DIASTOLIC, HealthThresholds.BP_HIGH_DIASTOLIC, Color(0xFFE8F5E9))
                    ),
                    stepY = 10.0,
                    minYConstraint = 70.0,
                    maxYConstraint = 160.0
                )
            }
            1 -> { // 脈拍
                val pulsePoints = sortedData.filter { it.pulse != null }.map { it.recordTime.toEpochMilli().toDouble() to it.pulse!!.toDouble() }
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_PULSE,
                    helpContent = HealthThresholds.PULSE_EXPLANATION,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_PULSE, pulsePoints, Color(0xFF4CAF50))),
                    ranges = listOf(ChartRangeHighlight(HealthThresholds.PULSE_LOW, HealthThresholds.PULSE_HIGH, Color(0xFFE8F5E9))),
                    stepY = 10.0,
                    minYConstraint = 40.0,
                    maxYConstraint = 110.0
                )
            }
            2 -> { // 体温
                val tempPoints = sortedData.filter { it.bodyTemperature != null }.map { it.recordTime.toEpochMilli().toDouble() to it.bodyTemperature!! }
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_BODY_TEMP,
                    helpContent = HealthThresholds.TEMP_EXPLANATION,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_BODY_TEMP, tempPoints, Color(0xFFFF9800))),
                    ranges = listOf(ChartRangeHighlight(HealthThresholds.TEMP_LOW, HealthThresholds.TEMP_HIGH, Color(0xFFE8F5E9))),
                    stepY = 0.5,
                    minYConstraint = 35.0,
                    maxYConstraint = 39.0,
                    showDecimal = true
                )
            }
            else -> null
        }
    }

    private fun getGlucoseAndHbA1cConfig(index: Int, data: List<GlucoseAndHbA1c>): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }
        return when (index) {
            0 -> { // 血糖値
                val glucoses = sortedData.mapNotNull { it.glucose?.toDouble() }
                val glucosePoints = sortedData.filter { it.glucose != null }.map { it.recordTime.toEpochMilli().toDouble() to it.glucose!!.toDouble() }
                val minG = glucoses.minOrNull() ?: 70.0
                val maxG = glucoses.maxOrNull() ?: 110.0
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_GLUCOSE,
                    helpContent = HealthThresholds.GLUCOSE_EXPLANATION,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_GLUCOSE, glucosePoints, Color.Magenta)),
                    ranges = listOf(ChartRangeHighlight(HealthThresholds.GLUCOSE_NORMAL_LOW, HealthThresholds.GLUCOSE_NORMAL_HIGH, Color(0xFFE8F5E9))),
                    stepY = 50.0,
                    minYConstraint = minG - 10.0,
                    maxYConstraint = maxG + 10.0
                )
            }
            1 -> { // HbA1c
                val hba1cs = sortedData.mapNotNull { it.hba1c }
                val hba1cPoints = sortedData.filter { it.hba1c != null }.map { it.recordTime.toEpochMilli().toDouble() to it.hba1c!! }
                val minH = hba1cs.minOrNull() ?: 5.0
                val maxH = hba1cs.maxOrNull() ?: 6.0
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_HBA1C,
                    helpContent = HealthThresholds.HBA1C_EXPLANATION,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_HBA1C, hba1cPoints, Color.Red)),
                    ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.HBA1C_GOOD, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_PREDIABETES, HealthThresholds.HBA1C_DIABETES, Color(0xFFFFFDE7)),
                        ChartRangeHighlight(HealthThresholds.HBA1C_DIABETES, 100.0, Color(0xFFFFEBEE))
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

    private fun getHeightAndWeightConfig(index: Int, data: List<HeightAndWeight>): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }
        return when (index) {
            0 -> { // 体重
                val weights = sortedData.mapNotNull { it.weight }
                val weightPoints = sortedData.filter { it.weight != null }.map { it.recordTime.toEpochMilli().toDouble() to it.weight!! }
                val minW = weights.minOrNull() ?: 50.0
                val maxW = weights.maxOrNull() ?: 60.0
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_WEIGHT,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_WEIGHT, weightPoints, Color.Blue)),
                    stepY = 5.0,
                    minYConstraint = minW - 2.0,
                    maxYConstraint = maxW + 2.0,
                    showDecimal = true
                )
            }
            1 -> { // BMI
                val bmis = sortedData.map { it.calculateBMI() }.filter { it > 0.0 }
                val bmiPoints = sortedData.map { it.recordTime.toEpochMilli().toDouble() to it.calculateBMI() }.filter { it.second > 0.0 }
                val minB = bmis.minOrNull() ?: 20.0
                val maxB = bmis.maxOrNull() ?: 25.0
                HealthChartConfig(
                    title = HealthThresholds.HEALTH_LABEL_BMI,
                    helpContent = HealthThresholds.BMI_EXPLANATION,
                    dataList = listOf(ChartLineData(HealthThresholds.HEALTH_LABEL_BMI, bmiPoints, Color.Red)),
                    ranges = listOf(
                        ChartRangeHighlight(0.0, HealthThresholds.BMI_NORMAL_LOW, Color(0xFFE3F2FD)),
                        ChartRangeHighlight(HealthThresholds.BMI_NORMAL_LOW, HealthThresholds.BMI_NORMAL_HIGH, Color(0xFFE8F5E9)),
                        ChartRangeHighlight(HealthThresholds.BMI_OBESITY_2, 100.0, Color(0xFFFFEBEE))
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
