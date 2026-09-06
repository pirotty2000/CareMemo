package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputOperation
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputScreenState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputSession
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.HealthProcessorRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * ViewModel：BatchInputViewModel
 *
 * 【役割】
 * 健康記録の一括入力画面（SCR-PH-002）における状態管理と保存実行を制御します。
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<BatchInputUiState, BatchInputViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    securitySession,
    auditLogRepository,
    BatchInputUiState(),
    savedStateHandle
) {

    companion object {
        private const val FEATURE_NAME = "BatchInput"
        private const val OP_SAVE_BATCH = "saveBatch"
        private const val TABLE_HEALTH = "health_db"

        // --- Restoration Keys ---
        private const val KEY_IN_HEIGHT = "restoration_in_height"
        private const val KEY_IN_WEIGHT = "restoration_in_weight"
        private const val KEY_IN_BP_S = "restoration_in_bp_s"
        private const val KEY_IN_BP_D = "restoration_in_bp_d"
        private const val KEY_IN_SAT = "restoration_in_sat"
        private const val KEY_IN_PULSE = "restoration_in_pulse"
        private const val KEY_IN_TEMP = "restoration_in_temp"
        private const val KEY_IN_GLUCOSE = "restoration_in_glucose"
        private const val KEY_IN_HBA1C = "restoration_in_hba1c"
        
        private const val KEY_IN_YEAR = "restoration_in_year"
        private const val KEY_IN_MONTH = "restoration_in_month"
        private const val KEY_IN_DAY = "restoration_in_day"
        private const val KEY_IN_HOUR = "restoration_in_hour"
        private const val KEY_IN_MINUTE = "restoration_in_minute"

        private const val KEY_BASE_YEAR = "restoration_base_year"
        private const val KEY_BASE_MONTH = "restoration_base_month"
        private const val KEY_BASE_DAY = "restoration_base_day"
        private const val KEY_BASE_HOUR = "restoration_base_hour"
        private const val KEY_BASE_MINUTE = "restoration_base_minute"
    }

    override val featureName: String = FEATURE_NAME

    /** 復元中であることを示すフラグ */
    private var isRestoring = false

    /** 保存処理の実行状態を管理する Job */
    private var saveJob: Job? = null

    init {
        // --- State Restoration ---
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // 最後に監視を開始 (featureName が初期化された後)
        startObservePersonId()
    }

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val handle = savedStateHandle ?: return
        updateUiState { current ->
            val input = current.input
            current.copy(
                screenState = BatchInputScreenState.Active,
                input = input.copy(
                    height = handle.get<String>(KEY_IN_HEIGHT) ?: "",
                    weight = handle.get<String>(KEY_IN_WEIGHT) ?: "",
                    bpSystolic = handle.get<String>(KEY_IN_BP_S) ?: "",
                    bpDiastolic = handle.get<String>(KEY_IN_BP_D) ?: "",
                    sat = handle.get<String>(KEY_IN_SAT) ?: "",
                    pulse = handle.get<String>(KEY_IN_PULSE) ?: "",
                    bodyTemperature = handle.get<String>(KEY_IN_TEMP) ?: "",
                    glucose = handle.get<String>(KEY_IN_GLUCOSE) ?: "",
                    hba1c = handle.get<String>(KEY_IN_HBA1C) ?: "",
                    year = handle.get<String>(KEY_IN_YEAR) ?: input.year,
                    month = handle.get<String>(KEY_IN_MONTH) ?: input.month,
                    day = handle.get<String>(KEY_IN_DAY) ?: input.day,
                    hour = handle.get<String>(KEY_IN_HOUR) ?: input.hour,
                    minute = handle.get<String>(KEY_IN_MINUTE) ?: input.minute,
                    initialYear = handle.get<String>(KEY_BASE_YEAR) ?: input.initialYear,
                    initialMonth = handle.get<String>(KEY_BASE_MONTH) ?: input.initialMonth,
                    initialDay = handle.get<String>(KEY_BASE_DAY) ?: input.initialDay,
                    initialHour = handle.get<String>(KEY_BASE_HOUR) ?: input.initialHour,
                    initialMinute = handle.get<String>(KEY_BASE_MINUTE) ?: input.initialMinute
                ).let { nextInput ->
                    nextInput.copy(
                        isValid = BatchInputLogic.isValid(nextInput),
                        isChanged = BatchInputLogic.isChanged(nextInput),
                        recordTime = BatchInputLogic.calculateRecordTime(nextInput)
                    )
                }
            )
        }
    }

    /**
     * 復元対象の状態をバックアップします。
     */
    private fun backupRestorableState(state: BatchInputUiState) {
        val handle = savedStateHandle ?: return
        val input = state.input
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_IN_HEIGHT] = input.height
        handle[KEY_IN_WEIGHT] = input.weight
        handle[KEY_IN_BP_S] = input.bpSystolic
        handle[KEY_IN_BP_D] = input.bpDiastolic
        handle[KEY_IN_SAT] = input.sat
        handle[KEY_IN_PULSE] = input.pulse
        handle[KEY_IN_TEMP] = input.bodyTemperature
        handle[KEY_IN_GLUCOSE] = input.glucose
        handle[KEY_IN_HBA1C] = input.hba1c
        handle[KEY_IN_YEAR] = input.year
        handle[KEY_IN_MONTH] = input.month
        handle[KEY_IN_DAY] = input.day
        handle[KEY_IN_HOUR] = input.hour
        handle[KEY_IN_MINUTE] = input.minute
        handle[KEY_BASE_YEAR] = input.initialYear
        handle[KEY_BASE_MONTH] = input.initialMonth
        handle[KEY_BASE_DAY] = input.initialDay
        handle[KEY_BASE_HOUR] = input.initialHour
        handle[KEY_BASE_MINUTE] = input.initialMinute
    }

    /**
     * 復元用データを破棄します。
     */
    private fun clearRestorableState() {
        clearRestorableState(
            KEY_IN_HEIGHT, KEY_IN_WEIGHT, KEY_IN_BP_S, KEY_IN_BP_D, KEY_IN_SAT, KEY_IN_PULSE, KEY_IN_TEMP, KEY_IN_GLUCOSE, KEY_IN_HBA1C,
            KEY_IN_YEAR, KEY_IN_MONTH, KEY_IN_DAY, KEY_IN_HOUR, KEY_IN_MINUTE,
            KEY_BASE_YEAR, KEY_BASE_MONTH, KEY_BASE_DAY, KEY_BASE_HOUR, KEY_BASE_MINUTE
        )
    }

    override fun copyWithLoadingState(state: BatchInputUiState, isLoading: Boolean, category: LoadingCategory): BatchInputUiState {
        return when (category) {
            is LoadingCategory.Structural, is LoadingCategory.Default -> {
                val nextScreenState = if (!isLoading) {
                    if (state.screenState is BatchInputScreenState.Error) state.screenState else BatchInputScreenState.Active
                } else {
                    if (state.screenState is BatchInputScreenState.Active) state.screenState else BatchInputScreenState.Loading
                }
                state.copy(screenState = nextScreenState, isLoading = isLoading)
            }
            is LoadingCategory.Operation -> {
                if (!isLoading) state.copy(operation = BatchInputOperation.Idle) else state
            }
            else -> state.copy(isLoading = isLoading)
        }
    }

    override fun updateWithPersonData(
        state: BatchInputUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): BatchInputUiState {
        // 復元中の場合は、初期化によるリセットをスキップして現在の状態を維持する
        if (isRestoring) {
            isRestoring = false // 復元処理を消費
            return state.copy(
                personId = person.id,
                person = person,
                personSummary = summary
            )
        }

        // 利用者が切り替わった場合、または初回ロード時は、入力をリセットし、記録日時を現在時刻に設定する
        val isFirstLoad = state.personId == null
        val isDifferentPerson = state.personId != person.id
        val input = state.input
        
        val next = if (isFirstLoad || isDifferentPerson) {
            val now = Instant.now().atZone(ZoneId.systemDefault())
            val y = now.year.toString()
            val m = now.monthValue.toString()
            val d = now.dayOfMonth.toString()
            val h = "%02d".format(now.hour)
            val min = "%02d".format(now.minute)

            state.copy(
                personId = person.id,
                person = person,
                personSummary = summary,
                input = input.copy(
                    height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                    sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                    year = y, month = m, day = d, hour = h, minute = min,
                    initialYear = y, initialMonth = m, initialDay = d, initialHour = h, initialMinute = min
                )
            )
        } else {
            // 同一利用者の再ロード時は、基本情報とサマリーのみ更新し、入力中の日時は維持する
            state.copy(
                personId = person.id,
                person = person,
                personSummary = summary
            )
        }
        
        // 最新の状態に基づき、バリデーションと変更有無、記録日時を再計算して返す
        return next.copy(
            input = next.input.copy(
                isValid = BatchInputLogic.isValid(next.input),
                isChanged = BatchInputLogic.isChanged(next.input),
                recordTime = BatchInputLogic.calculateRecordTime(next.input)
            )
        )
    }

    // --- UI 入力更新用メソッド群 ---

    fun updateYear(v: String) = updateInput { it.copy(year = v) }
    fun updateMonth(v: String) = updateInput { it.copy(month = v) }
    fun updateDay(v: String) = updateInput { it.copy(day = v) }
    fun updateHour(v: String) = updateInput { it.copy(hour = v) }
    fun updateMinute(v: String) = updateInput { it.copy(minute = v) }

    fun updateHeight(v: String) = updateInput { it.copy(height = v) }
    fun updateWeight(v: String) = updateInput { it.copy(weight = v) }
    fun updateBpSystolic(v: String) = updateInput { it.copy(bpSystolic = v) }
    fun updateBpDiastolic(v: String) = updateInput { it.copy(bpDiastolic = v) }
    fun updateSat(v: String) = updateInput { it.copy(sat = v) }
    fun updatePulse(v: String) = updateInput { it.copy(pulse = v) }
    fun updateBodyTemp(v: String) = updateInput { it.copy(bodyTemperature = v) }
    fun updateGlucose(v: String) = updateInput { it.copy(glucose = v) }
    fun updateHbA1c(v: String) = updateInput { it.copy(hba1c = v) }

    /** フィールドにフォーカスが当たったことを記録します */
    fun markFieldAsTouched(fieldName: String) {
        updateUiState { state ->
            val input = state.input
            val nextTouched = input.touchedFields + fieldName
            val (errors, errorArgs) = calculateFieldErrors(input, nextTouched)
            val next = state.copy(
                input = input.copy(
                    touchedFields = nextTouched,
                    fieldErrors = errors,
                    fieldErrorArgs = errorArgs
                )
            )
            backupRestorableState(next)
            next
        }
    }

    private fun updateInput(reducer: (BatchInputSession) -> BatchInputSession) {
        updateUiState { current ->
            val oldInput = current.input
            val partialNextInput = reducer(oldInput)
            
            // 操作されたフィールドの特定
            val nextTouched = getNewlyTouchedFields(oldInput, partialNextInput, oldInput.touchedFields)

            // バリデーション結果、変更検知、記録日時を算出
            val finalIsValid = BatchInputLogic.isValid(partialNextInput)
            val finalIsChanged = BatchInputLogic.isChanged(partialNextInput)
            val finalRecordTime = BatchInputLogic.calculateRecordTime(partialNextInput)
            
            // フィールドごとのエラーを計算
            val (errors, errorArgs) = calculateFieldErrors(partialNextInput, nextTouched)

            val next = current.copy(
                input = partialNextInput.copy(
                    isValid = finalIsValid,
                    isChanged = finalIsChanged,
                    recordTime = finalRecordTime,
                    touchedFields = nextTouched,
                    fieldErrors = errors,
                    fieldErrorArgs = errorArgs
                )
            )
            backupRestorableState(next)
            next
        }
    }

    private fun getNewlyTouchedFields(old: BatchInputSession, next: BatchInputSession, current: Set<String>): Set<String> {
        val touched = current.toMutableSet()
        if (old.height != next.height) touched.add("height")
        if (old.weight != next.weight) touched.add("weight")
        if (old.bpSystolic != next.bpSystolic) touched.add("bpSystolic")
        if (old.bpDiastolic != next.bpDiastolic) touched.add("bpDiastolic")
        if (old.sat != next.sat) touched.add("sat")
        if (old.pulse != next.pulse) touched.add("pulse")
        if (old.bodyTemperature != next.bodyTemperature) touched.add("bodyTemperature")
        if (old.glucose != next.glucose) touched.add("glucose")
        if (old.hba1c != next.hba1c) touched.add("hba1c")
        if (old.year != next.year) touched.add("year")
        if (old.month != next.month) touched.add("month")
        if (old.day != next.day) touched.add("day")
        if (old.hour != next.hour) touched.add("hour")
        if (old.minute != next.minute) touched.add("minute")
        return touched
    }

    private fun calculateFieldErrors(
        input: BatchInputSession,
        touched: Set<String>
    ): Pair<Map<String, Int?>, Map<String, List<String>>> {
        val errors = mutableMapOf<String, Int?>()
        val errorArgs = mutableMapOf<String, List<String>>()

        val fields = listOf(
            "height", "weight", "bpSystolic", "bpDiastolic", "sat", "pulse", "bodyTemperature", "glucose", "hba1c"
        )

        fields.forEach { field ->
            if (touched.contains(field)) {
                val value = getValueForField(input, field)
                val result = validateSingleField(field, value)
                if (result != HealthInputValidationResult.SUCCESS) {
                    errors[field] = translateHealthValidationResult(result)
                    if (result == HealthInputValidationResult.OUT_OF_RANGE) {
                        errorArgs[field] = getRangeArgs(field)
                    }
                }
            }
        }

        // 記録日時のチェック
        val timeTouched = touched.intersect(setOf("year", "month", "day", "hour", "minute")).isNotEmpty()
        if (timeTouched) {
            val recordTime = BatchInputLogic.calculateRecordTime(input)
            if (recordTime == null) {
                errors["recordTime"] = R.string.common_err_invalid_date
            } else if (recordTime.isAfter(Instant.now())) {
                errors["recordTime"] = R.string.common_err_future_date_not_allowed
            }
        }

        return errors to errorArgs
    }

    private fun getValueForField(input: BatchInputSession, field: String): String {
        return when (field) {
            "height" -> input.height
            "weight" -> input.weight
            "bpSystolic" -> input.bpSystolic
            "bpDiastolic" -> input.bpDiastolic
            "sat" -> input.sat
            "pulse" -> input.pulse
            "bodyTemperature" -> input.bodyTemperature
            "glucose" -> input.glucose
            "hba1c" -> input.hba1c
            else -> ""
        }
    }

    private fun validateSingleField(field: String, value: String): HealthInputValidationResult {
        if (value.isBlank()) return HealthInputValidationResult.SUCCESS
        val spec = getSpecForField(field) ?: return HealthInputValidationResult.SUCCESS

        val isValid = jp.mydns.fujiwara.carememo.logic.common.HealthLogic.isWithinFormat(
            value, spec.digitsInt, spec.digitsDec, spec.min, spec.max
        )

        if (!isValid) {
            val num = value.toDoubleOrNull()
            return if (num == null || !jp.mydns.fujiwara.carememo.logic.common.HealthLogic.isWithinFormat(value, spec.digitsInt, spec.digitsDec)) {
                HealthInputValidationResult.INVALID_FORMAT
            } else {
                HealthInputValidationResult.OUT_OF_RANGE
            }
        }
        return HealthInputValidationResult.SUCCESS
    }

    private data class FieldSpec(val digitsInt: Int, val digitsDec: Int, val min: Double, val max: Double)

    private fun getSpecForField(field: String): FieldSpec? {
        return when (field) {
            "height" -> AppSpecifications.Health.Height.run { FieldSpec(DIGITS_INT, DIGITS_DEC, MIN_VALUE, MAX_VALUE) }
            "weight" -> AppSpecifications.Health.Weight.run { FieldSpec(DIGITS_INT, DIGITS_DEC, MIN_VALUE, MAX_VALUE) }
            "bpSystolic", "bpDiastolic" -> AppSpecifications.Health.BloodPressure.run { FieldSpec(DIGITS_INT, 0, MIN_VALUE, MAX_VALUE) }
            "sat" -> AppSpecifications.Health.OxygenSaturation.run { FieldSpec(DIGITS_INT, 0, MIN_VALUE, MAX_VALUE) }
            "pulse" -> AppSpecifications.Health.Pulse.run { FieldSpec(DIGITS_INT, 0, MIN_VALUE, MAX_VALUE) }
            "bodyTemperature" -> AppSpecifications.Health.BodyTemperature.run { FieldSpec(DIGITS_INT, DIGITS_DEC, MIN_VALUE, MAX_VALUE) }
            "glucose" -> AppSpecifications.Health.BloodGlucose.run { FieldSpec(DIGITS_INT, 0, MIN_VALUE, MAX_VALUE) }
            "hba1c" -> AppSpecifications.Health.HbA1c.run { FieldSpec(DIGITS_INT, DIGITS_DEC, MIN_VALUE, MAX_VALUE) }
            else -> null
        }
    }

    private fun getRangeArgs(field: String): List<String> {
        val spec = getSpecForField(field) ?: return emptyList()
        val minStr = if (spec.digitsDec > 0) "%.1f".format(spec.min) else spec.min.toInt().toString()
        val maxStr = if (spec.digitsDec > 0) "%.1f".format(spec.max) else spec.max.toInt().toString()
        return listOf(minStr, maxStr)
    }

    private fun translateHealthValidationResult(result: HealthInputValidationResult): Int? {
        return when (result) {
            HealthInputValidationResult.INVALID_FORMAT -> R.string.common_error_invalid_input
            HealthInputValidationResult.OUT_OF_RANGE -> R.string.health_err_range_format
            else -> null
        }
    }

    /**
     * 入力された全カテゴリのデータを一括保存します。
     */
    fun saveBatch() {
        if (saveJob?.isActive == true) return

        val input = currentState.input
        val time = input.recordTime ?: return

        updateUiState { it.copy(operation = BatchInputOperation.Saving) }

        saveJob = safeLaunch(
            operation = OP_SAVE_BATCH,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = requiredPersonId
            }
        ) {
            val validationResult = BatchInputLogic.validate(input)
            if (validationResult != BatchInputValidationResult.SUCCESS) {
                translateValidationResult(validationResult, input)
            }

            val duplicateResIds = HealthProcessorRegistry.getAll()
                .filter { !it.isEmpty(input) }
                .filter { healthRepository.findHistoryRecordAtTime(it.generalCategory, requiredPersonId, time) != null }
                .map { it.categoryNameResId }

            if (duplicateResIds.isNotEmpty()) {
                val categoryNames = duplicateResIds.joinToString("、") { "__RES__$it" }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = R.string.batch_err_duplicate_blocked,
                    args = listOf(categoryNames),
                    logMessage = "Duplicate detected in categories: $duplicateResIds"
                )
            }
            
            val entities = BatchInputLogic.createEntities(requiredPersonId, time, input)
            healthRepository.saveHealthDataBatch(entities, featureName, OP_SAVE_BATCH)

            sendViewEvent(BatchInputViewEvent.SaveSuccessEffects)
            sendUiEvent(UiEvent.SaveSuccess())
            showSnackbar(R.string.batch_msg_save_success)
            
            updateUiState { current ->
                val session = current.input
                val nextSession = session.copy(
                    height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                    sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                    initialYear = session.year,
                    initialMonth = session.month,
                    initialDay = session.day,
                    initialHour = session.hour,
                    initialMinute = session.minute,
                    isChanged = false,
                    isValid = false
                )
                current.copy(
                    input = nextSession.copy(recordTime = BatchInputLogic.calculateRecordTime(nextSession))
                )
            }
            clearRestorableState()
        }
    }

    private fun translateValidationResult(result: BatchInputValidationResult, input: BatchInputSession) {
        val messageRes = when (result) {
            BatchInputValidationResult.EMPTY_ALL -> R.string.p_detail_empty_records
            else -> R.string.common_error_save
        }

        val args = if (result == BatchInputValidationResult.INVALID_VALUE) {
            val details = HealthProcessorRegistry.getAll()
                .filter { !it.isEmpty(input) && (it.validate(input) == HealthInputValidationResult.OUT_OF_RANGE) }
                .map { "__RES__${it.outOfRangeErrorResId}" }
            
            if (details.isEmpty()) listOf("__RES__${R.string.common_error_invalid_input}") else listOf(details.joinToString("、"))
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

    fun navigateBack() {
        sendViewEvent(BatchInputViewEvent.NavigateBack)
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return BatchInputViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
