package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.flow.Flow

/**
 * Repository：DeleteOrRestorePersonRepository
 *
 * 【役割】
 * 利用者の「利用終了（論理削除）」、「復帰（論理削除解除）」、および「完全抹消（物理削除）」に関連する横断的な操作を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：複数テーブルを横断した削除・復元操作の永続化に特化し、対象の選定やバリデーションは Logic 層へ委譲します。
 * 2. 依存方向の遵守：下位レイヤーとして、上位の Logic レイヤーに依存しない構成を維持します。
 */
class DeleteOrRestorePersonRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val emergencyContactDao: EmergencyContactDao,
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
            val timestamp = java.time.Instant.now().toEpochMilli()
            
            // 全テーブルの論理削除フラグを更新
            personDao.logicalDelete(personId, timestamp)
            heightAndWeightDao.logicalDeleteByPersonId(personId, timestamp)
            bpAndPulseDao.logicalDeleteByPersonId(personId, timestamp)
            glucoseAndHbA1cDao.logicalDeleteByPersonId(personId, timestamp)
            conditionAtVisitDao.logicalDeleteByPersonId(personId, timestamp)
            conditionPhotoDao.logicalDeleteByPersonId(personId, timestamp)
            medicationRecordDao.logicalDeleteByPersonId(personId, timestamp)
            emergencyContactDao.logicalDeleteByPersonId(personId, timestamp)

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
            emergencyContactDao.restoreByPersonId(personId)

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
        // 1. 物理画像ファイルの削除
        val photos = conditionPhotoDao.getAllByPersonId(personId)
        photos.forEach { photo ->
            try {
                ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
            } catch (e: Exception) {
                // ファイル削除の失敗は DB 抹消を妨げないように抑制するが、証跡として記録する (ID 6)
                auditLogRepository?.log(
                    featureName = "DeleteOrRestorePerson",
                    operation = "permanentlyDeletePerson(file)",
                    tableName = "external_storage",
                    actionType = "PERMANENT_DELETE",
                    affectedId = photo.id,
                    details = "Failed to delete physical photo file: ${e.message}",
                    resultType = "IO_ERROR"
                )
            }
        }

        // 2. DB レコードの物理削除
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
        database.withTransaction {
            // 1. 論理削除済みの利用者を取得
            val endedPersons = personDao.getDeletedPersonsRaw()

            // 2. 各利用者に紐付く物理画像ファイルを削除
            for (person in endedPersons) {
                val photos = conditionPhotoDao.getAllByPersonId(person.id)
                for (photo in photos) {
                    try {
                        ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
                    } catch (e: Exception) {
                        // ignore & record (ID 6)
                        auditLogRepository?.log(
                            featureName = "DeleteOrRestorePerson",
                            operation = "deleteAllEndedPersons(file)",
                            tableName = "external_storage",
                            actionType = "CLEAR_ALL_ARCHIVED",
                            affectedId = photo.id,
                            details = "Failed to delete physical photo file during batch delete: ${e.message}",
                            resultType = "IO_ERROR"
                        )
                    }
                }
            }

            // 3. DB レコードを一括物理削除
            personDao.deleteEndedPersons()
        }

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
