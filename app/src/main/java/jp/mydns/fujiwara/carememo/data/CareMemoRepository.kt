package jp.mydns.fujiwara.carememo.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * データベース操作を管理するリポジトリ
 */
class CareMemoRepository(
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao
) {
    // --- Person ---
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getDeletedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    fun getPersonById(id: Int): Flow<Person?> = personDao.getPersonById(id)
    
    suspend fun insertPerson(person: Person) = personDao.insert(person)
    
    suspend fun updatePerson(person: Person) = personDao.update(person)

    /**
     * 利用者を論理削除し、紐づくすべての記録も論理削除します（カスケード論理削除）。
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
     * 利用者を物理削除します。外部キーの CASCADE 設定により、子データも自動で物理削除されます。
     */
    suspend fun deletePerson(person: Person) = personDao.delete(person)

    // --- HeightAndWeight ---
    fun getHeightAndWeightByPersonId(personId: Int): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.insert(item)
    
    suspend fun updateHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.update(item)
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.delete(item)

    // --- BpAndPulse ---
    fun getBpAndPulseByPersonId(personId: Int): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)
    
    suspend fun insertBpAndPulse(item: BpAndPulse) = bpAndPulseDao.insert(item)
    
    suspend fun updateBpAndPulse(item: BpAndPulse) = bpAndPulseDao.update(item)
    
    suspend fun deleteBpAndPulse(item: BpAndPulse) = bpAndPulseDao.delete(item)

    // --- GlucoseAndHbA1c ---
    fun getGlucoseAndHbA1cByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.insert(item)
    
    suspend fun updateGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.update(item)
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.delete(item)

    // --- ConditionAtVisit ---
    fun getConditionAtVisitByPersonId(personId: Int): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.insert(item)
    
    suspend fun updateConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.update(item)
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.delete(item)
}
