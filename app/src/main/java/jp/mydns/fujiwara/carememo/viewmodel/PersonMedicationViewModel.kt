package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.logic.common.MedicationValidationResult
import jp.mydns.fujiwara.carememo.logic.common.SyncAction
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationViewEvent
import kotlinx.coroutines.Job
import java.time.YearMonth

/**
 * 利用者服薬管理画面用の ViewModel (B系統)
 */
class PersonMedicationViewModel(
    private val medicationRepository: MedicationRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseUiStateViewModel<PersonMedicationUiState, PersonMedicationViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonMedicationUiState()
) {

    companion object {
        private const val FEATURE_NAME = "PersonMedication"
        private const val OP_SYNC = "syncMedicationDay"
        private const val TABLE_MEDICATION = "medication_db"
    }

    override val featureName: String = FEATURE_NAME

    private var monthlyRecordsJob: Job? = null
    private var allRecordsJob: Job? = null

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: PersonMedicationUiState, isLoading: Boolean): PersonMedicationUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun getPersonId(state: PersonMedicationUiState): Int? = state.personId

    override fun updateWithPersonData(
        state: PersonMedicationUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonMedicationUiState {
        val next = state.copy(
            personId = person.id,
            selectedMonth = YearMonth.now()
        )
        refreshMonthlyRecords(next)
        refreshAllRecords(next)
        return next
    }

    // --- 購読ロジック (原子的な反映) ---

    private fun refreshMonthlyRecords(state: PersonMedicationUiState) {
        val personId = state.personId ?: return
        val month = state.selectedMonth

        monthlyRecordsJob?.cancel()
        monthlyRecordsJob = safeCollect(
            operation = "monthlyRecordsFlow",
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_MEDICATION },
            flowProvider = { medicationRepository.getMedicationRecordsByMonth(personId, month.toString()) }
        ) { records ->
            updateUiState { current ->
                current.copy(
                    monthlyRecords = records,
                    recordsByDate = PersonMedicationLogic.groupRecordsByDate(records)
                )
            }
        }
    }

    private fun refreshAllRecords(state: PersonMedicationUiState) {
        val personId = state.personId ?: return
        allRecordsJob?.cancel()
        allRecordsJob = safeCollect(
            operation = "allRecordsFlow",
            contextBuilder = { tableName = TABLE_MEDICATION },
            flowProvider = { medicationRepository.getMedicationRecords(personId) }
        ) { records ->
            updateUiState { it.copy(allRecords = records) }
        }
    }

    // --- UI アクション ---

    fun nextMonth() {
        updateUiState { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        refreshMonthlyRecords(currentState)
    }

    fun previousMonth() {
        updateUiState { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        refreshMonthlyRecords(currentState)
    }

    /**
     * 特定の日の服薬状況を一括同期（保存・更新・削除）します。
     */
    fun syncMedicationDay(date: String, slotRecords: List<MedicationRecord?>) {
        val personId = currentState.personId ?: return
        
        safeLaunch(
            operation = OP_SYNC,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_MEDICATION
                affectedId = personId.toString()
            }
        ) {
            // 1. バリデーション
            slotRecords.filterNotNull().forEach { record ->
                val validationResult = MedicationLogic.validateMedication(record)
                translateValidationResult(validationResult)
            }

            val currentDayRecords = currentState.recordsByDate[date] ?: emptyList()
            
            // 2. 同期アクションの判定
            val actions = MedicationLogic.determineSyncActions(currentDayRecords, slotRecords)

            // 3. アクションの実行
            actions.forEach { action ->
                when (action) {
                    is SyncAction.Insert -> medicationRepository.insertMedicationRecord(action.record, featureName, OP_SYNC)
                    is SyncAction.Delete -> medicationRepository.deleteMedicationRecord(action.record, featureName, OP_SYNC)
                    SyncAction.None -> {}
                }
            }
            
            if (actions.any { it !is SyncAction.None }) {
                showSnackbar(R.string.p_med_msg_update_success)
            }
        }
    }

    private fun translateValidationResult(result: MedicationValidationResult) {
        if (result == MedicationValidationResult.SUCCESS) return
        val messageRes = when (result) {
            MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED -> R.string.common_error_save
            else -> R.string.common_error_save
        }
        val args = if (result == MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED) listOf("未来の日付には記録できません") else emptyList()
        
        throw AppValidationException(R.string.common_error_title_save, messageRes, args, "Validation failed: $result")
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
            return PersonMedicationViewModel(
                medicationRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
