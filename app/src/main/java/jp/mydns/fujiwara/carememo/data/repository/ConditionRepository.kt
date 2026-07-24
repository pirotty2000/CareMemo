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

    suspend fun findConditionAtTime(personId: String, time: Instant): ConditionAtVisit? =
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

    /**
     * 特定の写真を所見メモに紐付けます（迷子写真の再登録用）。
     */
    suspend fun reattachPhotoToRecord(photoId: String, conditionId: String, featureName: String = "", operation: String = "") {
        conditionPhotoDao.updateConditionId(photoId, conditionId)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = "UPDATE",
            affectedId = photoId,
            details = "Re-attached photo to conditionId: $conditionId",
            resultType = "SUCCESS"
        )
    }

    /**
     * 物理ファイルのみ存在していた写真を、特定の利用者の記録として登録します（迷子写真の救済）。
     */
    suspend fun adoptFileAsPhoto(
        personId: String,
        conditionId: String,
        photoFileName: String,
        thumbnailFileName: String?,
        capturedAt: Instant,
        featureName: String = "",
        operation: String = ""
    ) {
        val photo = ConditionPhoto(
            conditionId = conditionId,
            personId = personId,
            photoFileName = photoFileName,
            thumbnailFileName = thumbnailFileName ?: "",
            capturedAt = capturedAt,
            updatedAt = Instant.now()
        )
        conditionPhotoDao.insert(photo)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "condition_photo_db",
            actionType = "INSERT",
            affectedId = photo.id,
            details = "Adopted orphaned file: $photoFileName into person: $personId",
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

    // --- メンテナンス用 ---
    suspend fun getAllConditionPhotosRaw(): List<ConditionPhoto> = conditionPhotoDao.getAllRaw()
    suspend fun getAllConditionAtVisitIds(): Set<String> = conditionAtVisitDao.getAllIds().toSet()
}
