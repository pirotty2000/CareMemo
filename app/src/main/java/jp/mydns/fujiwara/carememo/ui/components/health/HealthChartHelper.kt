package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：HealthChartHelper
 *
 * 【役割】
 * 健康記録のグラフ描画に必要なデータ変換、設定生成、および配色管理を行うユーティリティです。
 * 描画ロジック（Canvas操作）とビジネスロジック（判定基準）の橋渡しを担います。
 *
 * 【主な機能】
 * ・各健康カテゴリ（血圧、血糖値、BMI等）に応じたグラフ設定（HealthChartConfig）の生成。
 * ・原材料データ（HistoryRecord 等）からグラフ用座標データ（ChartPoint）への変換。
 * ・各テーマ（ライト/ダーク）に最適化したグラフ背景のハイライト色の定義。
 * ・判定基準（AppSpecifications）に基づくアラート範囲（VisualRange）のグラフ用マッピング。
 *
 * 【想定する利用場所】
 * HealthGraphView（履歴画面のグラフ）、GraphExpansionScreen（拡大グラフ）、PdfExporter（PDF帳票出力）。
 *
 * 【このコンポーネントでは行わないこと】
 * Canvas を用いた直接の描画処理（LineChart コンポーネントが担当）。
 */

import android.content.Context
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import jp.mydns.fujiwara.carememo.ui.theme.getHighlightColor

/**
 * グラフ描画用の視覚的範囲定義。
 * 特定の値の範囲に対して、警告レベルに応じた背景ハイライトを適用するために使用します。
 *
 * @param start 範囲の開始値（下限）
 * @param end 範囲の終了値（上限）
 * @param level 対応するアラートレベル
 */
data class VisualRange(val start: Double, val end: Double, val level: HealthAlertLevel)

/**
 * グラフ描画に必要なすべての設定情報を保持するデータクラス。
 *
 * @param title グラフのタイトル
 * @param helpContent グラフの意味や判定基準を説明するテキスト（改行区切り）
 * @param dataList 描画するデータ系列（折れ線）のリスト
 * @param ranges 背景に描画する色付きの範囲ハイライト
 * @param limits 目安として描画する補助線（上限線など）
 * @param stepY Y軸のメモリ間隔
 * @param minYConstraint Y軸の最小表示値（null の場合はデータから自動計算）
 * @param maxYConstraint Y軸の最大表示値（null の場合はデータから自動計算）
 * @param showDecimal Y軸のラベルに小数を表示するかどうか
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
     * ヘルプコンテンツ（解説文）を行ごとのリストとして取得します。
     * ダイアログ等でのリスト表示に使用します。
     */
    fun getSubtitleLines(): List<String> =
        if (helpContent.isNotBlank()) {
            helpContent.split("\n").filter { it.isNotBlank() }
        } else emptyList()
}

/**
 * 健康記録グラフの生成を支援するシングルトンオブジェクト。
 */
object HealthChartHelper {

    /**
     * 全データを通じた共通のX軸（時間軸）の範囲を計算します。
     * 複数のグラフを同じ時間軸で並べる際の同期に使用します。
     *
     * @param records 履歴レコードのリスト
     * @return 開始時間と終了時間のペア (エポックミリ秒の Double 値)
     */
    fun calculateGlobalXRange(records: List<Any>): Pair<Double?, Double?> {
        val allTimes = records.filterIsInstance<HistoryRecord>()
            .map { it.recordTime.toEpochMilli().toDouble() }

        return allTimes.minOrNull() to allTimes.maxOrNull()
    }

    /**
     * 指定されたカテゴリで表示すべきグラフの総数を返します。
     *
     * @param category 健康カテゴリ
     * @return グラフの数
     */
    fun getGraphCount(category: Category): Int {
        return when (category) {
            Category.BP_AND_PULSE -> 4 // 血圧、酸素飽和度、脈拍、体温
            Category.GLUCOSE_AND_HBA1C -> 2 // 血糖値、HbA1c
            Category.HEIGHT_AND_WEIGHT -> 2 // 体重、BMI
            Category.CONDITION_AT_VISIT, Category.MEDICATION -> 0
        }
    }

