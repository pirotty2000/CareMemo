package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
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
import java.time.YearMonth

/**
 * 利用者服薬管理画面用の ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonMedicationViewModel(
    private val medicationRepository: MedicationRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository) {

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
            medicationRepository.getMedicationRecordsByMonth(person.id, month.toString())
        } else {
            flowOf(emptyList())
        }
    }.onEach { 
        if (_currentPerson.value != null) {
            _isLoading.value = false 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 利用者の全服薬記録 (PDF出力用)
     */
    val allRecords: StateFlow<List<MedicationRecord>> = _currentPerson.flatMapLatest { person ->
        person?.let { medicationRepository.getMedicationRecords(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 日付ごとの記録にマッピングしたもの (カレンダー描画用)
     */
    val recordsByDate: StateFlow<Map<String, List<MedicationRecord>>> = monthlyRecords
        .map { records ->
            records.groupBy { it.dosageDate }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun nextMonth() {
        _isLoading.value = true
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _isLoading.value = true
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    override fun loadPerson(personId: Int) {
        val isDifferentPerson = currentPerson.value?.id != personId
        super.loadPerson(personId)
        if (isDifferentPerson) {
            _selectedMonth.value = YearMonth.now()
        }
    }

    /**
     * 特定の日の服薬状況を一括同期（保存・更新・削除）する。
     * @param date 対象日 (yyyy-MM-dd)
     * @param personId 利用者ID
     * @param slotRecords 4スロット（朝・昼・夕・寝る前）の最新状態。nullのスロットは「記録なし（削除対象）」とみなす。
     */
    fun syncMedicationDay(date: String, personId: Int, slotRecords: List<MedicationRecord?>) {
        viewModelScope.launch {
            try {
                // 現在のその日のデータを取得（差分判定用）
                // 本来はリポジトリ経由で最新を取得すべきだが、StateFlowのrecordsByDate[date]でも概ね安全
                val currentDayRecords = recordsByDate.value[date] ?: emptyList()
                
                slotRecords.forEachIndexed { index, newRecord ->
                    val existingRecord = currentDayRecords.find { it.timeSlot == index }
                    
                    if (newRecord != null) {
                        // ケース1: 入力あり -> 追加または更新
                        // IDが0でも @Upsert なので、既存があれば更新、なければ追加される
                        medicationRepository.insertMedicationRecord(newRecord)
                    } else {
                        // ケース2: 入力なし（未選択） -> 既存があれば削除
                        existingRecord?.let {
                            medicationRepository.deleteMedicationRecord(it)
                        }
                    }
                }
                showSnackbar("服薬状況を更新しました")
            } catch (e: Exception) {
                showError("更新エラー", "服薬状況の更新に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val medicationRepository: MedicationRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonMedicationViewModel::class.java)) {
                return PersonMedicationViewModel(medicationRepository, personRepository, summaryRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
