package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState

/**
 * 健康記録画面用の UI 状態
 */
data class PersonHealthUiState(
    val personId: Int? = null,
    override val currentCategory: Category = Category.HEIGHT_AND_WEIGHT,
    val records: List<HistoryRecord> = emptyList(),
    val preferredShowHistory: Boolean = true, // 追加: 履歴/グラフの選択状態
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 健康記録画面固有のイベント
 */
sealed interface PersonHealthViewEvent {
    // 必要に応じて定義
}

/**
 * 健康記録のバリデーション結果（事実）
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

    /**
     * レコードが新規登録（ID=0）かどうかを判定します。
     */
    fun isNew(record: Any?): Boolean {
        return (record as? HistoryRecord)?.id == 0
    }

    /**
     * 入力内容の妥当性を判定し、詳細な「事実」を返します。
     */
    fun validate(record: HistoryRecord?): HealthValidationResult {
        if (record == null) return HealthValidationResult.INVALID_VALUE
        
        // 1. 日時の確認
        @Suppress("SENSELESS_COMPARISON")
        if (record.recordTime == null) return HealthValidationResult.INVALID_TIME

        // 2. 数値の範囲確認
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
     * 保存しようとしているレコードが、自分自身（既存レコード）以外と重複しているか判定します。
     */
    fun validateDuplicate(current: HistoryRecord, existing: HistoryRecord?): HealthValidationResult {
        if (existing == null) return HealthValidationResult.SUCCESS

        val isDuplicate = if (current.id == 0) {
            // 新規なら、同じ時間のデータが存在する時点で重複
            true
        } else {
            // 更新なら、取得されたデータのIDが自分と異なれば重複
            current.id != existing.id
        }

        return if (isDuplicate) HealthValidationResult.DUPLICATE_TIME else HealthValidationResult.SUCCESS
    }
}
