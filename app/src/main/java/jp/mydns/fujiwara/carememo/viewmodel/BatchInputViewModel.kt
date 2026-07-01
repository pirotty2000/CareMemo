package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A系統（健康記録）の一括入力画面専用の ViewModel。
 * 複数のカテゴリを同時に保存し、連続入力のための状態管理を行います。
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository) {

    private val _recordTime = MutableStateFlow(Instant.now())
    val recordTime = _recordTime.asStateFlow()

    // 入力中の値を保持する状態 (Screen側での by viewModel.height.collectAsState() 等に対応)
    val height = MutableStateFlow("")
    val weight = MutableStateFlow("")
    val bpSystolic = MutableStateFlow("")
    val bpDiastolic = MutableStateFlow("")
    val pulse = MutableStateFlow("")
    val bodyTemperature = MutableStateFlow("")
    val glucose = MutableStateFlow("")
    val hba1c = MutableStateFlow("")

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun setRecordTime(time: Instant) {
        _recordTime.value = time
    }

    /**
     * 入力された全データを一括保存します。
     * 値が入力されているカテゴリのみが保存対象となります。
     */
    fun saveBatch() {
        val person = currentPerson.value ?: return
        val time = _recordTime.value

        viewModelScope.launch {
            try {
                _isSaving.value = true
                
                // 身長・体重
                if (height.value.isNotBlank() || weight.value.isNotBlank()) {
                    healthRepository.insertHeightAndWeight(
                        HeightAndWeight(
                            personId = person.id,
                            height = height.value.toDoubleOrNull(),
                            weight = weight.value.toDoubleOrNull(),
                            recordTime = time
                        )
                    )
                }

                // バイタル
                if (bpSystolic.value.isNotBlank() || bpDiastolic.value.isNotBlank() || 
                    pulse.value.isNotBlank() || bodyTemperature.value.isNotBlank()) {
                    healthRepository.insertBpAndPulse(
                        BpAndPulse(
                            personId = person.id,
                            bpSystolic = bpSystolic.value.toIntOrNull(),
                            bpDiastolic = bpDiastolic.value.toIntOrNull(),
                            pulse = pulse.value.toIntOrNull(),
                            bodyTemperature = bodyTemperature.value.toDoubleOrNull(),
                            recordTime = time
                        )
                    )
                }

                // 血糖値
                if (glucose.value.isNotBlank() || hba1c.value.isNotBlank()) {
                    healthRepository.insertGlucoseAndHbA1c(
                        GlucoseAndHbA1c(
                            personId = person.id,
                            glucose = glucose.value.toIntOrNull(),
                            hba1c = hba1c.value.toDoubleOrNull(),
                            recordTime = time
                        )
                    )
                }

                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar("健康記録を一括保存しました")
                
                // 保存成功後に一部をクリア（連続入力のため、身長などは残す運用もあるが、基本はリセット）
                resetInputs()
            } catch (e: Exception) {
                showError("保存エラー", "一括保存に失敗しました: ${e.localizedMessage}")
            } finally {
                _isSaving.value = false
            }
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
        pulse.value = ""
        bodyTemperature.value = ""
        glucose.value = ""
        hba1c.value = ""
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BatchInputViewModel::class.java)) {
                return BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
