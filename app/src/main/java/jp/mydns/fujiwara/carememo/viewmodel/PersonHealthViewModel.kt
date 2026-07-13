package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

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
    auditLogRepository: AuditLogRepository
) : PersonBaseViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonHealth"
        private const val OP_SAVE = "saveRecord"
        private const val OP_DELETE = "deleteRecord"
        private const val OP_RECORDS_FLOW = "recordsFlow"
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

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
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, OP_RECORDS_FLOW, TABLE_HEALTH))
        _isLoading.value = false
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
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "getHealthRecords", TABLE_HEALTH))
            _isLoading.value = false
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * 数値系レコードを保存または更新します。
     */
    fun saveRecord(record: Any?) {
        if (record == null) return
        safeLaunch(
            operation = OP_SAVE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = (record as? HistoryRecord)?.id?.toString()
            }
        ) {
            val isUpdate = if (record is HistoryRecord) !PersonHealthLogic.isNew(record) else false

            // --- 重複チェック (新規登録、または日時変更時) ---
            if (record is HistoryRecord) {
                val existing = when (record) {
                    is HeightAndWeight -> healthRepository.findHeightAndWeightAtTime(record.personId, record.recordTime)
                    is BpAndPulse -> healthRepository.findBpAndPulseAtTime(record.personId, record.recordTime)
                    is GlucoseAndHbA1c -> healthRepository.findGlucoseAndHbA1cAtTime(record.personId, record.recordTime)
                    else -> null
                }

                if (PersonHealthLogic.isDuplicate(record, existing)) {
                    showError(R.string.common_error_title_save, R.string.common_err_duplicate_blocked_simple)
                    return@safeLaunch
                }
            }

            performSave(record)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(if (isUpdate) R.string.p_health_msg_update_success else R.string.p_health_msg_save_success)
        }
    }

    private suspend fun performSave(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record, featureName, OP_SAVE)
        is BpAndPulse -> healthRepository.insertBpAndPulse(record, featureName, OP_SAVE)
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record, featureName, OP_SAVE)
        else -> {}
    }

    /**
     * 数値系レコードを削除します。
     */
    fun deleteRecord(record: Any) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = (record as? HistoryRecord)?.id?.toString()
            }
        ) {
            performDelete(record)
            showSnackbar(R.string.p_health_msg_delete_success)
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.deleteHeightAndWeight(record, featureName, OP_DELETE)
        is BpAndPulse -> healthRepository.deleteBpAndPulse(record, featureName, OP_DELETE)
        is GlucoseAndHbA1c -> healthRepository.deleteGlucoseAndHbA1c(record, featureName, OP_DELETE)
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
