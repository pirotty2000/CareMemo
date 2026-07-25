package jp.mydns.fujiwara.carememo.ui.mapping

import android.content.Context
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.common.*

/**
 * 健康記録の判定結果(Enum)を、表示用の資源（リソースID）や
 * セマンティックな警告レベル（HealthAlertLevel）に変換するマッパー。
 *
 * 【設計方針】
 * ・本クラスは「表示内容（何を表示するか）」の決定に専念します。
 * ・具体的な「色（Color）」の決定は行わず、HealthAlertLevel を返すに留めます。
 * ・色は UI 層で HealthAlertLevel.getDisplayColor() を使用して取得してください。
 */
object HealthDisplayMapper {

    /**
     * グラフに表示する境界線の定義
     */
    data class GraphLimit(val label: String, val value: Double)

    //////////////////////////////////////////////////////////////////////
    // 「身長・体重」：BMI
    //////////////////////////////////////////////////////////////////////

    /**
     * BMIステータスに対応する文字列リソースIDを返します。
     */
    fun getBmiLabel(status: BmiStatus?): Int? = when (status) {
        BmiStatus.UNDERWEIGHT -> R.string.bmi_label_underweight // 低体重
        BmiStatus.NORMAL -> R.string.bmi_label_normal // 普通体重
        BmiStatus.OBESITY_1 -> R.string.bmi_label_obesity_1 // 肥満(1度)
        BmiStatus.OBESITY_2 -> R.string.bmi_label_obesity_2 // 肥満(2度)
        BmiStatus.OBESITY_3 -> R.string.bmi_label_obesity_3 // 肥満(3度)
        BmiStatus.OBESITY_4 -> R.string.bmi_label_obesity_4 // 肥満(4度)
        null -> null
    }

    /**
     * BMIグラフに表示する境界線のリストを返します。
     */
    fun getBmiGraphLimits(context: Context): List<GraphLimit> {
        val spec = AppSpecifications.Health.BodyMassIndex
        return listOf(
            GraphLimit(context.getString(R.string.health_graph_limit_lower), spec.THRESHOLD_GRAPH_NORMAL_LOWER),
            GraphLimit(context.getString(R.string.health_graph_limit_upper), spec.THRESHOLD_GRAPH_NORMAL_UPPER)
        )
    }

    //////////////////////////////////////////////////////////////////////
    // 「バイタル」
    //////////////////////////////////////////////////////////////////////

    /**
     * バイタルステータスに対応する文字列リソースIDを返します。
     */
    fun getVitalLabel(status: VitalStatus): Int = when (status) {
        VitalStatus.NORMAL -> R.string.vital_label_normal // 正常
        VitalStatus.HIGH_BP -> R.string.vital_label_high_bp // 高血圧
        VitalStatus.LOW_BP -> R.string.vital_label_low_bp // 低血圧
        VitalStatus.TACHYCARDIA -> R.string.vital_label_tachycardia // 頻脈
        VitalStatus.BRADYCARDIA -> R.string.vital_label_bradycardia // 徐脈
        VitalStatus.LOW_SAT -> R.string.vital_label_low_sat // 低酸素飽和度
        VitalStatus.FEVER -> R.string.vital_label_fever // 発熱
        VitalStatus.HYPOTHERMIA -> R.string.vital_label_hypothermia // 低体温
    }

    /**
     * バイタルインジケーターのアラートレベルを決定します（On/Off判定）。
     */
    fun getVitalIndicatorLevel(isActive: Boolean): HealthAlertLevel {
        return if (isActive) HealthAlertLevel.ALERT else HealthAlertLevel.NONE
    }

    /**
     * 脈拍グラフに表示する境界線のリストを返します。
     */
    fun getPulseGraphLimits(context: Context): List<GraphLimit> {
        val spec = AppSpecifications.Health.Pulse
        return listOf(
            GraphLimit(context.getString(R.string.health_graph_limit_lower), spec.THRESHOLD_GRAPH_NORMAL_LOWER),
            GraphLimit(context.getString(R.string.health_graph_limit_upper), spec.THRESHOLD_GRAPH_NORMAL_UPPER)
        )
    }

    /**
     * 酸素飽和度グラフに表示する境界線のリストを返します。
     */
    fun getSatGraphLimits(context: Context): List<GraphLimit> {
        val spec = AppSpecifications.Health.OxygenSaturation
        return listOf(
            GraphLimit(context.getString(R.string.health_graph_limit_lower), spec.THRESHOLD_GRAPH_NORMAL_LOWER)
        )
    }

    //////////////////////////////////////////////////////////////////////
    // 「血糖値・HbA1c」
    //////////////////////////////////////////////////////////////////////

