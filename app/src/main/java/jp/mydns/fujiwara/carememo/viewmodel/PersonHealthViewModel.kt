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
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
import jp.mydns.fujiwara.carememo.logic.feature.HealthValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.HealthProcessorRegistry
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

    /** 履歴ロード用の Job */
    private var recordsJob: Job? = null

    /** 保存処理用の Job */
    private var saveJob: Job? = null

    /** 削除処理用の Job */
    private var deleteJob: Job? = null

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
            preferredShowHistory = true,
            isEditing = false,
            editInput = HealthEditInput()
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
        updateUiState { state ->
            val next = state.copy(selectedRecordId = id)
            if (id == null) {
                next.copy(
                    isEditing = false,
                    editInput = HealthEditInput(),
                    initialRecordTime = null,
                    initialSnapshot = null,
                    isChanged = false,
                    isSaveEnabled = false
                )
            } else if (IdLogic.isNew(id)) {
                // 新規作成時は即座に編集セッションを開始
                val latestHeight = if (state.currentCategory == Category.HEIGHT_AND_WEIGHT) {
                    state.records.filterIsInstance<HeightAndWeight>()
                        .filter { it.height != null }
                        .maxByOrNull { it.recordTime }?.height?.toString() ?: ""
                } else ""

                val now = Instant.now()
                val initialInput = HealthEditInput(
                    heightText = latestHeight,
                    recordTime = now
                )
                next.copy(
                    isEditing = true,
                    editInput = initialInput,
                    initialRecordTime = now,
                    initialSnapshot = initialInput,
                    isChanged = false,
                    isSaveEnabled = false
                )
            } else {
                // 既存レコード選択時は閲覧モードから開始
                next.copy(isEditing = false, initialRecordTime = null)
            }
        }
    }

    /**
     * 現在選択されているレコードの編集セッションを開始します。
     */
    fun startEditSession() {
        val recordId = currentState.selectedRecordId ?: return
        val record = currentState.records.find { it.id == recordId } ?: return

        val initialInput = HealthEditInput(
            heightText = (record as? HeightAndWeight)?.height?.toString() ?: "",
            weightText = (record as? HeightAndWeight)?.weight?.toString() ?: "",
            bpSystolicText = (record as? BpAndPulse)?.bpSystolic?.toString() ?: "",
            bpDiastolicText = (record as? BpAndPulse)?.bpDiastolic?.toString() ?: "",
            satText = (record as? BpAndPulse)?.sat?.toString() ?: "",
            pulseText = (record as? BpAndPulse)?.pulse?.toString() ?: "",
            bodyTemperatureText = (record as? BpAndPulse)?.bodyTemperature?.toString() ?: "",
            glucoseText = (record as? GlucoseAndHbA1c)?.glucose?.toString() ?: "",
            hba1cText = (record as? GlucoseAndHbA1c)?.hba1c?.toString() ?: "",
            recordTime = record.recordTime
        )

        updateUiState {
            it.copy(
                isEditing = true,
                editInput = initialInput,
                initialRecordTime = record.recordTime,
                initialSnapshot = initialInput,
                isChanged = false,
                isSaveEnabled = false
            )
        }
    }

    /**
     * 編集をキャンセルします。新規なら閉じ、既存なら閲覧モードに戻ります。
     */
    fun cancelEditSession() {
        val recordId = currentState.selectedRecordId
        if (recordId != null && IdLogic.isNew(recordId)) {
            setSelectedRecordId(null)
        } else {
            updateUiState { it.copy(isEditing = false) }
        }
    }

    /**
     * 入力フォームの内容を更新し、変更検知とバリデーションを再計算します。
     */
    fun updateEditInput(update: (HealthEditInput) -> HealthEditInput) {
        updateUiState { state ->
            val nextInput = update(state.editInput)
            val isChanged = nextInput != state.initialSnapshot

            // バリデーション
            val validationResult = PersonHealthLogic.validateInputs(state.currentCategory, nextInput.toValidationMap())
            val isDateTimeValid = nextInput.recordTime != null
            val isSaveEnabled = (validationResult == HealthInputValidationResult.SUCCESS) && isDateTimeValid && isChanged

            state.copy(
                editInput = nextInput,
                isChanged = isChanged,
                isSaveEnabled = isSaveEnabled
            )
        }
    }

    /**
     * 現在の入力内容で保存を実行します。
     */
    fun saveCurrentEdit() {
        val input = currentState.editInput
        val category = currentState.currentCategory
        val recordId = currentState.selectedRecordId ?: ""
        val recordTime = input.recordTime ?: return

        val values = input.toValidationMap().mapValues { (_, v) ->
            if (v.isBlank()) null
            else if (v.contains(".")) v.toDoubleOrNull()
            else v.toIntOrNull() ?: v.toDoubleOrNull()
        }

        saveRecord(category, recordId, recordTime, values)
    }

    private fun HealthEditInput.toValidationMap(): Map<String, String> {
        return mapOf(
            "height" to heightText,
            "weight" to weightText,
            "bpSystolic" to bpSystolicText,
            "bpDiastolic" to bpDiastolicText,
            "sat" to satText,
            "pulse" to pulseText,
            "bodyTemperature" to bodyTemperatureText,
            "glucose" to glucoseText,
            "hba1c" to hba1cText
        )
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
        // 二重実行防止
        if (saveJob?.isActive == true) return

        saveJob = safeLaunch(
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
            
            // プロセッサを使用した重複チェック
            val processor = HealthProcessorRegistry.getByGeneralCategory(category)
            val existing = processor?.findExisting(healthRepository, record.personId, record.recordTime) as? HistoryRecord

            val duplicateResult = PersonHealthLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            if (processor != null) {
                processor.save(healthRepository, record, featureName, OP_SAVE, isUpdate)
            }

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

    fun deleteRecord(record: Any) {
        // 二重実行防止
        if (deleteJob?.isActive == true) return

        deleteJob = safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = (record as? HistoryRecord)?.id
            }
        ) {
            val historyRecord = record as? HistoryRecord
            val processor = historyRecord?.let {
                val cat = when (it) {
                    is HeightAndWeight -> Category.HEIGHT_AND_WEIGHT
                    is BpAndPulse -> Category.BP_AND_PULSE
                    is GlucoseAndHbA1c -> Category.GLUCOSE_AND_HBA1C
                    else -> null
                }
                cat?.let { c -> HealthProcessorRegistry.getByGeneralCategory(c) }
            }

            if (processor != null) {
                processor.delete(healthRepository, record, featureName, OP_DELETE)
            }
            showSnackbar(R.string.p_health_msg_delete_success)
        }
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
