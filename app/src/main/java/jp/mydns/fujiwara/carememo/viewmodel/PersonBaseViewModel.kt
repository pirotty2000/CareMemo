package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者情報を扱う ViewModel の共通基底クラス。
 * 詳細画面や服薬画面など、特定の利用者をコンテキストに持つ画面で使用します。
 */
abstract class PersonBaseViewModel(
    protected val repository: PersonRepository,
    protected val summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    protected val auditLogRepository: AuditLogRepository // 追加
) : BaseViewModel(userSettingsRepository) {

    private val TAG_BASE = "PersonBaseViewModel"

    protected val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    protected val _currentPerson = MutableStateFlow<Person?>(null)
    val currentPerson: StateFlow<Person?> = _currentPerson.asStateFlow()

    protected var loadPersonJob: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    open val personCategorySummary: StateFlow<PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person != null) summaryRepository.getPersonCategorySummaryById(person.id)
            else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 利用者情報をロードします。
     */
    open fun loadPerson(personId: Int) {
        if (_currentPerson.value?.id == personId) return

        _isLoading.value = true
        _currentPerson.value = null

        loadPersonJob?.cancel()
        loadPersonJob = viewModelScope.launch {
            try {
                repository.getPersonById(personId)
                    .catch { e ->
                        handleLoadError(personId, e)
                    }
                    .collectLatest {
                        _currentPerson.value = it
                        if (it == null) {
                            _isLoading.value = false
                        }
                    }
            } catch (e: Exception) {
                handleLoadError(personId, e)
            }
        }
    }

    private suspend fun handleLoadError(personId: Int, e: Throwable) {
        if (e is CancellationException) throw e

        _isLoading.value = false
        Log.e(TAG_BASE, "loadPerson error", e)
        auditLogRepository.log(
            screenName = "PersonBase",
            operation = "loadPerson",
            tableName = "person_db",
            actionType = "ERROR",
            affectedId = personId.toString(),
            details = e.toString()
        )
        showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
    }
}
