package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import java.time.Instant
import kotlin.math.pow

/**
 * 健康状態の警告レベル定義
 */
enum class HealthAlertLevel(val severity: Int) {
    NORMAL(0),
    WARNING(1),
    ALERT(2),
    INFO(-1),
    NONE(-2);

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
 * AppSpecifications の定数を参照し、判定結果として Enum を返します。
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
        val specs = AppSpecifications.Health.BodyMassIndex
        return when {
            bmi <= 0.0 -> null to HealthAlertLevel.NORMAL
            bmi < specs.THRESHOLD_UNDERWEIGHT -> BmiStatus.UNDERWEIGHT to HealthAlertLevel.INFO
            bmi < specs.THRESHOLD_NORMAL_UPPER -> BmiStatus.NORMAL to HealthAlertLevel.NORMAL
            bmi < specs.THRESHOLD_OBESITY_1 -> BmiStatus.OBESITY_1 to HealthAlertLevel.WARNING
            bmi < specs.THRESHOLD_OBESITY_2 -> BmiStatus.OBESITY_2 to HealthAlertLevel.WARNING
            bmi < specs.THRESHOLD_OBESITY_3 -> BmiStatus.OBESITY_3 to HealthAlertLevel.ALERT
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
            val spec = AppSpecifications.Health.BloodPressure
            if (it >= spec.THRESHOLD_HIGH_SYSTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW_SYSTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        diastolic?.let {
            val spec = AppSpecifications.Health.BloodPressure
            if (it >= spec.THRESHOLD_HIGH_DIASTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW_DIASTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        sat?.let {
            val spec = AppSpecifications.Health.OxygenSaturation
            if (it <= spec.THRESHOLD_LOW) results.add(VitalStatus.LOW_SAT to HealthAlertLevel.ALERT)
        }
        pulse?.let {
            val spec = AppSpecifications.Health.Pulse
            if (it >= spec.THRESHOLD_HIGH) results.add(VitalStatus.TACHYCARDIA to HealthAlertLevel.ALERT)
            else if (it <= spec.THRESHOLD_LOW) results.add(VitalStatus.BRADYCARDIA to HealthAlertLevel.WARNING)
        }
        temp?.let {
            val spec = AppSpecifications.Health.BodyTemperature
            if (it >= spec.THRESHOLD_HIGH) results.add(VitalStatus.FEVER to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW) results.add(VitalStatus.HYPOTHERMIA to HealthAlertLevel.WARNING)
        }

        if (results.isEmpty()) return listOf(VitalStatus.NORMAL to HealthAlertLevel.NORMAL)
        return results.distinctBy { it.first }
    }

    /**
     * 血糖値を判定します。
     */
    fun evaluateGlucose(glucose: Int?): Pair<GlucoseStatus?, HealthAlertLevel> {
        val g = glucose ?: return null to HealthAlertLevel.NORMAL
        val spec = AppSpecifications.Health.BloodGlucose
        return when {
            g > spec.THRESHOLD_HIGH -> GlucoseStatus.HIGH to HealthAlertLevel.ALERT
            g >= spec.THRESHOLD_NORMAL_UPPER -> GlucoseStatus.WARNING to HealthAlertLevel.WARNING
            g >= spec.THRESHOLD_LOW -> GlucoseStatus.NORMAL to HealthAlertLevel.NORMAL
            else -> GlucoseStatus.LOW to HealthAlertLevel.ALERT
        }
    }

    /**
     * HbA1cを判定します。
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<HbA1cStatus?, HealthAlertLevel> {
        val h = hba1c ?: return null to HealthAlertLevel.NORMAL
        val spec = AppSpecifications.Health.HbA1c
        return when {
            h >= spec.THRESHOLD_DIABETES -> HbA1cStatus.DIABETES to HealthAlertLevel.ALERT
            h > spec.THRESHOLD_NORMAL_UPPER -> HbA1cStatus.WARNING to HealthAlertLevel.WARNING
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
        
        // 形式チェック
        val num = value.toDoubleOrNull() ?: return HealthInputValidationResult.INVALID_FORMAT
        
        if (!isWithinFormat(value, intDigits, decDigits)) {
            return HealthInputValidationResult.INVALID_FORMAT
        }

        return if (num in min..max) {
            HealthInputValidationResult.SUCCESS
        } else {
            HealthInputValidationResult.OUT_OF_RANGE
        }
    }

    /**
     * 文字列が指定された整数桁・小数桁の形式に合致し、かつ指定された範囲内にあるか判定する。
     * (AppSpecifications を参照するように移行)
     */
    fun isWithinFormat(
        value: String,
        intDigits: Int,
        decDigits: Int = 0,
        min: Double? = null,
        max: Double? = null
    ): Boolean {
        if (value.isBlank()) return true
        val parts = value.split(".")
        if (parts.size > 2) return false

        val intPart = parts[0]
        if (intPart.length > intDigits) return false

        if (parts.size == 2) {
            val decPart = parts[1]
            if (decPart.length > decDigits) return false
        }

        val num = value.toDoubleOrNull() ?: return false
        if (num < 0) return false

        if (min != null && num < min) return false
        if (max != null && num > max) return false

        return true
    }

    fun validateHeightAndWeight(height: String, weight: String): HealthInputValidationResult {
        if (height.isBlank() && weight.isBlank()) return HealthInputValidationResult.EMPTY
        
        val hSpec = AppSpecifications.Health.Height
        val wSpec = AppSpecifications.Health.Weight
        val hRes = if (height.isNotBlank()) validateValue(height, hSpec.DIGITS_INT, hSpec.DIGITS_DEC, hSpec.MIN_VALUE, hSpec.MAX_VALUE) else HealthInputValidationResult.SUCCESS
        val wRes = validateValue(weight, wSpec.DIGITS_INT, wSpec.DIGITS_DEC, wSpec.MIN_VALUE, wSpec.MAX_VALUE)

        return when {
            hRes == HealthInputValidationResult.INVALID_FORMAT || wRes == HealthInputValidationResult.INVALID_FORMAT -> HealthInputValidationResult.INVALID_FORMAT
            hRes == HealthInputValidationResult.OUT_OF_RANGE || wRes == HealthInputValidationResult.OUT_OF_RANGE -> HealthInputValidationResult.OUT_OF_RANGE
            wRes == HealthInputValidationResult.EMPTY -> HealthInputValidationResult.EMPTY // 体重は必須
            else -> HealthInputValidationResult.SUCCESS
        }
    }

    fun validateBpAndPulse(systolic: String, diastolic: String, sat: String, pulse: String, temp: String): HealthInputValidationResult {
        val bpSpec = AppSpecifications.Health.BloodPressure
        val satSpec = AppSpecifications.Health.OxygenSaturation
        val pulseSpec = AppSpecifications.Health.Pulse
        val tempSpec = AppSpecifications.Health.BodyTemperature
        
        val inputs = listOf(
            Triple(systolic, bpSpec.DIGITS_INT, 0 to (bpSpec.MIN_VALUE to bpSpec.MAX_VALUE)),
            Triple(diastolic, bpSpec.DIGITS_INT, 0 to (bpSpec.MIN_VALUE to bpSpec.MAX_VALUE)),
            Triple(sat, satSpec.DIGITS_INT, 0 to (satSpec.MIN_VALUE to satSpec.MAX_VALUE)),
            Triple(pulse, pulseSpec.DIGITS_INT, 0 to (pulseSpec.MIN_VALUE to pulseSpec.MAX_VALUE)),
            Triple(temp, tempSpec.DIGITS_INT, tempSpec.DIGITS_DEC to (tempSpec.MIN_VALUE to tempSpec.MAX_VALUE))
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

        val gSpec = AppSpecifications.Health.BloodGlucose
        val hSpec = AppSpecifications.Health.HbA1c
        val gRes = if (glucose.isNotBlank()) validateValue(glucose, gSpec.DIGITS_INT, 0, gSpec.MIN_VALUE, gSpec.MAX_VALUE) else HealthInputValidationResult.SUCCESS
        val hRes = if (hba1c.isNotBlank()) validateValue(hba1c, hSpec.DIGITS_INT, hSpec.DIGITS_DEC, hSpec.MIN_VALUE, hSpec.MAX_VALUE) else HealthInputValidationResult.SUCCESS

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

    // --- 表示用フォーマッタ (AppSpecifications への移行に伴い集約) ---
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
