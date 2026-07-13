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
        fun fromIndex(index: Int): MedicationTimeSlot = entries.find { it.index == index } ?: MORNING
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
 * 同期処理のアクション（追加・更新・削除）
 */
sealed class SyncAction {
    data class Insert(val record: MedicationRecord) : SyncAction()
    data class Delete(val record: MedicationRecord) : SyncAction()
}

/**
 * 服薬管理に関するドメインロジック
 */
object MedicationLogic {

    /**
     * カレンダー表示用の日付リストを生成します。
     * 日曜開始のグリッドに合わせるため、月初より前のマスには null を入れます。
     */
    fun getCalendarDays(yearMonth: YearMonth): List<LocalDate?> {
        val daysInMonth = yearMonth.lengthOfMonth()
        // 日曜日(7)を0、月曜日(1)を1 ... 土曜日(6)を6とする
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
     * @param current データベースにある現在のその日の記録
     * @param input 画面から渡された最新の状態（4スロット分）
     */
    fun determineSyncActions(
        current: List<MedicationRecord>,
        input: List<MedicationRecord?>
    ): List<SyncAction> {
        val actions = mutableListOf<SyncAction>()

        input.forEachIndexed { index, newRecord ->
            val existingRecord = current.find { it.timeSlot == index }
            
            if (newRecord != null) {
                // 新規または更新
                actions.add(SyncAction.Insert(newRecord))
            } else if (existingRecord != null) {
                // 入力が null になったので削除
                actions.add(SyncAction.Delete(existingRecord))
            }
        }
        return actions
    }
}
