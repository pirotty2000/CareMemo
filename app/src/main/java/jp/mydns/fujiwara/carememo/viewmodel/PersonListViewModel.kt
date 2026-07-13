package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonListLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * 利用者一覧の各項目の表示状態を保持するクラス
 */
data class PersonUiState(
    val person: Person,
    val maskedName: String,
    val maskedFurigana: String,
    val age: Int,
    val formattedBirthday: String,
    val summary: PersonCategorySummary
)

/**
 * 利用者一覧画面用の ViewModel
 */
class PersonListViewModel(
    private val repository: PersonRepository,
    private val archivedRepository: DeleteOrRestorePersonRepository,
    summaryRepository: PersonSummaryRepository,
    private val conditionRepository: ConditionRepository,
    userSettingsRepository: UserSettingsRepository,
    private val auditLogRepository: AuditLogRepository,
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonList"
        private const val OP_ADD = "addPerson"
        private const val OP_DELETE = "logicalDeletePerson"
        private const val OP_RESTORE = "restorePerson"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSection = MutableStateFlow("全")
    val selectedSection: StateFlow<String> = _selectedSection.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSelectedSection(section: String) {
        _selectedSection.value = section
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            _selectedSection.value = "全"
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _personsWithMatchedConditions = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(null)
            else conditionRepository.getPersonIdsByConditionKeyword(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categorySummaries: StateFlow<Map<Int, PersonCategorySummary>> = summaryRepository.getPersonCategorySummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userList: StateFlow<List<PersonUiState>> = combine(
        repository.getAllPersons().onEach { _isLoading.value = false },
        _selectedSection,
        _personsWithMatchedConditions,
        isNameMaskingEnabled,
        categorySummaries
    ) { allPersons, section, matchedIds, isMasking, summaries ->
        // 1. フィルタリングロジックを抽出した Logic へ委譲
        val filtered = PersonListLogic.filterPersons(allPersons, section, matchedIds)
        
        // 2. 表示用データの構築を Logic へ委譲
        filtered.map { person ->
            PersonListLogic.createPersonUiState(person, isMasking, summaries[person.id])
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, "userListFlow", TABLE_PERSON))
        _isLoading.value = false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPerson(person: Person) {
        safeLaunch(
            operation = OP_ADD,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // 1. 保存前に論理的な重複をチェック
            val existing = repository.findExistingPerson(person)
            if (existing != null) {
                handleDuplicateError(existing, person, isUpdate = false)
                return@safeLaunch
            }

            // 2. データベースへ保存
            repository.insertPerson(person, featureName, OP_ADD)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isNameMaskingEnabled.value))
        }
    }

    /**
     * 重複エラーが発生した際のメッセージ表示を共通化
     */
    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(isNameMaskingEnabled.value)
        val titleRes = if (isUpdate) R.string.main_err_title_duplicate_archived_update else R.string.main_err_title_duplicate_archived_add

        if (existing.deletedAt == null) {
            // アクティブな利用者に重複
            showError(
                titleRes,
                R.string.main_err_duplicate_active
            )
        } else {
            // アーカイブ済みの利用者に重複
            showError(titleRes, R.string.main_err_duplicate_archived, personName)
        }
    }

    fun logicalDeletePerson(person: Person) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id.toString()
            }
        ) {
            archivedRepository.logicalDeletePerson(person.id, featureName, OP_DELETE)
            showSnackbar(R.string.main_msg_user_archived, person.getMaskedName(isNameMaskingEnabled.value))
        }
    }

    fun restorePerson(person: Person) {
        safeLaunch(
            operation = OP_RESTORE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id.toString()
            }
        ) {
            archivedRepository.restorePerson(person.id, featureName, OP_RESTORE)
            showSnackbar(R.string.main_msg_user_restored, person.getMaskedName(isNameMaskingEnabled.value))
        }
    }

    class Factory(
        private val repository: PersonRepository,
        private val archivedRepository: DeleteOrRestorePersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonListViewModel::class.java)) {
                return PersonListViewModel(
                    repository,
                    archivedRepository,
                    summaryRepository,
                    conditionRepository,
                    userSettingsRepository,
                    auditLogRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
