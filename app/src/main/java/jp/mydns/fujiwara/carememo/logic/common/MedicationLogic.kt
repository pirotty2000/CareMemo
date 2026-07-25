package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import java.time.LocalDate
import java.time.YearMonth

/**
 * 服薬の時間枠（スロット）定義
 */
enum class MedicationTimeSlot(val index: Int) {
    MORNING(AppSpecifications.Medication.TimeSlot.INDEX_MORNING),
    LUNCH(AppSpecifications.Medication.TimeSlot.INDEX_LUNCH),
    DINNER(AppSpecifications.Medication.TimeSlot.INDEX_DINNER),
    BEDTIME(AppSpecifications.Medication.TimeSlot.INDEX_BEDTIME)
}

/**
 * 服薬のステータス定義
 */
enum class MedicationStatus(val code: Int) {
    NONE(AppSpecifications.Medication.Status.CODE_NONE),     // 未服用
    ASSIST(AppSpecifications.Medication.Status.CODE_ASSIST), // 服薬介助
    TAKEN(AppSpecifications.Medication.Status.CODE_TAKEN);   // 服用

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

    /** 新規作成を明示する特別なID */
    const val NEW_RECORD_ID = "__NEW__"

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
     * 特定の日の同期アクションを判定し、適切な ID 管理を伴う Entity を構築します。
     * UI 層から渡された ID は無視し、既存レコードの timeSlot に基づいて 
     * 「既存 ID の維持（更新）」または「新規 UUID の採番（追加）」を決定します。
     */
    fun determineSyncActions(
        current: List<MedicationRecord>,
        input: List<MedicationRecord?>
    ): List<SyncAction> {
        val actions = mutableListOf<SyncAction>()

        input.forEachIndexed { index, inputRecord ->
            val existingRecord = current.find { it.timeSlot == index }

            when {
                inputRecord != null -> {
                    // 入力がある場合：追加または更新
                    val finalRecord = if (existingRecord != null) {
                        // 既存あり：IDを維持してコピー（更新）
                        inputRecord.copy(id = existingRecord.id)
                    } else {
                        // 既存なし：新しい UUID を発行（新規追加）
                        inputRecord.copy(id = java.util.UUID.randomUUID().toString())
                    }

                    if (existingRecord != null && existingRecord.status == finalRecord.status && existingRecord.recordTime == finalRecord.recordTime) {
                        // 内容が完全に同じなら何もしない
                        actions.add(SyncAction.None)
                    } else {
                        // 変更がある、または新規なら Insert (RoomのReplaceにより更新もInsertで扱う)
                        actions.add(SyncAction.Insert(finalRecord))
                    }
                }
                existingRecord != null -> {
                    // 入力が null（解除）で既存がある場合：削除
                    actions.add(SyncAction.Delete(existingRecord))
                }
                else -> {
                    // 元々なく、今もない
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
        } catch (_: Exception) {
            return MedicationValidationResult.SUCCESS
        }
        
        if (dosageDate.isAfter(today)) {
            return MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED
        }

        // ステータスの範囲チェック
        if (record.status !in AppSpecifications.Medication.Status.VALID_RANGE) {
            return MedicationValidationResult.INVALID_STATUS
        }

        return MedicationValidationResult.SUCCESS
    }

    /**
     * 不正なステータスを持つレコードを除外します（インポート時などのクレンジング）。
     */
    fun filterValidRecords(records: List<MedicationRecord>): List<MedicationRecord> {
        return records.filter { it.status in AppSpecifications.Medication.Status.VALID_RANGE }
    }
}
