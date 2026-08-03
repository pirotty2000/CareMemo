package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.EmergencyContactDao
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：EmergencyContactRepository
 *
 * 【役割】
 * 利用者に紐付く「緊急連絡先（医師、家族、事業所等）」の管理を担当します。
 *
 * 【主な機能】
 * ・利用者ごとの連絡先一覧取得（種別優先度に基づくソート済み）。
 * ・連絡先の新規登録、更新、削除操作。
 * ・データ操作に応じた監査ログの記録。
 * ・特定 ID による連絡先情報の取得。
 *
 * 【設計指針】
 * 1. データの整合性：保存時には常に `updatedAt` を現在時刻に更新し、同期フラグ `isSynced` を false に設定する。
 * 2. 証跡の管理：連絡先の変更（住所や電話番号の修正、削除等）は、監査ログに詳細（施設名や種別）を記録する。
 * 3. 視認性：DAO 層でのソート仕様に基づき、緊急時に重要な連絡先が優先的に取得されることを保証する。
 */
class EmergencyContactRepository(
    private val emergencyContactDao: EmergencyContactDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * 利用者に紐付く連絡先一覧を Flow で取得します。
     *
     * 内部（DAO）で「種別優先度 (医師 > 看護師...) ➔ 表示順序 (priority) ➔ 施設名」の順に
     * ソートされた結果が返されます。
     *
     * @param personId 利用者ID
     * @return 連絡先リストを通知する Flow
     */
    fun getContactsByPersonId(personId: String): Flow<List<EmergencyContact>> =
        emergencyContactDao.getByPersonId(personId)

    /**
     * 指定された利用者に連絡先が1件でも登録されているか確認します。
     *
     * @param personId 利用者ID
     * @return 存在する場合は true
     */
    suspend fun hasContacts(personId: String): Boolean =
        emergencyContactDao.hasDataForPerson(personId)

    /**
     * IDを指定して特定の連絡先情報を取得します。
     *
     * @param id 連絡先ID
     * @return 該当する連絡先。存在しない場合は null。
     */
    suspend fun getContactById(id: String): EmergencyContact? =
        emergencyContactDao.getById(id)

    /**
     * 新しい連絡先を登録します。
     *
     * @param contact 保存対象の連絡先 Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun insertContact(contact: EmergencyContact, featureName: String = "", operation: String = "") {
        // IdLogic を使用して新規レコード判定を行い、ID が必要なら生成する
        val itemToSave = if (IdLogic.isNew(contact.id)) {
            contact.copy(id = java.util.UUID.randomUUID().toString(), updatedAt = Instant.now(), isSynced = false)
        } else {
            contact.copy(updatedAt = Instant.now(), isSynced = false)
        }
        emergencyContactDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "emergency_contact_db",
            actionType = "INSERT",
            affectedId = itemToSave.id,
            details = "Facility: ${itemToSave.facilityName}, Type: ${itemToSave.contactType}",
            resultType = "SUCCESS"
        )
    }

    /**
     * 既存の連絡先情報を更新します。
     *
     * @param contact 更新対象の連絡先 Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun updateContact(contact: EmergencyContact, featureName: String = "", operation: String = "") {
        val itemToUpdate = contact.copy(updatedAt = Instant.now(), isSynced = false)
        emergencyContactDao.update(itemToUpdate)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "emergency_contact_db",
            actionType = "UPDATE",
            affectedId = itemToUpdate.id,
            details = "Facility: ${itemToUpdate.facilityName}, Type: ${itemToUpdate.contactType}",
            resultType = "SUCCESS"
        )
    }

    /**
     * 連絡先情報を物理削除します。
     *
     * @param contact 削除対象の連絡先 Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun deleteContact(contact: EmergencyContact, featureName: String = "", operation: String = "") {
        emergencyContactDao.delete(contact)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "emergency_contact_db",
            actionType = "DELETE",
            affectedId = contact.id,
            details = "Facility: ${contact.facilityName}",
            resultType = "SUCCESS"
        )
    }
}
