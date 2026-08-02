package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：MedicationRepository
 *
 * 【役割】
 * 利用者の「服薬管理（カテゴリE）」に関連する記録の永続化管理を担当します。
 * 日次の服薬有無（朝・昼・夕・寝る前）の状況と、それらの確認時刻を管理します。
 *
 * 【主な機能】
 * ・利用者ごとの全服薬履歴の取得。
 * ・月指定による服薬履歴の取得（カレンダー・月間履歴用）。
 * ・服薬記録の追加、更新、物理削除操作。
 * ・データ操作に応じた監査ログの詳細記録。
 *
 * 【設計指針】
 * 1. データの整合性：保存時には常に `updatedAt` を現在時刻に更新し、同期フラグ `isSynced` を false に設定する。
 * 2. 証跡の管理：服薬状況の変更は重要な記録であるため、日付や時間枠（スロット）、ステータスの変化を監査ログに残す。
 * 3. 効率性：カレンダー描画を考慮し、月次での絞り込みクエリ（getByMonth）を提供する。
 */
class MedicationRepository(
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * 特定の利用者の全服薬履歴を Flow で取得します。
     *
     * @param personId 利用者ID
     * @return 服薬記録リストを通知する Flow
     */
    fun getMedicationRecords(personId: String): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByPersonId(personId)

    /**
     * 特定の利用者の服薬履歴を、月単位で絞り込んで Flow で取得します。
     * カレンダーや月間履歴テーブルの表示に使用します。
     *
     * @param personId 利用者ID
     * @param month 取得対象の年月（"yyyy-MM" 形式）
     * @return 該当月の服薬記録リストを通知する Flow
     */
    fun getMedicationRecordsByMonth(personId: String, month: String): Flow<List<MedicationRecord>> =
         medicationRecordDao.getByMonth(personId, month)

    /**
     * 服薬記録を保存または更新します。
     *
     * @param item 保存対象の記録 Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     * @param isUpdate 更新処理の場合は true
     */
    suspend fun insertMedicationRecord(item: MedicationRecord, featureName: String = "", operation: String = "", isUpdate: Boolean = false) {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        medicationRecordDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "medication_record_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}, Date: ${itemToSave.dosageDate}, Slot: ${itemToSave.timeSlot}, Status: ${itemToSave.status}",
            resultType = "SUCCESS"
        )
    }

    /**
     * 服薬記録を物理削除（服薬取り消し）します。
     *
     * @param item 削除対象の記録 Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun deleteMedicationRecord(item: MedicationRecord, featureName: String = "", operation: String = "") {
        medicationRecordDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "medication_record_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}, Date: ${item.dosageDate}, Slot: ${item.timeSlot}",
            resultType = "SUCCESS"
        )
    }
}
