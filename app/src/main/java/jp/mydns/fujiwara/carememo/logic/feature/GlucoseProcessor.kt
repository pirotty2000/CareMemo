package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import java.time.Instant

/**
 * 血糖値・HbA1cカテゴリの処理を担当するプロセッサ。
 */
object GlucoseProcessor : HealthCategoryProcessor {
    override val category: BatchInputCategory = BatchInputCategory.GLUCOSE
    override val generalCategory: Category = Category.GLUCOSE_AND_HBA1C
    override val categoryNameResId: Int = R.string.common_category_glucose
    override val outOfRangeErrorResId: Int = R.string.common_error_out_of_range_glucose

    override fun isEmpty(state: BatchInputUiState): Boolean {
        return state.glucose.isBlank() && state.hba1c.isBlank()
    }

    override fun validate(state: BatchInputUiState): HealthInputValidationResult {
        return HealthLogic.validateGlucoseAndHbA1c(state.glucose, state.hba1c)
    }

    override fun createEntity(personId: String, time: Instant, state: BatchInputUiState): Any? {
        if (isEmpty(state)) return null
        return GlucoseAndHbA1c(
            personId = personId,
            glucose = state.glucose.toIntOrNull(),
            hba1c = state.hba1c.toDoubleOrNull(),
            recordTime = time
        )
    }

    override fun validateFromMap(values: Map<String, String>): HealthInputValidationResult {
        return HealthLogic.validateGlucoseAndHbA1c(
            values["glucose"] ?: "",
            values["hba1c"] ?: ""
        )
    }

    override fun createEntityFromValues(
        personId: String,
        id: String,
        time: Instant,
        values: Map<String, Any?>
    ): Any {
        return GlucoseAndHbA1c(
            id = id,
            personId = personId,
            glucose = values.getInt("glucose"),
            hba1c = values.getDouble("hba1c"),
            recordTime = time
        )
    }

    override fun validateEntity(entity: Any): Boolean {
        val record = entity as? GlucoseAndHbA1c ?: return true
        val gSpec = AppSpecifications.Health.BloodGlucose
        val hSpec = AppSpecifications.Health.HbA1c
        return (record.glucose == null || record.glucose.toDouble() in gSpec.MIN_VALUE..gSpec.MAX_VALUE) &&
                (record.hba1c == null || record.hba1c in hSpec.MIN_VALUE..hSpec.MAX_VALUE)
    }
}
