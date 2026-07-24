package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * (C)系統: 服薬管理のデータ管理を担当するリポジトリ
 */
class MedicationRepository(
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    fun getMedicationRecords(personId: String): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByPersonId(personId)

    fun getMedicationRecordsByMonth(personId: String, month: String): Flow<List<MedicationRecord>> =
         medicationRecordDao.getByMonth(personId, month)

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

    suspend fun deleteMedicationRecord(item: MedicationRecord, featureName: String = "", operation: String = "") {
        medicationRecordDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "medication_record_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }
}
