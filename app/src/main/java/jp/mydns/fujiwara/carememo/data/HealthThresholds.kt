package jp.mydns.fujiwara.carememo.data

import android.content.Context
import android.graphics.Color
import jp.mydns.fujiwara.carememo.R

/**
 * 健康指標の判定基準と判定ロジックを管理する基軸オブジェクト
 */
object HealthThresholds {
    // --- 定数定義 ---
    const val BP_HIGH_SYSTOLIC = 140.0
    const val BP_HIGH_DIASTOLIC = 90.0
    const val BP_LOW_SYSTOLIC = 100.0
    const val BP_LOW_DIASTOLIC = 60.0
    const val PULSE_HIGH = 100.0
    const val PULSE_LOW = 50.0
    const val TEMP_HIGH = 37.5
    const val TEMP_LOW = 35.5
    const val GLUCOSE_NORMAL_HIGH = 99.0
    const val GLUCOSE_NORMAL_LOW = 70.0
    const val HBA1C_GOOD = 5.5
    const val HBA1C_PREDIABETES = 6.0
    const val HBA1C_DIABETES = 6.5
    const val BMI_NORMAL_LOW = 18.5
    const val BMI_NORMAL_HIGH = 25.0
    const val BMI_OBESITY_1 = 30.0
    const val BMI_OBESITY_2 = 35.0
    const val BMI_OBESITY_3 = 40.0

    // --- ラベル定義（Resource ID） ---
    val VITAL_LABEL_NORMAL = R.string.vital_label_normal
    val VITAL_LABEL_HIGH_BP = R.string.vital_label_high_bp
    val VITAL_LABEL_LOW_BP = R.string.vital_label_low_bp
    val VITAL_LABEL_TACHYCARDIA = R.string.vital_label_tachycardia
    val VITAL_LABEL_BRADYCARDIA = R.string.vital_label_bradycardia
    val VITAL_LABEL_FEVER = R.string.vital_label_fever
    val VITAL_LABEL_HYPOTHERMIA = R.string.vital_label_hypothermia
    val GLUCOSE_LABEL_LOW = R.string.glucose_label_low
    val GLUCOSE_LABEL_NORMAL = R.string.glucose_label_normal
    val GLUCOSE_LABEL_HIGH = R.string.glucose_label_high
    val HBA1C_LABEL_NORMAL = R.string.hba1c_label_normal
    val HBA1C_LABEL_NORMAL_HIGH = R.string.hba1c_label_normal_high
    val HBA1C_LABEL_PREDIABETES = R.string.hba1c_label_prediabetes
    val HBA1C_LABEL_DIABETES = R.string.hba1c_label_diabetes
    val BMI_LABEL_UNDERWEIGHT = R.string.bmi_label_underweight
    val BMI_LABEL_NORMAL = R.string.bmi_label_normal
    val BMI_LABEL_OBESITY_1 = R.string.bmi_label_obesity_1
    val BMI_LABEL_OBESITY_2 = R.string.bmi_label_obesity_2
    val BMI_LABEL_OBESITY_3 = R.string.bmi_label_obesity_3
    val BMI_LABEL_OBESITY_4 = R.string.bmi_label_obesity_4

    val HEALTH_LABEL_HEIGHT = R.string.health_label_height
    val HEALTH_LABEL_WEIGHT = R.string.health_label_weight
    val HEALTH_LABEL_BMI = R.string.health_label_bmi
    val HEALTH_LABEL_BP = R.string.health_label_bp
    val HEALTH_LABEL_BP_SYSTOLIC = R.string.health_label_bp_systolic
    val HEALTH_LABEL_BP_DIASTOLIC = R.string.health_label_bp_diastolic
    val HEALTH_LABEL_SYSTOLIC_SHORT = R.string.health_label_systolic_short
    val HEALTH_LABEL_DIASTOLIC_SHORT = R.string.health_label_diastolic_short
    val HEALTH_LABEL_PULSE = R.string.health_label_pulse
    val HEALTH_LABEL_PULSE_SHORT = R.string.health_label_pulse_short
    val HEALTH_LABEL_BODY_TEMP = R.string.health_label_body_temp
    val HEALTH_LABEL_GLUCOSE = R.string.health_label_glucose
    val HEALTH_LABEL_HBA1C = R.string.health_label_hba1c
    val HEALTH_LABEL_STATUS = R.string.health_label_status

    /**
     * アラートレベルの定義
     */
    enum class AlertLevel(val color: Int, val pdfBgColor: Int?, val severity: Int) {
        NORMAL(Color.BLUE, null, 0),
        WARNING(Color.BLACK, 0xFFF0F0F0.toInt(), 1),
        ALERT(Color.RED, 0xFFD8D8D8.toInt(), 2)
    }

    // --- 判定ロジック ---

