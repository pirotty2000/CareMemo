package jp.mydns.fujiwara.carememo.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * データベース操作を管理するリポジトリ
 */
class CareMemoRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao
) {
    // --- Person ---
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getDeletedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    fun getPersonById(id: Int): Flow<Person?> = personDao.getPersonById(id)
    
    suspend fun insertPerson(person: Person) = personDao.insert(person)
    
    suspend fun updatePerson(person: Person) = personDao.update(person)

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
     * トランザクション内で実行し、データの整合性を保証します。
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
        }
    }

    /**
     * 論理削除された利用者と、紐づくすべての記録を復元します。
     * トランザクション内で実行し、データの整合性を保証します。
     */
    suspend fun restorePerson(personId: Int) {
        database.withTransaction {
            personDao.restore(personId)
            heightAndWeightDao.restoreByPersonId(personId)
            bpAndPulseDao.restoreByPersonId(personId)
            glucoseAndHbA1cDao.restoreByPersonId(personId)
            conditionAtVisitDao.restoreByPersonId(personId)
        }
    }

    /**
     * 利用終了者（論理削除された利用者）と、そのすべての記録を物理削除します。
     * 外部キーの CASCADE 設定により、子データも自動で抹消されます。
     */
    suspend fun deleteEndedPersons() {
        personDao.deleteEndedPersons()
    }

    /**
     * 利用者を物理削除します。外部キーの CASCADE 設定により、子データも自動で物理削除されます。
     */
    suspend fun deletePerson(person: Person) = personDao.delete(person)

    // --- 各種記録操作 ---
    fun getHeightAndWeightByPersonId(personId: Int): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.insert(item)
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.delete(item)

    fun getBpAndPulseByPersonId(personId: Int): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)
    
    suspend fun insertBpAndPulse(item: BpAndPulse) = bpAndPulseDao.insert(item)
    
    suspend fun deleteBpAndPulse(item: BpAndPulse) = bpAndPulseDao.delete(item)

    fun getGlucoseAndHbA1cByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.insert(item)
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.delete(item)

    fun getConditionAtVisitByPersonId(personId: Int): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.insert(item)
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.delete(item)

    // --- ConditionPhoto ---
    fun getConditionPhotosByConditionId(conditionId: Int): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getByConditionId(conditionId)

    suspend fun insertConditionPhoto(item: ConditionPhoto) = conditionPhotoDao.insert(item)

    suspend fun deleteConditionPhoto(item: ConditionPhoto) = conditionPhotoDao.delete(item)

    suspend fun deleteConditionPhotoById(id: Int) = conditionPhotoDao.deleteById(id)
    
    suspend fun getAllPhotosByPersonId(personId: Int) = conditionPhotoDao.getAllByPersonId(personId)

    // --- バックアップ・インポート ---
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            persons = personDao.getAllRaw(),
            heightAndWeights = heightAndWeightDao.getAllRaw(),
            bpAndPulses = bpAndPulseDao.getAllRaw(),
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw(),
            conditionAtVisits = conditionAtVisitDao.getAllRaw(),
            conditionPhotos = conditionPhotoDao.getAllRaw()
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
        }
    }

    suspend fun clearAllData() {
        database.withTransaction {
            conditionPhotoDao.deleteAll()
            conditionAtVisitDao.deleteAll()
            glucoseAndHbA1cDao.deleteAll()
            bpAndPulseDao.deleteAll()
            heightAndWeightDao.deleteAll()
            personDao.deleteAll()
        }
    }

    /**
     * 全利用者の生年月日の時分秒を 00:00:00 (JST) に正規化します。
     * 重複登録防止の制約が正しく機能するようにするためのメンテナンス機能です。
     */
    suspend fun normalizeAllPersonBirthdays() {
        database.withTransaction {
            val persons = personDao.getAllRaw()
            persons.forEach { person ->
                val normalizedInstant = person.birthday
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()

                if (person.birthday != normalizedInstant) {
                    personDao.update(person.copy(birthday = normalizedInstant))
                }
            }
        }
    }

    /**
     * 全利用者の各カテゴリー記録の有無を統合したMapを返します。
     * メイン画面などのインジケーター表示に利用します。
     */
    fun getPersonCategorySummaries(): Flow<Map<Int, PersonCategorySummary>> {
        return combine(
            heightAndWeightDao.getPersonIdsWithHeightWeight(),
            bpAndPulseDao.getPersonIdsWithBp(),
            bpAndPulseDao.getPersonIdsWithPulse(),
            glucoseAndHbA1cDao.getPersonIdsWithGlucose(),
            conditionAtVisitDao.getPersonIdsWithCondition()
        ) { hw, bp, pulse, glucose, condition ->
            val allIds = (hw + bp + pulse + glucose + condition).distinct()
            allIds.associateWith { id ->
                PersonCategorySummary(
                    hasHeightWeight = hw.contains(id),
                    hasBpAndPulse = bp.contains(id) || pulse.contains(id),
                    hasGlucoseAndHbA1c = glucose.contains(id),
                    hasCondition = condition.contains(id)
                )
            }
        }
    }
}
