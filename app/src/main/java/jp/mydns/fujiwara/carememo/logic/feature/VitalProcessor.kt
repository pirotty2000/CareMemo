package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
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

    override fun isEmpty(state: BatchInputUiState): Boolean {
        return state.bpSystolic.isBlank() && state.bpDiastolic.isBlank() &&
                state.sat.isBlank() && state.pulse.isBlank() && state.bodyTemperature.isBlank()
    }

    override fun validate(state: BatchInputUiState): HealthInputValidationResult {
        return HealthLogic.validateBpAndPulse(
            state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature
        )
    }

    override fun createEntity(personId: String, time: Instant, state: BatchInputUiState): Any? {
        if (isEmpty(state)) return null
        return BpAndPulse(
            personId = personId,
            bpSystolic = state.bpSystolic.toIntOrNull(),
            bpDiastolic = state.bpDiastolic.toIntOrNull(),
            sat = state.sat.toIntOrNull(),
            pulse = state.pulse.toIntOrNull(),
            bodyTemperature = state.bodyTemperature.toDoubleOrNull(),
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
            bpSystolic = values["bpSystolic"] as? Int,
            bpDiastolic = values["bpDiastolic"] as? Int,
            sat = values["sat"] as? Int,
            pulse = values["pulse"] as? Int,
            bodyTemperature = values["bodyTemperature"] as? Double,
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

    override suspend fun save(
        repository: HealthRepository,
        record: Any,
        featureName: String,
        operation: String,
        isUpdate: Boolean
    ): String {
        return repository.insertHistoryRecord(record as BpAndPulse, featureName, operation, isUpdate)
    }

    override suspend fun delete(
        repository: HealthRepository,
        record: Any,
        featureName: String,
        operation: String
    ) {
        repository.deleteHistoryRecord(record as BpAndPulse, featureName, operation)
    }

    override suspend fun findExisting(repository: HealthRepository, personId: String, time: Instant): Any? {
        return repository.findBpAndPulseAtTime(personId, time)
    }
}
