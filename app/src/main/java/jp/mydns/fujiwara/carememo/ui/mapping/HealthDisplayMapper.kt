package jp.mydns.fujiwara.carememo.ui.mapping

import android.content.Context
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.logic.common.*

/**
 * 健康記録の判定結果(Enum)を表示用の資源(リソースID、色)に変換するマッパー。
 */
object HealthDisplayMapper {

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
     * 血糖値ステータスに対応する文字列リソースIDを返します。
     */
    fun getGlucoseLabel(status: GlucoseStatus?): Int? = when (status) {
        GlucoseStatus.LOW -> R.string.glucose_label_low // 低血糖
        GlucoseStatus.NORMAL -> R.string.glucose_label_normal // 正常
        GlucoseStatus.WARNING -> R.string.hba1c_label_prediabetes // 予備群
        GlucoseStatus.HIGH -> R.string.glucose_label_high // 高血糖
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
     * アラートレベルに対応する Compose 用のカラーを返します。
     */
    fun getAlertColor(level: HealthAlertLevel): Color = when (level) {
        HealthAlertLevel.NORMAL -> Color(0xFF2196F3) // Blue
        HealthAlertLevel.WARNING -> Color(0xFF000000) // Black (テキスト用)
        HealthAlertLevel.ALERT -> Color(0xFFF44336) // Red
        HealthAlertLevel.INFO -> Color(0xFF00BCD4) // Cyan
    }

    /**
     * アラートレベルに対応する PDF 用の背景色(android.graphics.Color)を返します。
     */
    fun getPdfBgColor(level: HealthAlertLevel): Int? = when (level) {
        HealthAlertLevel.WARNING -> 0xFFF0F0F0.toInt()
        HealthAlertLevel.ALERT -> 0xFFD8D8D8.toInt()
        else -> null
    }

    /**
     * 複数のバイタル判定結果を「・」で連結した文字列を生成します。
     */
    fun formatVitalResults(context: Context, results: List<Pair<VitalStatus, HealthAlertLevel>>): String {
        if (results.all { it.second == HealthAlertLevel.NORMAL }) {
            return context.getString(R.string.vital_label_normal)
        }
        return results
            .filter { it.second != HealthAlertLevel.NORMAL }
            .joinToString("・") { context.getString(getVitalLabel(it.first)) }
    }

    // --- 説明文（グラフ補助用） ---

    fun getBpExplanation(context: Context): String =
        context.getString(R.string.bp_explanation, 
            AppThresholds.BP_LOW_SYSTOLIC.toInt(), 
            AppThresholds.BP_HIGH_SYSTOLIC.toInt(), 
            AppThresholds.BP_LOW_DIASTOLIC.toInt(), 
            AppThresholds.BP_HIGH_DIASTOLIC.toInt())

    fun getSatExplanation(context: Context): String =
        context.getString(R.string.sat_explanation, AppThresholds.SAT_LOW.toInt())

    fun getPulseExplanation(context: Context): String =
        context.getString(R.string.pulse_explanation, AppThresholds.PULSE_LOW.toInt(), AppThresholds.PULSE_HIGH.toInt())

    fun getTempExplanation(context: Context): String =
        context.getString(R.string.temp_explanation, AppThresholds.TEMP_LOW, AppThresholds.TEMP_HIGH)

    fun getGlucoseExplanation(context: Context): String =
        context.getString(R.string.glucose_explanation, AppThresholds.GLUCOSE_NORMAL_LOW.toInt(), 99)

    fun getHbA1cExplanation(context: Context): String =
        context.getString(R.string.hba1c_explanation, AppThresholds.HBA1C_GOOD)

    fun getBmiExplanation(context: Context): String =
        context.getString(R.string.bmi_explanation, AppThresholds.BMI_NORMAL_LOW, AppThresholds.BMI_NORMAL_HIGH)
}
