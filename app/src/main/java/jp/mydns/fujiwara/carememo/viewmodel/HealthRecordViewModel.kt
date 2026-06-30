package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 健康記録（身長体重、バイタル、血糖値）固有のロジックを扱う ViewModel。
 * これら3つのカテゴリ(A系統)の取得・保存・削除を担当します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthRecordViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(personRepository, userSettingsRepository) {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentCategory = MutableStateFlow<Category?>(null)

    /**
     * 現在の数値系カテゴリの履歴データを取得します。
     */
    val records: StateFlow<List<Any>> = combine(_currentPerson, _currentCategory) { person, category ->
        person to category
    }.flatMapLatest { (person, category) ->
        if (person == null || category == null) flowOf(emptyList())
        else when (category) {
            Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(person.id)
            Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(person.id)
            Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(person.id)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 表示するカテゴリを設定します。
     */
    fun setCategory(category: Category) {
        _currentCategory.value = category
    }

    /**
     * 指定された数値系カテゴリの履歴データを取得します(拡大表示画面などで使用)。
     */
    fun getHealthRecords(category: Category): StateFlow<List<Any>> {
        return _currentPerson.flatMapLatest { person ->
            if (person == null) flowOf(emptyList())
            else when (category) {
                Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(person.id)
                Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(person.id)
                Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(person.id)
                else -> flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * 数値系レコードを保存または更新します。
     */
    fun saveRecord(record: Any?) {
        if (record == null) return
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val isUpdate = if (record is HistoryRecord) record.id != 0 else false
                performSave(record)
                showSnackbar(if (isUpdate) "記録を更新しました" else "記録を保存しました")
            } catch (e: Exception) {
                showError("保存エラー", "データの保存に失敗しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun performSave(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record)
        is BpAndPulse -> healthRepository.insertBpAndPulse(record)
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record)
        else -> {}
    }

    /**
     * 数値系レコードを削除します。
     */
    fun deleteRecord(record: Any) {
        viewModelScope.launch {
            try {
                performDelete(record)
                showSnackbar("記録を削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "データの削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.deleteHeightAndWeight(record)
        is BpAndPulse -> healthRepository.deleteBpAndPulse(record)
        is GlucoseAndHbA1c -> healthRepository.deleteGlucoseAndHbA1c(record)
        else -> {}
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthRecordViewModel::class.java)) {
                return HealthRecordViewModel(healthRepository, personRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
