package jp.mydns.fujiwara.carememo.data

import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel

/**
 * アプリ全体の判定基準、定数、および判定ロジックを管理する基軸オブジェクト
 */
object AppThresholds {

    // --- 定数定義 ---

    // ***** 血圧 *****************************************************
    const val BP_HIGH_SYSTOLIC = 140.0      // 高血圧：血圧(上)＞＝140
    const val BP_HIGH_DIASTOLIC = 90.0      // 高血圧：血圧(下)＞＝90
    const val BP_LOW_SYSTOLIC = 100.0       // 低血圧：血圧(上)＜100
    const val BP_LOW_DIASTOLIC = 60.0       // 低血圧：血圧(下)＜60
    // ***** 脈拍 *****************************************************
    const val PULSE_HIGH = 100.0            // 頻脈：脈拍＞＝100
    const val PULSE_LOW = 50.0              // 徐脈：脈拍＞＝50
    // ***** 酸素飽和度(SAT) *******************************************
    const val SAT_LOW = 90.0                // 異常：SAT＜＝90
    // ***** 体温 *****************************************************
    const val TEMP_HIGH = 37.5              // 発熱：体温＞＝37．5
    const val TEMP_LOW = 35.5               // 低体温：体温＜35.5
    // ***** 血糖値 ****************************************************
                                                    // 125 ＜　 高血糖
    const val GLUCOSE_NORMAL_PREDIABETES = 125.0    // 100 ＜＝ 予備群  ＜＝ 125
    const val GLUCOSE_NORMAL_HIGH = 100.0           //  70 ＜＝ 正常　  ＜　 100
    const val GLUCOSE_NORMAL_LOW = 70.0             //     　　 低血糖  ＜　 70
    // ***** HbA1c **************************************************
    const val HBA1C_GOOD = 5.5          //     　　 正常　　 ＜＝ 5.5
    const val HBA1C_DIABETES = 6.5      // 6.5 ＜＝ 糖尿病型
    // ***** BMI ****************************************************
                                        //      　　 低体重　　 ＜ 18.5
    const val BMI_NORMAL_LOW = 18.5     // 18.5 ＜＝ 普通体重　 ＜ 25.0
    const val BMI_NORMAL_HIGH = 25.0    // 25.0 ＜＝ 肥満(１度) ＜ 30.0
    const val BMI_OBESITY_1 = 30.0      // 30.0 ＜＝ 肥満(２度) ＜ 35.0
    const val BMI_OBESITY_2 = 35.0      // 35.0 ＜＝ 肥満(３度) ＜ 40.0
    const val BMI_OBESITY_3 = 40.0      // 40.0 ＜＝ 肥満(４度)

    // --- 各カテゴリのUI/データ制限値 ---
    const val CONDITION_PHOTO_MAX_COUNT = 3
    const val IMAGE_MAX_SIZE = 1024
    const val IMAGE_THUMBNAIL_SIZE = 256
    const val PHOTOS_DIR_NAME = "photos"

//    // --- 服薬管理 (C) 関連の制限値・定義 ---
//    const val MEDICATION_TIME_SLOT_COUNT = 4
//    const val TIME_SLOT_MORNING = 0
//    const val TIME_SLOT_LUNCH = 1
//    const val TIME_SLOT_DINNER = 2
//    const val TIME_SLOT_BEDTIME = 3

    /**
     * グラフ描画用の範囲定義
     */
    data class VisualRange(val start: Double, val end: Double, val level: HealthAlertLevel)

    /**
     * グラフ描画用の閾値線定義
     */
    data class VisualLimit(val label: String, val value: Double, val isAbove: Boolean)

    // --- グラフ用メタデータ取得 ---

    fun getBpRanges(): List<VisualRange> = listOf(
        VisualRange(BP_HIGH_SYSTOLIC, 300.0, HealthAlertLevel.ALERT),
        VisualRange(100.0, BP_HIGH_SYSTOLIC, HealthAlertLevel.NORMAL),
        VisualRange(90.0, 100.0, HealthAlertLevel.WARNING),
        VisualRange(BP_LOW_DIASTOLIC, 90.0, HealthAlertLevel.NORMAL),
        VisualRange(0.0, BP_LOW_DIASTOLIC, HealthAlertLevel.INFO)
    )

    fun getPulseRanges(): List<VisualRange> = listOf(
        VisualRange(PULSE_HIGH, 300.0, HealthAlertLevel.ALERT),
        VisualRange(PULSE_LOW, PULSE_HIGH, HealthAlertLevel.NORMAL),
        VisualRange(0.0, PULSE_LOW, HealthAlertLevel.INFO)
    )

