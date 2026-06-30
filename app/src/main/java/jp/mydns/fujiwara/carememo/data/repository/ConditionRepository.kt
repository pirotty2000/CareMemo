package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * (B)系統: 所見メモおよび写真のデータ管理を担当するリポジトリ
 */
class ConditionRepository(
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao
) {
    fun getConditionAtVisitByPersonId(personId: Int): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.insert(item)
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit) = conditionAtVisitDao.delete(item)

    // --- 写真 ---
    fun getConditionPhotosByConditionId(conditionId: Int): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getByConditionId(conditionId)

    suspend fun insertConditionPhoto(item: ConditionPhoto) = conditionPhotoDao.insert(item)

    suspend fun deleteConditionPhotoById(id: Int) = conditionPhotoDao.deleteById(id)
    
    suspend fun getAllPhotosByPersonId(personId: Int) = conditionPhotoDao.getAllByPersonId(personId)

    fun getAllPhotosByPersonIdFlow(personId: Int): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getAllByPersonIdFlow(personId)

    fun getPersonIdsByConditionKeyword(query: String): Flow<List<Int>> =
        conditionAtVisitDao.getPersonIdsByConditionKeyword(query)
}
