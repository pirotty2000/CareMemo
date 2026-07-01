package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val conditionRepository: ConditionRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseViewModel(userSettingsRepository) {

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

    val categorySummaries: StateFlow<Map<Int, PersonCategorySummary>> = repository.getPersonCategorySummaries()
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
        var filtered = allPersons
        if (section != "全") {
            filtered = filtered.filter { person ->
                getSectionForName(person.lastNameFurigana) == section
            }
        }
        if (matchedIds != null) {
            filtered = filtered.filter { person ->
                matchedIds.contains(person.id)
            }
        }
        
        filtered.map { person ->
            PersonUiState(
                person = person,
                maskedName = person.getMaskedName(isMasking),
                maskedFurigana = person.getMaskedFurigana(isMasking),
                age = DateTimeUtils.calculateAge(person.birthday),
                formattedBirthday = DateTimeUtils.formatDateJapaneseEra(person.birthday),
                summary = summaries[person.id] ?: PersonCategorySummary()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun getSectionForName(furigana: String): String {
        val firstChar = furigana.firstOrNull() ?: return "その他"
        return when (firstChar) {
            in 'あ'..'お' -> "あ"
            in 'か'..'こ', in 'が'..'ご' -> "か"
            in 'さ'..'そ', in 'ざ'..'ぞ' -> "さ"
            in 'た'..'と', in 'だ'..'ど', in 'っ'..'っ' -> "た"
            in 'な'..'の' -> "な"
            in 'は'..'ほ', in 'ば'..'ぼ', in 'ぱ'..'ぽ' -> "は"
            in 'ま'..'も' -> "ま"
            in 'や'..'よ' -> "や"
            in 'ら'..'ろ' -> "ら"
            in 'わ'..'ん' -> "わ"
            else -> "その他"
        }
    }

    val deletedUserList: StateFlow<List<Person>> = repository.getDeletedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addPerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.insertPerson(person)
                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんを登録しました")
            } catch (_: SQLiteConstraintException) {
                showError("登録エラー", "この利用者は既に登録されています。同姓同名・同生年月日の場合は、識別用メモを入力してください。")
            }
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.updatePerson(person)
                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar("利用者情報を更新しました")
            } catch (_: SQLiteConstraintException) {
                showError("更新エラー", "変更後の内容は既に他の利用者として登録されています。")
            }
        }
    }

    fun logicalDeletePerson(person: Person) {
        viewModelScope.launch {
            repository.logicalDeletePerson(person.id)
            showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんをアーカイブに移動しました")
        }
    }

    fun restorePerson(person: Person) {
        viewModelScope.launch {
            repository.restorePerson(person.id)
            showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんを一覧に戻しました")
        }
    }

    class Factory(
        private val repository: PersonRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonListViewModel::class.java)) {
                return PersonListViewModel(repository, conditionRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
