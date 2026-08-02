package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import kotlin.math.pow

/**
 * 健康状態の警告レベル定義。
 * 深刻度（severity）が高いほど、UI上で強調表示されます。
 */
enum class HealthAlertLevel(val severity: Int) {
    /** 正常値：強調なし */
    NORMAL(0),
    /** 注意が必要な値：黄色などで強調 */
    WARNING(1),
    /** 異常値・要対応：赤色などで強調 */
    ALERT(2),
    /** 参考情報（低体重や低血糖など）：青色などで表示 */
    INFO(-1),
    /** 表示なし */
    NONE(-2);

    companion object {
        /**
         * 複数の警告レベルの中から最も深刻なもの（severity が最大のもの）を選択します。
         * グラフやリストのサマリー表示に使用します。
         */
        fun worst(levels: List<HealthAlertLevel>): HealthAlertLevel {
            if (levels.isEmpty()) return NORMAL
            return levels.maxByOrNull { it.severity } ?: NORMAL
        }
    }
}

/** 各項目の詳細ステータス定義（ドメイン知識に基づく判定結果） */
enum class BmiStatus { UNDERWEIGHT, NORMAL, OBESITY_1, OBESITY_2, OBESITY_3, OBESITY_4 }
enum class VitalStatus { NORMAL, HIGH_BP, LOW_BP, TACHYCARDIA, BRADYCARDIA, LOW_SAT, FEVER, HYPOTHERMIA }
enum class GlucoseStatus { LOW, NORMAL, NORMAL_HIGH, PREDIABETES, DIABETES }
enum class HbA1cStatus { NORMAL, WARNING, DIABETES }

/**
 * 健康入力のバリデーション結果
 */
enum class HealthInputValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 必須項目が空 */
    EMPTY,
    /** 桁数やドットの配置が不正 */
    INVALID_FORMAT,
    /** 設定された最小値・最大値の範囲外 */
    OUT_OF_RANGE
}

/**
 * Logic：HealthLogic
 *
 * 【役割】
 * 健康記録に関する純粋な計算処理、異常値判定、および入力値のバリデーションロジックを集約します。
 * AppSpecifications で定義された医学的・システム的な閾値を参照し、Enum による判定結果を返します。
 *
 * 【主な機能】
 * ・BMIの計算と肥満度判定。
 * ・バイタル（血圧、SAT、脈拍、体温）の各指標に対する異常値判定。
 * ・血糖値および HbA1c の糖尿病関連指標の判定。
 * ・入力値に対する桁数制限および数値範囲チェック。
 * ・表示用数値のフォーマット処理（単位付与や丸め）。
 *
 * 【設計指針】
 * 1. 判定ロジックは Context に依存せず、純粋な数値計算として実装する。
 * 2. 閾値は直接書かず、必ず AppSpecifications を参照する。
 * 3. バリデーションは「形式チェック」と「範囲チェック」を明確に分離して判定する。
 */
object HealthLogic {

    /**
     * BMI（体格指数）を計算します。
     * 計算式: 体重(kg) / (身長(m)^2)
     *
     * @param heightCm 身長(cm)
     * @param weightKg 体重(kg)
     * @return 計算されたBMI値。無効な入力の場合は 0.0。
     */
    fun calculateBMI(heightCm: Double?, weightKg: Double?): Double {
        if (heightCm == null || weightKg == null || heightCm <= 0.0) return 0.0
        return weightKg / (heightCm / 100.0).pow(2.0)
    }

    /**
     * BMI値に基づきステータスとアラートレベルを判定します。
     * 日本肥満学会の判定基準に基づき、AppSpecifications の閾値を使用します。
     *
     * @param bmi 計算済みのBMI値
     * @return ステータスと警告レベルのペア
     */
    fun evaluateBMI(bmi: Double): Pair<BmiStatus?, HealthAlertLevel> {
        val specs = AppSpecifications.Health.BodyMassIndex
        return when {
            bmi <= 0.0 -> null to HealthAlertLevel.NORMAL
            // (1)低体重　　：THRESHOLD_UNDERWEIGHT 未満：INFO
            // (2)普通体重　：THRESHOLD_NORMAL_UPPER 未満：NORMAL
            // (3)肥満(1度)：THRESHOLD_OBESITY_1 未満：INFO
            // (4)肥満(2度)：THRESHOLD_OBESITY_2 未満：WARNING
            // (5)肥満(3度)：THRESHOLD_OBESITY_3 未満：ALERT
            // (6)肥満(4度)：THRESHOLD_OBESITY_3 以上：ALERT
            bmi < specs.THRESHOLD_UNDERWEIGHT -> BmiStatus.UNDERWEIGHT to HealthAlertLevel.INFO
            bmi < specs.THRESHOLD_NORMAL_UPPER -> BmiStatus.NORMAL to HealthAlertLevel.NORMAL
            bmi < specs.THRESHOLD_OBESITY_1 -> BmiStatus.OBESITY_1 to HealthAlertLevel.INFO
            bmi < specs.THRESHOLD_OBESITY_2 -> BmiStatus.OBESITY_2 to HealthAlertLevel.WARNING
            bmi < specs.THRESHOLD_OBESITY_3 -> BmiStatus.OBESITY_3 to HealthAlertLevel.ALERT
            else -> BmiStatus.OBESITY_4 to HealthAlertLevel.ALERT
        }
    }

