package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者健康記録（身長体重、バイタル、血糖値）固有のロジックを扱う ViewModel。
 */
class PersonHealthViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseUiStateViewModel<PersonHealthUiState, PersonHealthViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonHealthUiState()
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
        // 表示モードの永続化設定を購読 (案Aの追加)
        scope.launch {
            userSettingsRepository.healthDisplayModeIsHistory.collect { isHistory ->
                updateUiState { it.copy(preferredShowHistory = isHistory) }
            }
        }
    }

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: PersonHealthUiState, isLoading: Boolean): PersonHealthUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun getPersonId(state: PersonHealthUiState): String? = state.personId

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
        // ロード開始時にデータをクリアする（表示モードのリセットは loadPerson 側で永続化層に対して行う）
        return state.copy(personId = null, records = emptyList(), selectedRecordId = null)
    }

    /**
     * 履歴/グラフの表示優先設定を更新します。
     */
    fun updatePreferredShowHistory(preferredShowHistory: Boolean) {
        scope.launch {
            userSettingsRepository.setHealthDisplayModeIsHistory(preferredShowHistory)
        }
    }

    /**
     * 表示カテゴリを設定します。
     */
    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category, selectedRecordId = null) }
            refreshRecords(currentState.personId, category)
        }
    }

    /**
     * 選択されたレコードIDを設定します。
     */
    fun setSelectedRecordId(id: String?) {
        updateUiState { it.copy(selectedRecordId = id) }
    }

    /**
     * 指定されたカテゴリと人物に基づき、履歴データを購読します。
     */
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
            updateUiState { it.copy(records = records) }
        }
    }

    /**
     * 指定された数値系カテゴリの履歴データを取得します(拡大表示画面などで使用)。
     */
    fun getHealthRecords(category: Category): StateFlow<List<HistoryRecord>> {
        val personId = currentState.personId ?: return flowOf(emptyList<HistoryRecord>()).stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(personId)
            Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(personId)
            Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(personId)
            else -> flowOf(emptyList())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * 数値系レコードを保存または更新します。
     */
    fun saveRecord(record: Any?, originalId: String) {
        if (record !is HistoryRecord) return
        
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = record.id
            }
        ) {
            // 1. バリデーション
            val validationResult = PersonHealthLogic.validate(record)
            translateValidationResult(validationResult)

            val isUpdate = !IdLogic.isNew(originalId)

            // 2. 重複チェック
            val existing = when (record) {
                is HeightAndWeight -> healthRepository.findHeightAndWeightAtTime(record.personId, record.recordTime)
                is BpAndPulse -> healthRepository.findBpAndPulseAtTime(record.personId, record.recordTime)
                is GlucoseAndHbA1c -> healthRepository.findGlucoseAndHbA1cAtTime(record.personId, record.recordTime)
                else -> null
            }

            val duplicateResult = PersonHealthLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            // 3. 保存実行
            performSave(record, isUpdate)
            sendUiEvent(UiEvent.SaveSuccess)
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

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
    }

    private suspend fun performSave(record: Any, isUpdate: Boolean) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record, featureName, OP_SAVE, isUpdate)
        is BpAndPulse -> healthRepository.insertBpAndPulse(record, featureName, OP_SAVE, isUpdate)
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record, featureName, OP_SAVE, isUpdate)
        else -> {}
    }

    /**
     * 数値系レコードを削除します。
     */
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

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonHealthViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
