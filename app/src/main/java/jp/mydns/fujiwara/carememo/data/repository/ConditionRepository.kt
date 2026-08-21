package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import android.net.Uri
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant

/**
 * Repository：ConditionRepository
 *
 * 【役割】
 * 利用者の「所見メモ（カテゴリB）」およびそれに紐付く「写真データ」の永続化管理を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：DB 操作に特化し、写真の未割り当て判定や救済ロジックなどの複雑な判断は Logic レイヤーへ委譲します。
 * 2. 依存方向の遵守：下位レイヤーとして、上位の Logic レイヤーに依存しない構成を維持します。
 */
class ConditionRepository(
    private val context: Context,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val auditLogRepository: AuditLogRepository? = null,
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
     * 所見メモを保存（新規登録または更新）します。
     * 保存時に更新日時をセットし、監査ログを記録します。
     *
     * @param item 保存対象の Entity
     * @param isUpdate 更新処理の場合は true、新規登録なら false
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun saveConditionAtVisit(
        item: ConditionAtVisit,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
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
     * 写真のメタデータを保存（新規登録または更新）します。
     *
     * @param item 写真情報
     * @param isUpdate 更新の場合は true、新規なら false
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun saveConditionPhoto(
        item: ConditionPhoto,
        isUpdate: Boolean,
        featureName: String = "",
        operation: String = ""
    ) {
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
     * 特定の写真を既存の所見メモに紐付け直します（未割り当て写真の再登録用）。
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
     * 物理ファイルのみ存在していた写真を、特定の利用者の記録として登録します（未割り当て写真の救済）。
     *
     * @param personId 利用者ID
     * @param conditionId 所見メモID
     * @param photoFileName 物理ファイル名
     * @param thumbnailFileName サムネイルファイル名
     * @param capturedAt 撮影日時
     * @param id 明示的に割り当てる ID（UUID）
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun adoptFileAsPhoto(
        personId: String,
        conditionId: String,
        photoFileName: String,
        thumbnailFileName: String?,
        capturedAt: Instant,
        id: String,
        featureName: String = "",
        operation: String = ""
    ) {
        val photo = ConditionPhoto(
            id = id,
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
            details = "Adopted unassigned file: $photoFileName into person: $personId",
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

    // --- 物理ファイル操作 (ViewModel からの委譲) ---

    /**
     * 所見に関連する写真を処理して保存します（物理ファイル保存）。
     * 保存後、元の一時ファイル（Uri）を削除します。
     *
     * @param uri 入力画像のUri
     * @return 保存されたメイン画像とサムネイルのファイル名のペア
     */
    suspend fun processAndSavePhoto(uri: Uri): Pair<String, String> {
        val result = ImageUtils.processAndSaveImage(context, uri)
        
        // 元の一時ファイルを削除
        if ((uri.scheme == "file") || (uri.scheme == "content")) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // 削除失敗は致命的ではないが、証跡として記録する (ID 5)
                auditLogRepository?.log(
                    featureName = "PersonCondition",
                    operation = "deleteTempPhoto",
                    tableName = "external_storage",
                    actionType = "DELETE",
                    affectedId = uri.toString(),
                    details = "Failed to delete temporary camera file: ${e.message}",
                    resultType = "IO_ERROR"
                )
            }
        }
        return result
    }

    /**
     * 写真の物理ファイルを削除します。
     *
     * @param photoFileName メイン画像ファイル名
     * @param thumbnailFileName サムネイル画像ファイル名
     */
    suspend fun deletePhotoFiles(photoFileName: String?, thumbnailFileName: String?) {
        ImageUtils.deleteImageFiles(context, photoFileName, thumbnailFileName)
    }

    /**
     * 未割り当て写真のスキャンのために、物理ファイル一覧を取得します。
     *
     * @return 写真ディレクトリ内のファイルリスト
     */
    fun getPhotoPhysicalFiles(): List<File> {
        return ImageUtils.getPhotosDirPublic(context).listFiles()?.toList() ?: emptyList()
    }

    /**
     * カメラ撮影用の一時URIを取得します。
     *
     * @return FileProvider 経由の Uri
     */
    fun getTempPhotoUri(): Uri {
        return ImageUtils.getTempPhotoUri(context)
    }
}
