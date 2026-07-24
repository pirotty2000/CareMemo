package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * (B)系統: 所見メモおよび写真のデータ管理を担当するリポジトリ
 */
class ConditionRepository(
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    fun getConditionAtVisitByPersonId(personId: String): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)

    suspend fun findConditionAtTime(personId: String, time: java.time.Instant): ConditionAtVisit? =
        conditionAtVisitDao.findAtTime(personId, time)
    
    suspend fun insertConditionAtVisit(item: ConditionAtVisit, featureName: String = "", operation: String = "", isUpdate: Boolean = false): String {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        conditionAtVisitDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_at_visit_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}, Title: ${itemToSave.title}",
            resultType = "SUCCESS"
        )
        return itemToSave.id
    }
    
    suspend fun deleteConditionAtVisit(item: ConditionAtVisit, featureName: String = "", operation: String = "") {
        conditionAtVisitDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_at_visit_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }

    // --- 写真 ---
    fun getConditionPhotosByConditionId(conditionId: String): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getByConditionId(conditionId)

    suspend fun insertConditionPhoto(item: ConditionPhoto, featureName: String = "", operation: String = "", isUpdate: Boolean = false): String {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        conditionPhotoDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}, ConditionId: ${itemToSave.conditionId}",
            resultType = "SUCCESS"
        )
        return itemToSave.id
    }

    suspend fun linkTemporaryPhotosToRecord(personId: String, newConditionId: String, featureName: String = "", operation: String = "") {
        conditionPhotoDao.linkTemporaryPhotosToRecord(personId, newConditionId)
        // linkTemporaryPhotosToRecord は DAO 側で SQL で直接更新されるため、
        // 厳密には DAO 内で updatedAt も更新するようにすべきだが、
        // 現状の設計では Repository でのログ出力に留める。
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

    suspend fun deleteConditionPhotoById(id: String, personId: String = "", featureName: String = "", operation: String = "") {
        conditionPhotoDao.deleteById(id)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = "DELETE",
            affectedId = id,
            details = "PersonId: $personId",
            resultType = "SUCCESS"
        )
    }
    
    suspend fun getAllPhotosByPersonId(personId: String) = conditionPhotoDao.getAllByPersonId(personId)

    fun getAllPhotosByPersonIdFlow(personId: String): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getAllByPersonIdFlow(personId)

    fun getPersonIdsByConditionKeyword(query: String): Flow<List<String>> =
        conditionAtVisitDao.getPersonIdsByConditionKeyword(query)
}
