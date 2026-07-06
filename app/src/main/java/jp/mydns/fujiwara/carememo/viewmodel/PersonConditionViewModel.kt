package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository) {

    private val _selectedConditionId = MutableStateFlow<Int?>(null)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 検索クエリでフィルタリングされた所見メモ一覧
     */
    val filteredRecords: StateFlow<List<ConditionAtVisit>> = combine(records, _searchQuery) { recs, query ->
        if (query.isBlank()) recs
        else {
            recs.filter { record ->
                val titleMatch = record.title?.contains(query, ignoreCase = true) == true
                val conditionMatch = record.condition?.contains(query, ignoreCase = true) == true
                titleMatch || conditionMatch
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 選択された所見メモに紐づく写真一覧
     */
    val currentConditionPhotos: StateFlow<List<ConditionPhoto>> = _selectedConditionId
        .flatMapLatest { id ->
            if (id != null) conditionRepository.getConditionPhotosByConditionId(id)
            else flowOf(emptyList())
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
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val isUpdate = record.id != 0
                val newId = conditionRepository.insertConditionAtVisit(record)
                
                // 新規登録の場合、一時保存されていた写真（conditionId=0）を新しいIDに紐付ける
                if (!isUpdate) {
                    conditionRepository.linkTemporaryPhotosToRecord(record.personId, newId.toInt())
                }

                showSnackbar(if (isUpdate) R.string.p_cond_msg_update_success else R.string.p_cond_msg_save_success)
                
                // 選択中IDを更新して、詳細画面が新しいIDを参照するようにする
                setSelectedConditionId(if (isUpdate) record.id else newId.toInt())

                // コールバックを実行
                onSuccess(if (isUpdate) record.id else newId.toInt())
            } catch (e: Exception) {
                showError(R.string.common_error_title_save, R.string.common_error_save, e.localizedMessage ?: "")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 所見メモを削除します。
     */
    fun deleteRecord(record: ConditionAtVisit) {
        viewModelScope.launch {
            try {
                conditionRepository.deleteConditionAtVisit(record)
                showSnackbar(R.string.p_cond_msg_delete_success)
            } catch (e: Exception) {
                showError(R.string.common_error_title_delete, R.string.common_error_delete, e.localizedMessage ?: "")
            }
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 写真をリサイズ・保存し、データベースに登録します。
     */
    fun processAndSavePhoto(context: Context, uri: Uri, personId: Int, conditionId: Int, caption: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
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
                    conditionRepository.insertConditionPhoto(photo)
                    
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
            } catch (e: Exception) {
                showError(R.string.common_error_title_save, R.string.common_error_save, e.localizedMessage ?: "")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 写真データおよび物理ファイルを削除します。
     */
    fun deletePhoto(context: Context, photo: ConditionPhoto) {
        viewModelScope.launch {
            try {
                conditionRepository.deleteConditionPhotoById(photo.id)
                ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
                showSnackbar(R.string.p_cond_msg_photo_delete_success)
            } catch (e: Exception) {
                showError(R.string.common_error_title_delete, R.string.common_error_delete, e.localizedMessage ?: "")
            }
        }
    }

    suspend fun getAllPhotosForPerson(personId: Int): List<ConditionPhoto> {
        return conditionRepository.getAllPhotosByPersonId(personId)
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonConditionViewModel::class.java)) {
                return PersonConditionViewModel(conditionRepository, personRepository, summaryRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
