package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * (C)系統: 服薬管理のデータ管理を担当するリポジトリ
 */
class MedicationRepository(
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    fun getMedicationRecords(personId: Int): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByPersonId(personId)

    fun getMedicationRecordsByMonth(personId: Int, month: String): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByMonth(personId, month)

    suspend fun insertMedicationRecord(item: MedicationRecord, featureName: String = "", operation: String = "") {
        val id = medicationRecordDao.insert(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "medication_record_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}, Date: ${item.dosageDate}, Slot: ${item.timeSlot}, Status: ${item.status}",
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
            affectedId = item.id.toString(),
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }
}
