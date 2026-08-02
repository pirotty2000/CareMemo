package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository：ConditionRepository
 *
 * 【役割】
 * 利用者の「所見メモ（カテゴリB）」およびそれに紐付く「写真データ」の永続化管理を担当します。
 * データベースへのアクセス（DAO）と操作履歴の記録（AuditLogRepository）を統合したインターフェースを提供します。
 *
 * 【主な機能】
 * ・所見メモの CRUD 操作、および同一日時レコードの検索。
 * ・写真メタデータの管理（一時保存写真の紐付け、迷子写真の救済登録を含む）。
 * ・キーワードによる所見内容の検索と、該当する利用者IDの抽出。
 * ・データ操作に応じた監査ログの自動生成。
 *
 * 【設計指針】
 * 1. 透明性：すべてのデータ変更操作（挿入・更新・削除）に対して、監査ログの詳細出力を試行する。
 * 2. 整合性：写真の追加時には親レコードとの紐付け状態を適切に管理し、孤立データの発生を最小限に抑える。
 * 3. 同期対応：保存時には `updatedAt` の自動更新と `isSynced = false` の設定を行い、外部同期に備える。
 */
class ConditionRepository(
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * 特定の利用者の所見メモ一覧を Flow で取得します。
     *
     * @param personId 利用者ID
     * @return 所見メモリストを通知する Flow
     */
    fun getConditionAtVisitByPersonId(personId: String): Flow<List<ConditionAtVisit>> = 
        conditionAtVisitDao.getByPersonId(personId)

    /**
     * 特定の利用者の、指定された日時に完全に一致するレコードを検索します。
     * 重複チェックや、既存レコードの特定に使用します。
     *
     * @param personId 利用者ID
     * @param time 記録日時
     * @return 該当するレコード。存在しない場合は null。
     */
    suspend fun findConditionAtTime(personId: String, time: Instant): ConditionAtVisit? =
        conditionAtVisitDao.findAtTime(personId, time)
    
    /**
     * 所見メモを保存または更新します。
     * 保存時に更新日時をセットし、監査ログを記録します。
     *
     * @param item 保存対象の Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     * @param isUpdate 更新処理の場合は true
     * @return 保存されたデータのID
     */
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
    
    /**
     * 所見メモを物理削除します。あわせて監査ログを記録します。
     *
     * @param item 削除対象の Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
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

    // --- 写真管理 ---

    /**
     * 特定の所見メモに紐付いている写真リストを取得します。
     *
     * @param conditionId 所見メモのID
     * @return 写真情報のリストを通知する Flow
     */
    fun getConditionPhotosByConditionId(conditionId: String): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getByConditionId(conditionId)

    /**
     * 写真のメタデータを保存します。
     *
     * @param item 写真情報
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     * @param isUpdate 更新の場合は true
     * @return 保存された写真データのID
     */
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

    /**
     * 一時保存状態（親の所見メモIDが未確定）の写真を、特定の記録に紐付けます。
     * 新規所見メモの保存完了直後に実行されます。
     *
     * @param personId 利用者ID
     * @param newConditionId 確定した所見メモのID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun linkTemporaryPhotosToRecord(personId: String, newConditionId: String, featureName: String = "", operation: String = "") {
        conditionPhotoDao.linkTemporaryPhotosToRecord(personId, newConditionId)
        
        // linkTemporaryPhotosToRecord は DAO 側で SQL で一括更新される。
        // 現状、リポジトリ層でログ出力を担当。
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
     * 特定の写真を既存の所見メモに紐付け直します（迷子写真の再登録用）。
     *
     * @param photoId 対象の写真ID
     * @param conditionId 紐付け先の所見メモID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
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
     *
     * @param personId 利用者ID
     * @param conditionId 所見メモID
     * @param photoFileName 物理ファイル名
     * @param thumbnailFileName サムネイルファイル名
     * @param capturedAt 撮影日時
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
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

    /**
     * 写真のメタデータをID指定で物理削除します。
     *
     * @param id 写真ID
     * @param personId ログ記録用の利用者ID
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
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
    
    /**
     * 利用者に関連付けられたすべての写真を一括取得します。
     *
     * @param personId 利用者ID
     * @return 写真リスト（List形式）
     */
    suspend fun getAllPhotosByPersonId(personId: String) = conditionPhotoDao.getAllByPersonId(personId)

    /**
     * 利用者に関連付けられたすべての写真を Flow で取得します。
     *
     * @param personId 利用者ID
     * @return 写真リストを通知する Flow
     */
    fun getAllPhotosByPersonIdFlow(personId: String): Flow<List<ConditionPhoto>> = 
        conditionPhotoDao.getAllByPersonIdFlow(personId)

    /**
     * 所見の内容（タイトルまたは本文）に含まれるキーワードから、該当する利用者のID一覧を取得します。
     * 利用者一覧のフィルタリングに使用します。
     *
     * @param query 検索キーワード
     * @return 条件に合致する利用者IDのリストを通知する Flow
     */
    fun getPersonIdsByConditionKeyword(query: String): Flow<List<String>> =
        conditionAtVisitDao.getPersonIdsByConditionKeyword(query)

    // --- メンテナンス・システム用 ---

    /** 保存されているすべての写真メタデータを取得します。 */
    suspend fun getAllConditionPhotosRaw(): List<ConditionPhoto> = conditionPhotoDao.getAllRaw()
    /** 保存されているすべての所見メモのIDセットを取得します。 */
    suspend fun getAllConditionAtVisitIds(): Set<String> = conditionAtVisitDao.getAllIds().toSet()
}
