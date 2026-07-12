package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * 利用者の復帰（論理削除解除）および完全抹消（物理削除）を担当するリポジトリ
 */
class DeleteOrRestorePersonRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * アーカイブ（論理削除）されている利用者一覧を取得します。
     */
    fun getArchivedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
     * ※現在は主にSettingsScreenなどから利用されます。
     */
    suspend fun logicalDeletePerson(personId: Int, featureName: String = "", operation: String = "") {
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            personDao.logicalDelete(personId, timestamp)
            heightAndWeightDao.logicalDeleteByPersonId(personId, timestamp)
            bpAndPulseDao.logicalDeleteByPersonId(personId, timestamp)
            glucoseAndHbA1cDao.logicalDeleteByPersonId(personId, timestamp)
            conditionAtVisitDao.logicalDeleteByPersonId(personId, timestamp)
            conditionPhotoDao.logicalDeleteByPersonId(personId, timestamp)
            medicationRecordDao.logicalDeleteByPersonId(personId, timestamp)

            auditLogRepository?.log(
                featureName = featureName,
                operation = operation,
                tableName = "person_db",
                actionType = "LOGICAL_DELETE",
                affectedId = personId.toString(),
                details = "Cascade logical delete for person and all related records"
            )
        }
    }

    /**
     * 論理削除された利用者と、紐づくすべての記録を復帰させます。
     */
    suspend fun restorePerson(personId: Int, featureName: String = "", operation: String = "") {
        database.withTransaction {
            personDao.restore(personId)
            heightAndWeightDao.restoreByPersonId(personId)
            bpAndPulseDao.restoreByPersonId(personId)
            glucoseAndHbA1cDao.restoreByPersonId(personId)
            conditionAtVisitDao.restoreByPersonId(personId)
            conditionPhotoDao.restoreByPersonId(personId)
            medicationRecordDao.restoreByPersonId(personId)

            auditLogRepository?.log(
                featureName = featureName,
                operation = operation,
                tableName = "person_db",
                actionType = "RESTORE",
                affectedId = personId.toString(),
                details = "Restore person and all related records"
            )
        }
    }

    /**
     * 指定された利用者を完全に抹消（物理削除）します。
     */
    suspend fun permanentlyDeletePerson(personId: Int, featureName: String = "", operation: String = "") {
        personDao.deletePersonPhysically(personId)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "PERMANENT_DELETE",
            affectedId = personId.toString()
        )
    }

    /**
     * 全ての利用終了者（論理削除された利用者）と、そのすべての記録を物理削除します。
     */
    suspend fun deleteAllEndedPersons(featureName: String = "", operation: String = "") {
        personDao.deleteEndedPersons()
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "CLEAR_ALL_ARCHIVED",
            affectedId = "all",
            details = "Permanently deleted all logical-deleted persons"
        )
    }
}
