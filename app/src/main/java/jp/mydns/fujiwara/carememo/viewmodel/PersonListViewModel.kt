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
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonDuplicateResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonListLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonListUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonListViewEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者一覧画面用の ViewModel (System B)
 */
class PersonListViewModel(
    private val repository: PersonRepository,
    private val archivedRepository: DeleteOrRestorePersonRepository,
    summaryRepository: PersonSummaryRepository,
    private val conditionRepository: ConditionRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
) : BaseUiStateViewModel<PersonListUiState, PersonListViewEvent>(
    userSettingsRepository,
    PersonListUiState()
) {

    companion object {
        private const val FEATURE_NAME = "PersonList"
        private const val OP_ADD = "addPerson"
        private const val OP_DELETE = "logicalDeletePerson"
        private const val OP_RESTORE = "restorePerson"
        private const val OP_LOAD_CONTACTS = "loadEmergencyContacts"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    // 後方互換性のためのプロパティ（テストや外部からの参照用）
    val searchQuery: StateFlow<String> get() = MutableStateFlow(currentState.searchQuery).asStateFlow() // 実際には UI 側は uiState を見るべき

    @OptIn(ExperimentalCoroutinesApi::class)
    private val personsWithMatchedConditions: StateFlow<List<String>?> = uiState
        .flatMapLatest { state ->
            val query = state.searchQuery
            if (query.isBlank()) flowOf(null)
            else conditionRepository.getPersonIdsByConditionKeyword(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val categorySummaries: StateFlow<Map<String, PersonCategorySummary>> = summaryRepository.getPersonCategorySummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        safeCollect(
            operation = "userListFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = {
                combine(
                    repository.getAllPersons(),
                    uiState, // selectedSection, searchQuery, isNameMaskingEnabled を含む
                    personsWithMatchedConditions,
                    categorySummaries
                ) { allPersons, state, matchedIds, summaries ->
                    val filtered = PersonListLogic.filterPersons(allPersons, state.selectedSection, matchedIds)
                    filtered.map { person ->
                        PersonListLogic.createPersonUiState(person, state.isNameMaskingEnabled, summaries[person.id])
                    }
                }
            }
        ) { newList ->
            updateUiState { it.copy(userList = newList) }
        }
    }

    override fun copyWithLoadingState(state: PersonListUiState, isLoading: Boolean): PersonListUiState {
        return state.copy(isLoading = isLoading)
    }

    fun setSelectedSection(section: String) {
        updateUiState { it.copy(selectedSection = section) }
    }

    fun setSearchQuery(query: String) {
        updateUiState { 
            it.copy(
                searchQuery = query,
                // 検索時はセクションを「全」にリセットする
                selectedSection = if (query.isNotBlank()) "全" else it.selectedSection
            )
        }
    }

    fun addPerson(person: Person) {
        safeLaunch(
            operation = OP_ADD,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            val isMasking = currentState.isNameMaskingEnabled
            
            // 1. 保存前に論理的な重複をチェック（事実の判定）
            val existing = repository.findExistingPerson(person)
            val duplicateResult = PersonListLogic.validateDuplicate(person, existing)
            
            // 2. 重複結果を翻訳（例外スロー）
            translateDuplicateResult(duplicateResult, person, isMasking)

            // 3. データベースへ保存
            repository.insertPerson(person, featureName, OP_ADD)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isMasking))
        }
    }

    /**
     * 重複判定の結果（事実）を UI 通知用の例外（翻訳）に変換します。
     */
    private fun translateDuplicateResult(result: PersonDuplicateResult, input: Person, isMasking: Boolean) {
        if (result == PersonDuplicateResult.SUCCESS) return

        val personName = input.getMaskedName(isMasking)
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
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id
            }
        ) {
            archivedRepository.logicalDeletePerson(person.id, featureName, OP_DELETE)
            showSnackbar(R.string.main_msg_user_archived, person.getMaskedName(currentState.isNameMaskingEnabled))
        }
    }

    fun restorePerson(person: Person) {
        safeLaunch(
            operation = OP_RESTORE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id
            }
        ) {
            archivedRepository.restorePerson(person.id, featureName, OP_RESTORE)
            showSnackbar(R.string.main_msg_user_restored, person.getMaskedName(currentState.isNameMaskingEnabled))
        }
    }

    /**
     * クイックメニューを表示します。
     */
    fun showQuickMenu(person: Person) {
        updateUiState { 
            it.copy(
                selectedPersonForQuickMenu = person,
                isQuickActionMenuExpanded = true
            ) 
        }
    }

    /**
     * クイックメニューを閉じます。
     */
    fun dismissQuickMenu() {
        updateUiState { it.copy(isQuickActionMenuExpanded = false) }
    }

    /**
     * 緊急連絡先表示（ボトムシート等）の状態をクリアします。
     */
    fun clearEmergencyContactState() {
        updateUiState {
            it.copy(
                selectedPersonForQuickMenu = null,
                isQuickActionMenuExpanded = false,
                emergencyContactsForSheet = null,
                isEmergencyContactLoading = false
            )
        }
    }

    /**
     * 緊急連絡先を取得します（オンデマンド）。
     */
    fun loadEmergencyContacts(personId: String) {
        // メニューは先に閉じる
        dismissQuickMenu()

        // 一時的なローディングプロキシ
        val contactLoadingState = MutableStateFlow(false)
        scope.launch {
            contactLoadingState.collect { loading ->
                updateUiState { it.copy(isEmergencyContactLoading = loading) }
            }
        }

        safeLaunch(
            operation = OP_LOAD_CONTACTS,
            loadingState = contactLoadingState,
            contextBuilder = {
                tableName = "emergency_contact_db"
                affectedId = personId
            }
        ) {
            val contacts = emergencyContactRepository.getContactsByPersonId(personId).first()
            if (contacts.isEmpty()) {
                showSnackbar(R.string.medical_msg_no_contacts)
                clearEmergencyContactState()
            } else {
                updateUiState { it.copy(emergencyContactsForSheet = contacts) }
            }
        }
    }

    /**
     * 利用者詳細画面への遷移準備を行います。
     * 表示モードをデフォルト（履歴）にリセットします。
     */
    fun prepareDetailNavigation() {
        scope.launch {
            userSettingsRepository.setHealthDisplayModeIsHistory(true)
        }
    }

    // 互換性ヘルパー
    private fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this

    class Factory(
        private val repository: PersonRepository,
        private val archivedRepository: DeleteOrRestorePersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val emergencyContactRepository: EmergencyContactRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonListViewModel(
                repository,
                archivedRepository,
                summaryRepository,
                conditionRepository,
                emergencyContactRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