    /**
     * 指定されたカテゴリとインデックスに対応するグラフ詳細設定（HealthChartConfig）を生成します。
     *
     * @param context Android コンテキスト（文字列リソース取得用）
     * @param category カテゴリ
     * @param index カテゴリ内でのグラフのインデックス
     * @param records 全履歴データ
     * @param isDark ダークモードかどうか（配色決定に使用）
     * @return グラフ設定オブジェクト。該当がない場合は null。
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

    /**
     * 内部用：VisualRange リストをグラフ描画用の ChartRangeHighlight リストに変換します。
     */
    private fun mapRanges(ranges: List<VisualRange>, isDark: Boolean): List<ChartRangeHighlight> {
        return ranges.map {
            val color = it.level.getHighlightColor(isDark)
            ChartRangeHighlight(it.start, it.end, color)
        }
    }

    /**
     * 内部用：HealthDisplayMapper の定義をグラフ描画用の ChartLimitLine リストに変換します。
     */
    private fun mapLimits(limits: List<HealthDisplayMapper.GraphLimit>): List<ChartLimitLine> {
        return limits.map {
            // ラベルの内容に基づき、線の上下どちらに文字を表示するかを決定
            val isAbove = it.label.contains("上限")
            ChartLimitLine(it.label, it.value, isLabelAbove = isAbove)
        }
    }

    // --- 判定基準に基づく範囲定義の取得メソッド群 ---

