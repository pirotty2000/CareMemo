package jp.mydns.fujiwara.carememo.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

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
) {
    // --- Person ---
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getDeletedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    fun getPersonById(id: Int): Flow<Person?> = personDao.getPersonById(id)
    
    suspend fun insertPerson(person: Person) = personDao.insert(person)
    
    suspend fun updatePerson(person: Person) = personDao.update(person)

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
     * Roomの標準機能では論理削除の連動が難しいため、手動でタイムスタンプを一括更新しています。
     */
    suspend fun logicalDeletePerson(personId: Int) {
        val timestamp = System.currentTimeMillis()
        personDao.logicalDelete(personId, timestamp)
        heightAndWeightDao.logicalDeleteByPersonId(personId, timestamp)
        bpAndPulseDao.logicalDeleteByPersonId(personId, timestamp)
        glucoseAndHbA1cDao.logicalDeleteByPersonId(personId, timestamp)
        conditionAtVisitDao.logicalDeleteByPersonId(personId, timestamp)
    }

    /**
     * 論理削除された利用者と、紐づくすべての記録を復元します。
     */
    suspend fun restorePerson(personId: Int) {
        personDao.restore(personId)
        heightAndWeightDao.restoreByPersonId(personId)
        bpAndPulseDao.restoreByPersonId(personId)
        glucoseAndHbA1cDao.restoreByPersonId(personId)
        conditionAtVisitDao.restoreByPersonId(personId)
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

    // --- HeightAndWeight ---
    fun getHeightAndWeightByPersonId(personId: Int): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.insert(item)
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.delete(item)

    // --- BpAndPulse ---
    fun getBpAndPulseByPersonId(personId: Int): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)
    
    suspend fun insertBpAndPulse(item: BpAndPulse) = bpAndPulseDao.insert(item)
    
    suspend fun deleteBpAndPulse(item: BpAndPulse) = bpAndPulseDao.delete(item)

    // --- GlucoseAndHbA1c ---
    fun getGlucoseAndHbA1cByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.insert(item)
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.delete(item)

    // --- ConditionAtVisit ---
    fun getConditionAtVisitByPersonId(personId: Int): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.insert(item)
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.delete(item)

    // --- バックアップ・インポート ---
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            persons = personDao.getAllRaw(),
            heightAndWeights = heightAndWeightDao.getAllRaw(),
            bpAndPulses = bpAndPulseDao.getAllRaw(),
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw(),
            conditionAtVisits = conditionAtVisitDao.getAllRaw()
        )
    }

    suspend fun replaceAllData(backup: CareMemoBackup) {
        database.withTransaction {
            clearAllData()

            // データの挿入
            personDao.insertAll(backup.persons)
            heightAndWeightDao.insertAll(backup.heightAndWeights)
            bpAndPulseDao.insertAll(backup.bpAndPulses)
            glucoseAndHbA1cDao.insertAll(backup.glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(backup.conditionAtVisits)
        }
    }

    /**
     * すべてのテーブルのデータを物理削除します。
     * データベースの初期化や旧アプリデータの引き継ぎ時に使用します。
     */
    suspend fun clearAllData() {
        database.withTransaction {
            conditionAtVisitDao.deleteAll()
            glucoseAndHbA1cDao.deleteAll()
            bpAndPulseDao.deleteAll()
            heightAndWeightDao.deleteAll()
            personDao.deleteAll()
        }
    }

    // --- existence check ---
    fun getPersonIdsWithHeightWeight(): Flow<List<Int>> = heightAndWeightDao.getPersonIdsWithHeightWeight()
    fun getPersonIdsWithPulse(): Flow<List<Int>> = bpAndPulseDao.getPersonIdsWithPulse()
    fun getPersonIdsWithBp(): Flow<List<Int>> = bpAndPulseDao.getPersonIdsWithBp()
    fun getPersonIdsWithGlucose(): Flow<List<Int>> = glucoseAndHbA1cDao.getPersonIdsWithGlucose()
    fun getPersonIdsWithCondition(): Flow<List<Int>> = conditionAtVisitDao.getPersonIdsWithCondition()
}
