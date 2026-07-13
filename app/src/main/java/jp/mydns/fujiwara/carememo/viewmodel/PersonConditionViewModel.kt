package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.ConditionLogic
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    /**
     * 現在の利用者に紐づく所見メモ一覧
     */
    val records: StateFlow<List<ConditionAtVisit>> = _currentPerson
        .flatMapLatest { person ->
            if (person == null) flowOf(emptyList())
            else conditionRepository.getConditionAtVisitByPersonId(person.id)
        }
        .onEach { 
            if (_currentPerson.value != null) {
                _isLoading.value = false
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "recordsFlow", TABLE_CONDITION))
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 検索クエリでフィルタリングされた所見メモ一覧
     */
    val filteredRecords: StateFlow<List<ConditionAtVisit>> = combine(records, _searchQuery) { recs, query ->
        ConditionLogic.filterRecords(recs, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setSelectedConditionId(id: Int?) {
        _selectedConditionId.value = id
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadPerson(personId: Int, initialQuery: String) {
        val isDifferentPerson = currentPerson.value?.id != personId
        super.loadPerson(personId)
        if (isDifferentPerson) {
            _searchQuery.value = initialQuery
            _selectedConditionId.value = null
        }
    }

    override fun loadPerson(personId: Int) {
        loadPerson(personId, "")
    }

    /**
     * 所見メモを保存または更新します。
     */
    fun saveRecord(record: ConditionAtVisit, onSuccess: (Int) -> Unit = {}) {
        safeLaunch(
            operation = OP_SAVE,
            loadingState = _isProcessing,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = record.id.toString()
            }
        ) {
            val isUpdate = record.id != 0

            // --- 重複チェック (新規登録、または日時変更時) ---
            val existing = conditionRepository.findConditionAtTime(record.personId, record.recordTime)
            if (ConditionLogic.isDuplicate(record, existing)) {
                showError(R.string.common_error_title_save, R.string.common_err_duplicate_blocked_simple)
                return@safeLaunch
            }

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
        viewModelScope.launch {
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
            }
        ) {
            val fileNames = ImageUtils.processAndSaveImage(context, uri)
            if (fileNames != null) {
                val (photoName, thumbName) = fileNames
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
            } else {
                showError(R.string.common_error_title_save, R.string.p_cond_err_photo_process_failure)
            }
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