    fun getSatRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, SAT_LOW, HealthAlertLevel.ALERT),
        VisualRange(SAT_LOW + 0.1, 100.0, HealthAlertLevel.NORMAL)
    )

    fun getTempRanges(): List<VisualRange> = listOf(
        VisualRange(TEMP_HIGH, 50.0, HealthAlertLevel.ALERT),
        VisualRange(TEMP_LOW, TEMP_HIGH, HealthAlertLevel.NORMAL),
        VisualRange(0.0, TEMP_LOW, HealthAlertLevel.INFO)
    )

    fun getGlucoseRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, GLUCOSE_NORMAL_LOW, HealthAlertLevel.INFO),
        VisualRange(GLUCOSE_NORMAL_LOW, GLUCOSE_NORMAL_HIGH, HealthAlertLevel.NORMAL),
        VisualRange(GLUCOSE_NORMAL_HIGH, GLUCOSE_NORMAL_PREDIABETES, HealthAlertLevel.WARNING),
        VisualRange(GLUCOSE_NORMAL_PREDIABETES + 0.1, 1000.0, HealthAlertLevel.ALERT)
    )

    fun getGlucoseLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(下限)", GLUCOSE_NORMAL_LOW, false),
        VisualLimit("正常(上限)", GLUCOSE_NORMAL_HIGH, true)
    )

    fun getHbA1cRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, HBA1C_GOOD, HealthAlertLevel.NORMAL),
        VisualRange(HBA1C_GOOD + 0.01, HBA1C_DIABETES - 0.01, HealthAlertLevel.WARNING),
        VisualRange(HBA1C_DIABETES, 20.0, HealthAlertLevel.ALERT)
    )

    fun getHbA1cLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(上限)", HBA1C_GOOD, true)
    )

    fun getBmiRanges(): List<VisualRange> = listOf(
        VisualRange(0.0, BMI_NORMAL_LOW, HealthAlertLevel.INFO),
        VisualRange(BMI_NORMAL_LOW, BMI_NORMAL_HIGH, HealthAlertLevel.NORMAL),
        VisualRange(BMI_NORMAL_HIGH, BMI_OBESITY_1, HealthAlertLevel.WARNING),
        VisualRange(BMI_OBESITY_1, BMI_OBESITY_2, HealthAlertLevel.ALERT),
        VisualRange(BMI_OBESITY_2, BMI_OBESITY_3, HealthAlertLevel.ALERT),
        VisualRange(BMI_OBESITY_3, 100.0, HealthAlertLevel.ALERT)
    )

    fun getBmiLimits(): List<VisualLimit> = listOf(
        VisualLimit("正常(下限)", BMI_NORMAL_LOW, false),
        VisualLimit("正常(上限)", BMI_NORMAL_HIGH, true)
    )

    // --- 数値入力・表示制限（A系統） ---
    const val DIGITS_HEIGHT_INT = 3
    const val DIGITS_HEIGHT_DEC = 1
    const val DIGITS_WEIGHT_INT = 3
    const val DIGITS_WEIGHT_DEC = 1
    const val DIGITS_BP_INT = 3
    const val DIGITS_PULSE_INT = 3
    const val DIGITS_SAT_INT = 3
    const val DIGITS_TEMP_INT = 2
    const val DIGITS_TEMP_DEC = 1
    const val DIGITS_GLUCOSE_INT = 3
    const val DIGITS_HBA1C_INT = 2
    const val DIGITS_HBA1C_DEC = 1

    // --- 単位定義 ---
    const val UNIT_HEIGHT = "cm"
    const val UNIT_WEIGHT = "kg"
    const val UNIT_BP = "mmHg"
    const val UNIT_PULSE = "bpm"
    const val UNIT_SAT = "%"
    const val UNIT_BODY_TEMP = "℃"
    const val UNIT_GLUCOSE = "mg/dL"
    const val UNIT_HBA1C = "%"

    /**
     * 文字列が指定された整数桁・小数桁の形式に合致するか判定する
     */
    fun isWithinFormat(value: String, intDigits: Int, decDigits: Int = 0): Boolean {
        if (value.isBlank()) return true
        val parts = value.split(".")
        if (parts.size > 2) return false

        val intPart = parts[0]
        if (intPart.length > intDigits) return false

        if (parts.size == 2) {
            val decPart = parts[1]
            if (decPart.length > decDigits) return false
        }

        val num = value.toDoubleOrNull()
        return num != null && num >= 0
    }

    // --- 表示用フォーマッタ ---
    fun formatHeight(value: Double?): String = value?.let { "%.1f".format(it) } ?: "---"
    fun formatWeight(value: Double?): String = value?.let { "%.1f".format(it) } ?: "---"
    fun formatBodyTemp(value: Double?): String = value?.let { "%.1f".format(it) } ?: "---"
    fun formatSat(value: Int?): String = value?.toString() ?: "---"
    fun formatHbA1c(value: Double?): String = value?.let { "%.1f".format(it) } ?: "---"
    fun formatGlucose(value: Int?): String = value?.toString() ?: "---"
    fun formatBpValue(value: Int?): String = value?.toString() ?: "---"
    fun formatPulse(value: Int?): String = value?.toString() ?: "---"
    fun formatBmi(value: Double?): String = if (value != null && value > 0) "%.1f".format(value) else "---"
}
