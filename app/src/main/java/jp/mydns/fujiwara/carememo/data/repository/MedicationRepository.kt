package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：MedicationRepository
 *
 * 【役割】
 * 利用者の「服薬管理（カテゴリE）」に関連する記録の永続化管理を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：DB 操作に特化し、同期アクション（保存・削除）の要否判定などの業務判断は Logic レイヤーへ委譲します。
 * 2. 依存方向の遵守：下位レイヤーとして、上位の Logic レイヤーに依存しない構成を維持します。
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
