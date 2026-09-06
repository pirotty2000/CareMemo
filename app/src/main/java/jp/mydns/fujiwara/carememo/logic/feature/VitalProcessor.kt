package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import java.time.Instant

/**
 * バイタル（血圧、脈拍、SAT、体温）カテゴリの処理を担当するプロセッサ。
 */
object VitalProcessor : HealthCategoryProcessor {
    override val category: BatchInputCategory = BatchInputCategory.VITAL
    override val generalCategory: Category = Category.BP_AND_PULSE
    override val categoryNameResId: Int = R.string.common_category_vital
    override val outOfRangeErrorResId: Int = R.string.common_error_out_of_range_vital

    override fun isEmpty(input: BatchInputSession): Boolean {
        return input.bpSystolic.isBlank() && input.bpDiastolic.isBlank() &&
                input.sat.isBlank() && input.pulse.isBlank() && input.bodyTemperature.isBlank()
    }

    override fun validate(input: BatchInputSession): HealthInputValidationResult {
        return HealthLogic.validateBpAndPulse(
            input.bpSystolic, input.bpDiastolic, input.sat, input.pulse, input.bodyTemperature
        )
    }

    override fun createEntity(personId: String, time: Instant, input: BatchInputSession): Any? {
        if (isEmpty(input)) return null
        return BpAndPulse(
            personId = personId,
            bpSystolic = input.bpSystolic.toIntOrNull(),
            bpDiastolic = input.bpDiastolic.toIntOrNull(),
            sat = input.sat.toIntOrNull(),
            pulse = input.pulse.toIntOrNull(),
            bodyTemperature = input.bodyTemperature.toDoubleOrNull(),
            recordTime = time
        )
    }

    override fun validateFromMap(values: Map<String, String>): HealthInputValidationResult {
        return HealthLogic.validateBpAndPulse(
            values["bpSystolic"] ?: "",
            values["bpDiastolic"] ?: "",
            values["sat"] ?: "",
            values["pulse"] ?: "",
            values["bodyTemperature"] ?: ""
        )
    }

    override fun createEntityFromValues(
        personId: String,
        id: String,
        time: Instant,
        values: Map<String, Any?>
    ): Any {
        return BpAndPulse(
            id = id,
            personId = personId,
            bpSystolic = values.getInt("bpSystolic"),
            bpDiastolic = values.getInt("bpDiastolic"),
            sat = values.getInt("sat"),
            pulse = values.getInt("pulse"),
            bodyTemperature = values.getDouble("bodyTemperature"),
            recordTime = time
        )
    }

    override fun validateEntity(entity: Any): Boolean {
        val record = entity as? BpAndPulse ?: return true
        val bpSpec = AppSpecifications.Health.BloodPressure
        val pulseSpec = AppSpecifications.Health.Pulse
        val satSpec = AppSpecifications.Health.OxygenSaturation
        val tempSpec = AppSpecifications.Health.BodyTemperature
        return (record.bpSystolic == null || record.bpSystolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.bpDiastolic == null || record.bpDiastolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.pulse == null || record.pulse.toDouble() in pulseSpec.MIN_VALUE..pulseSpec.MAX_VALUE) &&
                (record.sat == null || record.sat.toDouble() in satSpec.MIN_VALUE..satSpec.MAX_VALUE) &&
                (record.bodyTemperature == null || record.bodyTemperature in tempSpec.MIN_VALUE..tempSpec.MAX_VALUE)
    }
}
