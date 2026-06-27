package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 服薬管理画面用の ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModel(
    repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(repository, userSettingsRepository) {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /**
     * 選択された月の服薬記録一覧
     */
    val monthlyRecords: StateFlow<List<MedicationRecord>> = combine(
        _currentPerson,
        _selectedMonth
    ) { person, month ->
        person to month
    }.flatMapLatest { (person, month) ->
        if (person != null) {
            repository.getMedicationRecordsByMonth(person.id, month.toString())
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 利用者の全服薬記録 (PDF出力用)
     */
    val allRecords: StateFlow<List<MedicationRecord>> = _currentPerson.flatMapLatest { person ->
        person?.let { repository.getMedicationRecords(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 日付ごとの記録にマッピングしたもの (カレンダー描画用)
     */
    val recordsByDate: StateFlow<Map<String, List<MedicationRecord>>> = monthlyRecords
        .map { records ->
            records.groupBy { it.dosageDate }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

//    fun selectMonth(month: YearMonth) {
//        _selectedMonth.value = month
//    }

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
            } catch (e: Exception) {
                showError("保存エラー", "服薬記録の保存に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 特定の記録を削除する
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
