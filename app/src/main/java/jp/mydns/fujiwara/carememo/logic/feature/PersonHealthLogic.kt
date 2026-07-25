package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant
import java.util.UUID

/**
 * 健康記録画面用の UI 状態
 */
data class PersonHealthUiState(
    val personId: String? = null,
    override val currentCategory: Category = Category.HEIGHT_AND_WEIGHT,
    val records: List<HistoryRecord> = emptyList(),
    val preferredShowHistory: Boolean = true,
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 健康記録画面固有のイベント
 */
sealed interface PersonHealthViewEvent {
    // 将来的に「特定の画面への遷移」や「複雑なアニメーションの開始」などのイベントが必要になる可能性があります。
}

/**
 * 健康記録のバリデーション結果
 */
enum class HealthValidationResult {
    SUCCESS,
    INVALID_VALUE,
    INVALID_TIME,
    DUPLICATE_TIME
}

/**
 * 健康記録画面固有のドメインロジック
 */
object PersonHealthLogic {
    /** 新規作成を明示する特別なID */
    const val NEW_RECORD_ID = "__NEW__"

    /**
     * レコードが新規登録かどうかを判定します。
     * 明示的な新規IDまたは空の場合のみ新規とみなします。
     * これにより、DBに混入した "0" データを既存データとして救出可能にします。
     */
    fun isNew(id: String?): Boolean {
        return id.isNullOrEmpty() || id == NEW_RECORD_ID
    }

    /**
     * 入力内容の妥当性を判定します。
     */
    fun validate(record: HistoryRecord?): HealthValidationResult {
        if (record == null) return HealthValidationResult.INVALID_VALUE

        val isValid = when (record) {
            is HeightAndWeight -> {
                val hSpec = AppSpecifications.Health.Height
                val wSpec = AppSpecifications.Health.Weight
                (record.height == null || record.height in hSpec.MIN_VALUE..hSpec.MAX_VALUE) &&
                (record.weight == null || record.weight in wSpec.MIN_VALUE..wSpec.MAX_VALUE)
            }
            is BpAndPulse -> {
                val bpSpec = AppSpecifications.Health.BloodPressure
                val pulseSpec = AppSpecifications.Health.Pulse
                val satSpec = AppSpecifications.Health.OxygenSaturation
                val tempSpec = AppSpecifications.Health.BodyTemperature
                (record.bpSystolic == null || record.bpSystolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.bpDiastolic == null || record.bpDiastolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.pulse == null || record.pulse.toDouble() in pulseSpec.MIN_VALUE..pulseSpec.MAX_VALUE) &&
                (record.sat == null || record.sat.toDouble() in satSpec.MIN_VALUE..satSpec.MAX_VALUE) &&
                (record.bodyTemperature == null || record.bodyTemperature in tempSpec.MIN_VALUE..tempSpec.MAX_VALUE)
            }
            is GlucoseAndHbA1c -> {
                val gSpec = AppSpecifications.Health.BloodGlucose
                val hSpec = AppSpecifications.Health.HbA1c
                (record.glucose == null || record.glucose.toDouble() in gSpec.MIN_VALUE..gSpec.MAX_VALUE) &&
                (record.hba1c == null || record.hba1c in hSpec.MIN_VALUE..hSpec.MAX_VALUE)
            }
            else -> true
        }

        return if (isValid) HealthValidationResult.SUCCESS else HealthValidationResult.INVALID_VALUE
    }

    /**
     * 重複チェック（自分自身を除外）
     */
    fun validateDuplicate(current: HistoryRecord, existing: HistoryRecord?): HealthValidationResult {
        if (existing == null) return HealthValidationResult.SUCCESS
        return if (current.id != existing.id) HealthValidationResult.DUPLICATE_TIME else HealthValidationResult.SUCCESS
    }

    /**
     * Entity を構築します。
     * ID採番の責任を完全にこのメソッドが負います。
     */
    fun createEntity(
        category: Category,
        personId: String,
        recordId: String,
        recordTime: Instant,
        values: Map<String, Any?>
    ): Any {
        val finalId = if (isNew(recordId)) UUID.randomUUID().toString() else recordId
        
        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = finalId, personId = personId, height = values["height"] as? Double, weight = values["weight"] as? Double, recordTime = recordTime)
            Category.BP_AND_PULSE -> BpAndPulse(id = finalId, personId = personId, bpSystolic = values["bpSystolic"] as? Int, bpDiastolic = values["bpDiastolic"] as? Int, sat = values["sat"] as? Int, pulse = values["pulse"] as? Int, bodyTemperature = values["bodyTemperature"] as? Double, recordTime = recordTime)
            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = finalId, personId = personId, glucose = values["glucose"] as? Int, hba1c = values["hba1c"] as? Double, recordTime = recordTime)
            else -> throw IllegalArgumentException("Unsupported category")
        }
    }
}
