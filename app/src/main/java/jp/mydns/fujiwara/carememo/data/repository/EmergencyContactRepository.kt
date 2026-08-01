package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.EmergencyContactDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * 緊急連絡先 (EmergencyContact) の管理を担当するリポジトリ
 */
class EmergencyContactRepository(
    private val emergencyContactDao: EmergencyContactDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /** 利用者に紐付く連絡先一覧を取得する */
    fun getContactsByPersonId(personId: String): Flow<List<EmergencyContact>> =
        emergencyContactDao.getByPersonId(personId)

    /** 連絡先の存在確認 */
    suspend fun hasContacts(personId: String): Boolean =
        emergencyContactDao.hasDataForPerson(personId)

    /** ID指定で取得 */
    suspend fun getContactById(id: String): EmergencyContact? =
        emergencyContactDao.getById(id)

    /** 新規登録 */
    suspend fun insertContact(contact: EmergencyContact, featureName: String = "", operation: String = "") {
        val itemToSave = contact.copy(updatedAt = Instant.now(), isSynced = false)
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

    /** 更新 */
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

    /** 削除 */
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
