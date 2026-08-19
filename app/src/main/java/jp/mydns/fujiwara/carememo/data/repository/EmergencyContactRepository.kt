package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.EmergencyContactDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：EmergencyContactRepository
 *
 * 【役割】
 * 利用者に紐付く「緊急連絡先（医師、家族、事業所等）」の管理を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：連絡先情報の CRUD 操作に特化します。
 * 2. 依存方向の遵守：下位レイヤーとして、上位の Logic レイヤーや ViewModel に依存しない構成を維持します。
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

    /* 
     * 将来的に利用者一覧画面等で「緊急連絡先あり」アイコンを表示する等の
     * 最適化された判定処理が必要になった際に復活させるため保持。
    suspend fun hasContacts(personId: String): Boolean =
        emergencyContactDao.hasDataForPerson(personId)
    */

    /**
     * IDを指定して特定の連絡先情報を取得します。
     *
     * @param id 連絡先ID
     * @return 該当する連絡先。存在しない場合は null。
     */
    suspend fun getContactById(id: String): EmergencyContact? =
        emergencyContactDao.getById(id)

    /**
     * 連絡先情報を保存（新規登録または更新）します。
     *
     * @param contact 保存対象の連絡先 Entity
     * @param isUpdate 新規登録なら false、既存更新なら true
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun saveContact(
        contact: EmergencyContact,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
        val itemToSave = contact.copy(updatedAt = Instant.now(), isSynced = false)

        if (isUpdate) {
            emergencyContactDao.update(itemToSave)
        } else {
            emergencyContactDao.insert(itemToSave)
        }

        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "emergency_contact_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "Facility: ${itemToSave.facilityName}, Type: ${itemToSave.contactType}",
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
