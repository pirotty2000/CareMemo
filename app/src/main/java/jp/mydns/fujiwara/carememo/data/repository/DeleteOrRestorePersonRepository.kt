package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository：DeleteOrRestorePersonRepository
 *
 * 【役割】
 * 利用者の「利用終了（論理削除）」、「復帰（論理削除解除）」、および「完全抹消（物理削除）」に関連する横断的な操作を担当します。
 * 利用者本人だけでなく、紐付くすべての臨床データ（健康記録、所見メモ、服薬管理等）の一貫性を保ちながら削除・復元を行います。
 *
 * 【主な機能】
 * ・論理削除済み（アーカイブ）利用者一覧の取得。
 * ・利用者と全関連データのカスケード論理削除。
 * ・利用者と全関連データのカスケード復元（アーカイブ復帰）。
 * ・特定利用者、または全アーカイブ対象者の物理削除（完全消去）。
 * ・各重要操作に対する監査ログの記録。
 *
 * 【設計指針】
 * 1. 一貫性の保証：削除や復帰は Room のトランザクション内で実行し、本人とデータの不整合を防ぐ。
 * 2. データの保護：誤操作に備え、一次的な削除は `deleted_at` カラムの更新（論理削除）として実装し、
 *    物理削除はユーザーが明示的に「抹消」を選択した場合のみ実行する。
 */
class DeleteOrRestorePersonRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * アーカイブ（論理削除）されている利用者一覧を取得します。
     *
     * @return 削除済み利用者リストを通知する Flow
     */
    fun getArchivedPersons(): Flow<List<Person>> = personDao.getDeletedPersons()

    /**
     * 利用者を論理削除し、紐づくすべての記録も同時に論理削除します（カスケード論理削除）。
     *
     * 内部でトランザクションを開始し、すべてのテーブルの `deleted_at` カラムに
     * 同一のタイムスタンプを設定します。
     *
     * @param personId 対象の利用者ID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun logicalDeletePerson(personId: String, featureName: String = "", operation: String = "") {
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            
            // 全テーブルの論理削除フラグを更新
            personDao.logicalDelete(personId, timestamp)
            heightAndWeightDao.logicalDeleteByPersonId(personId, timestamp)
            bpAndPulseDao.logicalDeleteByPersonId(personId, timestamp)
            glucoseAndHbA1cDao.logicalDeleteByPersonId(personId, timestamp)
            conditionAtVisitDao.logicalDeleteByPersonId(personId, timestamp)
            conditionPhotoDao.logicalDeleteByPersonId(personId, timestamp)
            medicationRecordDao.logicalDeleteByPersonId(personId, timestamp)

            auditLogRepository?.log(
                featureName = featureName,
                operation = operation,
                tableName = "person_db",
                actionType = "LOGICAL_DELETE",
                affectedId = personId,
                details = "Cascade logical delete for person and all related records",
                resultType = "SUCCESS"
            )
        }
    }

    /**
     * 論理削除された利用者と、紐づくすべての記録を復帰させます。
     *
     * 内部でトランザクションを開始し、すべてのテーブルの `deleted_at` カラムを null に戻します。
     *
     * @param personId 対象の利用者ID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun restorePerson(personId: String, featureName: String = "", operation: String = "") {
        database.withTransaction {
            // 全テーブルの論理削除フラグを解除
            personDao.restore(personId)
            heightAndWeightDao.restoreByPersonId(personId)
            bpAndPulseDao.restoreByPersonId(personId)
            glucoseAndHbA1cDao.restoreByPersonId(personId)
            conditionAtVisitDao.restoreByPersonId(personId)
            conditionPhotoDao.restoreByPersonId(personId)
            medicationRecordDao.restoreByPersonId(personId)

            auditLogRepository?.log(
                featureName = featureName,
                operation = operation,
                tableName = "person_db",
                actionType = "RESTORE",
                affectedId = personId,
                details = "Restore person and all related records",
                resultType = "SUCCESS"
            )
        }
    }

    /**
     * 指定された利用者を完全に抹消（物理削除）します。
     * ※この操作は元に戻せません。
     *
     * @param personId 対象の利用者ID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun permanentlyDeletePerson(personId: String, featureName: String = "", operation: String = "") {
        personDao.deletePersonPhysically(personId)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "PERMANENT_DELETE",
            affectedId = personId,
            resultType = "SUCCESS"
        )
    }

    /**
     * 全ての利用終了者（論理削除された利用者）と、そのすべての記録を物理削除します。
     * ※この操作は元に戻せません。
     *
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun deleteAllEndedPersons(featureName: String = "", operation: String = "") {
        personDao.deleteEndedPersons()
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "CLEAR_ALL_ARCHIVED",
            affectedId = "all",
            details = "Permanently deleted all logical-deleted persons",
            resultType = "SUCCESS"
        )
    }

    /**
     * 指定された複数の利用者を一括で復帰させます（トランザクション対応）。
     *
     * @param personIds 対象の利用者IDリスト
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun restorePersonsBatch(personIds: List<String>, featureName: String = "", operation: String = "") {
        database.withTransaction {
            personIds.forEach { id ->
                restorePerson(id, featureName, operation)
            }
        }
    }

    /**
     * 指定された複数の利用者を一括で完全に抹消（物理削除）します（トランザクション対応）。
     *
     * @param personIds 対象の利用者IDリスト
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun permanentlyDeletePersonsBatch(personIds: List<String>, featureName: String = "", operation: String = "") {
        database.withTransaction {
            personIds.forEach { id ->
                permanentlyDeletePerson(id, featureName, operation)
            }
        }
    }
}
