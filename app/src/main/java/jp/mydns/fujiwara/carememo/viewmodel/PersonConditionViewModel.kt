package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.ConditionLogic
import jp.mydns.fujiwara.carememo.logic.common.ConditionValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionValidationResult
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.Instant

/**
 * 所見メモ（体調記録）固有のロジック(B系統)を扱う ViewModel。
 * 所見テキストデータの取得・保存・削除と、付随する写真処理を担当します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonConditionViewModel(
    private val conditionRepository: ConditionRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonCondition"
        private const val OP_SAVE = "saveRecord"
        private const val OP_DELETE = "deleteRecord"
        private const val OP_SAVE_PHOTO = "processAndSavePhoto"
        private const val OP_DELETE_PHOTO = "deletePhoto"
        private const val TABLE_CONDITION = "condition_db"
    }

    override val featureName: String = FEATURE_NAME

    private val _selectedConditionId = MutableStateFlow<Int?>(null)
    val selectedConditionId: StateFlow<Int?> = _selectedConditionId.asStateFlow()

    private val _records = MutableStateFlow<List<ConditionAtVisit>>(emptyList())

    /**
     * 現在の利用者に紐づく所見メモ一覧
     */
    val records: StateFlow<List<ConditionAtVisit>> = _records.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 検索クエリでフィルタリングされた所見メモ一覧
     */
    val filteredRecords: StateFlow<List<ConditionAtVisit>> = combine(records, _searchQuery) { recs, query ->
        ConditionLogic.filterRecords(recs, query)
    }.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.Eagerly, 
        initialValue = emptyList()
    )

    /**
     * 選択された所見メモに紐づく写真一覧
     */
    val currentConditionPhotos: StateFlow<List<ConditionPhoto>> = _selectedConditionId
        .flatMapLatest { id ->
            if (id != null) conditionRepository.getConditionPhotosByConditionId(id)
            else flowOf(emptyList())
        }
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "photosFlow", TABLE_CONDITION))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 所見メモIDと「写真の有無」のマップ（一覧表示でのアイコン制御用）
     */
    val conditionPhotoMap: StateFlow<Map<Int, Boolean>> = combine(_currentPerson, records) { person, recs ->
        person to recs
    }.flatMapLatest { (person, recs) ->
        if (person == null || recs.isEmpty()) {
            flowOf(emptyMap())
        } else {
            conditionRepository.getAllPhotosByPersonIdFlow(person.id).map { photos ->
                recs.associate { memo ->
                    memo.id to photos.any { it.conditionId == memo.id }
                }
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, "photoMapFlow", TABLE_CONDITION))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setSelectedConditionId(id: Int?) {
        _selectedConditionId.value = id
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * UI層で発生した撮影・選択などのエラーを通知します。
     */
    fun notifyPhotoError(message: String) {
        _errorMessage.value = message
        safeLaunch(
            operation = "photoOperation",
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = _selectedConditionId.value?.toString() ?: ""
            }
        ) {
            throw AppExternalException(
                titleResId = R.string.p_cond_err_title_photo,
                messageResId = R.string.p_cond_err_photo_capture_failed,
                logMessage = "Photo operation failed: $message"
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadPerson(personId: Int, initialQuery: String) {
        if (_currentPerson.value?.id == personId) return

        _currentPerson.value = null
        _searchQuery.value = initialQuery
        _selectedConditionId.value = null

        loadPersonJob?.cancel()
        loadPersonJob = safeCollect(
            operation = "loadPersonAndRecords",
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = personId.toString()
            },
            flowProvider = {
                combine(
                    repository.getPersonById(personId),
                    conditionRepository.getConditionAtVisitByPersonId(personId)
                ) { person, records -> person to records }
            }
        ) { (person, records) ->
            _currentPerson.value = person
            _records.value = records
        }
    }

    override fun loadPerson(personId: Int) {
        loadPerson(personId, "")
    }

    /**
     * 所見メモを保存または更新します。
     */
    fun saveRecord(
        personId: Int,
        conditionId: Int,
        state: PersonConditionUiState,
        onSuccess: (Int) -> Unit = {}
    ) {
        safeLaunch(
            operation = OP_SAVE,
            loadingState = _isProcessing,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId.toString()
            }
        ) {
            // 1. バリデーション（事実の判定）
            val validationResult = PersonConditionLogic.validate(state)

            // 2. バリデーション結果の翻訳（ViewModelの責務）
            translateValidationResult(validationResult)

            // 3. Entity 構築
            val record = PersonConditionLogic.createRecord(personId, conditionId, state)
            val isUpdate = record.id != 0

            // 4. 重複チェック (新規登録、または日時変更時)
            val existing = conditionRepository.findConditionAtTime(record.personId, record.recordTime)
            val duplicateResult = ConditionLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            // 5. 保存実行
            val newId = conditionRepository.insertConditionAtVisit(record, featureName, OP_SAVE)
            
            // 新規登録の場合、一時保存されていた写真（conditionId=0）を新しいIDに紐付ける
            if (!isUpdate) {
                conditionRepository.linkTemporaryPhotosToRecord(record.personId, newId.toInt(), featureName, "${OP_SAVE}(link)")
            }

            showSnackbar(if (isUpdate) R.string.p_cond_msg_update_success else R.string.p_cond_msg_save_success)
            
            // 選択中IDを更新して、詳細画面が新しいIDを参照するようにする
            setSelectedConditionId(if (isUpdate) record.id else newId.toInt())

            // コールバックを実行
            onSuccess(if (isUpdate) record.id else newId.toInt())
        }
    }

    /**
     * バリデーション結果（事実）を UI 通知用の例外（翻訳）に変換します。
     */
    private fun translateValidationResult(result: PersonConditionValidationResult) {
        if (result == PersonConditionValidationResult.SUCCESS) return

        val messageRes = R.string.common_error_save
        val args = when (result) {
            PersonConditionValidationResult.EMPTY_CONDITION -> listOf("内容を入力してください")
            PersonConditionValidationResult.EMPTY_AUTHOR -> listOf("記録者を入力してください")
            PersonConditionValidationResult.CONDITION_TOO_LONG -> listOf("内容が長すぎます（${AppThresholds.CONDITION_MAX_LENGTH}文字以内）")
            PersonConditionValidationResult.INVALID_TIME -> listOf("日時を正しく入力してください")
            else -> emptyList()
        }

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
    }

    /**
     * ドメイン共通のバリデーション結果（事実）を UI 通知用の例外（翻訳）に変換します。
     */
    private fun translateValidationResult(result: ConditionValidationResult) {
        if (result == ConditionValidationResult.SUCCESS) return

        val (titleRes, messageRes) = when (result) {
            ConditionValidationResult.DUPLICATE_TIME -> R.string.common_error_title_save to R.string.common_err_duplicate_blocked_simple
            else -> R.string.common_error_title_save to R.string.common_error_save
        }

        throw AppValidationException(
            titleResId = titleRes,
            messageResId = messageRes,
            logMessage = "Validation failed: $result"
        )
    }

    /**
     * 所見メモを削除します。
     */
    fun deleteRecord(record: ConditionAtVisit) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = record.id.toString()
            }
        ) {
            conditionRepository.deleteConditionAtVisit(record, featureName, OP_DELETE)
            showSnackbar(R.string.p_cond_msg_delete_success)
        }
    }

    /**
     * 一時ファイルを削除します。
     */
    fun deleteTempFile(context: Context, uri: Uri) {
        safeLaunch(operation = "deleteTempFile") {
            try {
                if (uri.scheme == "file" || uri.scheme == "content") {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                        uri.path?.let { File(it).delete() }
                    }
                }
            } catch (_: Exception) {
                // ここは補助的な処理なのでハンドリングのみ
            }
        }
    }

    /**
     * 写真をリサイズ・保存し、データベースに登録します。
     */
    fun processAndSavePhoto(context: Context, uri: Uri, personId: Int, conditionId: Int, caption: String) {
        safeLaunch(
            operation = OP_SAVE_PHOTO,
            loadingState = _isProcessing,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId.toString()
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
            
            // Exif情報（GPS等）が含まれている可能性がある一時ファイルを削除
            if (uri.scheme == "file" || uri.scheme == "content") {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                    uri.path?.let { File(it).delete() }
                }
            }

            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

    /**
     * 写真データおよび物理ファイルを削除します。
     */
    fun deletePhoto(context: Context, photo: ConditionPhoto) {
        safeLaunch(
            operation = OP_DELETE_PHOTO,
            loadingState = _isProcessing,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = photo.id.toString()
            }
        ) {
            conditionRepository.deleteConditionPhotoById(photo.id, photo.personId, featureName, OP_DELETE_PHOTO)
            ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
            showSnackbar(R.string.p_cond_msg_photo_delete_success)
        }
    }

    suspend fun getAllPhotosForPerson(personId: Int): List<ConditionPhoto> {
        return conditionRepository.getAllPhotosByPersonId(personId)
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonConditionViewModel::class.java)) {
                return PersonConditionViewModel(
                    conditionRepository,
                    personRepository,
                    summaryRepository,
                    userSettingsRepository,
                    auditLogRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
