package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * (A)系統: 健康記録（身長体重、バイタル、血糖値）のデータ管理を担当するリポジトリ
 */
class HealthRepository(
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    // --- 身長・体重 ---
    fun getHeightAndWeightByPersonId(personId: String): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)

    suspend fun findHeightAndWeightAtTime(personId: String, time: java.time.Instant): HeightAndWeight? =
        heightAndWeightDao.findAtTime(personId, time)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight, featureName: String = "", operation: String = "", isUpdate: Boolean = false): String {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        heightAndWeightDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "height_and_weight_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}",
            resultType = "SUCCESS"
        )
        return itemToSave.id
    }
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight, featureName: String = "", operation: String = "") {
        heightAndWeightDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "height_and_weight_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }

    // --- 血圧・脈拍・体温 ---
    fun getBpAndPulseByPersonId(personId: String): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)

    suspend fun findBpAndPulseAtTime(personId: String, time: java.time.Instant): BpAndPulse? =
        bpAndPulseDao.findAtTime(personId, time)
    
    suspend fun insertBpAndPulse(item: BpAndPulse, featureName: String = "", operation: String = "", isUpdate: Boolean = false): String {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        bpAndPulseDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "bp_and_pulse_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}",
            resultType = "SUCCESS"
        )
        return itemToSave.id
    }
    
    suspend fun deleteBpAndPulse(item: BpAndPulse, featureName: String = "", operation: String = "") {
        bpAndPulseDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "bp_and_pulse_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }

    // --- 血糖値・HbA1c ---
    fun getGlucoseAndHbA1cByPersonId(personId: String): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)

    suspend fun findGlucoseAndHbA1cAtTime(personId: String, time: java.time.Instant): GlucoseAndHbA1c? =
        glucoseAndHbA1cDao.findAtTime(personId, time)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c, featureName: String = "", operation: String = "", isUpdate: Boolean = false): String {
        val itemToSave = item.copy(updatedAt = Instant.now(), isSynced = false)
        glucoseAndHbA1cDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "glucose_and_hba1c_db",
            actionType = if (isUpdate) "UPDATE" else "INSERT",
            affectedId = itemToSave.id,
            details = "PersonId: ${itemToSave.personId}",
            resultType = "SUCCESS"
        )
        return itemToSave.id
    }
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c, featureName: String = "", operation: String = "") {
        glucoseAndHbA1cDao.delete(item)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "glucose_and_hba1c_db",
            actionType = "DELETE",
            affectedId = item.id,
            details = "PersonId: ${item.personId}",
            resultType = "SUCCESS"
        )
    }
}