    /**
     * バイタル各項目を個別に判定し、異常がある項目のリストを返します。
     * すべて正常な場合は VitalStatus.NORMAL を含むリストを返します。
     *
     * @param systolic 血圧(上)
     * @param diastolic 血圧(下)
     * @param sat 酸素飽和度(%)
     * @param pulse 脈拍(回/分)
     * @param temp 体温(℃)
     * @return 判定されたステータスと警告レベルのペアリスト
     */
    fun evaluateVitalItems(
        systolic: Int?,
        diastolic: Int?,
        sat: Int?,
        pulse: Int?,
        temp: Double?
    ): List<Pair<VitalStatus, HealthAlertLevel>> {
        val results = mutableListOf<Pair<VitalStatus, HealthAlertLevel>>()

        // 血圧(上)
        systolic?.let {
            // (1)高血圧(上)：THRESHOLD_HIGH_SYSTOLIC 以上：ALERT
            // (2)低血圧(上)：THRESHOLD_LOW_SYSTOLIC 未満：WARNING
            val spec = AppSpecifications.Health.BloodPressure
            if (it >= spec.THRESHOLD_HIGH_SYSTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW_SYSTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        // 血圧(下)
        diastolic?.let {
            // (1)高血圧(下)：THRESHOLD_HIGH_DIASTOLIC 以上：ALERT
            // (2)低血圧(下)：THRESHOLD_LOW_DIASTOLIC 未満：WARNING
            val spec = AppSpecifications.Health.BloodPressure
            if (it >= spec.THRESHOLD_HIGH_DIASTOLIC) results.add(VitalStatus.HIGH_BP to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW_DIASTOLIC) results.add(VitalStatus.LOW_BP to HealthAlertLevel.WARNING)
        }
        // 酸素飽和度(SAT)
        sat?.let {
            // (1)呼吸不全：THRESHOLD_LOW [%]以下：ALERT
            val spec = AppSpecifications.Health.OxygenSaturation
            if (it <= spec.THRESHOLD_LOW) results.add(VitalStatus.LOW_SAT to HealthAlertLevel.ALERT)
        }
        // 脈拍
        pulse?.let {
            // (1)頻脈：THRESHOLD_HIGH 以上：ALERT
            // (2)徐脈：THRESHOLD_LOW 以下：WARNING
            val spec = AppSpecifications.Health.Pulse
            if (it >= spec.THRESHOLD_HIGH) results.add(VitalStatus.TACHYCARDIA to HealthAlertLevel.ALERT)
            else if (it <= spec.THRESHOLD_LOW) results.add(VitalStatus.BRADYCARDIA to HealthAlertLevel.WARNING)
        }
        // 体温
        temp?.let {
            // (1)発熱：THRESHOLD_HIGH 以上：ALERT
            // (2)低体温：THRESHOLD_LOW 未満：WARNING
            val spec = AppSpecifications.Health.BodyTemperature
            if (it >= spec.THRESHOLD_HIGH) results.add(VitalStatus.FEVER to HealthAlertLevel.ALERT)
            else if (it < spec.THRESHOLD_LOW) results.add(VitalStatus.HYPOTHERMIA to HealthAlertLevel.WARNING)
        }

        if (results.isEmpty()) return listOf(VitalStatus.NORMAL to HealthAlertLevel.NORMAL)
        return results.distinctBy { it.first }
    }

    /**
     * 血糖値を判定します。
     *
     * @param glucose 血糖値(mg/dL)
     * @return ステータスと警告レベルのペア
     */
    fun evaluateGlucose(glucose: Int?): Pair<GlucoseStatus?, HealthAlertLevel> {
        val g = glucose ?: return null to HealthAlertLevel.NORMAL
        val spec = AppSpecifications.Health.BloodGlucose
        return when {
            // (1)糖尿病型：THRESHOLD_HIGH 以上：ALERT
            // (2)予備群：THRESHOLD_PREDIABETES 以上：WARNING
            // (3)正常高値：THRESHOLD_NORMAL_UPPER 以上：INFO
            // (4)正常型：THRESHOLD_LOW 以上：NORMAL
            // (5)低血糖：THRESHOLD_LOW 未満：INFO
            g >= spec.THRESHOLD_HIGH -> GlucoseStatus.DIABETES to HealthAlertLevel.ALERT
            g >= spec.THRESHOLD_PREDIABETES -> GlucoseStatus.PREDIABETES to HealthAlertLevel.WARNING
            g >= spec.THRESHOLD_NORMAL_UPPER -> GlucoseStatus.NORMAL_HIGH to HealthAlertLevel.INFO
            g >= spec.THRESHOLD_LOW -> GlucoseStatus.NORMAL to HealthAlertLevel.NORMAL
            else -> GlucoseStatus.LOW to HealthAlertLevel.INFO
        }
    }

    /**
     * HbA1cを判定します。
     *
     * @param hba1c HbA1c(%)
     * @return ステータスと警告レベルのペア
     */
    fun evaluateHbA1c(hba1c: Double?): Pair<HbA1cStatus?, HealthAlertLevel> {
        val h = hba1c ?: return null to HealthAlertLevel.NORMAL
        val spec = AppSpecifications.Health.HbA1c
        return when {
            // (1)糖尿病型：THRESHOLD_DIABETES 以上：ALERT
            // (2)予備群　：THRESHOLD_NORMAL_UPPER 超：WARNING
            // (3)正常　　：THRESHOLD_NORMAL_UPPER 以下：NORMAL
            h >= spec.THRESHOLD_DIABETES -> HbA1cStatus.DIABETES to HealthAlertLevel.ALERT
            h > spec.THRESHOLD_NORMAL_UPPER -> HbA1cStatus.WARNING to HealthAlertLevel.WARNING
            else -> HbA1cStatus.NORMAL to HealthAlertLevel.NORMAL
        }
    }

    // --- バリデーション関連 ---

    /**
     * 指定された数値文字列のバリデーションを行います。
     *
     * @param value 入力文字列
     * @param intDigits 許容される整数桁数
     * @param decDigits 許容される小数桁数
     * @param min 最小値
     * @param max 最大値
     * @return バリデーション結果
     */
    private fun validateValue(
        value: String,
        intDigits: Int,
        decDigits: Int,
        min: Double,
        max: Double
    ): HealthInputValidationResult {
        if (value.isBlank()) return HealthInputValidationResult.EMPTY
        
        // 数値として変換可能かチェック
        val num = value.toDoubleOrNull() ?: return HealthInputValidationResult.INVALID_FORMAT
        
        // 桁数およびドット形式のチェック
        if (!isWithinFormat(value, intDigits, decDigits)) {
            return HealthInputValidationResult.INVALID_FORMAT
        }

        // 範囲チェック
        return if (num in min..max) {
            HealthInputValidationResult.SUCCESS
        } else {
            HealthInputValidationResult.OUT_OF_RANGE
        }
    }

    /**
     * 文字列が指定された整数桁・小数桁の形式に合致し、かつ指定された範囲内にあるか判定します。
     *
     * @param value 入力文字列
     * @param intDigits 許容される整数桁数
     * @param decDigits 許容される小数桁数
     * @param min 最小値(任意)
     * @param max 最大値(任意)
     * @return 妥当な場合は true
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

        // 整数部の桁数チェック
        val intPart = parts[0]
        if (intPart.length > intDigits) return false

        // 小数部の桁数チェック
        if (parts.size == 2) {
            val decPart = parts[1]
            if (decPart.length > decDigits) return false
        }

        // 数値変換と正数チェック
        val num = value.toDoubleOrNull() ?: return false
        if (num < 0) return false

        // 最小・最大値チェック
        if (min != null && num < min) return false
        if (max != null && num > max) return false

        return true
    }

    /**
     * 身長・体重の入力バリデーションを一括で行います。
     * 体重は必須項目として扱います。
     */
    fun validateHeightAndWeight(height: String, weight: String): HealthInputValidationResult {
        if (height.isBlank() && weight.isBlank()) return HealthInputValidationResult.EMPTY
        
        val hSpec = AppSpecifications.Health.Height
        val wSpec = AppSpecifications.Health.Weight
        val hRes =  if (height.isNotBlank())
                        validateValue(height, hSpec.DIGITS_INT, hSpec.DIGITS_DEC, hSpec.MIN_VALUE, hSpec.MAX_VALUE)
                    else
                        HealthInputValidationResult.SUCCESS
        val wRes = validateValue(weight, wSpec.DIGITS_INT, wSpec.DIGITS_DEC, wSpec.MIN_VALUE, wSpec.MAX_VALUE)

        return when {
            hRes == HealthInputValidationResult.INVALID_FORMAT || wRes == HealthInputValidationResult.INVALID_FORMAT -> HealthInputValidationResult.INVALID_FORMAT
            hRes == HealthInputValidationResult.OUT_OF_RANGE || wRes == HealthInputValidationResult.OUT_OF_RANGE -> HealthInputValidationResult.OUT_OF_RANGE
            wRes == HealthInputValidationResult.EMPTY -> HealthInputValidationResult.EMPTY // 体重は必須
            else -> HealthInputValidationResult.SUCCESS
        }
    }

    /**
     * バイタル（血圧、SAT、脈拍、体温）の入力バリデーションを一括で行います。
     */
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

    /**
     * 血糖値・HbA1cの入力バリデーションを一括で行います。
     */
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
