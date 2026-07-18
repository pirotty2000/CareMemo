package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputCategory
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * A系統（健康記録）の一括入力画面専用の ViewModel。
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository) {

    companion object {
        private const val FEATURE_NAME = "BatchInput"
        private const val OP_SAVE_BATCH = "saveBatch"
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    private val _recordTime = MutableStateFlow(Instant.now())
    val recordTime = _recordTime.asStateFlow()

    private val _uiState = MutableStateFlow(BatchInputUiState())
    val uiState: StateFlow<BatchInputUiState> = _uiState.asStateFlow()

    fun updateHeight(v: String) { _uiState.update { it.copy(height = v) } }
    fun updateWeight(v: String) { _uiState.update { it.copy(weight = v) } }
    fun updateBpSystolic(v: String) { _uiState.update { it.copy(bpSystolic = v) } }
    fun updateBpDiastolic(v: String) { _uiState.update { it.copy(bpDiastolic = v) } }
    fun updateSat(v: String) { _uiState.update { it.copy(sat = v) } }
    fun updatePulse(v: String) { _uiState.update { it.copy(pulse = v) } }
    fun updateBodyTemp(v: String) { _uiState.update { it.copy(bodyTemperature = v) } }
    fun updateGlucose(v: String) { _uiState.update { it.copy(glucose = v) } }
    fun updateHbA1c(v: String) { _uiState.update { it.copy(hba1c = v) } }

    /**
     * 現在の入力内容が保存可能かどうかを判定する。
     */
    val isInputValid: StateFlow<Boolean> = uiState.map { state ->
        BatchInputLogic.isValid(state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun setRecordTime(time: Instant) {
        _recordTime.value = time
    }

    override fun loadPerson(personId: Int) {
        val isDifferentPerson = currentPerson.value?.id != personId
        super.loadPerson(personId)
        if (isDifferentPerson) {
            resetInputs()
            _recordTime.value = Instant.now()
        }
    }

    /**
     * 入力された全データを一括保存します。
     */
    fun saveBatch() {
        val person = currentPerson.value ?: return
        val time = _recordTime.value
        val currentState = _uiState.value

        safeLaunch(
            operation = OP_SAVE_BATCH,
            loadingState = _isSaving,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = person.id.toString()
            }
        ) {
            // 1. バリデーション（事実の判定）
            val validationResult = BatchInputLogic.validate(currentState)

            // 2. バリデーション結果の翻訳（ViewModelの責務）
            if (validationResult != BatchInputValidationResult.SUCCESS) {
                translateValidationResult(validationResult, currentState)
            }

            // 3. 重複チェック
            val effectiveCategories = BatchInputLogic.getEffectiveCategories(currentState)
            val duplicateCategories = mutableListOf<Int>()
            val duplicateCategoryNames = mutableListOf<String>()

            effectiveCategories.forEach { category ->
                val isDuplicate = when (category) {
                    BatchInputCategory.HEIGHT_WEIGHT -> healthRepository.findHeightAndWeightAtTime(person.id, time) != null
                    BatchInputCategory.VITAL -> healthRepository.findBpAndPulseAtTime(person.id, time) != null
                    BatchInputCategory.GLUCOSE -> healthRepository.findGlucoseAndHbA1cAtTime(person.id, time) != null
                }
                if (isDuplicate) {
                    duplicateCategoryNames.add(category.name)
                    duplicateCategories.add(
                        when (category) {
                            BatchInputCategory.HEIGHT_WEIGHT -> R.string.common_category_height_weight
                            BatchInputCategory.VITAL -> R.string.common_category_vital
                            BatchInputCategory.GLUCOSE -> R.string.common_category_glucose
                        }
                    )
                }
            }

            if (duplicateCategories.isNotEmpty()) {
                val categoryNames = duplicateCategories.joinToString("、") { "__RES__$it" }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = R.string.batch_err_duplicate_blocked,
                    args = listOf(categoryNames),
                    logMessage = "Duplicate categories detected: ${duplicateCategoryNames.joinToString(", ")}"
                )
            }
            
            // 4. 保存実行 (バリデーション済みなので安全に Entity 生成)
            val entities = BatchInputLogic.createEntities(person.id, time, currentState)
            entities.forEach { entity ->
                when (entity) {
                    is HeightAndWeight -> healthRepository.insertHeightAndWeight(entity, featureName, OP_SAVE_BATCH)
                    is BpAndPulse -> healthRepository.insertBpAndPulse(entity, featureName, OP_SAVE_BATCH)
                    is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(entity, featureName, OP_SAVE_BATCH)
                }
            }

            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.batch_msg_save_success)
            
            resetInputs()
        }
    }

    /**
     * バリデーション結果を詳細な例外に翻訳します。
     */
    private fun translateValidationResult(result: BatchInputValidationResult, state: BatchInputUiState) {
        if (result == BatchInputValidationResult.SUCCESS) return

        val messageRes = when (result) {
            BatchInputValidationResult.EMPTY_ALL -> R.string.p_detail_empty_records
            BatchInputValidationResult.INVALID_VALUE -> R.string.common_error_save
            else -> R.string.common_error_save
        }

        val args = if (result == BatchInputValidationResult.INVALID_VALUE) {
            val details = mutableListOf<String>()
            if (HealthLogic.validateHeightAndWeight(state.height, state.weight) == HealthInputValidationResult.OUT_OF_RANGE) details.add("身長・体重が範囲外です")
            if (HealthLogic.validateBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature) == HealthInputValidationResult.OUT_OF_RANGE) details.add("バイタルが範囲外です")
            if (HealthLogic.validateGlucoseAndHbA1c(state.glucose, state.hba1c) == HealthInputValidationResult.OUT_OF_RANGE) details.add("血糖値が範囲外です")
            
            if (details.isEmpty()) listOf("入力値が正しくありません") else listOf(details.joinToString("\n"))
        } else {
            emptyList()
        }

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
    }

    fun resetInputs() {
        _uiState.value = BatchInputUiState()
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
            if (modelClass.isAssignableFrom(BatchInputViewModel::class.java)) {
                return BatchInputViewModel(
                    healthRepository,
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
