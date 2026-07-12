package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 利用者情報を扱う ViewModel の共通基底クラス。
 * 詳細画面や服薬画面など、特定の利用者をコンテキストに持つ画面で使用します。
 */
abstract class PersonBaseViewModel(
    protected val repository: PersonRepository,
    protected val summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    protected val auditLogRepository: AuditLogRepository
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonBase"
        private const val OP_LOAD_PERSON = "loadPerson"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準ハンドラのセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

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

        _currentPerson.value = null

        loadPersonJob?.cancel()
        loadPersonJob = safeCollect(
            operation = OP_LOAD_PERSON,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId.toString()
            },
            flowProvider = { repository.getPersonById(personId) }
        ) {
            _currentPerson.value = it
        }
    }
}
