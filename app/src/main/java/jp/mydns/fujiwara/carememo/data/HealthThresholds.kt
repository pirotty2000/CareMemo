package jp.mydns.fujiwara.carememo.data

import android.content.Context
import android.graphics.Color
import jp.mydns.fujiwara.carememo.R

/**
 * 健康指標の判定基準と判定ロジックを管理する基軸オブジェクト
 */
object HealthThresholds {

    // --- 定数定義 ---

    // ***** 血圧 *****************************************************
    const val BP_HIGH_SYSTOLIC = 140.0      // 高血圧：血圧(上)＞＝140
    const val BP_HIGH_DIASTOLIC = 90.0      // 高血圧：血圧(下)＞＝90
    const val BP_LOW_SYSTOLIC = 100.0       // 低血圧：血圧(上)＜100
    const val BP_LOW_DIASTOLIC = 60.0       // 低血圧：血圧(下)＜60
    // ***** 脈拍 *****************************************************
    const val PULSE_HIGH = 100.0            // 頻脈：脈拍＞＝100
    const val PULSE_LOW = 50.0              // 徐脈：脈拍＞＝50
    // ***** 体温 *****************************************************
    const val TEMP_HIGH = 37.5              // 発熱：体温＞＝37．5
    const val TEMP_LOW = 35.5               // 低体温：体温＜35.5
    // ***** 血糖値 ****************************************************
                                                    // 125 ＜　 高血糖
    const val GLUCOSE_NORMAL_PREDIABETES = 125.0    // 100 ＜＝ 予備群  ＜＝ 125
    const val GLUCOSE_NORMAL_HIGH = 100.0           //  70 ＜＝ 正常　  ＜　 100
    const val GLUCOSE_NORMAL_LOW = 70.0             //     　　 低血糖  ＜　 70
    // ***** HbA1c **************************************************
    const val HBA1C_GOOD = 5.5          //     　　 正常　　 ＜＝ 5.5 ：グラフ・ハイライトは白
    const val HBA1C_DIABETES = 6.5      // 6.5 ＜＝ 糖尿病型 　　     ：グラフ・ハイライトは薄い赤
    // ***** BMI ****************************************************
                                        //      　　 低体重　　 ＜ 18.5 ：グラフ・ハイライトは薄い青
    const val BMI_NORMAL_LOW = 18.5     // 18.5 ＜＝ 普通体重　 ＜ 25.0 ：グラフ・ハイライトは白(ハイライトなし)
    const val BMI_NORMAL_HIGH = 25.0    // 25.0 ＜＝ 肥満(１度) ＜ 30.0 ：グラフ・ハイライトは薄い黄色
    const val BMI_OBESITY_1 = 30.0      // 30.0 ＜＝ 肥満(２度) ＜ 35.0 ：グラフ・ハイライトは薄い赤
    const val BMI_OBESITY_2 = 35.0      // 35.0 ＜＝ 肥満(３度) ＜ 40.0 ：グラフ・ハイライトは薄い紫
    const val BMI_OBESITY_3 = 40.0      // 40.0 ＜＝ 肥満(４度) 　      ：グラフ・ハイライトは紫

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
        ALERT(Color.RED, 0xFFD8D8D8.toInt(), 2),
        INFO(Color.CYAN, null, -1) // 低血圧や低体重などの「低め」の状態用
    }

    /**
     * グラフ描画用の範囲定義
     */
    data class VisualRange(val start: Double, val end: Double, val level: AlertLevel)

    /**
     * グラフ描画用の閾値線定義
     */
    data class VisualLimit(val label: String, val value: Double, val isAbove: Boolean)

    // --- グラフ用メタデータ取得 ---

    fun getBpRanges(): List<VisualRange> = listOf(
        VisualRange(BP_HIGH_SYSTOLIC, 300.0, AlertLevel.ALERT),
        VisualRange(100.0, BP_HIGH_SYSTOLIC, AlertLevel.NORMAL), // 正常(上)
        VisualRange(90.0, 100.0, AlertLevel.WARNING),
        VisualRange(BP_LOW_DIASTOLIC, 90.0, AlertLevel.NORMAL),  // 正常(下)
        VisualRange(0.0, BP_LOW_DIASTOLIC, AlertLevel.INFO)
    )

    fun getPulseRanges(): List<VisualRange> = listOf(
        VisualRange(PULSE_HIGH, 300.0, AlertLevel.ALERT),
        VisualRange(PULSE_LOW, PULSE_HIGH, AlertLevel.NORMAL), // 正常
        VisualRange(0.0, PULSE_LOW, AlertLevel.INFO)
    )

    fun getTempRanges(): List<VisualRange> = listOf(
        VisualRange(TEMP_HIGH, 50.0, AlertLevel.ALERT),
        VisualRange(TEMP_LOW, TEMP_HIGH, AlertLevel.NORMAL), // 正常
        VisualRange(0.0, TEMP_LOW, AlertLevel.INFO)
    )

    fun getGlucoseRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, GLUCOSE_NORMAL_LOW, AlertLevel.INFO),
        VisualRange(GLUCOSE_NORMAL_LOW, GLUCOSE_NORMAL_HIGH, AlertLevel.NORMAL), // 正常
        VisualRange(GLUCOSE_NORMAL_HIGH, GLUCOSE_NORMAL_PREDIABETES, AlertLevel.WARNING),
        VisualRange(GLUCOSE_NORMAL_PREDIABETES + 0.1, 1000.0, AlertLevel.ALERT)
    )

    fun getGlucoseLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(下限)", GLUCOSE_NORMAL_LOW, false),
        VisualLimit("正常(上限)", GLUCOSE_NORMAL_HIGH, true)
    )

    fun getHbA1cRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, HBA1C_GOOD, AlertLevel.NORMAL), // 正常
        VisualRange(HBA1C_GOOD + 0.01, HBA1C_DIABETES - 0.01, AlertLevel.WARNING),
        VisualRange(HBA1C_DIABETES, 20.0, AlertLevel.ALERT)
    )

    fun getHbA1cLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(上限)", HBA1C_GOOD, true)
    )

    fun getBmiRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, BMI_NORMAL_LOW, AlertLevel.INFO),
        VisualRange(BMI_NORMAL_LOW, BMI_NORMAL_HIGH, AlertLevel.NORMAL), // 正常
        VisualRange(BMI_NORMAL_HIGH, BMI_OBESITY_1, AlertLevel.WARNING),
        VisualRange(BMI_OBESITY_1, BMI_OBESITY_2, AlertLevel.ALERT),
        VisualRange(BMI_OBESITY_2, BMI_OBESITY_3, AlertLevel.ALERT),
        VisualRange(BMI_OBESITY_3, 100.0, AlertLevel.ALERT)
    )

    fun getBmiLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(下限)", BMI_NORMAL_LOW, false),
        VisualLimit("正常(上限)", BMI_NORMAL_HIGH, true)
    )

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
            g > GLUCOSE_NORMAL_PREDIABETES -> GLUCOSE_LABEL_HIGH to AlertLevel.ALERT
            g >= GLUCOSE_NORMAL_HIGH -> HBA1C_LABEL_PREDIABETES to AlertLevel.WARNING
            g >= GLUCOSE_NORMAL_LOW -> GLUCOSE_LABEL_NORMAL to AlertLevel.NORMAL
            else -> GLUCOSE_LABEL_LOW to AlertLevel.ALERT
        }
    }

    /**
     * HbA1cの判定
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<Int?, AlertLevel> {
        val h = hba1c ?: return null to AlertLevel.NORMAL
        return when {
            h >= HBA1C_DIABETES -> HBA1C_LABEL_DIABETES to AlertLevel.ALERT
            h > HBA1C_GOOD -> HBA1C_LABEL_PREDIABETES to AlertLevel.WARNING
            else -> HBA1C_LABEL_NORMAL to AlertLevel.NORMAL
        }
    }

    // --- 説明文（グラフ補助用） ---
    fun getBpExplanation(context: Context): String = context.getString(R.string.bp_explanation, BP_LOW_SYSTOLIC.toInt(), BP_HIGH_SYSTOLIC.toInt(), BP_LOW_DIASTOLIC.toInt(), BP_HIGH_DIASTOLIC.toInt())
    fun getPulseExplanation(context: Context): String = context.getString(R.string.pulse_explanation, PULSE_LOW.toInt(), PULSE_HIGH.toInt())
    fun getTempExplanation(context: Context): String = context.getString(R.string.temp_explanation, TEMP_LOW, TEMP_HIGH)
    fun getGlucoseExplanation(context: Context): String = context.getString(R.string.glucose_explanation, GLUCOSE_NORMAL_LOW.toInt(), 99)
    fun getHbA1cExplanation(context: Context): String = context.getString(R.string.hba1c_explanation, HBA1C_GOOD)
    fun getBmiExplanation(context: Context): String = context.getString(R.string.bmi_explanation, BMI_NORMAL_LOW, BMI_NORMAL_HIGH)
}
