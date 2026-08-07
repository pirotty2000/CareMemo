package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.HealthValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthViewEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel：PersonHealthViewModel
 */
class PersonHealthViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonHealthUiState, PersonHealthViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonHealthUiState(),
    savedStateHandle
) {

    companion object {
        private const val FEATURE_NAME = "PersonHealth"
        private const val OP_SAVE = "saveRecord"
        private const val OP_DELETE = "deleteRecord"
        private const val OP_RECORDS_FLOW = "recordsFlow"
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    private var recordsJob: Job? = null

    init {
        // 引数（categoryName）の変更を購読
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_CATEGORY_NAME, null).collect { name ->
                if (name != null) {
                    try {
                        setCategory(Category.valueOf(name))
                    } catch (_: Exception) {
                        // 無視
                    }
                }
            }
        }

        // 表示モード設定を購読
        scope.launch {
            userSettingsRepository.healthDisplayModeIsHistory.collect { isHistory ->
                updateUiState { it.copy(preferredShowHistory = isHistory) }
            }
        }
        
        // 最後に監視を開始 (featureName が初期化された後)
        startObservePersonId()
    }

    override fun copyWithLoadingState(state: PersonHealthUiState, isLoading: Boolean): PersonHealthUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonHealthUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonHealthUiState {
        val next = state.copy(personId = person.id)
        refreshRecords(next.personId, next.currentCategory)
        return next
    }

    override fun onPrepareLoadPerson(state: PersonHealthUiState): PersonHealthUiState {
        return state.copy(
            personId = null,
            records = persistentListOf(),
            selectedRecordId = null,
            preferredShowHistory = true
        )
    }

    fun updatePreferredShowHistory(preferredShowHistory: Boolean) {
        scope.launch {
            userSettingsRepository.setHealthDisplayModeIsHistory(preferredShowHistory)
        }
    }

    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category, selectedRecordId = null) }
            refreshRecords(currentState.personId, category)
        }
    }

    fun setSelectedRecordId(id: String?) {
        updateUiState { it.copy(selectedRecordId = id) }
    }

    private fun refreshRecords(personId: String?, category: Category) {
        if (personId == null) return

        recordsJob?.cancel()
        recordsJob = safeCollect(
            operation = OP_RECORDS_FLOW,
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_HEALTH },
            flowProvider = {
                when (category) {
                    Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(personId)
                    Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(personId)
                    Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(personId)
                    else -> flowOf(emptyList())
                }
            }
        ) { records ->
            updateUiState { it.copy(records = records.toImmutableList()) }
        }
    }

    fun getHealthRecords(category: Category): StateFlow<ImmutableList<HistoryRecord>> {
        val personId = currentState.personId ?: return flowOf(persistentListOf<HistoryRecord>()).stateIn(scope, SharingStarted.WhileSubscribed(5000), persistentListOf())

        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(personId)
            Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(personId)
            Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(personId)
            else -> flowOf(emptyList())
        }.map { it.toImmutableList() }.stateIn(scope, SharingStarted.WhileSubscribed(5000), persistentListOf())
    }

    fun saveRecord(category: Category, recordId: String, recordTime: Instant, values: Map<String, Any?>) {
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = recordId
            }
        ) {
            val record = PersonHealthLogic.createEntity(category, requiredPersonId, recordId, recordTime, values) as HistoryRecord
            val validationResult = PersonHealthLogic.validate(record)
            translateValidationResult(validationResult)

            val isUpdate = !IdLogic.isNew(recordId)
            val existing = when (record) {
                is HeightAndWeight -> healthRepository.findHeightAndWeightAtTime(record.personId, record.recordTime)
                is BpAndPulse -> healthRepository.findBpAndPulseAtTime(record.personId, record.recordTime)
                is GlucoseAndHbA1c -> healthRepository.findGlucoseAndHbA1cAtTime(record.personId, record.recordTime)
                else -> null
            }

            val duplicateResult = PersonHealthLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            performSave(record, isUpdate)
            sendUiEvent(UiEvent.SaveSuccess(record.personId))
            showSnackbar(if (isUpdate) R.string.p_health_msg_update_success else R.string.p_health_msg_save_success)
        }
    }

    private fun translateValidationResult(result: HealthValidationResult) {
        if (result == HealthValidationResult.SUCCESS) return
        val messageRes = when (result) {
            HealthValidationResult.INVALID_VALUE -> R.string.common_error_save
            HealthValidationResult.DUPLICATE_TIME -> R.string.common_err_duplicate_blocked_simple
            else -> R.string.common_error_save
        }
        val args = when (result) {
            HealthValidationResult.INVALID_VALUE -> listOf("入力値が範囲外です。正しい数値を入力してください。")
            else -> emptyList()
        }
        throw AppValidationException(R.string.common_error_title_save, messageRes, args, "Validation failed: $result")
    }

    private suspend fun performSave(record: Any, isUpdate: Boolean) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record, featureName, OP_SAVE, isUpdate)
        is BpAndPulse -> healthRepository.insertBpAndPulse(record, featureName, OP_SAVE, isUpdate)
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record, featureName, OP_SAVE, isUpdate)
        else -> {}
    }

    fun deleteRecord(record: Any) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = (record as? HistoryRecord)?.id
            }
        ) {
            performDelete(record)
            showSnackbar(R.string.p_health_msg_delete_success)
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.deleteHeightAndWeight(record, featureName, OP_DELETE)
        is BpAndPulse -> healthRepository.deleteBpAndPulse(record, featureName, OP_DELETE)
        is GlucoseAndHbA1c -> healthRepository.deleteGlucoseAndHbA1c(record, featureName, OP_DELETE)
        else -> {}
    }

    fun navigateToGraphExpansion(personId: String, category: Category, initialIndex: Int) {
        sendViewEvent(PersonHealthViewEvent.NavigateToGraphExpansion(personId, category, initialIndex))
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonHealthViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
