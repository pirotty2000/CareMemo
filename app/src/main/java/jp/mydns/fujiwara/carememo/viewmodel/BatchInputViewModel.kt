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
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * A系統（健康記録）の一括入力画面専用の ViewModel。
 * 複数のカテゴリを同時に保存し、連続入力のための状態管理を行います。
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

    init {
        // 利用者情報がロードされたらローディング状態を解除する
        currentPerson.onEach { person ->
            if (person != null) {
                _isLoading.value = false
            }
        }.launchIn(viewModelScope)
    }

    private val _recordTime = MutableStateFlow(Instant.now())
    val recordTime = _recordTime.asStateFlow()

    // UI状態の一括管理
    private val _uiState = MutableStateFlow(BatchInputUiState())
    val uiState: StateFlow<BatchInputUiState> = _uiState.asStateFlow()

    // UI側からの直接アクセス用 (プロパティ委譲のような形式)
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
     * 現在の入力内容が保存可能かどうかを判定する（A系統のルールに基づく）。
     * いずれかのカテゴリが有効な入力を持っていれば true を返す。
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
     * 値が入力されているカテゴリのみが保存対象となります。
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
            // --- 重複チェック (新規登録のみを許可するため) ---
            val duplicateCategories = mutableListOf<Int>()
            
            if (HealthLogic.isValidHeightAndWeight(currentState.height, currentState.weight)) {
                if (healthRepository.findHeightAndWeightAtTime(person.id, time) != null) {
                    duplicateCategories.add(R.string.common_category_height_weight)
                }
            }
            if (HealthLogic.isValidBpAndPulse(currentState.bpSystolic, currentState.bpDiastolic, currentState.sat, currentState.pulse, currentState.bodyTemperature)) {
                if (healthRepository.findBpAndPulseAtTime(person.id, time) != null) {
                    duplicateCategories.add(R.string.common_category_vital)
                }
            }
            if (HealthLogic.isValidGlucoseAndHbA1c(currentState.glucose, currentState.hba1c)) {
                if (healthRepository.findGlucoseAndHbA1cAtTime(person.id, time) != null) {
                    duplicateCategories.add(R.string.common_category_glucose)
                }
            }

            if (duplicateCategories.isNotEmpty()) {
                // 重複がある場合は保存をブロック
                val categoryNames = duplicateCategories.joinToString("、") { "__RES__$it" }
                sendUiEvent(UiEvent.ShowErrorDialogRes(
                    R.string.common_error_title_save,
                    R.string.batch_err_duplicate_blocked,
                    listOf(categoryNames)
                ))
                return@safeLaunch
            }
            
            // --- 保存実行 ---
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
            
            // 保存成功後にクリア
            resetInputs()
        }
    }

    /**
     * 入力値をリセットします（次の利用者の入力に備えるため）。
     */
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
