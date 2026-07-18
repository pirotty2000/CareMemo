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
 * 各項目の詳細ステータス定義（事実）
 */
enum class BmiStatus { UNDERWEIGHT, NORMAL, OBESITY_1, OBESITY_2, OBESITY_3, OBESITY_4 }
enum class VitalStatus { NORMAL, HIGH_BP, LOW_BP, TACHYCARDIA, BRADYCARDIA, LOW_SAT, FEVER, HYPOTHERMIA }
enum class GlucoseStatus { LOW, NORMAL, WARNING, HIGH }
enum class HbA1cStatus { NORMAL, WARNING, DIABETES }

/**
 * 健康入力のバリデーション結果（事実）
 */
enum class HealthInputValidationResult {
    SUCCESS,
    EMPTY,
    INVALID_FORMAT,
    OUT_OF_RANGE
}

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

    private fun validateValue(
        value: String,
        intDigits: Int,
        decDigits: Int,
        min: Double,
        max: Double
    ): HealthInputValidationResult {
        if (value.isBlank()) return HealthInputValidationResult.EMPTY
        
        // 形式チェック (AppThresholds.isWithinFormat は Boolean なので、より詳細に判定)
        val num = value.toDoubleOrNull() ?: return HealthInputValidationResult.INVALID_FORMAT
        
        if (!AppThresholds.isWithinFormat(value, intDigits, decDigits)) {
            return HealthInputValidationResult.INVALID_FORMAT
        }

        return if (num in min..max) {
            HealthInputValidationResult.SUCCESS
        } else {
            HealthInputValidationResult.OUT_OF_RANGE
        }
    }

    fun validateHeightAndWeight(height: String, weight: String): HealthInputValidationResult {
        if (height.isBlank() && weight.isBlank()) return HealthInputValidationResult.EMPTY
        
        val hRes = if (height.isNotBlank()) validateValue(height, AppThresholds.DIGITS_HEIGHT_INT, AppThresholds.DIGITS_HEIGHT_DEC, AppThresholds.MIN_HEIGHT, AppThresholds.MAX_HEIGHT) else HealthInputValidationResult.SUCCESS
        val wRes = validateValue(weight, AppThresholds.DIGITS_WEIGHT_INT, AppThresholds.DIGITS_WEIGHT_DEC, AppThresholds.MIN_WEIGHT, AppThresholds.MAX_WEIGHT)

        return when {
            hRes == HealthInputValidationResult.INVALID_FORMAT || wRes == HealthInputValidationResult.INVALID_FORMAT -> HealthInputValidationResult.INVALID_FORMAT
            hRes == HealthInputValidationResult.OUT_OF_RANGE || wRes == HealthInputValidationResult.OUT_OF_RANGE -> HealthInputValidationResult.OUT_OF_RANGE
            wRes == HealthInputValidationResult.EMPTY -> HealthInputValidationResult.EMPTY // 体重は必須
            else -> HealthInputValidationResult.SUCCESS
        }
    }

    fun validateBpAndPulse(systolic: String, diastolic: String, sat: String, pulse: String, temp: String): HealthInputValidationResult {
        val inputs = listOf(
            Triple(systolic, AppThresholds.DIGITS_BP_INT, 0 to (AppThresholds.MIN_BP to AppThresholds.MAX_BP)),
            Triple(diastolic, AppThresholds.DIGITS_BP_INT, 0 to (AppThresholds.MIN_BP to AppThresholds.MAX_BP)),
            Triple(sat, AppThresholds.DIGITS_SAT_INT, 0 to (AppThresholds.MIN_SAT to AppThresholds.MAX_SAT)),
            Triple(pulse, AppThresholds.DIGITS_PULSE_INT, 0 to (AppThresholds.MIN_PULSE to AppThresholds.MAX_PULSE)),
            Triple(temp, AppThresholds.DIGITS_TEMP_INT, AppThresholds.DIGITS_TEMP_DEC to (AppThresholds.MIN_TEMP to AppThresholds.MAX_TEMP))
        )

        val activeInputs = inputs.filter { it.first.isNotBlank() }
        if (activeInputs.isEmpty()) return HealthInputValidationResult.EMPTY

        val results = activeInputs.map { (v, i, d) -> validateValue(v, i, d.first, d.second.first, d.second.second) }

        return when {
            results.any { it == HealthInputValidationResult.INVALID_FORMAT } -> HealthInputValidationResult.INVALID_FORMAT
            results.any { it == HealthInputValidationResult.OUT_OF_RANGE } -> HealthInputValidationResult.OUT_OF_RANGE
            else -> HealthInputValidationResult.SUCCESS
        }
    }

    fun validateGlucoseAndHbA1c(glucose: String, hba1c: String): HealthInputValidationResult {
        if (glucose.isBlank() && hba1c.isBlank()) return HealthInputValidationResult.EMPTY

        val gRes = if (glucose.isNotBlank()) validateValue(glucose, AppThresholds.DIGITS_GLUCOSE_INT, 0, AppThresholds.MIN_GLUCOSE, AppThresholds.MAX_GLUCOSE) else HealthInputValidationResult.SUCCESS
        val hRes = if (hba1c.isNotBlank()) validateValue(hba1c, AppThresholds.DIGITS_HBA1C_INT, AppThresholds.DIGITS_HBA1C_DEC, AppThresholds.MIN_HBA1C, AppThresholds.MAX_HBA1C) else HealthInputValidationResult.SUCCESS

        return when {
            gRes == HealthInputValidationResult.INVALID_FORMAT || hRes == HealthInputValidationResult.INVALID_FORMAT -> HealthInputValidationResult.INVALID_FORMAT
            gRes == HealthInputValidationResult.OUT_OF_RANGE || hRes == HealthInputValidationResult.OUT_OF_RANGE -> HealthInputValidationResult.OUT_OF_RANGE
            else -> HealthInputValidationResult.SUCCESS
        }
    }

    // 互換性維持のための古いメソッド (将来的に削除)
    fun isValidHeightAndWeight(height: String, weight: String) = validateHeightAndWeight(height, weight) == HealthInputValidationResult.SUCCESS
    fun isValidBpAndPulse(systolic: String, diastolic: String, sat: String, pulse: String, temp: String) = validateBpAndPulse(systolic, diastolic, sat, pulse, temp) == HealthInputValidationResult.SUCCESS
    fun isValidGlucoseAndHbA1c(glucose: String, hba1c: String) = validateGlucoseAndHbA1c(glucose, hba1c) == HealthInputValidationResult.SUCCESS
}
