package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * 利用終了者（アーカイブ済み利用者）の管理を担当するリポジトリ
 */
class ArchivedPersonRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao
) {
    fun getDeletedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
     */
    suspend fun logicalDeletePerson(personId: Int) {
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            personDao.logicalDelete(personId, timestamp)
            heightAndWeightDao.logicalDeleteByPersonId(personId, timestamp)
            bpAndPulseDao.logicalDeleteByPersonId(personId, timestamp)
            glucoseAndHbA1cDao.logicalDeleteByPersonId(personId, timestamp)
            conditionAtVisitDao.logicalDeleteByPersonId(personId, timestamp)
            conditionPhotoDao.logicalDeleteByPersonId(personId, timestamp)
            medicationRecordDao.logicalDeleteByPersonId(personId, timestamp)
        }
    }

    /**
     * 論理削除された利用者と、紐づくすべての記録を復元します。
     */
    suspend fun restorePerson(personId: Int) {
        database.withTransaction {
            personDao.restore(personId)
            heightAndWeightDao.restoreByPersonId(personId)
            bpAndPulseDao.restoreByPersonId(personId)
            glucoseAndHbA1cDao.restoreByPersonId(personId)
            conditionAtVisitDao.restoreByPersonId(personId)
            conditionPhotoDao.restoreByPersonId(personId)
            medicationRecordDao.restoreByPersonId(personId)
        }
    }

    /**
     * 利用終了者（論理削除された利用者）と、そのすべての記録を物理削除します。
     */
    suspend fun deleteEndedPersons() {
        personDao.deleteEndedPersons()
    }
}
