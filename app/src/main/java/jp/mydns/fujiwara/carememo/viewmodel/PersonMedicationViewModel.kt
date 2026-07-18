package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.logic.common.MedicationValidationResult
import jp.mydns.fujiwara.carememo.logic.common.SyncAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    companion object {
        private const val FEATURE_NAME = "PersonMedication"
        private const val OP_SYNC = "syncMedicationDay"
        private const val TABLE_MEDICATION = "medication_db"
    }

    override val featureName: String = FEATURE_NAME

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _monthlyRecords = MutableStateFlow<List<MedicationRecord>>(emptyList())
    private var monthlyRecordsJob: Job? = null

    /**
     * 選択された月の服薬記録一覧
     */
    val monthlyRecords: StateFlow<List<MedicationRecord>> = _monthlyRecords.asStateFlow()

    private fun refreshMonthlyRecords() {
        val personId = _currentPerson.value?.id ?: return
        val month = _selectedMonth.value

        monthlyRecordsJob?.cancel()
        monthlyRecordsJob = safeCollect(
            operation = "monthlyRecordsFlow",
            loadingState = _isLoading,
            contextBuilder = { tableName = TABLE_MEDICATION },
            flowProvider = {
                medicationRepository.getMedicationRecordsByMonth(personId, month.toString())
            }
        ) {
            _monthlyRecords.value = it
        }
    }

    /**
     * 利用者の全服薬記録 (PDF出力用)
     */
    val allRecords: StateFlow<List<MedicationRecord>> = _currentPerson.flatMapLatest { person ->
        person?.let { medicationRepository.getMedicationRecords(it.id) } ?: flowOf(emptyList())
    }.catch { e ->
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, "allRecordsFlow", TABLE_MEDICATION))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 日付ごとの記録にマッピングしたもの (カレンダー描画用)
     */
    val recordsByDate: StateFlow<Map<String, List<MedicationRecord>>> = monthlyRecords
        .map { records ->
            records.groupBy { it.dosageDate }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        refreshMonthlyRecords()
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
        refreshMonthlyRecords()
    }

    override fun loadPerson(personId: Int) {
        if (_currentPerson.value?.id == personId) return

        _currentPerson.value = null

        loadPersonJob?.cancel()
        loadPersonJob = safeCollect(
            operation = "loadPerson",
            loadingState = _isLoading,
            contextBuilder = {
                tableName = "person_db"
                affectedId = personId.toString()
            },
            flowProvider = { repository.getPersonById(personId) }
        ) {
            _currentPerson.value = it
            refreshMonthlyRecords()
        }
        
        _selectedMonth.value = YearMonth.now()
    }

    /**
     * 特定の日の服薬状況を一括同期（保存・更新・削除）する。
     * @param date 対象日 (yyyy-MM-dd)
     * @param slotRecords 4スロット（朝・昼・夕・寝る前）の最新状態。nullのスロットは「記録なし（削除対象）」とみなす。
     */
    fun syncMedicationDay(date: String, slotRecords: List<MedicationRecord?>) {
        safeLaunch(
            operation = OP_SYNC,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_MEDICATION
                affectedId = _currentPerson.value?.id?.toString()
            }
        ) {
            // 1. バリデーション（事実の判定）
            slotRecords.filterNotNull().forEach { record ->
                val validationResult = MedicationLogic.validateMedication(record)
                translateValidationResult(validationResult)
            }

            val currentDayRecords = recordsByDate.value[date] ?: emptyList()
            
            // 2. 同期アクションの判定
            val actions = MedicationLogic.determineSyncActions(currentDayRecords, slotRecords)

            // 3. アクションの実行（Noneは無視）
            actions.forEach { action ->
                when (action) {
                    is SyncAction.Insert -> medicationRepository.insertMedicationRecord(action.record, featureName, OP_SYNC)
                    is SyncAction.Delete -> medicationRepository.deleteMedicationRecord(action.record, featureName, "$OP_SYNC(delete)")
                    SyncAction.None -> { /* 何もしない */ }
                }
            }
            
            if (actions.any { it !is SyncAction.None }) {
                showSnackbar(R.string.p_med_msg_update_success)
            }
        }
    }

    /**
     * バリデーション結果（事実）を UI 通知用の例外（翻訳）に変換します。
     */
    private fun translateValidationResult(result: MedicationValidationResult) {
        if (result == MedicationValidationResult.SUCCESS) return

        val messageRes = when (result) {
            MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED -> R.string.common_err_duplicate_blocked_simple // 仮：適切なリソースがないため
            MedicationValidationResult.INVALID_STATUS -> R.string.common_error_save
            else -> R.string.common_error_save
        }
        
        val args = when (result) {
            MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED -> listOf("未来の日付には記録できません")
            else -> emptyList()
        }

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
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
