package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.YearMonth

/**
 * 服薬管理画面用の UI 状態
 */
data class PersonMedicationUiState(
    val personId: Int? = null,

    val selectedMonth: YearMonth = YearMonth.now(),
    val monthlyRecords: List<MedicationRecord> = emptyList(),
    val recordsByDate: Map<String, List<MedicationRecord>> = emptyMap(),
    val allRecords: List<MedicationRecord> = emptyList(),

    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 服薬管理画面固有のイベント
 */
sealed interface PersonMedicationViewEvent {
    // 必要に応じて定義
}

/**
 * 服薬管理画面固有のドメインロジック
 */
object PersonMedicationLogic {
    /**
     * 履歴レコードを日付ごとのマップに変換します。
     */
    fun groupRecordsByDate(records: List<MedicationRecord>): Map<String, List<MedicationRecord>> {
        return records.groupBy { it.dosageDate }
    }
}