    /**
     * 血糖値ステータスに対応する文字列リソースIDを返します。
     */
    fun getGlucoseLabel(status: GlucoseStatus?): Int? = when (status) {
        GlucoseStatus.LOW -> R.string.glucose_label_low // 低血糖
        GlucoseStatus.NORMAL -> R.string.glucose_label_normal // 正常型
        GlucoseStatus.NORMAL_HIGH -> R.string.glucose_label_normal_high // 正常高値
        GlucoseStatus.PREDIABETES -> R.string.glucose_label_prediabetes // 予備群
        GlucoseStatus.DIABETES -> R.string.glucose_label_diabetes // 糖尿病型
        null -> null
    }

    /**
     * HbA1cステータスに対応する文字列リソースIDを返します。
     */
    fun getHbA1cLabel(status: HbA1cStatus?): Int? = when (status) {
        HbA1cStatus.NORMAL -> R.string.hba1c_label_normal // 正常
        HbA1cStatus.WARNING -> R.string.hba1c_label_prediabetes // 予備群
        HbA1cStatus.DIABETES -> R.string.hba1c_label_diabetes // 糖尿病型
        null -> null
    }

    /**
     * 血糖値グラフに表示する境界線のリストを返します。
     */
    fun getGlucoseGraphLimits(context: Context): List<GraphLimit> {
        val spec = AppSpecifications.Health.BloodGlucose
        return listOf(
            GraphLimit(context.getString(R.string.health_graph_limit_lower), spec.THRESHOLD_GRAPH_NORMAL_LOWER),
            GraphLimit(context.getString(R.string.health_graph_limit_upper), spec.THRESHOLD_GRAPH_NORMAL_UPPER)
        )
    }

    /**
     * HbA1cグラフに表示する境界線のリストを返します。
     */
    fun getHbA1cGraphLimits(context: Context): List<GraphLimit> {
        val spec = AppSpecifications.Health.HbA1c
        return listOf(
            GraphLimit(context.getString(R.string.health_graph_limit_upper), spec.THRESHOLD_GRAPH_NORMAL_UPPER)
        )
    }

    //////////////////////////////////////////////////////////////////////
    // PDF用設定
    //////////////////////////////////////////////////////////////////////

    /**
     * アラートレベルに対応する PDF 用の背景色(android.graphics.Color)を返します。
     * ※PDF出力は Android View システム(Canvas)を使用するため、固定値を維持します。
     */
    fun getPdfBgColor(level: HealthAlertLevel): Int? = when (level) {
        HealthAlertLevel.WARNING -> 0xFFF0F0F0.toInt()
        HealthAlertLevel.ALERT -> 0xFFD8D8D8.toInt()
        else -> null
    }

    //////////////////////////////////////////////////////////////////////
    // 説明文（グラフ補助用）
    //////////////////////////////////////////////////////////////////////

    fun getBpExplanation(context: Context): String {
        val spec = AppSpecifications.Health.BloodPressure
        return context.getString(
            R.string.bp_explanation,
            spec.THRESHOLD_LOW_SYSTOLIC.toInt(),
            spec.THRESHOLD_HIGH_SYSTOLIC.toInt(),
            spec.THRESHOLD_LOW_DIASTOLIC.toInt(),
            spec.THRESHOLD_HIGH_DIASTOLIC.toInt()
        )
    }

    fun getSatExplanation(context: Context): String =
        context.getString(R.string.sat_explanation, AppSpecifications.Health.OxygenSaturation.THRESHOLD_LOW.toInt())

    fun getPulseExplanation(context: Context): String {
        val spec = AppSpecifications.Health.Pulse
        return context.getString(R.string.pulse_explanation, spec.THRESHOLD_LOW.toInt(), spec.THRESHOLD_HIGH.toInt())
    }

    fun getTempExplanation(context: Context): String {
        val spec = AppSpecifications.Health.BodyTemperature
        return context.getString(R.string.temp_explanation, spec.THRESHOLD_LOW, spec.THRESHOLD_HIGH)
    }

    fun getGlucoseExplanation(context: Context): String {
        val spec = AppSpecifications.Health.BloodGlucose
        return context.getString(
            R.string.glucose_explanation,
            spec.THRESHOLD_LOW.toInt(),
            spec.THRESHOLD_NORMAL_UPPER.toInt(),
            spec.THRESHOLD_PREDIABETES.toInt(),
            spec.THRESHOLD_HIGH.toInt()
        )
    }

    fun getHbA1cExplanation(context: Context): String =
        context.getString(R.string.hba1c_explanation, AppSpecifications.Health.HbA1c.THRESHOLD_NORMAL_UPPER)

    fun getBmiExplanation(context: Context): String {
        val spec = AppSpecifications.Health.BodyMassIndex
        return context.getString(R.string.bmi_explanation, spec.THRESHOLD_UNDERWEIGHT, spec.THRESHOLD_NORMAL_UPPER)
    }
}
