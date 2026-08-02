package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import java.time.LocalDate
import java.time.YearMonth

/**
 * 服薬の時間枠（スロット）定義。
 * 朝・昼・夕・寝る前の4つの時間帯を管理します。
 */
enum class MedicationTimeSlot(val index: Int) {
    /** 朝 (Index: 0) */
    MORNING(AppSpecifications.Medication.TimeSlot.INDEX_MORNING),
    /** 昼 (Index: 1) */
    LUNCH(AppSpecifications.Medication.TimeSlot.INDEX_LUNCH),
    /** 夕 (Index: 2) */
    DINNER(AppSpecifications.Medication.TimeSlot.INDEX_DINNER),
    /** 寝る前 (Index: 3) */
    BEDTIME(AppSpecifications.Medication.TimeSlot.INDEX_BEDTIME)
}

/**
 * 服薬のステータス定義。
 */
enum class MedicationStatus(val code: Int) {
    /** 未服用（チェックなし） */
    NONE(AppSpecifications.Medication.Status.CODE_NONE),
    /** 服薬介助（介助者が服用を助けた） */
    ASSIST(AppSpecifications.Medication.Status.CODE_ASSIST),
    /** 服用（本人による自立した服用） */
    TAKEN(AppSpecifications.Medication.Status.CODE_TAKEN);

    companion object {
        /**
         * 保存値（Int）から Enum 型を取得します。
         */
        fun fromCode(code: Int?): MedicationStatus? = entries.find { it.code == code }
    }
}

/**
 * 服薬バリデーションの結果（事実）。
 */
enum class MedicationValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 未来の日付に対する記録は不可 */
    FUTURE_DATE_NOT_ALLOWED,
    /** ステータスコードが規定範囲外 */
    INVALID_STATUS
}

/**
 * 同期処理のアクション（追加・更新・削除・維持）。
 * UIの状態とDBの状態を比較し、最終的にどのようなSQLを発行すべきかを決定するために使用します。
 */
sealed class SyncAction {
    /** 新規追加または既存更新（Room の Replace 指定を前提とする） */
    data class Insert(val record: MedicationRecord) : SyncAction()
    /** 既存レコードの削除（服薬解除） */
    data class Delete(val record: MedicationRecord) : SyncAction()
    /** 変更なし（何もしない） */
    data object None : SyncAction()
}

/**
 * Logic：MedicationLogic
 *
 * 【役割】
 * 服薬管理（カテゴリC）に関するドメインロジックを提供します。
 * カレンダー表示用のデータ生成、UI入力値と既存データの同期アクション判定、バリデーションを担当します。
 *
 * 【主な機能】
 * ・月間カレンダー表示用の日付グリッドデータ生成。
 * ・特定の日における4つの時間枠（スロット）の、追加・更新・削除の自動判定。
 * ・未来日付チェックやステータス範囲チェック。
 *
 * 【設計指針】
 * 1. UI層からの入力には ID が付与されない（または NEW_RECORD_ID である）ことを前提とし、
 *    既存レコードの timeSlot と比較して適切な ID 維持（更新）または新規採番（追加）を決定する。
 * 2. 状態の「解除」は物理削除として扱う。
 * 3. 変更がない場合は SyncAction.None を返し、無駄な DB アクセスやログ記録を抑制する。
 */
object MedicationLogic {

    /**
     * カレンダー表示用の日付リストを生成します。
     * 曜日の位置を合わせるため、月初に先行する空白（null）を含んだリストを返します。
     *
     * @param yearMonth 対象の年月
     * @return 曜日位置を考慮した LocalDate? のリスト
     */
    fun getCalendarDays(yearMonth: YearMonth): List<LocalDate?> {
        val daysInMonth = yearMonth.lengthOfMonth()
        // 月の最初の日の曜日 (1:月..7:日) を日曜開始 (0:日..6:土) に変換
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
     *
     * @param current 現在DBに保存されている、その日の全スロット記録リスト
     * @param input UI層で入力された、4スロット分の記録（null は未入力・解除を意味する）
     * @return 各スロットに対する SyncAction のリスト
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
                        // 内容（ステータス、記録時刻）が完全に同じなら何もしない
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
     *
     * @param record 検証対象のレコード
     * @param today 基準となる当日日付（テスト用に指定可能）
     * @return バリデーション結果
     */
    fun validateMedication(record: MedicationRecord, today: LocalDate = LocalDate.now()): MedicationValidationResult {
        // 未来日付のチェック (dosageDate は yyyy-MM-dd 形式)
        val dosageDate = try {
            LocalDate.parse(record.dosageDate)
        } catch (_: Exception) {
            // フォーマット不正は別の責務（DB制約等）とするか、一旦成功として扱う
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
     * 不正なステータスを持つレコードを除外します（インポート時などのデータクレンジング用）。
     *
     * @param records 対象のレコードリスト
     * @return 妥当なレコードのみのリスト
     */
    fun filterValidRecords(records: List<MedicationRecord>): List<MedicationRecord> {
        return records.filter { it.status in AppSpecifications.Medication.Status.VALID_RANGE }
    }
}
