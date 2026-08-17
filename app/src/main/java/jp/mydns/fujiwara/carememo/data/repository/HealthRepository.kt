package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：HealthRepository
 *
 * 【役割】
 * 利用者の「健康記録」に関連する3つのデータ系統（身長体重、バイタル、血糖値・HbA1c）の永続化管理を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：本クラスは DB (DAO) との入出力に専念し、ビジネスロジックや業務判断を含みません。
 * 2. 依存方向の遵守：UI レイヤーや Logic レイヤーへの依存を排除し、データ層の独立性を保ちます。
 */
class HealthRepository(
    private val database: AppDatabase,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    // --------------------------------------------------------------------------------------------
    // 身長・体重 (HeightAndWeight)
    // --------------------------------------------------------------------------------------------

    /** 利用者の身長・体重履歴を Flow で取得します。 */
    fun getHeightAndWeightByPersonId(personId: String): Flow<List<HeightAndWeight>> = 
        heightAndWeightDao.getByPersonId(personId)

    /** 指定日時の身長・体重レコードを検索します。 */
    suspend fun findHeightAndWeightAtTime(personId: String, time: Instant): HeightAndWeight? =
        heightAndWeightDao.findAtTime(personId, time)
    
    /** 身長・体重を保存（新規登録または更新）します。 */
    suspend fun saveHeightAndWeight(
        item: HeightAndWeight,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
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
    }
    
    /** 身長・体重レコードを物理削除します。 */
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

    // --------------------------------------------------------------------------------------------
    // 血圧・脈拍・体温 (BpAndPulse)
    // --------------------------------------------------------------------------------------------

    /** 利用者のバイタル履歴を Flow で取得します。 */
    fun getBpAndPulseByPersonId(personId: String): Flow<List<BpAndPulse>> = 
        bpAndPulseDao.getByPersonId(personId)

    /** 指定日時のバイタルレコードを検索します。 */
    suspend fun findBpAndPulseAtTime(personId: String, time: Instant): BpAndPulse? =
        bpAndPulseDao.findAtTime(personId, time)
    
    /** バイタルレコードを保存（新規登録または更新）します。 */
    suspend fun saveBpAndPulse(
        item: BpAndPulse,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
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
    }
    
    /** バイタルレコードを物理削除します。 */
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

    // --------------------------------------------------------------------------------------------
    // 血糖値・HbA1c (GlucoseAndHbA1c)
    // --------------------------------------------------------------------------------------------

    /** 利用者の血糖値・HbA1c履歴を Flow で取得します。 */
    fun getGlucoseAndHbA1cByPersonId(personId: String): Flow<List<GlucoseAndHbA1c>> = 
        glucoseAndHbA1cDao.getByPersonId(personId)

    /** 指定日時の血糖レコードを検索します。 */
    suspend fun findGlucoseAndHbA1cAtTime(personId: String, time: Instant): GlucoseAndHbA1c? =
        glucoseAndHbA1cDao.findAtTime(personId, time)
    
    /** 血糖レコードを保存（新規登録または更新）します。 */
    suspend fun saveGlucoseAndHbA1c(
        item: GlucoseAndHbA1c,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
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
    }
    
    /** 血糖レコードを物理削除します。 */
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

    /**
     * 健康記録データを保存（新規登録または更新）します。
     * 型判定を内部で行い、適切な DAO メソッドを呼び出します。
     */
    suspend fun saveHistoryRecord(
        item: HistoryRecord,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
        when (item) {
            is HeightAndWeight -> saveHeightAndWeight(item, isUpdate, featureName, operation)
            is BpAndPulse -> saveBpAndPulse(item, isUpdate, featureName, operation)
            is GlucoseAndHbA1c -> saveGlucoseAndHbA1c(item, isUpdate, featureName, operation)
            else -> throw IllegalArgumentException("Unsupported health record type: ${item::class.java.simpleName}")
        }
    }

    /**
     * 健康記録データを物理削除します。
     */
    suspend fun deleteHistoryRecord(item: HistoryRecord, featureName: String = "", operation: String = "") {
        when (item) {
            is HeightAndWeight -> deleteHeightAndWeight(item, featureName, operation)
            is BpAndPulse -> deleteBpAndPulse(item, featureName, operation)
            is GlucoseAndHbA1c -> deleteGlucoseAndHbA1c(item, featureName, operation)
            else -> throw IllegalArgumentException("Unsupported health record type: ${item::class.java.simpleName}")
        }
    }

    /**
     * 指定されたカテゴリと日時の既存レコードを検索します。
     */
    suspend fun findHistoryRecordAtTime(category: Category, personId: String, time: Instant): HistoryRecord? {
        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> findHeightAndWeightAtTime(personId, time)
            Category.BP_AND_PULSE -> findBpAndPulseAtTime(personId, time)
            Category.GLUCOSE_AND_HBA1C -> findGlucoseAndHbA1cAtTime(personId, time)
            else -> null
        }
    }

    /**
     * 複数の健康記録データを一括で保存します（トランザクション対応）。
     *
     * ブロック内のすべての保存処理は一つのトランザクションとして実行され、
     * いずれかが失敗した場合は全ての変更がロールバックされます。
     *
     * @param items 保存対象のエンティティ（HeightAndWeight, BpAndPulse, GlucoseAndHbA1c）のリスト
     * @param featureName 監査ログ用機能名
     * @param operation 監査ログ用操作名
     */
    suspend fun saveHealthDataBatch(items: List<Any>, featureName: String, operation: String) {
        database.withTransaction {
            for (item in items) {
                if (item is HistoryRecord) {
                    saveHistoryRecord(item, isUpdate = false, featureName, operation)
                }
            }
        }
    }
}
