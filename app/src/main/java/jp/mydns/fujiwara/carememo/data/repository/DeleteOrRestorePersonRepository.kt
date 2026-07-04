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
    private val medicationRecordDao: MedicationRecordDao
) {
    /**
     * アーカイブ（論理削除）されている利用者一覧を取得します。
     */
    fun getArchivedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
     * ※現在は主にSettingsScreenなどから利用されます。
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
     * 論理削除された利用者と、紐づくすべての記録を復帰させます。
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
     * 指定された利用者を完全に抹消（物理削除）します。
     */
    suspend fun permanentlyDeletePerson(personId: Int) {
        // PersonDao の物理削除メソッドを個別に呼ぶか、Dao側の整理が必要
        // 現状は一括削除しかない場合、指定IDのみの削除をDaoに追加する必要があるかもしれません
        personDao.deletePersonPhysically(personId)
    }

    /**
     * 全ての利用終了者（論理削除された利用者）と、そのすべての記録を物理削除します。
     */
    suspend fun deleteAllEndedPersons() {
        personDao.deleteEndedPersons()
    }
}
