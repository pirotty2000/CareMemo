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
 * 所見メモ（体調記録）固有のロジック(B系統)を扱う ViewModel。
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
        private const val FEATURE_NAME = "PersonCondition"
        private const val OP_SAVE = "saveRecord"
        private const val OP_DELETE = "deleteRecord"
        private const val OP_SAVE_PHOTO = "processAndSavePhoto"
        private const val OP_DELETE_PHOTO = "deletePhoto"
        private const val OP_RECORDS_FLOW = "recordsFlow"
        private const val OP_PHOTO_MAP_FLOW = "photoMapFlow"
        private const val OP_PHOTOS_FLOW = "photosFlow"
        private const val TABLE_CONDITION = "condition_db"
    }

    override val featureName: String = FEATURE_NAME

    private var recordsJob: Job? = null
    private var photoJob: Job? = null
    private var photoMapJob: Job? = null

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: PersonConditionUiState, isLoading: Boolean): PersonConditionUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun getPersonId(state: PersonConditionUiState): String? = state.personId

    override fun updateWithPersonData(
        state: PersonConditionUiState,
        person: Person,
        summary: PersonCategorySummary?,
    ): PersonConditionUiState {
        val next = state.copy(personId = person.id)
        refreshRecords(next)
        refreshPhotoMap(next)
        return next
    }

    override fun onPrepareLoadPerson(state: PersonConditionUiState): PersonConditionUiState {
        return state.copy(searchQuery = "") // ロード開始時に検索クエリをリセット
    }

    // --- 購読ロジック (原子的な反映) ---

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

            // この利用者が再登録可能なもの:
            // (A) personIdが一致しているDB孤立レコード
            // (B) 物理ファイルのみでDBレコードがないもの
            val adoptableOrphans = allOrphaned.filter { 
                (it.personId == personId) || (it.type == jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoType.FILE_ONLY) 
            }

            updateUiState { current ->
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
     */
    fun saveRecord(
        conditionId: String,
        title: String,
        condition: String,
        author: String,
        recordTime: Instant,
        onSuccess: (String) -> Unit = {}
    ) {
        val personId = currentState.personId ?: return
        val inputState = PersonConditionUiState(title, condition, author, recordTime)
        
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
            }
        ) {
            // 1. バリデーション
            val validationResult = PersonConditionLogic.validate(inputState)
            translateValidationResult(validationResult)

            // 2. Entity 構築
            val record = PersonConditionLogic.createRecord(personId, conditionId, inputState)
            val isUpdate = !IdLogic.isNew(conditionId)

            // 3. 重複チェック
            val existing = conditionRepository.findConditionAtTime(record.personId, record.recordTime)
            val duplicateResult = ConditionLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            // 4. 保存実行
            val newId = conditionRepository.insertConditionAtVisit(record, featureName, OP_SAVE, isUpdate)
            
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

    private fun translateValidationResult(result: ConditionValidationResult) {
        if (result == ConditionValidationResult.SUCCESS) return
        val messageRes = if (result == ConditionValidationResult.DUPLICATE_TIME) R.string.common_err_duplicate_blocked_simple else R.string.common_error_save
        throw AppValidationException(R.string.common_error_title_save, messageRes, emptyList(), "Validation failed: $result")
    }

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

    fun onPhotoCaptured(uri: Uri, conditionId: String) {
        val personId = currentState.personId ?: return
        sendViewEvent(PersonConditionViewEvent.NavigateToPhotoPreview(uri, personId, conditionId))
    }

    /**
     * 選択された迷子写真を現在のレコードに紐付けます。
     */
    fun reattachOrphanedPhoto(conditionId: String, photoInfo: jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) {
        val personId = currentState.personId ?: return
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
                // DBにレコードがある場合 (TEMPORARY, ORPHANED_RECORD)
                conditionRepository.reattachPhotoToRecord(photoInfo.photoId, conditionId, featureName, "reattachOrphanedPhoto")
            } else {
                // ファイルのみの場合
                conditionRepository.adoptFileAsPhoto(
                    personId = personId,
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

    fun processAndSavePhoto(context: Context, uri: Uri, conditionId: String, caption: String) {
        val personId = currentState.personId ?: return
        safeLaunch(
            operation = OP_SAVE_PHOTO,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
                errorMessageRes = R.string.p_cond_err_photo_process_failure
            }
        ) {
            val (photoName, thumbName) = ImageUtils.processAndSaveImage(context, uri)
            val photo = ConditionPhoto(
                conditionId = conditionId,
                personId = personId,
                photoFileName = photoName,
                thumbnailFileName = thumbName,
                capturedAt = Instant.now(),
                caption = caption
            )
            conditionRepository.insertConditionPhoto(photo, featureName, OP_SAVE_PHOTO)
            
            if ((uri.scheme == "file") || (uri.scheme == "content")) {
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

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

    fun notifyPhotoError(message: String) {
        updateUiState { it.copy(errorMessage = message) }
        showError(message)
    }

    suspend fun getAllPhotosForPerson(): List<ConditionPhoto> {
        val personId = currentState.personId ?: return emptyList()
        return conditionRepository.getAllPhotosByPersonId(personId)
    }

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