    /**
     * BMIの判定
     */
    fun evaluateBMI(bmi: Double): Pair<Int?, AlertLevel> {
        return when {
            bmi <= 0.0 -> null to AlertLevel.NORMAL
            bmi < BMI_NORMAL_LOW -> BMI_LABEL_UNDERWEIGHT to AlertLevel.WARNING
            bmi < BMI_NORMAL_HIGH -> BMI_LABEL_NORMAL to AlertLevel.NORMAL
            bmi < BMI_OBESITY_1 -> BMI_LABEL_OBESITY_1 to AlertLevel.WARNING
            bmi < BMI_OBESITY_2 -> BMI_LABEL_OBESITY_2 to AlertLevel.WARNING
            bmi < BMI_OBESITY_3 -> BMI_LABEL_OBESITY_3 to AlertLevel.ALERT
            else -> BMI_LABEL_OBESITY_4 to AlertLevel.ALERT
        }
    }

    /**
     * バイタルの判定
     */
    fun evaluateVital(systolic: Int?, diastolic: Int?, pulse: Int?, temp: Double?): List<Pair<Int, AlertLevel>> {
        val results = mutableListOf<Pair<Int, AlertLevel>>()
        
        systolic?.let {
            if (it >= BP_HIGH_SYSTOLIC) results.add(VITAL_LABEL_HIGH_BP to AlertLevel.ALERT)
            else if (it < BP_LOW_SYSTOLIC) results.add(VITAL_LABEL_LOW_BP to AlertLevel.WARNING)
        }
        diastolic?.let {
            if (it >= BP_HIGH_DIASTOLIC) results.add(VITAL_LABEL_HIGH_BP to AlertLevel.ALERT)
            else if (it < BP_LOW_DIASTOLIC) results.add(VITAL_LABEL_LOW_BP to AlertLevel.WARNING)
        }
        pulse?.let {
            if (it >= PULSE_HIGH) results.add(VITAL_LABEL_TACHYCARDIA to AlertLevel.ALERT)
            else if (it <= PULSE_LOW) results.add(VITAL_LABEL_BRADYCARDIA to AlertLevel.WARNING)
        }
        temp?.let {
            if (it >= TEMP_HIGH) results.add(VITAL_LABEL_FEVER to AlertLevel.ALERT)
            else if (it < TEMP_LOW) results.add(VITAL_LABEL_HYPOTHERMIA to AlertLevel.WARNING)
        }

        if (results.isEmpty()) return listOf(VITAL_LABEL_NORMAL to AlertLevel.NORMAL)
        return results.distinctBy { it.first }
    }

    /**
     * 血糖値の判定
     */
    fun evaluateGlucose(glucose: Int?): Pair<Int?, AlertLevel> {
        val g = glucose ?: return null to AlertLevel.NORMAL
        return when {
            g < GLUCOSE_NORMAL_LOW -> GLUCOSE_LABEL_LOW to AlertLevel.ALERT
            g <= GLUCOSE_NORMAL_HIGH -> GLUCOSE_LABEL_NORMAL to AlertLevel.NORMAL
            else -> GLUCOSE_LABEL_HIGH to AlertLevel.ALERT
        }
    }

    /**
     * HbA1cの判定
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<Int?, AlertLevel> {
        val h = hba1c ?: return null to AlertLevel.NORMAL
        return when {
            h >= HBA1C_DIABETES -> HBA1C_LABEL_DIABETES to AlertLevel.ALERT
            h >= HBA1C_PREDIABETES -> HBA1C_LABEL_PREDIABETES to AlertLevel.ALERT
            h > HBA1C_GOOD -> HBA1C_LABEL_NORMAL_HIGH to AlertLevel.WARNING
            else -> HBA1C_LABEL_NORMAL to AlertLevel.NORMAL
        }
    }

    // --- 説明文（グラフ補助用） ---
    fun getBpExplanation(context: Context): String = context.getString(R.string.bp_explanation, BP_LOW_SYSTOLIC.toInt(), BP_HIGH_SYSTOLIC.toInt(), BP_LOW_DIASTOLIC.toInt(), BP_HIGH_DIASTOLIC.toInt())
    fun getPulseExplanation(context: Context): String = context.getString(R.string.pulse_explanation, PULSE_LOW.toInt(), PULSE_HIGH.toInt())
    fun getTempExplanation(context: Context): String = context.getString(R.string.temp_explanation, TEMP_LOW, TEMP_HIGH)
    fun getGlucoseExplanation(context: Context): String = context.getString(R.string.glucose_explanation, GLUCOSE_NORMAL_LOW.toInt(), GLUCOSE_NORMAL_HIGH.toInt())
    fun getHbA1cExplanation(context: Context): String = context.getString(R.string.hba1c_explanation, HBA1C_GOOD)
    fun getBmiExplanation(context: Context): String = context.getString(R.string.bmi_explanation, BMI_NORMAL_LOW, BMI_NORMAL_HIGH)
}
