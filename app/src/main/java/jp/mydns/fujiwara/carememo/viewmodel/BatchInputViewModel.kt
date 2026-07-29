package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
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
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import kotlinx.coroutines.launch
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
) : PersonBaseUiStateViewModel<BatchInputUiState, BatchInputViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    BatchInputUiState()
) {

    companion object {
        private const val FEATURE_NAME = "BatchInput"
        private const val OP_SAVE_BATCH = "saveBatch"
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 共通設定の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
    }

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: BatchInputUiState, isLoading: Boolean): BatchInputUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: BatchInputUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): BatchInputUiState {
        // 利用者が切り替わった場合は入力をリセットし、日時を現在時刻にする
        val isDifferentPerson = state.personId != person.id
        val next = if (isDifferentPerson) {
            val now = Instant.now()
            state.copy(
                personId = person.id,
                person = person,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary,
                height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                recordTime = now,
                initialRecordTime = now
            )
        } else {
            state.copy(
                personId = person.id,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary
            )
        }
        // 派生状態（isValid, isChanged）を更新して返す
        return next.copy(
            isValid = BatchInputLogic.isValid(next),
            isChanged = BatchInputLogic.isChanged(next)
        )
    }

    // --- 更新用メソッド群 (原子的な一括更新) ---

    fun setRecordTime(time: Instant) = updateState { it.copy(recordTime = time) }
    fun updateHeight(v: String) = updateState { it.copy(height = v) }
    fun updateWeight(v: String) = updateState { it.copy(weight = v) }
    fun updateBpSystolic(v: String) = updateState { it.copy(bpSystolic = v) }
    fun updateBpDiastolic(v: String) = updateState { it.copy(bpDiastolic = v) }
    fun updateSat(v: String) = updateState { it.copy(sat = v) }
    fun updatePulse(v: String) = updateState { it.copy(pulse = v) }
    fun updateBodyTemp(v: String) = updateState { it.copy(bodyTemperature = v) }
    fun updateGlucose(v: String) = updateState { it.copy(glucose = v) }
    fun updateHbA1c(v: String) = updateState { it.copy(hba1c = v) }

    private fun updateState(reducer: (BatchInputUiState) -> BatchInputUiState) {
        updateUiState { current ->
            val next = reducer(current)
            next.copy(
                isValid = BatchInputLogic.isValid(next),
                isChanged = BatchInputLogic.isChanged(next)
            )
        }
    }

    /**
     * 入力された全データを一括保存します。
     */
    fun saveBatch() {
        val state = currentState
        val time = state.recordTime

        safeLaunch(
            operation = OP_SAVE_BATCH,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = requiredPersonId
            }
        ) {
            // 1. バリデーション（事実の判定）
            val validationResult = BatchInputLogic.validate(state)

            // 2. 翻訳
            if (validationResult != BatchInputValidationResult.SUCCESS) {
                translateValidationResult(validationResult, state)
            }

            // 3. 重複チェック
            val effectiveCategories = BatchInputLogic.getEffectiveCategories(state)
            val duplicateCategoryNames = mutableListOf<String>()
            val duplicateResIds = mutableListOf<Int>()

            effectiveCategories.forEach { category ->
                val isDuplicate = when (category) {
                    BatchInputCategory.HEIGHT_WEIGHT -> healthRepository.findHeightAndWeightAtTime(requiredPersonId, time) != null
                    BatchInputCategory.VITAL -> healthRepository.findBpAndPulseAtTime(requiredPersonId, time) != null
                    BatchInputCategory.GLUCOSE -> healthRepository.findGlucoseAndHbA1cAtTime(requiredPersonId, time) != null
                }
                if (isDuplicate) {
                    duplicateCategoryNames.add(category.name)
                    duplicateResIds.add(
                        when (category) {
                            BatchInputCategory.HEIGHT_WEIGHT -> R.string.common_category_height_weight
                            BatchInputCategory.VITAL -> R.string.common_category_vital
                            BatchInputCategory.GLUCOSE -> R.string.common_category_glucose
                        }
                    )
                }
            }

            if (duplicateResIds.isNotEmpty()) {
                val categoryNames = duplicateResIds.joinToString("、") { "__RES__$it" }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = R.string.batch_err_duplicate_blocked,
                    args = listOf(categoryNames),
                    logMessage = "Duplicate categories detected: ${duplicateCategoryNames.joinToString(", ")}"
                )
            }
            
            // 4. 保存実行
            val entities = BatchInputLogic.createEntities(requiredPersonId, time, state)
            entities.forEach { entity ->
                when (entity) {
                    is HeightAndWeight -> healthRepository.insertHeightAndWeight(entity, featureName, OP_SAVE_BATCH)
                    is BpAndPulse -> healthRepository.insertBpAndPulse(entity, featureName, OP_SAVE_BATCH)
                    is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(entity, featureName, OP_SAVE_BATCH)
                }
            }

            // 保存成功イベント (演出用)
            sendViewEvent(BatchInputViewEvent.SaveSuccessEffects)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.batch_msg_save_success)
            
            // 保存後のリセット（日時は保持、数値はクリア、変更基準点を更新）
            updateUiState { current ->
                current.copy(
                    height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                    sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                    initialRecordTime = current.recordTime,
                    isChanged = false,
                    isValid = false
                )
            }
        }
    }

    private fun translateValidationResult(result: BatchInputValidationResult, state: BatchInputUiState) {
        val messageRes = when (result) {
            BatchInputValidationResult.EMPTY_ALL -> R.string.p_detail_empty_records
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

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BatchInputViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
