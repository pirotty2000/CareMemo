package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * (B)系統: 所見メモおよび写真のデータ管理を担当するリポジトリ
 */
class ConditionRepository(
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    fun getConditionAtVisitByPersonId(personId: Int): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)

    suspend fun findConditionAtTime(personId: Int, time: java.time.Instant): ConditionAtVisit? =
        conditionAtVisitDao.findAtTime(personId, time)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit, featureName: String = "", operation: String = ""): Long {
        val id = conditionAtVisitDao.insert(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_at_visit_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}, Title: ${item.title}",
            resultType = "SUCCESS"
        )
        return id
    }
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit, featureName: String = "", operation: String = "") {
        conditionAtVisitDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_at_visit_db",
            actionType = "DELETE",
            affectedId = item.id.toString(),
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }

    // --- 写真 ---
    fun getConditionPhotosByConditionId(conditionId: Int): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getByConditionId(conditionId)

    suspend fun insertConditionPhoto(item: ConditionPhoto, featureName: String = "", operation: String = ""): Long {
        val id = conditionPhotoDao.insert(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}, ConditionId: ${item.conditionId}",
            resultType = "SUCCESS"
        )
        return id
    }

    suspend fun linkTemporaryPhotosToRecord(personId: Int, newConditionId: Int, featureName: String = "", operation: String = "") {
        conditionPhotoDao.linkTemporaryPhotosToRecord(personId, newConditionId)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = "UPDATE",
            affectedId = "person:$personId",
            details = "Linked temporary photos to conditionId: $newConditionId",
            resultType = "SUCCESS"
        )
    }

    suspend fun deleteConditionPhotoById(id: Int, personId: Int = 0, featureName: String = "", operation: String = "") {
        conditionPhotoDao.deleteById(id)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = "DELETE",
            affectedId = id.toString(),
            details = "PersonId: $personId",
            resultType = "SUCCESS"
        )
    }
    
    suspend fun getAllPhotosByPersonId(personId: Int) = conditionPhotoDao.getAllByPersonId(personId)

    fun getAllPhotosByPersonIdFlow(personId: Int): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getAllByPersonIdFlow(personId)

    fun getPersonIdsByConditionKeyword(query: String): Flow<List<Int>> =
        conditionAtVisitDao.getPersonIdsByConditionKeyword(query)
}
