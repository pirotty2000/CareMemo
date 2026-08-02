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
 * 【主な機能】
 * ・身長・体重 (HeightAndWeight) の CRUD 操作。
 * ・血圧・脈拍・体温 (BpAndPulse) の CRUD 操作。
 * ・血糖値・HbA1c (GlucoseAndHbA1c) の CRUD 操作。
 * ・健康記録の一括保存（トランザクション対応）。
 * ・各記録系統における、同一日時レコードの特定（重複チェック用）。
 * ・データ操作に応じた監査ログの自動生成。
 *
 * 【設計指針】
 * 1. 独立性：3つの記録系統はそれぞれ独立した DAO で管理するが、一貫したリポジトリインターフェースを介して操作する。
 * 2. 透明性：すべてのデータ変更操作に対して、監査ログの詳細出力を試行する。
 * 3. 同期対応：保存時には `updatedAt` の自動更新と `isSynced = false` の設定を行い、外部同期に備える。
 * 4. 原子性の保証：複数カテゴリの同時保存時はデータベーストランザクションを使用し、データの整合性を守る。
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
    
    /** 身長・体重を保存または更新します。 */
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
    
    /** バイタルレコードを保存または更新します。 */
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
    
    /** 血糖レコードを保存または更新します。 */
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
     * 複数の健康記録データを一括で保存します（トランザクション対応）。
     *
     * ブロック内のすべての保存処理は一つのトランザクションとして実行され、
     * いずれかが失敗した場合は全ての変更がロールバックされます。
     * 監査ログは案Aを採用し、各エンティティの保存ごとに個別に生成されます。
     *
     * @param items 保存対象のエンティティ（HeightAndWeight, BpAndPulse, GlucoseAndHbA1c）のリスト
     * @param featureName 監査ログ用機能名
     * @param operation 監査ログ用操作名
     */
    suspend fun insertHealthDataBatch(items: List<Any>, featureName: String, operation: String) {
        database.withTransaction {
            for (item in items) {
                when (item) {
                    is HeightAndWeight -> insertHeightAndWeight(item, featureName, operation)
                    is BpAndPulse -> insertBpAndPulse(item, featureName, operation)
                    is GlucoseAndHbA1c -> insertGlucoseAndHbA1c(item, featureName, operation)
                }
            }
        }
    }
}
