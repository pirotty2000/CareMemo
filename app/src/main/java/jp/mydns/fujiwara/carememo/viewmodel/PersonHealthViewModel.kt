package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
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
 *
 * 【役割】
 * 健康記録（身長体重、バイタル、血糖値等）の表示、入力、保存、削除のライフサイクルを管理します。
 *
 * 【設計指針：UI 境界の責務】
 * 1. 状態の不変化：Repository や Logic から渡される標準の List を、UI での安定したレンダリングのために 
 *    `toImmutableList()` を用いて ImmutableList へ変換し、UiState として公開します。
 * 2. 業務ロジックの集約：変更検知 (`isChanged`) や保存の妥当性判定 (`isSaveEnabled`) を 
 *    Composable から ViewModel へ移行し、純粋な業務判断として集中管理します。
 *
 * 【この ViewModel では行わないこと】
 * ・個別の異常値判定の具体的閾値計算（HealthLogic が担当）。
 * ・グラフ描画用の設定生成（HealthChartHelper が担当）。
 */
class PersonHealthViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonHealthUiState, PersonHealthViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    securitySession,
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

        // --- Restoration Keys ---
        private const val KEY_IS_EDITING = "restoration_is_editing"
        private const val KEY_SELECTED_ID = "restoration_selected_id"
        // Input Fields
        private const val KEY_IN_HEIGHT = "restoration_in_height"
        private const val KEY_IN_WEIGHT = "restoration_in_weight"
        private const val KEY_IN_BP_S = "restoration_in_bp_s"
        private const val KEY_IN_BP_D = "restoration_in_bp_d"
        private const val KEY_IN_SAT = "restoration_in_sat"
        private const val KEY_IN_PULSE = "restoration_in_pulse"
        private const val KEY_IN_TEMP = "restoration_in_temp"
        private const val KEY_IN_GLUCOSE = "restoration_in_glucose"
        private const val KEY_IN_HBA1C = "restoration_in_hba1c"
        private const val KEY_IN_TIME = "restoration_in_time"
        // Snapshot (Baseline) Fields
        private const val KEY_BASE_HEIGHT = "restoration_base_height"
        private const val KEY_BASE_WEIGHT = "restoration_base_weight"
        private const val KEY_BASE_BP_S = "restoration_base_bp_s"
        private const val KEY_BASE_BP_D = "restoration_base_bp_d"
        private const val KEY_BASE_SAT = "restoration_base_sat"
        private const val KEY_BASE_PULSE = "restoration_base_pulse"
        private const val KEY_BASE_TEMP = "restoration_base_temp"
        private const val KEY_BASE_GLUCOSE = "restoration_base_glucose"
        private const val KEY_BASE_HBA1C = "restoration_base_hba1c"
        private const val KEY_BASE_TIME = "restoration_base_time"
    }

    override val featureName: String = FEATURE_NAME

    /** 復元中であることを示すフラグ */
    private var isRestoring = false

    /** 履歴ロード用の Job */
    private var recordsJob: Job? = null

    /** 保存処理用の Job */
    private var saveJob: Job? = null

    /** 削除処理用の Job */
    private var deleteJob: Job? = null

    init {
        // --- State Restoration ---
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        // 引数（categoryName）の変更を購読
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_CATEGORY_NAME, null).collect { name ->
                if (name != null) {
                    try {
                        val category = Category.valueOf(name)
                        // 復元中かつ、カテゴリがSSHのものと一致する場合は再セットを避ける
                        if (!isRestoring || (currentState.currentCategory != category)) {
                            setCategory(category)
                        }
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

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val handle = savedStateHandle ?: return
        val selectedId = handle.get<String>(KEY_SELECTED_ID)
        val isEditing = handle.get<Boolean>(KEY_IS_EDITING) ?: false

        val input = HealthEditInput(
            heightText = handle.get<String>(KEY_IN_HEIGHT) ?: "",
            weightText = handle.get<String>(KEY_IN_WEIGHT) ?: "",
            bpSystolicText = handle.get<String>(KEY_IN_BP_S) ?: "",
            bpDiastolicText = handle.get<String>(KEY_IN_BP_D) ?: "",
            satText = handle.get<String>(KEY_IN_SAT) ?: "",
            pulseText = handle.get<String>(KEY_IN_PULSE) ?: "",
            bodyTemperatureText = handle.get<String>(KEY_IN_TEMP) ?: "",
            glucoseText = handle.get<String>(KEY_IN_GLUCOSE) ?: "",
            hba1cText = handle.get<String>(KEY_IN_HBA1C) ?: "",
            recordTime = handle.get<Long>(KEY_IN_TIME)?.let { Instant.ofEpochMilli(it) }
        )

        val snapshot = if (handle.contains(KEY_BASE_TIME)) {
            HealthEditInput(
                heightText = handle.get<String>(KEY_BASE_HEIGHT) ?: "",
                weightText = handle.get<String>(KEY_BASE_WEIGHT) ?: "",
                bpSystolicText = handle.get<String>(KEY_BASE_BP_S) ?: "",
                bpDiastolicText = handle.get<String>(KEY_BASE_BP_D) ?: "",
                satText = handle.get<String>(KEY_BASE_SAT) ?: "",
                pulseText = handle.get<String>(KEY_BASE_PULSE) ?: "",
                bodyTemperatureText = handle.get<String>(KEY_BASE_TEMP) ?: "",
                glucoseText = handle.get<String>(KEY_BASE_GLUCOSE) ?: "",
                hba1cText = handle.get<String>(KEY_BASE_HBA1C) ?: "",
                recordTime = handle.get<Long>(KEY_BASE_TIME)?.let { Instant.ofEpochMilli(it) }
            )
        } else null

        val isChanged = (input != snapshot)

        updateUiState { current ->
            current.copy(
                selectedRecordId = selectedId,
                isEditing = isEditing,
                editInput = input,
                initialSnapshot = snapshot,
                isChanged = isChanged,
                isSaveEnabled = (PersonHealthLogic.validateInputs(current.currentCategory, input.toValidationMap()) == HealthInputValidationResult.SUCCESS) 
                        && input.recordTime != null && isChanged
            )
        }
    }

    /**
     * 復元対象の状態をバックアップします。
     */
    private fun backupRestorableState(state: PersonHealthUiState) {
        val handle = savedStateHandle ?: return
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_SELECTED_ID] = state.selectedRecordId
        handle[KEY_IS_EDITING] = state.isEditing

        // Input
        handle[KEY_IN_HEIGHT] = state.editInput.heightText
        handle[KEY_IN_WEIGHT] = state.editInput.weightText
        handle[KEY_IN_BP_S] = state.editInput.bpSystolicText
        handle[KEY_IN_BP_D] = state.editInput.bpDiastolicText
        handle[KEY_IN_SAT] = state.editInput.satText
        handle[KEY_IN_PULSE] = state.editInput.pulseText
        handle[KEY_IN_TEMP] = state.editInput.bodyTemperatureText
        handle[KEY_IN_GLUCOSE] = state.editInput.glucoseText
        handle[KEY_IN_HBA1C] = state.editInput.hba1cText
        handle[KEY_IN_TIME] = state.editInput.recordTime?.toEpochMilli()

        // Snapshot
        state.initialSnapshot?.let { base ->
            handle[KEY_BASE_HEIGHT] = base.heightText
            handle[KEY_BASE_WEIGHT] = base.weightText
            handle[KEY_BASE_BP_S] = base.bpSystolicText
            handle[KEY_BASE_BP_D] = base.bpDiastolicText
            handle[KEY_BASE_SAT] = base.satText
            handle[KEY_BASE_PULSE] = base.pulseText
            handle[KEY_BASE_TEMP] = base.bodyTemperatureText
            handle[KEY_BASE_GLUCOSE] = base.glucoseText
            handle[KEY_BASE_HBA1C] = base.hba1cText
            handle[KEY_BASE_TIME] = base.recordTime?.toEpochMilli()
        }
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
        // 復元中の場合は、初期化によるリセットをスキップして現在の状態を維持する
        if (isRestoring) return state

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
                val cleared = next.copy(
                    isEditing = false,
                    editInput = HealthEditInput(),
                    initialRecordTime = null,
                    initialSnapshot = null,
                    isChanged = false,
                    isSaveEnabled = false
                )
                clearRestorableState(
                    KEY_SELECTED_ID, KEY_IS_EDITING, 
                    KEY_IN_HEIGHT, KEY_IN_WEIGHT, KEY_IN_BP_S, KEY_IN_BP_D, KEY_IN_SAT, KEY_IN_PULSE, KEY_IN_TEMP, KEY_IN_GLUCOSE, KEY_IN_HBA1C, KEY_IN_TIME,
                    KEY_BASE_HEIGHT, KEY_BASE_WEIGHT, KEY_BASE_BP_S, KEY_BASE_BP_D, KEY_BASE_SAT, KEY_BASE_PULSE, KEY_BASE_TEMP, KEY_BASE_GLUCOSE, KEY_BASE_HBA1C, KEY_BASE_TIME
                )
                cleared
            } else if (IdLogic.isNew(id)) {
                // 復元中の場合は、自動補完ロジックをスキップして SSH からの値を優先する
                if (isRestoring) {
                    isRestoring = false // 復元処理を消費
                    next
                } else {
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
                    val nextWithInput = next.copy(
                        isEditing = true,
                        editInput = initialInput,
                        initialRecordTime = now,
                        initialSnapshot = initialInput,
                        isChanged = false,
                        isSaveEnabled = false
                    )
                    backupRestorableState(nextWithInput)
                    nextWithInput
                }
            } else {
                // 既存レコード選択時は閲覧モードから開始
                // 復元中の場合は既に state に値が入っているため、そのまま返す
                if (isRestoring) {
                    isRestoring = false
                    next
                } else {
                    val nextView = next.copy(isEditing = false, initialRecordTime = null)
                    backupRestorableState(nextView)
                    nextView
                }
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
            val next = it.copy(
                isEditing = true,
                editInput = initialInput,
                initialRecordTime = record.recordTime,
                initialSnapshot = initialInput,
                isChanged = false,
                isSaveEnabled = false
            )
            backupRestorableState(next)
            next
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
            updateUiState { 
                val next = it.copy(isEditing = false)
                backupRestorableState(next)
                next
            }
        }
    }

    /**
     * 入力フォームの内容を更新し、変更検知とバリデーションを再計算します。
     */
    fun updateEditInput(update: (HealthEditInput) -> HealthEditInput) {
        updateUiState { state ->
            val nextInput = update(state.editInput)
            val isChanged = (nextInput != state.initialSnapshot)

            // 自動的に touched とするフィールドの特定
            val nextTouched = getNewlyTouchedFields(state.editInput, nextInput, state.touchedFields)

            // バリデーションとエラーメッセージの生成
            val (errors, errorArgs) = calculateFieldErrors(state.currentCategory, nextInput, nextTouched)

            val validationResult = PersonHealthLogic.validateInputs(state.currentCategory, nextInput.toValidationMap())
            val isDateTimeValid = nextInput.recordTime != null
            val isSaveEnabled = (validationResult == HealthInputValidationResult.SUCCESS) && isDateTimeValid && isChanged

            val next = state.copy(
                editInput = nextInput,
                isChanged = isChanged,
                isSaveEnabled = isSaveEnabled,
                touchedFields = nextTouched,
                fieldErrors = errors,
                fieldErrorArgs = errorArgs
            )
            backupRestorableState(next)
            next
        }
    }

    /** フィールドにフォーカスが当たったことを記録します */
    fun markFieldAsTouched(fieldName: String) {
        updateUiState { state ->
            val nextTouched = state.touchedFields + fieldName
            val (errors, errorArgs) = calculateFieldErrors(state.currentCategory, state.editInput, nextTouched)
            val next = state.copy(
                touchedFields = nextTouched,
                fieldErrors = errors,
                fieldErrorArgs = errorArgs
            )
            backupRestorableState(next)
            next
        }
    }

    private fun getNewlyTouchedFields(old: HealthEditInput, next: HealthEditInput, current: Set<String>): Set<String> {
        val touched = current.toMutableSet()
        if (old.heightText != next.heightText) touched.add("height")
        if (old.weightText != next.weightText) touched.add("weight")
        if (old.bpSystolicText != next.bpSystolicText) touched.add("bpSystolic")
        if (old.bpDiastolicText != next.bpDiastolicText) touched.add("bpDiastolic")
        if (old.satText != next.satText) touched.add("sat")
        if (old.pulseText != next.pulseText) touched.add("pulse")
        if (old.bodyTemperatureText != next.bodyTemperatureText) touched.add("bodyTemperature")
        if (old.glucoseText != next.glucoseText) touched.add("glucose")
        if (old.hba1cText != next.hba1cText) touched.add("hba1c")
        if (old.recordTime != next.recordTime) touched.add("recordTime")
        return touched
    }

    private fun calculateFieldErrors(
        category: Category,
        input: HealthEditInput,
        touched: Set<String>
    ): Pair<Map<String, Int?>, Map<String, List<String>>> {
        val errors = mutableMapOf<String, Int?>()
        val errorArgs = mutableMapOf<String, List<String>>()
        
        val validationMap = input.toValidationMap()
        
        validationMap.forEach { (field, value) ->
            if (touched.contains(field)) {
                val result = validateSingleField(category, field, value)
                if (result != HealthInputValidationResult.SUCCESS) {
                    errors[field] = translateHealthValidationResult(result)
                    if (result == HealthInputValidationResult.OUT_OF_RANGE) {
                        errorArgs[field] = getRangeArgs(category, field)
                    }
                }
            }
        }

        // 記録日時のチェック
        if (touched.contains("recordTime")) {
            if (input.recordTime == null) {
                errors["recordTime"] = R.string.common_err_invalid_date
            } else if (input.recordTime.isAfter(Instant.now())) {
                errors["recordTime"] = R.string.common_err_future_date_not_allowed
            }
        }

        return errors to errorArgs
    }

    private fun validateSingleField(category: Category, field: String, value: String): HealthInputValidationResult {
        // 体重は必須とするなどの個別ルールがあるため、HealthLogic の各メソッドを部分的に利用する
        if (value.isBlank()) {
            return if (field == "weight") HealthInputValidationResult.EMPTY else HealthInputValidationResult.SUCCESS
        }
        
        val spec = getSpecForField(category, field) ?: return HealthInputValidationResult.SUCCESS
        
        // HealthLogic.isWithinFormat は内部で変換も行っている
        val isValid = jp.mydns.fujiwara.carememo.logic.common.HealthLogic.isWithinFormat(
            value, spec.digitsInt, spec.digitsDec, spec.min, spec.max
        )
        
        if (!isValid) {
            // 形式エラーか範囲エラーかを判定
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

    private fun getSpecForField(category: Category, field: String): FieldSpec? {
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

    private fun getRangeArgs(category: Category, field: String): List<String> {
        val spec = getSpecForField(category, field) ?: return emptyList()
        val minStr = if (spec.digitsDec > 0) "%.1f".format(spec.min) else spec.min.toInt().toString()
        val maxStr = if (spec.digitsDec > 0) "%.1f".format(spec.max) else spec.max.toInt().toString()
        return listOf(minStr, maxStr)
    }

    private fun translateHealthValidationResult(result: HealthInputValidationResult): Int? {
        return when (result) {
            HealthInputValidationResult.EMPTY -> R.string.p_cond_err_empty_condition // とりあえず既存の "内容を入力してください" 的なものか共通の
            HealthInputValidationResult.INVALID_FORMAT -> R.string.common_error_invalid_input
            HealthInputValidationResult.OUT_OF_RANGE -> R.string.health_err_range_format
            else -> null
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
            // UI 層に公開する直前に ImmutableList へ変換し、不変性と描画の安定性を保証する
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
            
            // リポジトリを使用した重複チェック
            val existing = healthRepository.findHistoryRecordAtTime(category, record.personId, record.recordTime)

            val duplicateResult = PersonHealthLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            healthRepository.saveHistoryRecord(record, isUpdate, featureName, OP_SAVE)

            sendUiEvent(UiEvent.SaveSuccess(record.personId))
            showSnackbar(if (isUpdate) R.string.p_health_msg_update_success else R.string.p_health_msg_save_success)
            
            // 状態復元データを破棄
            clearRestorableState(
                KEY_SELECTED_ID, KEY_IS_EDITING, 
                KEY_IN_HEIGHT, KEY_IN_WEIGHT, KEY_IN_BP_S, KEY_IN_BP_D, KEY_IN_SAT, KEY_IN_PULSE, KEY_IN_TEMP, KEY_IN_GLUCOSE, KEY_IN_HBA1C, KEY_IN_TIME,
                KEY_BASE_HEIGHT, KEY_BASE_WEIGHT, KEY_BASE_BP_S, KEY_BASE_BP_D, KEY_BASE_SAT, KEY_BASE_PULSE, KEY_BASE_TEMP, KEY_BASE_GLUCOSE, KEY_BASE_HBA1C, KEY_BASE_TIME
            )
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
            val historyRecord = record as? HistoryRecord ?: return@safeLaunch
            healthRepository.deleteHistoryRecord(historyRecord, featureName, OP_DELETE)
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
        private val securitySession: SecuritySession,
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
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
