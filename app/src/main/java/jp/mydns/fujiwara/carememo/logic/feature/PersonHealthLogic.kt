package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState

/**
 * 健康記録画面用の UI 状態
 */
data class PersonHealthUiState(
    val personId: Int? = null,
    val records: List<HistoryRecord> = emptyList(),
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

        // 2. 数値の範囲確認（AppThresholds を利用）
        val isValid = when (record) {
            is HeightAndWeight -> {
                (record.height == null || record.height in AppThresholds.MIN_HEIGHT..AppThresholds.MAX_HEIGHT) &&
                (record.weight == null || record.weight in AppThresholds.MIN_WEIGHT..AppThresholds.MAX_WEIGHT)
            }
            is BpAndPulse -> {
                (record.bpSystolic == null || record.bpSystolic.toDouble() in AppThresholds.MIN_BP..AppThresholds.MAX_BP) &&
                (record.bpDiastolic == null || record.bpDiastolic.toDouble() in AppThresholds.MIN_BP..AppThresholds.MAX_BP) &&
                (record.pulse == null || record.pulse.toDouble() in AppThresholds.MIN_PULSE..AppThresholds.MAX_PULSE) &&
                (record.sat == null || record.sat.toDouble() in AppThresholds.MIN_SAT..AppThresholds.MAX_SAT) &&
                (record.bodyTemperature == null || record.bodyTemperature in AppThresholds.MIN_TEMP..AppThresholds.MAX_TEMP)
            }
            is GlucoseAndHbA1c -> {
                (record.glucose == null || record.glucose.toDouble() in AppThresholds.MIN_GLUCOSE..AppThresholds.MAX_GLUCOSE) &&
                (record.hba1c == null || record.hba1c in AppThresholds.MIN_HBA1C..AppThresholds.MAX_HBA1C)
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
