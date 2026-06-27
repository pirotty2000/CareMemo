package jp.mydns.fujiwara.carememo.data

import android.graphics.Color

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

    // --- ラベル定義 ---
    const val VITAL_LABEL_NORMAL = "正常"
    const val VITAL_LABEL_HIGH_BP = "高血圧"
    const val VITAL_LABEL_LOW_BP = "低血圧"
    const val VITAL_LABEL_TACHYCARDIA = "頻脈"
    const val VITAL_LABEL_BRADYCARDIA = "徐脈"
    const val VITAL_LABEL_FEVER = "発熱"
    const val VITAL_LABEL_HYPOTHERMIA = "低体温"
    const val GLUCOSE_LABEL_LOW = "低血糖"
    const val GLUCOSE_LABEL_NORMAL = "良好"
    const val GLUCOSE_LABEL_HIGH = "高血糖"
    const val HBA1C_LABEL_NORMAL = "正常"
    const val HBA1C_LABEL_NORMAL_HIGH = "正常高値"
    const val HBA1C_LABEL_PREDIABETES = "糖尿病予備軍"
    const val HBA1C_LABEL_DIABETES = "糖尿病型"
    const val BMI_LABEL_UNDERWEIGHT = "低体重"
    const val BMI_LABEL_NORMAL = "普通体重"
    const val BMI_LABEL_OBESITY_1 = "肥満(１度)"
    const val BMI_LABEL_OBESITY_2 = "肥満(２度)"
    const val BMI_LABEL_OBESITY_3 = "肥満(３度)"
    const val BMI_LABEL_OBESITY_4 = "肥満(４度)"

    const val HEALTH_LABEL_HEIGHT = "身長"
    const val HEALTH_LABEL_WEIGHT = "体重"
    const val HEALTH_LABEL_BMI = "BMI"
    const val HEALTH_LABEL_BP = "血圧"
    const val HEALTH_LABEL_BP_SYSTOLIC = "血圧(上)"
    const val HEALTH_LABEL_BP_DIASTOLIC = "血圧(下)"
    const val HEALTH_LABEL_SYSTOLIC_SHORT = "上"
    const val HEALTH_LABEL_DIASTOLIC_SHORT = "下"
    const val HEALTH_LABEL_PULSE = "脈拍"
    const val HEALTH_LABEL_PULSE_SHORT = "脈"
    const val HEALTH_LABEL_BODY_TEMP = "体温"
    const val HEALTH_LABEL_GLUCOSE = "血糖値"
    const val HEALTH_LABEL_HBA1C = "HbA1c"
    const val HEALTH_LABEL_STATUS = "判定"

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
    fun evaluateBMI(bmi: Double): Pair<String, AlertLevel> {
        return when {
            bmi <= 0.0 -> "---" to AlertLevel.NORMAL
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
    fun evaluateVital(systolic: Int?, diastolic: Int?, pulse: Int?, temp: Double?): List<Pair<String, AlertLevel>> {
        val results = mutableListOf<Pair<String, AlertLevel>>()
        
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
    fun evaluateGlucose(glucose: Int?): Pair<String, AlertLevel> {
        val g = glucose ?: return "---" to AlertLevel.NORMAL
        return when {
            g < GLUCOSE_NORMAL_LOW -> GLUCOSE_LABEL_LOW to AlertLevel.ALERT
            g <= GLUCOSE_NORMAL_HIGH -> GLUCOSE_LABEL_NORMAL to AlertLevel.NORMAL
            else -> GLUCOSE_LABEL_HIGH to AlertLevel.ALERT
        }
    }

    /**
     * HbA1cの判定
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<String, AlertLevel> {
        val h = hba1c ?: return "---" to AlertLevel.NORMAL
        return when {
            h >= HBA1C_DIABETES -> HBA1C_LABEL_DIABETES to AlertLevel.ALERT
            h >= HBA1C_PREDIABETES -> HBA1C_LABEL_PREDIABETES to AlertLevel.ALERT
            h > HBA1C_GOOD -> HBA1C_LABEL_NORMAL_HIGH to AlertLevel.WARNING
            else -> HBA1C_LABEL_NORMAL to AlertLevel.NORMAL
        }
    }

    // --- 説明文（グラフ補助用） ---
    val BP_EXPLANATION = "血圧グラフの見方：\n・${BP_LOW_SYSTOLIC.toInt()}〜${BP_HIGH_SYSTOLIC.toInt()}（上）、${BP_LOW_DIASTOLIC.toInt()}〜${BP_HIGH_DIASTOLIC.toInt()}（下）が正常範囲です。"
    val PULSE_EXPLANATION = "脈拍グラフの見方：\n・${PULSE_LOW.toInt()}〜${PULSE_HIGH.toInt()} が正常範囲です。"
    val TEMP_EXPLANATION = "体温グラフの見方：\n・$TEMP_LOW〜$TEMP_HIGH が平熱の目安です。"
    val GLUCOSE_EXPLANATION = "血糖値グラフの見方：\n・${GLUCOSE_NORMAL_LOW.toInt()}〜${GLUCOSE_NORMAL_HIGH.toInt()} mg/dL が良好な範囲です。"
    val HBA1C_EXPLANATION = "HbA1cグラフの見方：\n・$HBA1C_GOOD％以下が正常範囲です。"
    val BMI_EXPLANATION = "BMIグラフの見方：\n・${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満が普通体重です。"
}
