package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

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
    fun getHeightAndWeightByPersonId(personId: Int): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight, screenName: String = "", operation: String = "") {
        val id = heightAndWeightDao.insert(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "height_and_weight_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight, screenName: String = "", operation: String = "") {
        heightAndWeightDao.delete(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "height_and_weight_db",
            actionType = "DELETE",
            affectedId = item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }

    // --- 血圧・脈拍・体温 ---
    fun getBpAndPulseByPersonId(personId: Int): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)
    
    suspend fun insertBpAndPulse(item: BpAndPulse, screenName: String = "", operation: String = "") {
        val id = bpAndPulseDao.insert(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "bp_and_pulse_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }
    
    suspend fun deleteBpAndPulse(item: BpAndPulse, screenName: String = "", operation: String = "") {
        bpAndPulseDao.delete(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "bp_and_pulse_db",
            actionType = "DELETE",
            affectedId = item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }

    // --- 血糖値・HbA1c ---
    fun getGlucoseAndHbA1cByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c, screenName: String = "", operation: String = "") {
        val id = glucoseAndHbA1cDao.insert(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "glucose_and_hba1c_db",
            actionType = if (item.id == 0) "INSERT" else "UPDATE",
            affectedId = if (item.id == 0) id.toString() else item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c, screenName: String = "", operation: String = "") {
        glucoseAndHbA1cDao.delete(item)
        auditLogRepository?.log(
            screenName = screenName,
            operation = operation,
            tableName = "glucose_and_hba1c_db",
            actionType = "DELETE",
            affectedId = item.id.toString(),
            details = "PersonId: ${item.personId}"
        )
    }
}
