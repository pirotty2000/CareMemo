package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import java.time.Instant

/**
 * 身長・体重カテゴリの処理を担当するプロセッサ。
 *
 * 【注意】現状 `HealthRepository` に依存しており、アーキテクチャ境界のリファクタリング対象です。
 */
object HeightWeightProcessor : HealthCategoryProcessor {
    override val category: BatchInputCategory = BatchInputCategory.HEIGHT_WEIGHT
    override val generalCategory: Category = Category.HEIGHT_AND_WEIGHT
    override val categoryNameResId: Int = R.string.common_category_height_weight
    override val outOfRangeErrorResId: Int = R.string.common_error_out_of_range_height_weight

    override fun isEmpty(state: BatchInputUiState): Boolean {
        return state.height.isBlank() && state.weight.isBlank()
    }

    override fun validate(state: BatchInputUiState): HealthInputValidationResult {
        return HealthLogic.validateHeightAndWeight(state.height, state.weight)
    }

    override fun createEntity(personId: String, time: Instant, state: BatchInputUiState): Any? {
        if (isEmpty(state)) return null
        return HeightAndWeight(
            personId = personId,
            height = state.height.toDoubleOrNull(),
            weight = state.weight.toDoubleOrNull(),
            recordTime = time
        )
    }

    override fun validateFromMap(values: Map<String, String>): HealthInputValidationResult {
        return HealthLogic.validateHeightAndWeight(
            values["height"] ?: "",
            values["weight"] ?: ""
        )
    }

    override fun createEntityFromValues(
        personId: String,
        id: String,
        time: Instant,
        values: Map<String, Any?>
    ): Any {
        return HeightAndWeight(
            id = id,
            personId = personId,
            height = values.getDouble("height"),
            weight = values.getDouble("weight"),
            recordTime = time
        )
    }

    override fun validateEntity(entity: Any): Boolean {
        val record = entity as? HeightAndWeight ?: return true
        val hSpec = AppSpecifications.Health.Height
        val wSpec = AppSpecifications.Health.Weight
        return (record.height == null || record.height in hSpec.MIN_VALUE..hSpec.MAX_VALUE) &&
                (record.weight == null || record.weight in wSpec.MIN_VALUE..wSpec.MAX_VALUE)
    }

    override suspend fun save(
        repository: HealthRepository,
        record: Any,
        featureName: String,
        operation: String,
        isUpdate: Boolean
    ): String {
        return repository.insertHistoryRecord(record as HeightAndWeight, featureName, operation, isUpdate)
    }

    override suspend fun delete(
        repository: HealthRepository,
        record: Any,
        featureName: String,
        operation: String
    ) {
        repository.deleteHistoryRecord(record as HeightAndWeight, featureName, operation)
    }

    override suspend fun findExisting(repository: HealthRepository, personId: String, time: Instant): Any? {
        return repository.findHeightAndWeightAtTime(personId, time)
    }
}
