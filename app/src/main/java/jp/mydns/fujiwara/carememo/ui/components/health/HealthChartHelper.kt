package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：HealthChartHelper
 *
 * 【役割】：
 * 健康記録のグラフ描画に必要なデータ変換、設定生成、および配色管理を行うユーティリティ。
 *
 * 【主な機能】：
 * ・各健康カテゴリ（血圧、血糖値、BMI等）に応じたグラフ設定（HealthChartConfig）の生成。
 * ・原材料データ（HistoryRecord 等）からグラフ用座標データ（ChartPoint）への変換。
 * ・各テーマ（ライト/ダーク）に最適化したグラフ背景のハイライト色の定義。
 * ・判定基準（AppThresholds）に基づくアラート範囲（VisualRange）のグラフ用マッピング。
 *
 * 【想定する利用場所】：
 * HealthGraphView, GraphExpansionScreen, PdfExporter。
 *
 * 【このコンポーネントでは行わないこと】：
 * Canvas を用いた直接の描画処理（LineChart が担当）。
 *
 * 【公開composable】：
 * なし（ロジッククラス）
 */

import android.content.Context
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper

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
            Category.BP_AND_PULSE -> 4
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

    // --- ハイライト色の定義 (テーマに合わせて調整) ---
    private fun getWarningHighlight(isDark: Boolean) = if (isDark) Color(0xFF2E2A00) else Color(0xFFFFFDE7) // 警告（黄）
    private fun getAlertHighlight(isDark: Boolean) = if (isDark) Color(0xFF3B1010) else Color(0xFFFFEBEE) // 注意（赤）
    private fun getInfoHighlight(isDark: Boolean) = if (isDark) Color(0xFF0D1C33) else Color(0xFFE3F2FD) // 情報（青）
    private fun getObesityColor2(isDark: Boolean) = if (isDark) Color(0xFF2D1A3D) else Color(0xFFF3E5F5) // 肥満1（薄紫）
    private fun getObesityColor3(isDark: Boolean) = if (isDark) Color(0xFF4A148C) else Color(0xFFCE93D8) // 肥満2（紫）

    // --- ユーティリティ ---

    private fun mapRanges(ranges: List<AppThresholds.VisualRange>, isDark: Boolean): List<ChartRangeHighlight> {
        return ranges.map {
            val color = when (it.level) {
                HealthAlertLevel.ALERT -> getAlertHighlight(isDark)
                HealthAlertLevel.WARNING -> getWarningHighlight(isDark)
                HealthAlertLevel.INFO -> getInfoHighlight(isDark)
                HealthAlertLevel.NORMAL -> if (isDark) Color.Transparent else Color.White
            }
            ChartRangeHighlight(it.start, it.end, color)
        }
    }

    private fun mapLimits(limits: List<AppThresholds.VisualLimit>): List<ChartLimitLine> {
        return limits.map {
            ChartLimitLine(it.label, it.value, isLabelAbove = it.isAbove)
        }
    }

    private fun getBpAndPulseConfig(context: Context, index: Int, data: List<BpAndPulse>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }

        return when (index) {
            0 -> { // 血圧
                val sysPoints = sortedData.filter { it.bpSystolic != null }.map { 
                    val status = HealthLogic.evaluateVitalItems(it.bpSystolic, null, null, null, null)
                        .firstOrNull { r -> r.second != HealthAlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bpSystolic!!.toDouble(),
                        status?.let { s -> context.getString(HealthDisplayMapper.getVitalLabel(s)) }
                    )
                }
                val diaPoints = sortedData.filter { it.bpDiastolic != null }.map { 
                    val status = HealthLogic.evaluateVitalItems(null, it.bpDiastolic, null, null, null)
                        .firstOrNull { r -> r.second != HealthAlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bpDiastolic!!.toDouble(),
                        status?.let { s -> context.getString(HealthDisplayMapper.getVitalLabel(s)) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(R.string.health_label_bp),
                    helpContent = HealthDisplayMapper.getBpExplanation(context),
                    dataList = listOf(
                        ChartLineData("${context.getString(R.string.health_label_bp)}(${context.getString(R.string.health_label_systolic_short)})", sysPoints, Color.Red, "mmHg"),
                        ChartLineData("${context.getString(R.string.health_label_bp)}(${context.getString(R.string.health_label_diastolic_short)})", diaPoints, Color.Blue, "mmHg")
                    ),
                    ranges = mapRanges(AppThresholds.getBpRanges(), isDark),
                    stepY = 10.0,
                    minYConstraint = 70.0,
                    maxYConstraint = 160.0
                )
            }
            1 -> { // 酸素飽和度(SAT)
                val satPoints = sortedData.filter { it.sat != null }.map { 
                    val status = HealthLogic.evaluateVitalItems(null, null, it.sat, null, null)
                        .firstOrNull { r -> r.second != HealthAlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.sat!!.toDouble(),
                        status?.let { s -> context.getString(HealthDisplayMapper.getVitalLabel(s)) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(R.string.health_label_sat),
                    helpContent = HealthDisplayMapper.getSatExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_sat), satPoints, Color(0xFF00BCD4), "%")),
                    ranges = mapRanges(AppThresholds.getSatRanges(), isDark),
                    stepY = 2.0,
                    minYConstraint = 85.0,
                    maxYConstraint = 100.0
                )
            }
            2 -> { // 脈拍
                val pulsePoints = sortedData.filter { it.pulse != null }.map { 
                    val status = HealthLogic.evaluateVitalItems(null, null, null, it.pulse, null)
                        .firstOrNull { r -> r.second != HealthAlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.pulse!!.toDouble(),
                        status?.let { s -> context.getString(HealthDisplayMapper.getVitalLabel(s)) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(R.string.health_label_pulse),
                    helpContent = HealthDisplayMapper.getPulseExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_pulse), pulsePoints, Color(0xFF4CAF50), "bpm")),
                    ranges = mapRanges(AppThresholds.getPulseRanges(), isDark),
                    stepY = 10.0,
                    minYConstraint = 40.0,
                    maxYConstraint = 110.0
                )
            }
            3 -> { // 体温
                val tempPoints = sortedData.filter { it.bodyTemperature != null }.map { 
                    val status = HealthLogic.evaluateVitalItems(null, null, null, null, it.bodyTemperature)
                        .firstOrNull { r -> r.second != HealthAlertLevel.NORMAL }?.first
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.bodyTemperature!!,
                        status?.let { s -> context.getString(HealthDisplayMapper.getVitalLabel(s)) }
                    )
                }
                HealthChartConfig(
                    title = context.getString(R.string.health_label_body_temp),
                    helpContent = HealthDisplayMapper.getTempExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_body_temp), tempPoints, Color(0xFFFF9800), "℃")),
                    ranges = mapRanges(AppThresholds.getTempRanges(), isDark),
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
                    val (status, level) = HealthLogic.evaluateGlucose(it.glucose)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.glucose!!.toDouble(),
                        if (level != HealthAlertLevel.NORMAL) status?.let { s -> context.getString(HealthDisplayMapper.getGlucoseLabel(s)!!) } else null
                    )
                }
                val minG = glucoses.minOrNull() ?: 70.0
                val maxG = glucoses.maxOrNull() ?: 110.0
                HealthChartConfig(
                    title = context.getString(R.string.health_label_glucose),
                    helpContent = HealthDisplayMapper.getGlucoseExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_glucose), glucosePoints, Color.Magenta, "mg/dL")),
                    ranges = mapRanges(AppThresholds.getGlucoseRanges(), isDark),
                    limits = mapLimits(AppThresholds.getGlucoseLimits()),
                    stepY = 50.0,
                    minYConstraint = minG - 10.0,
                    maxYConstraint = maxG + 10.0
                )
            }
            1 -> { // HbA1c
                val hba1cs = sortedData.mapNotNull { it.hba1c }
                val hba1cPoints = sortedData.filter { it.hba1c != null }.map { 
                    val (status, level) = HealthLogic.evaluateHbA1c(it.hba1c)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.hba1c!!,
                        if (level != HealthAlertLevel.NORMAL) status?.let { s -> context.getString(HealthDisplayMapper.getHbA1cLabel(s)!!) } else null
                    )
                }
                val minH = hba1cs.minOrNull() ?: 5.0
                val maxH = hba1cs.maxOrNull() ?: 6.0
                HealthChartConfig(
                    title = context.getString(R.string.health_label_hba1c),
                    helpContent = HealthDisplayMapper.getHbA1cExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_hba1c), hba1cPoints, Color.Red, "%")),
                    ranges = mapRanges(AppThresholds.getHbA1cRanges(), isDark),
                    limits = mapLimits(AppThresholds.getHbA1cLimits()),
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
                    title = context.getString(R.string.health_label_weight),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_weight), weightPoints, Color.Blue, "kg")),
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
                    val (status, level) = HealthLogic.evaluateBMI(bmi)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        bmi,
                        if (level != HealthAlertLevel.NORMAL) status?.let { s -> context.getString(HealthDisplayMapper.getBmiLabel(s)!!) } else null
                    )
                }.filter { it.y > 0.0 }
                val minB = bmis.minOrNull() ?: 20.0
                val maxB = bmis.maxOrNull() ?: 25.0

                // BMIの肥満度に応じた特殊なハイライト処理
                val baseRanges = AppThresholds.getBmiRanges()
                val mappedRanges = baseRanges.map {
                    val color = when (it.start) {
                        AppThresholds.BMI_OBESITY_1 -> getAlertHighlight(isDark)
                        AppThresholds.BMI_OBESITY_2 -> getObesityColor2(isDark)
                        AppThresholds.BMI_OBESITY_3 -> getObesityColor3(isDark)
                        else -> when (it.level) {
                            HealthAlertLevel.ALERT -> getAlertHighlight(isDark)
                            HealthAlertLevel.WARNING -> getWarningHighlight(isDark)
                            HealthAlertLevel.INFO -> getInfoHighlight(isDark)
                            else -> Color.Transparent
                        }
                    }
                    ChartRangeHighlight(it.start, it.end, color)
                }

                HealthChartConfig(
                    title = context.getString(R.string.health_label_bmi),
                    helpContent = HealthDisplayMapper.getBmiExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_bmi), bmiPoints, Color.Red, "")),
                    ranges = mappedRanges,
                    limits = mapLimits(AppThresholds.getBmiLimits()),
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
