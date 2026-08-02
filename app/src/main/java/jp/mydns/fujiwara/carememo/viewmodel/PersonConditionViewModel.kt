package jp.mydns.fujiwara.carememo.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.ConditionLogic
import jp.mydns.fujiwara.carememo.logic.common.ConditionValidationResult
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * ViewModel：PersonConditionViewModel
 *
 * 【役割】
 * 利用者の所見メモ（体調記録）画面における状態管理と実行制御を担当します。
 * 日々の体調の変化や気づきをテキストと写真で記録し、時系列で管理する機能を提供します。
 *
 * 【主要な機能】
 * ・所見メモ一覧の購読、および検索クエリによる動的なフィルタリング。
 * ・所見メモの新規保存・更新・削除。
 * ・各メモに関連付けられた写真の管理（撮影、保存、削除）。
 * ・迷子写真（DBとの整合性が取れていないファイル）の特定と再紐付け機能。
 * ・バリデーション結果の UI メッセージ変換。
 *
 * 【依存している Repository】
 * ・ConditionRepository: 所見データおよび写真データの永続化と取得。
 * ・PersonRepository / PersonSummaryRepository: 利用者基本情報とサマリー情報の管理（基底クラスで使用）。
 * ・AuditLogRepository: 重要な操作（保存、削除等）の証跡を記録。
 * ・UserSettingsRepository: 共通設定の参照。
 *
 * 【設計指針】
 * 1. データの即時反映：Repository からの Flow を `safeCollect` し、DB の更新を即座に UI へ反映する。
 * 2. 整合性の担保：写真の保存時には物理ファイルと DB レコードの両方を原子的に扱い、不整合を最小限に抑える。
 * 3. ユーザー体験：保存成功時のスナックバー通知や、ナビゲーションイベントの送出により、操作感を向上させる。
 */
