package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者健康記録（身長体重、バイタル、血糖値）固有のロジックを扱う ViewModel。
 * これら3つのカテゴリ(A系統)の取得・保存・削除を担当します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonHealthViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    private val auditLogRepository: AuditLogRepository // 追加
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository) {

    private val TAG = "PersonHealthViewModel"
    private val _currentCategory = MutableStateFlow<Category?>(null)

    /**
     * 現在の数値系カテゴリの履歴データを取得します。
     */
    val records: StateFlow<List<HistoryRecord>> = combine(_currentPerson, _currentCategory) { person, category ->
        person to category
    }.flatMapLatest { (person, category) ->
        if (person == null || category == null) flowOf(emptyList())
        else {
            when (category) {
                Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(person.id)
                Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(person.id)
                Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(person.id)
                else -> flowOf(emptyList())
            }
        }
    }.onEach {
        _isLoading.value = false
    }.catch { e ->
        _isLoading.value = false
        Log.e(TAG, "Data load error", e)
        auditLogRepository.log(
            screenName = "PersonHealth",
            operation = "recordsFlow",
            tableName = "health_db",
            actionType = "ERROR",
            affectedId = _currentPerson.value?.id?.toString() ?: "0",
            details = e.toString()
        )
        showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 表示するカテゴリを設定します。
     */
    fun setCategory(category: Category) {
        if (_currentCategory.value != category) {
            _isLoading.value = true
            _currentCategory.value = category
        }
    }

    /**
     * 指定された数値系カテゴリの履歴データを取得します(拡大表示画面などで使用)。
     */
    fun getHealthRecords(category: Category): StateFlow<List<HistoryRecord>> {
        return _currentPerson.flatMapLatest { person ->
            if (person == null) flowOf(emptyList())
            else when (category) {
                Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(person.id)
                Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(person.id)
                Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(person.id)
                else -> flowOf(emptyList())
            }
        }.onEach {
            _isLoading.value = false
        }.catch { e ->
            _isLoading.value = false
            Log.e(TAG, "getHealthRecords error", e)
            auditLogRepository.log(
                screenName = "PersonHealth",
                operation = "getHealthRecords",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = _currentPerson.value?.id?.toString() ?: "0",
                details = e.toString()
            )
            showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * 数値系レコードを保存または更新します。
     */
    fun saveRecord(record: Any?) {
        if (record == null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val isUpdate = if (record is HistoryRecord) record.id != 0 else false
                
                // --- 重複チェック (新規登録、または日時変更時) ---
                if (record is HistoryRecord) {
                    val existing = when (record) {
                        is HeightAndWeight -> healthRepository.findHeightAndWeightAtTime(record.personId, record.recordTime)
                        is BpAndPulse -> healthRepository.findBpAndPulseAtTime(record.personId, record.recordTime)
                        is GlucoseAndHbA1c -> healthRepository.findGlucoseAndHbA1cAtTime(record.personId, record.recordTime)
                        else -> null
                    }

                    // 自分自身以外（IDが異なる）の既存データがある場合は保存をブロック
                    if (existing != null && (record.id == 0 || existing.id != record.id)) {
                        showError(R.string.common_error_title_save, R.string.common_err_duplicate_blocked_simple)
                        return@launch
                    }
                }

                performSave(record)
                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar(if (isUpdate) R.string.p_health_msg_update_success else R.string.p_health_msg_save_success)
            } catch (e: Exception) {
                Log.e(TAG, "Save error", e)
                auditLogRepository.log(
                    screenName = "PersonHealth",
                    operation = "saveRecord",
                    tableName = "health_db",
                    actionType = "ERROR",
                    affectedId = (record as? HistoryRecord)?.id?.toString() ?: "0",
                    details = e.toString()
                )
                showError(R.string.common_error_title_save, R.string.common_error_save, e.localizedMessage ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performSave(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record, "PersonHealth", "saveRecord")
        is BpAndPulse -> healthRepository.insertBpAndPulse(record, "PersonHealth", "saveRecord")
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record, "PersonHealth", "saveRecord")
        else -> {}
    }

    /**
     * 数値系レコードを削除します。
     */
    fun deleteRecord(record: Any) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                performDelete(record)
                showSnackbar(R.string.p_health_msg_delete_success)
            } catch (e: Exception) {
                Log.e(TAG, "Delete error", e)
                auditLogRepository.log(
                    screenName = "PersonHealth",
                    operation = "deleteRecord",
                    tableName = "health_db",
                    actionType = "ERROR",
                    affectedId = (record as? HistoryRecord)?.id?.toString() ?: "0",
                    details = e.toString()
                )
                showError(R.string.common_error_title_delete, R.string.common_error_delete, e.localizedMessage ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.deleteHeightAndWeight(record, "PersonHealth", "deleteRecord")
        is BpAndPulse -> healthRepository.deleteBpAndPulse(record, "PersonHealth", "deleteRecord")
        is GlucoseAndHbA1c -> healthRepository.deleteGlucoseAndHbA1c(record, "PersonHealth", "deleteRecord")
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
            if (modelClass.isAssignableFrom(PersonHealthViewModel::class.java)) {
                return PersonHealthViewModel(
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
