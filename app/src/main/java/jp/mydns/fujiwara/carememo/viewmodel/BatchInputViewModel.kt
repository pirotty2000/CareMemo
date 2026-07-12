package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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

    // 入力中の値を保持する状態 (Screen側での by viewModel.height.collectAsState() 等に対応)
    val height = MutableStateFlow("")
    val weight = MutableStateFlow("")
    val bpSystolic = MutableStateFlow("")
    val bpDiastolic = MutableStateFlow("")
    val sat = MutableStateFlow("")
    val pulse = MutableStateFlow("")
    val bodyTemperature = MutableStateFlow("")
    val glucose = MutableStateFlow("")
    val hba1c = MutableStateFlow("")

    /**
     * 現在の入力内容が保存可能かどうかを判定する（A系統のルールに基づく）。
     * いずれかのカテゴリが有効な入力を持っていれば true を返す。
     */
    val isInputValid: StateFlow<Boolean> = combine(
        height, weight, bpSystolic, bpDiastolic, sat, pulse, bodyTemperature, glucose, hba1c
    ) { args: Array<String> ->
        val h = args[0]
        val w = args[1]
        val sys = args[2]
        val dia = args[3]
        val s = args[4]
        val p = args[5]
        val temp = args[6]
        val glu = args[7]
        val hb = args[8]
        AppThresholds.isValidHeightAndWeight(h, w) ||
        AppThresholds.isValidBpAndPulse(sys, dia, s, p, temp) ||
        AppThresholds.isValidGlucoseAndHbA1c(glu, hb)
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
            
            if (AppThresholds.isValidHeightAndWeight(height.value, weight.value)) {
                if (healthRepository.findHeightAndWeightAtTime(person.id, time) != null) {
                    duplicateCategories.add(R.string.common_category_height_weight)
                }
            }
            if (AppThresholds.isValidBpAndPulse(bpSystolic.value, bpDiastolic.value, sat.value, pulse.value, bodyTemperature.value)) {
                if (healthRepository.findBpAndPulseAtTime(person.id, time) != null) {
                    duplicateCategories.add(R.string.common_category_vital)
                }
            }
            if (AppThresholds.isValidGlucoseAndHbA1c(glucose.value, hba1c.value)) {
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
            // 身長・体重（体重必須）
            if (AppThresholds.isValidHeightAndWeight(height.value, weight.value)) {
                healthRepository.insertHeightAndWeight(
                    HeightAndWeight(
                        personId = person.id,
                        height = height.value.toDoubleOrNull(),
                        weight = weight.value.toDoubleOrNull(),
                    recordTime = time
                ),
                featureName, OP_SAVE_BATCH
            )
        }

        // バイタル（いずれか一つ）
        if (AppThresholds.isValidBpAndPulse(bpSystolic.value, bpDiastolic.value, sat.value, pulse.value, bodyTemperature.value)) {
            healthRepository.insertBpAndPulse(
                BpAndPulse(
                    personId = person.id,
                    bpSystolic = bpSystolic.value.toIntOrNull(),
                    bpDiastolic = bpDiastolic.value.toIntOrNull(),
                    sat = sat.value.toIntOrNull(),
                    pulse = pulse.value.toIntOrNull(),
                    bodyTemperature = bodyTemperature.value.toDoubleOrNull(),
                    recordTime = time
                ),
                featureName, OP_SAVE_BATCH
            )
        }

        // 血糖値（いずれか一つ）
        if (AppThresholds.isValidGlucoseAndHbA1c(glucose.value, hba1c.value)) {
            healthRepository.insertGlucoseAndHbA1c(
                GlucoseAndHbA1c(
                    personId = person.id,
                    glucose = glucose.value.toIntOrNull(),
                    hba1c = hba1c.value.toDoubleOrNull(),
                    recordTime = time
                ),
                featureName, OP_SAVE_BATCH
            )
        }

            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.batch_msg_save_success)
            
            // 保存成功後に一部をクリア（連続入力のため、身長などは残す運用もあるが、基本はリセット）
            resetInputs()
        }
    }

    /**
     * 入力値をリセットします（次の利用者の入力に備えるため）。
     */
    fun resetInputs() {
        height.value = ""
        weight.value = ""
        bpSystolic.value = ""
        bpDiastolic.value = ""
        sat.value = ""
        pulse.value = ""
        bodyTemperature.value = ""
        glucose.value = ""
        hba1c.value = ""
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
