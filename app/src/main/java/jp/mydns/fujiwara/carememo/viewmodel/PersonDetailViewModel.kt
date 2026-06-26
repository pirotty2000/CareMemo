package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class PersonDetailViewModel(
    private val repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseViewModel(userSettingsRepository) {

    private val _currentPerson = MutableStateFlow<Person?>(null)
    val currentPerson: StateFlow<Person?> = _currentPerson.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val personCategorySummary: StateFlow<PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person != null) repository.getPersonCategorySummaryById(person.id)
            else kotlinx.coroutines.flow.flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _records = MutableStateFlow<List<Any>>(emptyList())
    val records: StateFlow<List<Any>> = _records.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredRecords: StateFlow<List<Any>> = combine(_records, _searchQuery) { records, query ->
        if (query.isBlank()) {
            records
        } else {
            records.filter { record ->
                if (record is ConditionAtVisit) {
                    val titleMatch = record.title?.contains(query, ignoreCase = true) == true
                    val conditionMatch = record.condition?.contains(query, ignoreCase = true) == true
                    titleMatch || conditionMatch
                } else {
                    true
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedConditionId = MutableStateFlow<Int?>(null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentConditionPhotos: StateFlow<List<ConditionPhoto>> = _selectedConditionId
        .flatMapLatest { id ->
            if (id != null) repository.getConditionPhotosByConditionId(id)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _conditionPhotoMap = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val conditionPhotoMap: StateFlow<Map<Int, Boolean>> = _conditionPhotoMap.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun loadPerson(personId: Int) {
        viewModelScope.launch {
            repository.getPersonById(personId).collectLatest {
                _currentPerson.value = it
            }
        }
    }

    fun loadRecords(personId: Int, category: Category) {
        viewModelScope.launch {
            when (category) {
                Category.HEIGHT_AND_WEIGHT -> {
                    repository.getHeightAndWeightByPersonId(personId).collectLatest { _records.value = it }
                }
                Category.BP_AND_PULSE -> {
                    repository.getBpAndPulseByPersonId(personId).collectLatest { _records.value = it }
                }
                Category.GLUCOSE_AND_HBA1C -> {
                    repository.getGlucoseAndHbA1cByPersonId(personId).collectLatest { _records.value = it }
                }
                Category.CONDITION_AT_VISIT -> {
                    repository.getConditionAtVisitByPersonId(personId).collectLatest { memos ->
                        _records.value = memos
                        val photos = repository.getAllPhotosByPersonId(personId)
                        val map = memos.associate { memo ->
                            memo.id to photos.any { it.conditionId == memo.id }
                        }
                        _conditionPhotoMap.value = map
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveRecord(record: Any?) {
        if (record == null) return
        viewModelScope.launch {
            try {
                val isUpdate = isRecordUpdate(record)
                performSave(record)
                showSnackbar(if (isUpdate) "記録を更新しました" else "記録を保存しました")
            } catch (e: Exception) {
                showError("保存エラー", "データの保存に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    private fun isRecordUpdate(record: Any): Boolean = when (record) {
        is HeightAndWeight -> record.id != 0
        is BpAndPulse -> record.id != 0
        is GlucoseAndHbA1c -> record.id != 0
        is ConditionAtVisit -> record.id != 0
        else -> false
    }

    private suspend fun performSave(record: Any) = when (record) {
        is HeightAndWeight -> repository.insertHeightAndWeight(record)
        is BpAndPulse -> repository.insertBpAndPulse(record)
        is GlucoseAndHbA1c -> repository.insertGlucoseAndHbA1c(record)
        is ConditionAtVisit -> repository.insertConditionAtVisit(record)
        else -> {}
    }

    fun deleteRecord(record: Any) {
        viewModelScope.launch {
            try {
                performDelete(record)
                showSnackbar("記録を削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "データの削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> repository.deleteHeightAndWeight(record)
        is BpAndPulse -> repository.deleteBpAndPulse(record)
        is GlucoseAndHbA1c -> repository.deleteGlucoseAndHbA1c(record)
        is ConditionAtVisit -> repository.deleteConditionAtVisit(record)
        else -> {}
    }

    fun setSelectedConditionId(id: Int?) {
        _selectedConditionId.value = id
    }

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
                    repository.insertConditionPhoto(photo)
                    showSnackbar("写真を保存しました")
                } else {
                    showError("保存エラー", "画像の処理に失敗しました。空き容量を確認してください。")
                }
            } catch (e: Exception) {
                showError("保存エラー", "写真の保存中にエラーが発生しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deletePhoto(context: Context, photo: ConditionPhoto) {
        viewModelScope.launch {
            try {
                repository.deleteConditionPhotoById(photo.id)
                ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
                showSnackbar("写真を削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "写真の削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    suspend fun getAllPhotosForPerson(personId: Int): List<ConditionPhoto> {
        return repository.getAllPhotosByPersonId(personId)
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonDetailViewModel::class.java)) {
                return PersonDetailViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