    private fun getBpRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.BloodPressure
        val graph = AppSpecifications.Health.BloodPressure.Graph
        return listOf(
            VisualRange(spec.THRESHOLD_HIGH_SYSTOLIC, graph.RANGE_MAX, HealthAlertLevel.ALERT),
            VisualRange(100.0, spec.THRESHOLD_HIGH_SYSTOLIC, HealthAlertLevel.NORMAL),
            VisualRange(90.0, spec.THRESHOLD_LOW_SYSTOLIC, HealthAlertLevel.WARNING),
            VisualRange(spec.THRESHOLD_LOW_DIASTOLIC, 90.0, HealthAlertLevel.NORMAL),
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_LOW_DIASTOLIC, HealthAlertLevel.INFO)
        )
    }

    private fun getPulseRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.Pulse
        val graph = AppSpecifications.Health.Pulse.Graph
        return listOf(
            VisualRange(spec.THRESHOLD_HIGH, graph.RANGE_MAX, HealthAlertLevel.ALERT),
            VisualRange(spec.THRESHOLD_LOW, spec.THRESHOLD_HIGH, HealthAlertLevel.NORMAL),
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_LOW, HealthAlertLevel.INFO)
        )
    }

    private fun getSatRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.OxygenSaturation
        val graph = AppSpecifications.Health.OxygenSaturation.Graph
        return listOf(
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_LOW, HealthAlertLevel.ALERT),
            VisualRange(spec.THRESHOLD_LOW + 0.1, graph.RANGE_MAX, HealthAlertLevel.NORMAL)
        )
    }

    private fun getTempRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.BodyTemperature
        val graph = AppSpecifications.Health.BodyTemperature.Graph
        return listOf(
            VisualRange(spec.THRESHOLD_HIGH, graph.RANGE_MAX, HealthAlertLevel.ALERT),
            VisualRange(spec.THRESHOLD_LOW, spec.THRESHOLD_HIGH, HealthAlertLevel.NORMAL),
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_LOW, HealthAlertLevel.INFO)
        )
    }

    private fun getGlucoseRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.BloodGlucose
        val graph = AppSpecifications.Health.BloodGlucose.Graph
        return listOf(
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_LOW, HealthAlertLevel.INFO),
            VisualRange(spec.THRESHOLD_LOW, spec.THRESHOLD_NORMAL_UPPER, HealthAlertLevel.NORMAL),
            VisualRange(spec.THRESHOLD_NORMAL_UPPER, spec.THRESHOLD_PREDIABETES, HealthAlertLevel.INFO),
            VisualRange(spec.THRESHOLD_PREDIABETES, spec.THRESHOLD_HIGH, HealthAlertLevel.WARNING),
            VisualRange(spec.THRESHOLD_HIGH, graph.RANGE_MAX, HealthAlertLevel.ALERT)
        )
    }

    private fun getHbA1cRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.HbA1c
        val graph = AppSpecifications.Health.HbA1c.Graph
        return listOf(
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_NORMAL_UPPER, HealthAlertLevel.NORMAL),
            VisualRange(spec.THRESHOLD_NORMAL_UPPER + 0.01, spec.THRESHOLD_DIABETES - 0.01, HealthAlertLevel.WARNING),
            VisualRange(spec.THRESHOLD_DIABETES, graph.RANGE_MAX, HealthAlertLevel.ALERT)
        )
    }

    private fun getBmiRanges(): List<VisualRange> {
        val spec = AppSpecifications.Health.BodyMassIndex
        val graph = AppSpecifications.Health.BodyMassIndex.Graph
        return listOf(
            VisualRange(graph.RANGE_MIN, spec.THRESHOLD_UNDERWEIGHT, HealthAlertLevel.INFO),
            VisualRange(spec.THRESHOLD_UNDERWEIGHT, spec.THRESHOLD_NORMAL_UPPER, HealthAlertLevel.NORMAL),
            VisualRange(spec.THRESHOLD_NORMAL_UPPER, spec.THRESHOLD_OBESITY_1, HealthAlertLevel.WARNING),
            VisualRange(spec.THRESHOLD_OBESITY_1, spec.THRESHOLD_OBESITY_2, HealthAlertLevel.ALERT),
            VisualRange(spec.THRESHOLD_OBESITY_2, spec.THRESHOLD_OBESITY_3, HealthAlertLevel.ALERT),
            VisualRange(spec.THRESHOLD_OBESITY_3, graph.RANGE_MAX, HealthAlertLevel.ALERT)
        )
    }

    // --- 各カテゴリの具体的なグラフ設定生成ロジック ---

    private fun getBpAndPulseConfig(context: Context, index: Int, data: List<BpAndPulse>, isDark: Boolean): HealthChartConfig? {
        val sortedData = data.sortedBy { it.recordTime }

        return when (index) {
            0 -> { // 血圧
                val graph = AppSpecifications.Health.BloodPressure.Graph
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
                    ranges = mapRanges(getBpRanges(), isDark),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = graph.Y_MIN_VIEW_LIMIT,
                    maxYConstraint = graph.Y_MAX_VIEW_LIMIT
                )
            }
            1 -> { // 酸素飽和度(SAT)
                val graph = AppSpecifications.Health.OxygenSaturation.Graph
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
                    ranges = mapRanges(getSatRanges(), isDark),
                    limits = mapLimits(HealthDisplayMapper.getSatGraphLimits(context)),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = graph.Y_MIN_VIEW_LIMIT,
                    maxYConstraint = graph.Y_MAX_VIEW_LIMIT
                )
            }
            2 -> { // 脈拍
                val graph = AppSpecifications.Health.Pulse.Graph
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
                    ranges = mapRanges(getPulseRanges(), isDark),
                    limits = mapLimits(HealthDisplayMapper.getPulseGraphLimits(context)),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = graph.Y_MIN_VIEW_LIMIT,
                    maxYConstraint = graph.Y_MAX_VIEW_LIMIT
                )
            }
            3 -> { // 体温
                val graph = AppSpecifications.Health.BodyTemperature.Graph
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
                    ranges = mapRanges(getTempRanges(), isDark),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = graph.Y_MIN_VIEW_LIMIT,
                    maxYConstraint = graph.Y_MAX_VIEW_LIMIT,
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
                val graph = AppSpecifications.Health.BloodGlucose.Graph
                val glucoses = sortedData.mapNotNull { it.glucose?.toDouble() }
                val glucosePoints = sortedData.filter { it.glucose != null }.map { 
                    val (status, level) = HealthLogic.evaluateGlucose(it.glucose)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.glucose!!.toDouble(),
                        if (level != HealthAlertLevel.NORMAL) status?.let { s -> context.getString(HealthDisplayMapper.getGlucoseLabel(s)!!) } else null
                    )
                }
                val minG = glucoses.minOrNull() ?: graph.DEFAULT_MIN
                val maxG = glucoses.maxOrNull() ?: graph.DEFAULT_MAX
                HealthChartConfig(
                    title = context.getString(R.string.health_label_glucose),
                    helpContent = HealthDisplayMapper.getGlucoseExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_glucose), glucosePoints, Color.Magenta, "mg/dL")),
                    ranges = mapRanges(getGlucoseRanges(), isDark),
                    limits = mapLimits(HealthDisplayMapper.getGlucoseGraphLimits(context)),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = minG - graph.VIEW_PADDING,
                    maxYConstraint = maxG + graph.VIEW_PADDING
                )
            }
            1 -> { // HbA1c
                val graph = AppSpecifications.Health.HbA1c.Graph
                val hba1cs = sortedData.mapNotNull { it.hba1c }
                val hba1cPoints = sortedData.filter { it.hba1c != null }.map { 
                    val (status, level) = HealthLogic.evaluateHbA1c(it.hba1c)
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.hba1c!!,
                        if (level != HealthAlertLevel.NORMAL) status?.let { s -> context.getString(HealthDisplayMapper.getHbA1cLabel(s)!!) } else null
                    )
                }
                val minH = hba1cs.minOrNull() ?: graph.DEFAULT_MIN
                val maxH = hba1cs.maxOrNull() ?: graph.DEFAULT_MAX
                HealthChartConfig(
                    title = context.getString(R.string.health_label_hba1c),
                    helpContent = HealthDisplayMapper.getHbA1cExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_hba1c), hba1cPoints, Color.Red, "%")),
                    ranges = mapRanges(getHbA1cRanges(), isDark),
                    limits = mapLimits(HealthDisplayMapper.getHbA1cGraphLimits(context)),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = minH - graph.VIEW_PADDING,
                    maxYConstraint = maxH + graph.VIEW_PADDING,
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
                val graph = AppSpecifications.Health.Weight.Graph
                val weights = sortedData.mapNotNull { it.weight }
                val weightPoints = sortedData.filter { it.weight != null }.map { 
                    ChartPoint(
                        it.recordTime.toEpochMilli().toDouble(),
                        it.weight!!,
                        null
                    )
                }
                val minW = weights.minOrNull() ?: graph.DEFAULT_MIN
                val maxW = weights.maxOrNull() ?: graph.DEFAULT_MAX
                HealthChartConfig(
                    title = context.getString(R.string.health_label_weight),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_weight), weightPoints, Color.Blue, "kg")),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = minW - graph.VIEW_PADDING,
                    maxYConstraint = maxW + graph.VIEW_PADDING,
                    showDecimal = true
                )
            }
            1 -> { // BMI
                val graph = AppSpecifications.Health.BodyMassIndex.Graph
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
                val minB = bmis.minOrNull() ?: graph.DEFAULT_MIN
                val maxB = bmis.maxOrNull() ?: graph.DEFAULT_MAX

                // BMIのハイライト処理（履歴の判定基準と共通化）
                val baseRanges = getBmiRanges()
                val mappedRanges = baseRanges.map {
                    ChartRangeHighlight(it.start, it.end, it.level.getHighlightColor(isDark))
                }

                HealthChartConfig(
                    title = context.getString(R.string.health_label_bmi),
                    helpContent = HealthDisplayMapper.getBmiExplanation(context),
                    dataList = listOf(ChartLineData(context.getString(R.string.health_label_bmi), bmiPoints, Color.Red, "")),
                    ranges = mappedRanges,
                    limits = mapLimits(HealthDisplayMapper.getBmiGraphLimits(context)),
                    stepY = graph.Y_AXIS_STEP,
                    minYConstraint = minB - graph.VIEW_PADDING,
                    maxYConstraint = maxB + graph.VIEW_PADDING,
                    showDecimal = true
                )
            }
            else -> null
        }
    }
}
