package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppThresholds
import kotlin.math.pow

/**
 * 健康状態の警告レベル定義
 */
enum class HealthAlertLevel(val severity: Int) {
    NORMAL(0),
    WARNING(1),
    ALERT(2),
    INFO(-1);

    companion object {
        /**
         * 複数の警告レベルの中から最も深刻なものを選択します。
         */
        fun worst(levels: List<HealthAlertLevel>): HealthAlertLevel {
            if (levels.isEmpty()) return NORMAL
            return levels.maxByOrNull { it.severity } ?: NORMAL
        }
    }
}

/**
 * 各項目の詳細ステータス定義
 */
enum class BmiStatus { UNDERWEIGHT, NORMAL, OBESITY_1, OBESITY_2, OBESITY_3, OBESITY_4 }
enum class VitalStatus { NORMAL, HIGH_BP, LOW_BP, TACHYCARDIA, BRADYCARDIA, LOW_SAT, FEVER, HYPOTHERMIA }
enum class GlucoseStatus { LOW, NORMAL, WARNING, HIGH }
enum class HbA1cStatus { NORMAL, WARNING, DIABETES }

/**
 * 健康記録に関する純粋な計算・判定ロジック。
 * AppThresholds の定数を参照し、判定結果として Enum を返します。
 */
object HealthLogic {

    /**
     * BMIを計算します。
     */
    fun calculateBMI(heightCm: Double?, weightKg: Double?): Double {
        if (heightCm == null || weightKg == null || heightCm <= 0.0) return 0.0
        return weightKg / (heightCm / 100.0).pow(2.0)
    }

    /**
     * BMI値に基づきステータスとアラートレベルを判定します。
     */
    fun evaluateBMI(bmi: Double): Pair<BmiStatus?, HealthAlertLevel> {
        return when {
            bmi <= 0.0 -> null to HealthAlertLevel.NORMAL
            bmi < AppThresholds.BMI_NORMAL_LOW -> BmiStatus.UNDERWEIGHT to HealthAlertLevel.INFO
            bmi < AppThresholds.BMI_NORMAL_HIGH -> BmiStatus.NORMAL to HealthAlertLevel.NORMAL
            bmi < AppThresholds.BMI_OBESITY_1 -> BmiStatus.OBESITY_1 to HealthAlertLevel.WARNING
            bmi < AppThresholds.BMI_OBESITY_2 -> BmiStatus.OBESITY_2 to HealthAlertLevel.WARNING
            bmi < AppThresholds.BMI_OBESITY_3 -> BmiStatus.OBESITY_3 to HealthAlertLevel.ALERT
            else -> BmiStatus.OBESITY_4 to HealthAlertLevel.ALERT
        }
    }