class PersonConditionViewModel(
    private val conditionRepository: ConditionRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
    @param:SuppressLint("StaticFieldLeak")
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context, // アプリケーションコンテキストを想定
) : PersonBaseUiStateViewModel<PersonConditionUiState, PersonConditionViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonConditionUiState(),
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonCondition"
        /** 監査ログ用：保存操作名 */
        private const val OP_SAVE = "saveRecord"
        /** 監査ログ用：削除操作名 */
        private const val OP_DELETE = "deleteRecord"
        /** 監査ログ用：写真保存操作名 */
        private const val OP_SAVE_PHOTO = "processAndSavePhoto"
        /** 監査ログ用：写真削除操作名 */
        private const val OP_DELETE_PHOTO = "deletePhoto"
        /** 監査ログ用：所見リスト購読名 */
        private const val OP_RECORDS_FLOW = "recordsFlow"
        /** 監査ログ用：写真マップ購読名 */
        private const val OP_PHOTO_MAP_FLOW = "photoMapFlow"
        /** 監査ログ用：写真リスト購読名 */
        private const val OP_PHOTOS_FLOW = "photosFlow"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_CONDITION = "condition_db"
    }

    override val featureName: String = FEATURE_NAME

    /** 所見リスト購読用 Job */
    private var recordsJob: Job? = null
    /** 特定レコードの写真リスト購読用 Job */
    private var photoJob: Job? = null
    /** 全レコードの写真有無マップ購読用 Job */
    private var photoMapJob: Job? = null

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: PersonConditionUiState, isLoading: Boolean): PersonConditionUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonConditionUiState,
        person: Person,
        summary: PersonCategorySummary?,
    ): PersonConditionUiState {
        // 利用者IDを設定し、関連データの購読を開始する
        val next = state.copy(personId = person.id)
        refreshRecords(next)
        refreshPhotoMap(next)
        return next
    }

    override fun onPrepareLoadPerson(state: PersonConditionUiState): PersonConditionUiState {
        // 利用者が切り替わる際は検索クエリをリセットし、意図しないフィルタを防止する
        return state.copy(searchQuery = "")
    }

    // --- 購読ロジック (原子的な反映) ---

    /**
     * 所見メモ一覧の購読を開始・更新します。
     * DB 内のデータ変更を検知し、フィルタリングを適用して UI へ通知します。
     */
    private fun refreshRecords(state: PersonConditionUiState) {
        val personId = state.personId ?: return
        recordsJob?.cancel()
        recordsJob = safeCollect(
            operation = OP_RECORDS_FLOW,
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_CONDITION },
            flowProvider = { conditionRepository.getConditionAtVisitByPersonId(personId) }
        ) { records ->
            updateUiState { current ->
                current.copy(
                    records = records,
                    filteredRecords = ConditionLogic.filterRecords(records, current.searchQuery)
                )
            }
        }
    }

    /**
     * 写真の存在有無マップの購読を開始します。
     * 同時に、DB またはストレージ上に孤立している写真（迷子写真）を特定します。
     */
    private fun refreshPhotoMap(state: PersonConditionUiState) {
        val personId = state.personId ?: return
        photoMapJob?.cancel()
        photoMapJob = safeCollect(
            operation = OP_PHOTO_MAP_FLOW,
            mode = CollectMode.INITIAL,
            contextBuilder = { tableName = TABLE_CONDITION },
            flowProvider = { conditionRepository.getAllPhotosByPersonIdFlow(personId) }
        ) { photos ->
            // --- 迷子写真（ファイル・DB両方）の特定 ---
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val physicalFiles = ImageUtils.getPhotosDirPublic(context).listFiles()?.toList() ?: emptyList()

            val allOrphaned = jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic.identifyOrphanedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            // この利用者が再登録可能な迷子写真をフィルタリング:
            // (A) personIdが一致しているDB孤立レコード
            // (B) 物理ファイルのみでDBレコードがないもの（どの利用者にも属し得る）
            val adoptableOrphans = allOrphaned.filter { 
                (it.personId == personId) || (it.type == jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoType.FILE_ONLY) 
            }

            updateUiState { current ->
                // レコード ID をキーに、写真の有無をマップ化
                val map = current.records.associateBy({ it.id }) { memo ->
                    photos.any { it.conditionId == memo.id }
                }
                current.copy(
                    conditionPhotoMap = map, 
                    orphanedPhotoCount = adoptableOrphans.size,
                    availableOrphanedPhotos = adoptableOrphans
                )
            }
        }
    }

    /**
     * 詳細表示対象の所見レコードを設定します。
     * 設定された ID に紐付く写真一覧の購読も開始します。
     *
     * @param id 対象レコードのID。null の場合は選択解除。
     */
    fun setSelectedConditionId(id: String?) {
        updateUiState { it.copy(selectedConditionId = id) }
        
        photoJob?.cancel()
        if (id != null) {
            photoJob = safeCollect(
                operation = OP_PHOTOS_FLOW,
                mode = CollectMode.INITIAL,
                contextBuilder = { tableName = TABLE_CONDITION },
                flowProvider = { conditionRepository.getConditionPhotosByConditionId(id) }
            ) { photos ->
                updateUiState { it.copy(currentConditionPhotos = photos) }
            }
        } else {
            updateUiState { it.copy(currentConditionPhotos = emptyList()) }
        }
    }

    // --- UI アクション ---

    /**
     * 検索クエリを更新し、リストのフィルタリングを再実行します。
     */
    fun updateSearchQuery(query: String) {
        updateUiState { current ->
            current.copy(
                searchQuery = query,
                filteredRecords = ConditionLogic.filterRecords(current.records, query)
            )
        }
    }

    /**
     * 所見メモを保存または更新します。
     *
     * バリデーション、重複チェックを経て DB へ保存し、
     * 未紐付けの一時的な写真があればこのレコードに関連付けます。
     */
    fun saveRecord(
        conditionId: String,
        title: String,
        condition: String,
        author: String,
        recordTime: Instant,
        onSuccess: (String) -> Unit = {}
    ) {
        val inputState = PersonConditionUiState(title, condition, author, recordTime)
        
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
            }
        ) {
            // 1. バリデーション実行
            val validationResult = PersonConditionLogic.validate(inputState)
            translateValidationResult(validationResult)

            // 2. Entity 構築
            val record = PersonConditionLogic.createRecord(requiredPersonId, conditionId, inputState)
            val isUpdate = !IdLogic.isNew(conditionId)

            // 3. 記録時間の重複チェック
            val existing = conditionRepository.findConditionAtTime(record.personId, record.recordTime)
            val duplicateResult = ConditionLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            // 4. DB 保存実行
            val newId = conditionRepository.insertConditionAtVisit(record, featureName, OP_SAVE, isUpdate)
            
            // 新規保存時：レコード確定前に撮影された一時的な写真をこのレコードに紐付ける
            if (!isUpdate) {
                conditionRepository.linkTemporaryPhotosToRecord(record.personId, newId, featureName, "$OP_SAVE(link)")
            }

            showSnackbar(if (isUpdate) R.string.p_cond_msg_update_success else R.string.p_cond_msg_save_success)
            sendUiEvent(UiEvent.SaveSuccess)
            
            val finalId = if (isUpdate) record.id else newId
            setSelectedConditionId(finalId)
            onSuccess(finalId)
        }
    }

    /** 所見メモ固有のバリデーション結果を例外に変換してスローします。 */
    private fun translateValidationResult(result: PersonConditionValidationResult) {
        if (result == PersonConditionValidationResult.SUCCESS) return
        val messageRes = when (result) {
            PersonConditionValidationResult.EMPTY_CONDITION -> R.string.p_cond_err_empty_condition
            PersonConditionValidationResult.EMPTY_AUTHOR -> R.string.p_cond_err_empty_author
            PersonConditionValidationResult.CONDITION_TOO_LONG -> R.string.p_cond_err_condition_too_long
            PersonConditionValidationResult.TITLE_TOO_LONG -> R.string.p_cond_err_title_too_long
            PersonConditionValidationResult.INVALID_TIME -> R.string.main_err_edit_invalid_birthday
            else -> R.string.common_error_save
        }
        throw AppValidationException(R.string.common_error_title_save, messageRes, emptyList(), "Validation failed: $result")
    }

    /** 共通バリデーション（重複チェック等）の結果を例外に変換してスローします。 */
    private fun translateValidationResult(result: ConditionValidationResult) {
        if (result == ConditionValidationResult.SUCCESS) return
        val messageRes = if (result == ConditionValidationResult.DUPLICATE_TIME) R.string.common_err_duplicate_blocked_simple else R.string.common_error_save
        throw AppValidationException(R.string.common_error_title_save, messageRes, emptyList(), "Validation failed: $result")
    }

    /** 所見レコードを物理削除します。 */
    fun deleteRecord(record: ConditionAtVisit) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = record.id
            }
        ) {
            conditionRepository.deleteConditionAtVisit(record, featureName, OP_DELETE)
            showSnackbar(R.string.p_cond_msg_delete_success)
        }
    }

    /** 写真撮影完了時のナビゲーションイベントを送出します。 */
    fun onPhotoCaptured(uri: Uri, conditionId: String) {
        sendViewEvent(PersonConditionViewEvent.NavigateToPhotoPreview(uri, requiredPersonId, conditionId))
    }

    /**
     * 選択された迷子写真を現在の所見レコードに再紐付けします。
     *
     * @param conditionId 紐付け先の所見レコードID
     * @param photoInfo 再紐付け対象の迷子写真情報
     */
    fun reattachOrphanedPhoto(conditionId: String, photoInfo: jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) {
        if (IdLogic.isNew(conditionId)) return

        safeLaunch(
            operation = "reattachOrphanedPhoto",
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
            }
        ) {
            if (photoInfo.photoId != null) {
                // DB にレコードが残っている場合 (一時データまたは整合性エラー)
                conditionRepository.reattachPhotoToRecord(photoInfo.photoId, conditionId, featureName, "reattachOrphanedPhoto")
            } else {
                // 物理ファイルのみ存在する場合、新規レコードとして DB に登録
                conditionRepository.adoptFileAsPhoto(
                    personId = requiredPersonId,
                    conditionId = conditionId,
                    photoFileName = photoInfo.photoFileName,
                    thumbnailFileName = photoInfo.thumbnailFileName,
                    capturedAt = photoInfo.capturedAt,
                    featureName = featureName,
                    operation = "adoptFileAsPhoto"
                )
            }
            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

    /**
     * 撮影された写真を加工（リサイズ・サムネイル作成）して保存し、DB に登録します。
     * 保存後は元の一時ファイルを削除します。
     */
    fun processAndSavePhoto(context: Context, uri: Uri, conditionId: String, caption: String) {
        safeLaunch(
            operation = OP_SAVE_PHOTO,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
                errorMessageRes = R.string.p_cond_err_photo_process_failure
            }
        ) {
            // 画像の加工と保存
            val (photoName, thumbName) = ImageUtils.processAndSaveImage(context, uri)
            
            // DB レコードの作成
            val photo = ConditionPhoto(
                conditionId = conditionId,
                personId = requiredPersonId,
                photoFileName = photoName,
                thumbnailFileName = thumbName,
                capturedAt = Instant.now(),
                caption = caption
            )
            conditionRepository.insertConditionPhoto(photo, featureName, OP_SAVE_PHOTO)
            
            // 一時ファイルのクリーンアップ
            if ((uri.scheme == "file") || (uri.scheme == "content")) {
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

    /** 写真を DB レコードおよび物理ファイルから削除します。 */
    fun deletePhoto(context: Context, photo: ConditionPhoto) {
        safeLaunch(
            operation = OP_DELETE_PHOTO,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = photo.id
            }
        ) {
            conditionRepository.deleteConditionPhotoById(photo.id, photo.personId, featureName, OP_DELETE_PHOTO)
            ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
            showSnackbar(R.string.p_cond_msg_photo_delete_success)
        }
    }

    /** 写真関連のエラーを UI に通知します。 */
    fun notifyPhotoError(message: String) {
        updateUiState { it.copy(errorMessage = message) }
        showError(message)
    }

    /** 利用者に紐付く全ての写真データを取得します。 */
    suspend fun getAllPhotosForPerson(): List<ConditionPhoto> {
        return conditionRepository.getAllPhotosByPersonId(requiredPersonId)
    }

    /**
     * PersonConditionViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonConditionViewModel(
                conditionRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository,
                context
            ) as T
        }
    }
}
