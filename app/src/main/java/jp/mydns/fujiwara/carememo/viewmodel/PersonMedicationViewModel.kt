package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.R
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
import java.time.YearMonth

/**
 * 利用者服薬管理画面用の ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonMedicationViewModel(
    private val medicationRepository: MedicationRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository) {

    private val TAG = "PersonMedicationViewModel"

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
                .onEach { _isLoading.value = false }
        } else {
            flowOf(emptyList())
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        _isLoading.value = false
        Log.e(TAG, "Monthly records load error", e)
        auditLogRepository.log(
            screenName = "PersonMedication",
            operation = "monthlyRecordsFlow",
            tableName = "medication_db",
            actionType = "ERROR",
            affectedId = _currentPerson.value?.id?.toString() ?: "0",
            details = e.toString()
        )
        showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 利用者の全服薬記録 (PDF出力用)
     */
    val allRecords: StateFlow<List<MedicationRecord>> = _currentPerson.flatMapLatest { person ->
        person?.let { medicationRepository.getMedicationRecords(it.id) } ?: flowOf(emptyList())
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.e(TAG, "All records load error", e)
        auditLogRepository.log(
            screenName = "PersonMedication",
            operation = "allRecordsFlow",
            tableName = "medication_db",
            actionType = "ERROR",
            affectedId = _currentPerson.value?.id?.toString() ?: "0",
            details = e.toString()
        )
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
     * @param slotRecords 4スロット（朝・昼・夕・寝る前）の最新状態。nullのスロットは「記録なし（削除対象）」とみなす。
     */
    fun syncMedicationDay(date: String, slotRecords: List<MedicationRecord?>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 現在のその日のデータを取得（差分判定用）
                val currentDayRecords = recordsByDate.value[date] ?: emptyList()
                
                slotRecords.forEachIndexed { index, newRecord ->
                    val existingRecord = currentDayRecords.find { it.timeSlot == index }
                    
                    if (newRecord != null) {
                        // IDが0でも @Upsert なので、既存があれば更新、なければ追加される
                        medicationRepository.insertMedicationRecord(newRecord, "PersonMedication", "syncMedicationDay")
                    } else {
                        existingRecord?.let {
                            medicationRepository.deleteMedicationRecord(it, "PersonMedication", "syncMedicationDay(delete)")
                        }
                    }
                }
                showSnackbar(R.string.p_med_msg_update_success)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Sync error", e)
                auditLogRepository.log(
                    screenName = "PersonMedication",
                    operation = "syncMedicationDay",
                    tableName = "medication_db",
                    actionType = "ERROR",
                    affectedId = _currentPerson.value?.id?.toString() ?: "0",
                    details = e.toString()
                )
                showError(R.string.common_error_title_update, R.string.p_med_err_update_failure, e.localizedMessage ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val medicationRepository: MedicationRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonMedicationViewModel::class.java)) {
                return PersonMedicationViewModel(
                    medicationRepository,
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
