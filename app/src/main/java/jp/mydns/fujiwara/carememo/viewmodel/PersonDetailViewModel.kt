package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class PersonDetailViewModel(
    private val repository: CareMemoRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : ViewModel() {
    private val _currentPerson = MutableStateFlow<Person?>(null)
    val currentPerson: StateFlow<Person?> = _currentPerson.asStateFlow()

    val isNameMaskingEnabled: StateFlow<Boolean> = userSettingsRepository.isNameMaskingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val defaultRecorderName: StateFlow<String> = userSettingsRepository.defaultRecorderName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    private val _currentRecordState = MutableStateFlow<Any?>(null)
    val currentRecordState: StateFlow<Any?> = _currentRecordState.asStateFlow()

    private val _records = MutableStateFlow<List<Any>>(emptyList())
    val records: StateFlow<List<Any>> = _records.asStateFlow()

    private val _snackbarFlow = MutableSharedFlow<String>()
    val snackbarFlow = _snackbarFlow.asSharedFlow()

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
                    repository.getConditionAtVisitByPersonId(personId).collectLatest { _records.value = it }
                }
            }
        }
    }

    fun clearCurrentRecord() {
        _currentRecordState.value = null
    }

    fun saveRecord(record: Any?) {
        viewModelScope.launch {
            val isUpdate = when (record) {
                is HeightAndWeight -> record.id != 0
                is BpAndPulse -> record.id != 0
                is GlucoseAndHbA1c -> record.id != 0
                is ConditionAtVisit -> record.id != 0
                else -> false
            }

            when (record) {
                is HeightAndWeight -> repository.insertHeightAndWeight(record)
                is BpAndPulse -> repository.insertBpAndPulse(record)
                is GlucoseAndHbA1c -> repository.insertGlucoseAndHbA1c(record)
                is ConditionAtVisit -> repository.insertConditionAtVisit(record)
            }
            
            _snackbarFlow.emit(if (isUpdate) "記録を更新しました" else "記録を保存しました")
            _currentRecordState.value = null
        }
    }

    fun deleteRecord(record: Any) {
        viewModelScope.launch {
            when (record) {
                is HeightAndWeight -> repository.deleteHeightAndWeight(record)
                is BpAndPulse -> repository.deleteBpAndPulse(record)
                is GlucoseAndHbA1c -> repository.deleteGlucoseAndHbA1c(record)
                is ConditionAtVisit -> repository.deleteConditionAtVisit(record)
            }
            _snackbarFlow.emit("記録を削除しました")
        }
    }

    fun selectRecord(record: Any) {
        _currentRecordState.value = record
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
