package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * (A)系統: 健康記録（身長体重、バイタル、血糖値）のデータ管理を担当するリポジトリ
 */
class HealthRepository(
    private val database: AppDatabase,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao
) {
    /**
     * 健康記録（A系統）をまとめて保存します。
     * トランザクションにより、すべて成功するか、すべて失敗（ロールバック）することを保証します。
     */
    suspend fun insertHealthBatch(
        heightAndWeight: HeightAndWeight?,
        bpAndPulse: BpAndPulse?,
        glucoseAndHbA1c: GlucoseAndHbA1c?
    ) {
        database.withTransaction {
            heightAndWeight?.let { heightAndWeightDao.insert(it) }
            bpAndPulse?.let { bpAndPulseDao.insert(it) }
            glucoseAndHbA1c?.let { glucoseAndHbA1cDao.insert(it) }
        }
    }
    // --- 身長・体重 ---
    fun getHeightAndWeightByPersonId(personId: Int): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)
    
    suspend fun insertHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.insert(item)
    
    suspend fun deleteHeightAndWeight(item: HeightAndWeight) = heightAndWeightDao.delete(item)

    // --- 血圧・脈拍・体温 ---
    fun getBpAndPulseByPersonId(personId: Int): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)
    
    suspend fun insertBpAndPulse(item: BpAndPulse) = bpAndPulseDao.insert(item)
    
    suspend fun deleteBpAndPulse(item: BpAndPulse) = bpAndPulseDao.delete(item)

    // --- 血糖値・HbA1c ---
    fun getGlucoseAndHbA1cByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)
    
    suspend fun insertGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.insert(item)
    
    suspend fun deleteGlucoseAndHbA1c(item: GlucoseAndHbA1c) = glucoseAndHbA1cDao.delete(item)
}