    /**
     * バイタル各項目を個別に判定します。
     */
    fun evaluateVitalItems(
        systolic: Int?,
        diastolic: Int?,
        sat: Int?,
        pulse: Int?,
        temp: Double?
    ): List<Pair<VitalStatus, HealthAlertLevel>> {
        val results = mutableListOf<Pair<VitalStatus, HealthAlertLevel>>()

        systolic?.let {
            if (it >= AppThresholds.BP_HIGH_SYSTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < AppThresholds.BP_LOW_SYSTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        diastolic?.let {
            if (it >= AppThresholds.BP_HIGH_DIASTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < AppThresholds.BP_LOW_DIASTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        sat?.let {
            if (it <= AppThresholds.SAT_LOW) results.add(VitalStatus.LOW_SAT to HealthAlertLevel.ALERT)
        }
        pulse?.let {
            if (it >= AppThresholds.PULSE_HIGH) results.add(VitalStatus.TACHYCARDIA to HealthAlertLevel.ALERT)
            else if (it <= AppThresholds.PULSE_LOW) results.add(VitalStatus.BRADYCARDIA to HealthAlertLevel.WARNING)
        }
        temp?.let {
            if (it >= AppThresholds.TEMP_HIGH) results.add(VitalStatus.FEVER to HealthAlertLevel.ALERT)
            else if (it < AppThresholds.TEMP_LOW) results.add(VitalStatus.HYPOTHERMIA to HealthAlertLevel.WARNING)
        }

        if (results.isEmpty()) return listOf(VitalStatus.NORMAL to HealthAlertLevel.NORMAL)
        return results.distinctBy { it.first }
    }

    /**
     * 血糖値を判定します。
     */
    fun evaluateGlucose(glucose: Int?): Pair<GlucoseStatus?, HealthAlertLevel> {
        val g = glucose ?: return null to HealthAlertLevel.NORMAL
        return when {
            g > AppThresholds.GLUCOSE_NORMAL_PREDIABETES -> GlucoseStatus.HIGH to HealthAlertLevel.ALERT
            g >= AppThresholds.GLUCOSE_NORMAL_HIGH -> GlucoseStatus.WARNING to HealthAlertLevel.WARNING
            g >= AppThresholds.GLUCOSE_NORMAL_LOW -> GlucoseStatus.NORMAL to HealthAlertLevel.NORMAL
            else -> GlucoseStatus.LOW to HealthAlertLevel.ALERT
        }
    }

    /**
     * HbA1cを判定します。
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<HbA1cStatus?, HealthAlertLevel> {
        val h = hba1c ?: return null to HealthAlertLevel.NORMAL
        return when {
            h >= AppThresholds.HBA1C_DIABETES -> HbA1cStatus.DIABETES to HealthAlertLevel.ALERT
            h > AppThresholds.HBA1C_GOOD -> HbA1cStatus.WARNING to HealthAlertLevel.WARNING
            else -> HbA1cStatus.NORMAL to HealthAlertLevel.NORMAL
        }
    }

    // --- バリデーション ---

    fun isValidHeightAndWeight(height: String, weight: String): Boolean {
        val hValid = AppThresholds.isWithinFormat(height, AppThresholds.DIGITS_HEIGHT_INT, AppThresholds.DIGITS_HEIGHT_DEC)
        val wValid = AppThresholds.isWithinFormat(weight, AppThresholds.DIGITS_WEIGHT_INT, AppThresholds.DIGITS_WEIGHT_DEC)
        return weight.isNotBlank() && hValid && wValid
    }

    fun isValidBpAndPulse(systolic: String, diastolic: String, sat: String, pulse: String, temp: String): Boolean {
        val sValid = AppThresholds.isWithinFormat(systolic, AppThresholds.DIGITS_BP_INT)
        val dValid = AppThresholds.isWithinFormat(diastolic, AppThresholds.DIGITS_BP_INT)
        val satValid = AppThresholds.isWithinFormat(sat, AppThresholds.DIGITS_SAT_INT)
        val pValid = AppThresholds.isWithinFormat(pulse, AppThresholds.DIGITS_PULSE_INT)
        val tValid = AppThresholds.isWithinFormat(temp, AppThresholds.DIGITS_TEMP_INT, AppThresholds.DIGITS_TEMP_DEC)

        val anyInput = systolic.isNotBlank() || diastolic.isNotBlank() || sat.isNotBlank() || pulse.isNotBlank() || temp.isNotBlank()
        return anyInput && sValid && dValid && satValid && pValid && tValid
    }

    fun isValidGlucoseAndHbA1c(glucose: String, hba1c: String): Boolean {
        val gValid = AppThresholds.isWithinFormat(glucose, AppThresholds.DIGITS_GLUCOSE_INT)
        val hValid = AppThresholds.isWithinFormat(hba1c, AppThresholds.DIGITS_HBA1C_INT, AppThresholds.DIGITS_HBA1C_DEC)

        val anyInput = glucose.isNotBlank() || hba1c.isNotBlank()
        return anyInput && gValid && hValid
    }
}
