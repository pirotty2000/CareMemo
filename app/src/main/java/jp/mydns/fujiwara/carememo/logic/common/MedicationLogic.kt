package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.MedicationRecord
import java.time.LocalDate
import java.time.YearMonth

/**
 * 服薬の時間枠（スロット）定義
 */
enum class MedicationTimeSlot(val index: Int) {
    MORNING(0),
    LUNCH(1),
    DINNER(2),
    BEDTIME(3);

    companion object {
        fun fromIndex(index: Int): MedicationTimeSlot? = entries.find { it.index == index }
    }
}

/**
 * 服薬のステータス定義
 */
enum class MedicationStatus(val code: Int) {
    NONE(0),   // 未服用（×）
    ASSIST(1), // 服薬介助（△）
    TAKEN(2);  // 服用（○）

    companion object {
        fun fromCode(code: Int?): MedicationStatus? = entries.find { it.code == code }
    }
}

/**
 * 服薬バリデーションの結果（事実）
 */
enum class MedicationValidationResult {
    SUCCESS,
    FUTURE_DATE_NOT_ALLOWED,
    INVALID_STATUS
}

/**
 * 同期処理のアクション（追加・更新・削除・維持）
 */
sealed class SyncAction {
    data class Insert(val record: MedicationRecord) : SyncAction()
    data class Delete(val record: MedicationRecord) : SyncAction()
    data object None : SyncAction()
}

/**
 * 服薬管理に関するドメインロジック
 */
object MedicationLogic {

    /**
     * カレンダー表示用の日付リストを生成します。
     */
    fun getCalendarDays(yearMonth: YearMonth): List<LocalDate?> {
        val daysInMonth = yearMonth.lengthOfMonth()
        val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7
        
        val calendarDays = mutableListOf<LocalDate?>()
        repeat(firstDayOfWeek) { calendarDays.add(null) }
        for (day in 1..daysInMonth) {
            calendarDays.add(yearMonth.atDay(day))
        }
        return calendarDays
    }

    /**
     * 特定の日の同期アクションを判定します。
     * 各スロット（0〜3）に対して、どのような DB 操作が必要か（あるいは不要か）を返します。
     */
    fun determineSyncActions(
        current: List<MedicationRecord>,
        input: List<MedicationRecord?>
    ): List<SyncAction> {
        val actions = mutableListOf<SyncAction>()

        input.forEachIndexed { index, newRecord ->
            val existingRecord = current.find { it.timeSlot == index }

            when {
                newRecord != null -> {
                    if (existingRecord != null && existingRecord.status == newRecord.status) {
                        // 内容が同じなので「維持」
                        actions.add(SyncAction.None)
                    } else {
                        // 新規またはステータス変更なので「保存」
                        actions.add(SyncAction.Insert(newRecord))
                    }
                }
                existingRecord != null -> {
                    // 入力が null になったので「削除」
                    actions.add(SyncAction.Delete(existingRecord))
                }
                else -> {
                    // 元々なく、今もないので「何もしない」
                    actions.add(SyncAction.None)
                }
            }
        }
        return actions
    }

    /**
     * 服薬記録の内容をバリデーションします。
     */
    fun validateMedication(record: MedicationRecord, today: LocalDate = LocalDate.now()): MedicationValidationResult {
        // 未来日付のチェック (dosageDate は yyyy-MM-dd 形式)
        val dosageDate = try {
            LocalDate.parse(record.dosageDate)
        } catch (e: Exception) {
            return MedicationValidationResult.SUCCESS
        }
        
        if (dosageDate.isAfter(today)) {
            return MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED
        }

        // ステータスの範囲チェック
        if (record.status !in 0..2) {
            return MedicationValidationResult.INVALID_STATUS
        }

        return MedicationValidationResult.SUCCESS
    }

    /**
     * 不正なステータスを持つレコードを除外します（インポート時などのクレンジング）。
     */
    fun filterValidRecords(records: List<MedicationRecord>): List<MedicationRecord> {
        return records.filter { it.status in 0..2 }
    }
}
