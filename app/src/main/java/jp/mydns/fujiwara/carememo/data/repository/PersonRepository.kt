package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 利用者情報およびアプリ全体のデータ管理を担当するリポジトリ
 */
class PersonRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao
) {
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getDeletedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    fun getPersonById(id: Int): Flow<Person?> = personDao.getPersonById(id)
    
    suspend fun insertPerson(person: Person) = personDao.insert(person)
    
    suspend fun updatePerson(person: Person) = personDao.update(person)

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

    /**
     * 特定の利用者の各カテゴリー記録の有無サマリーを返します。
     */
    fun getPersonCategorySummaryById(personId: Int): Flow<PersonCategorySummary> {
        return combine(
            heightAndWeightDao.hasDataForPerson(personId),
            bpAndPulseDao.hasDataForPerson(personId),
            glucoseAndHbA1cDao.hasDataForPerson(personId),
            conditionAtVisitDao.hasDataForPerson(personId),
            medicationRecordDao.hasDataForPerson(personId)
        ) { hw, bp, glucose, condition, medication ->
            PersonCategorySummary(
                hasHeightWeight = hw,
                hasBpAndPulse = bp,
                hasGlucoseAndHbA1c = glucose,
                hasCondition = condition,
                hasMedication = medication
            )
        }
    }

    /**
     * 全利用者のサマリー情報を取得します。
     */
    fun getPersonCategorySummaries(): Flow<Map<Int, PersonCategorySummary>> {
        return personDao.getPersonCategorySummaries().map { list ->
            list.associate { result ->
                result.id to PersonCategorySummary(
                    hasHeightWeight = result.hasHeightWeight,
                    hasBpAndPulse = result.hasBpAndPulse,
                    hasGlucoseAndHbA1c = result.hasGlucoseAndHbA1c,
                    hasCondition = result.hasCondition,
                    hasMedication = result.hasMedication
                )
            }
        }
    }

    // --- バックアップ・メンテナンス用 ---
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            persons = personDao.getAllRaw(),
            heightAndWeights = heightAndWeightDao.getAllRaw(),
            bpAndPulses = bpAndPulseDao.getAllRaw(),
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw(),
            conditionAtVisits = conditionAtVisitDao.getAllRaw(),
            conditionPhotos = conditionPhotoDao.getAllRaw(),
            medicationRecords = medicationRecordDao.getAllRaw()
        )
    }

    suspend fun replaceAllData(backup: CareMemoBackup) {
        database.withTransaction {
            clearAllData()
            personDao.insertAll(backup.persons)
            heightAndWeightDao.insertAll(backup.heightAndWeights)
            bpAndPulseDao.insertAll(backup.bpAndPulses)
            glucoseAndHbA1cDao.insertAll(backup.glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(backup.conditionAtVisits)
            conditionPhotoDao.insertAll(backup.conditionPhotos)
            medicationRecordDao.insertAll(backup.medicationRecords)
        }
    }

    suspend fun clearAllData() {
        database.withTransaction {
            medicationRecordDao.deleteAll()
            conditionPhotoDao.deleteAll()
            conditionAtVisitDao.deleteAll()
            glucoseAndHbA1cDao.deleteAll()
            bpAndPulseDao.deleteAll()
            heightAndWeightDao.deleteAll()
            personDao.deleteAll()
        }
    }
}
