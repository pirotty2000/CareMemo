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
import jp.mydns.fujiwara.carememo.logic.feature.PersonDuplicateResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonListLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 利用者一覧画面用の ViewModel
 */
class PersonListViewModel(
    private val repository: PersonRepository,
    private val archivedRepository: DeleteOrRestorePersonRepository,
    private val summaryRepository: PersonSummaryRepository,
    private val conditionRepository: ConditionRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonList"
        private const val OP_ADD = "addPerson"
        private const val OP_DELETE = "logicalDeletePerson"
        private const val OP_RESTORE = "restorePerson"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSection = MutableStateFlow("全")
    val selectedSection: StateFlow<String> = _selectedSection.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userList = MutableStateFlow<List<PersonUiState>>(emptyList())

    /**
     * 利用者一覧
     */
    val userList: StateFlow<List<PersonUiState>> = _userList.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _personsWithMatchedConditions = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(null)
            else conditionRepository.getPersonIdsByConditionKeyword(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val categorySummaries: StateFlow<Map<Int, PersonCategorySummary>> = summaryRepository.getPersonCategorySummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        safeCollect(
            operation = "userListFlow",
            loadingState = _isLoading,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = {
                combine(
                    repository.getAllPersons(),
                    _selectedSection,
                    _personsWithMatchedConditions,
                    isNameMaskingEnabled,
                    categorySummaries
                ) { allPersons, section, matchedIds, isMasking, summaries ->
                    val filtered = PersonListLogic.filterPersons(allPersons, section, matchedIds)
                    filtered.map { person ->
                        PersonListLogic.createPersonUiState(person, isMasking, summaries[person.id])
                    }
                }
            }
        ) {
            _userList.value = it
        }
    }

    fun setSelectedSection(section: String) {
        _selectedSection.value = section
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            _selectedSection.value = "全"
        }
    }

    fun addPerson(person: Person) {
        safeLaunch(
            operation = OP_ADD,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // 1. 保存前に論理的な重複をチェック（事実の判定）
            val existing = repository.findExistingPerson(person)
            val duplicateResult = PersonListLogic.validateDuplicate(person, existing)
            
            // 2. 重複結果を翻訳（例外スロー）
            translateDuplicateResult(duplicateResult, person)

            // 3. データベースへ保存
            repository.insertPerson(person, featureName, OP_ADD)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isNameMaskingEnabled.value))
        }
    }

    /**
     * 重複判定の結果（事実）を UI 通知用の例外（翻訳）に変換します。
     */
    private fun translateDuplicateResult(result: PersonDuplicateResult, input: Person) {
        if (result == PersonDuplicateResult.SUCCESS) return

        val personName = input.getMaskedName(isNameMaskingEnabled.value)
        val titleRes = R.string.main_err_title_duplicate_archived_add

        val messageRes = when (result) {
            PersonDuplicateResult.DUPLICATE_ACTIVE -> R.string.main_err_duplicate_active
            PersonDuplicateResult.DUPLICATE_ARCHIVED -> R.string.main_err_duplicate_archived
            else -> R.string.common_error_save
        }

        throw AppValidationException(
            titleResId = titleRes,
            messageResId = messageRes,
            args = if (result == PersonDuplicateResult.DUPLICATE_ARCHIVED) listOf(personName) else emptyList(),
            logMessage = "Duplicate person detected: $result"
        )
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
