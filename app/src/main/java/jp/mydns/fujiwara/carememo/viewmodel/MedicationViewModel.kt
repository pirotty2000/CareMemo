package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 服薬管理画面用のViewModel
 */
class MedicationViewModel(
    private val repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    private val _currentPerson = MutableStateFlow<Person?>(null)
    val currentPerson: StateFlow<Person?> = _currentPerson.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val personCategorySummary: StateFlow<jp.mydns.fujiwara.carememo.data.PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person != null) repository.getPersonCategorySummaryById(person.id)
            else kotlinx.coroutines.flow.flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /**
     * 選択された月の服薬記録一覧
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val monthlyRecords: StateFlow<List<MedicationRecord>> = combineState(
        _currentPerson,
        _selectedMonth
    ) { person, month ->
        person to month
    }.flatMapLatest { (person, month) ->
        if (person != null) {
            repository.getMedicationRecordsByMonth(person.id, month.toString())
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 日付ごとの記録にマッピングしたもの (カレンダー描画用)
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recordsByDate: StateFlow<Map<String, List<MedicationRecord>>> = monthlyRecords
        .flatMapLatest { records ->
            kotlinx.coroutines.flow.flowOf(records.groupBy { it.dosageDate })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun loadPerson(personId: Int) {
        viewModelScope.launch {
            repository.getPersonById(personId).collectLatest {
                _currentPerson.value = it
            }
        }
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    /**
     * 服薬記録を保存または更新する
     */
    fun saveMedicationRecord(record: MedicationRecord) {
        viewModelScope.launch {
            try {
                repository.insertMedicationRecord(record)
                // スナックバー表示などは必要に応じて
            } catch (e: Exception) {
                showError("保存エラー", "服薬記録の保存に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 特定の日・時間枠の記録を削除する
     */
    fun deleteMedicationRecord(record: MedicationRecord) {
        viewModelScope.launch {
            try {
                repository.deleteMedicationRecord(record)
            } catch (e: Exception) {
                showError("削除エラー", "服薬記録の削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 補助関数：StateFlowの結合
     */
    private fun <T1, T2, R> combineState(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        transform: (T1, T2) -> R
    ): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(flow1, flow2, transform)

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
                return MedicationViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
