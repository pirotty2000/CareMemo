package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A系統（身体計測、バイタル、血糖値）の一括入力を担当する ViewModel
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(personRepository, userSettingsRepository) {

    // 入力状態
    private val _recordTime = MutableStateFlow(Instant.now().truncatedTo(ChronoUnit.MINUTES))
    val recordTime: StateFlow<Instant> = _recordTime.asStateFlow()

    // 身長・体重
    val height = MutableStateFlow("")
    val weight = MutableStateFlow("")

    // バイタル
    val bpSystolic = MutableStateFlow("")
    val bpDiastolic = MutableStateFlow("")
    val pulse = MutableStateFlow("")
    val bodyTemperature = MutableStateFlow("")

    // 血糖値・HbA1c
    val glucose = MutableStateFlow("")
    val hba1c = MutableStateFlow("")

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun setRecordTime(time: Instant) {
        _recordTime.value = time.truncatedTo(ChronoUnit.MINUTES)
    }

    /**
     * 一括保存処理
     */
    fun saveBatch() {
        val personId = currentPerson.value?.id ?: return

        viewModelScope.launch {
            try {
                _isProcessing.value = true

                val time = _recordTime.value

                // 各データオブジェクトの生成（入力がある場合のみ）
                val hw = if (height.value.isNotBlank() || weight.value.isNotBlank()) {
                    HeightAndWeight(
                        personId = personId,
                        height = height.value.toDoubleOrNull(),
                        weight = weight.value.toDoubleOrNull(),
                        recordTime = time
                    )
                } else null

                val bp = if (bpSystolic.value.isNotBlank() || bpDiastolic.value.isNotBlank() || 
                             pulse.value.isNotBlank() || bodyTemperature.value.isNotBlank()) {
                    BpAndPulse(
                        personId = personId,
                        bpSystolic = bpSystolic.value.toIntOrNull(),
                        bpDiastolic = bpDiastolic.value.toIntOrNull(),
                        pulse = pulse.value.toIntOrNull(),
                        bodyTemperature = bodyTemperature.value.toDoubleOrNull(),
                        recordTime = time
                    )
                } else null

                val gl = if (glucose.value.isNotBlank() || hba1c.value.isNotBlank()) {
                    GlucoseAndHbA1c(
                        personId = personId,
                        glucose = glucose.value.toIntOrNull(),
                        hba1c = hba1c.value.toDoubleOrNull(),
                        recordTime = time
                    )
                } else null

                if (hw == null && bp == null && gl == null) {
                    showError("入力エラー", "少なくとも1つの項目を入力してください")
                    return@launch
                }

                healthRepository.insertHealthBatch(hw, bp, gl)
                
                resetInputs()
                showSnackbar("保存しました。続けて入力できます")
                sendUiEvent(UiEvent.SaveSuccess)
            } catch (e: Exception) {
                showError("保存エラー", "一括保存に失敗しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun resetInputs() {
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
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BatchInputViewModel::class.java)) {
                return BatchInputViewModel(healthRepository, personRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
