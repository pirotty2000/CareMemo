package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
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
    private val archivedRepository: DeleteOrRestorePersonRepository,
    summaryRepository: PersonSummaryRepository,
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

    fun addPerson(person: Person) {
        viewModelScope.launch {
            try {
                // 1. 保存前に論理的な重複をチェック
                val existing = repository.findExistingPerson(person)
                if (existing != null) {
                    handleDuplicateError(existing, person, isUpdate = false)
                    return@launch
                }

                // 2. データベースへ保存
                repository.insertPerson(person)
                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんを登録しました")
            } catch (_: SQLiteConstraintException) {
                // 万が一、事前のチェックをすり抜けた場合
                val existing = repository.findExistingPerson(person)
                if (existing != null) {
                    handleDuplicateError(existing, person, isUpdate = false)
                } else {
                    showError("登録エラー", "データベースの制約により登録できませんでした。")
                }
            }
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            try {
                // 1. 自分自身以外で重複している人がいないかチェック
                val existing = repository.findExistingPerson(person)
                if (existing != null && existing.id != person.id) {
                    handleDuplicateError(existing, person, isUpdate = true)
                    return@launch
                }

                // 2. データベースを更新
                repository.updatePerson(person)
                sendUiEvent(UiEvent.SaveSuccess)
                showSnackbar("利用者情報を更新しました")
            } catch (_: SQLiteConstraintException) {
                val existing = repository.findExistingPerson(person)
                if (existing != null && existing.id != person.id) {
                    handleDuplicateError(existing, person, isUpdate = true)
                } else {
                    showError("更新エラー", "情報の更新に失敗しました。")
                }
            }
        }
    }

    /**
     * 重複エラーが発生した際のメッセージ表示を共通化
     */
    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(isNameMaskingEnabled.value)
        val title = if (isUpdate) "更新エラー" else "登録エラー"
        
        if (existing.deletedAt == null) {
            // アクティブな利用者に重複
            showError(
                title,
                "既に同じ内容の利用者が登録されています。別人として登録したい場合は「同姓同名識別用メモ」の内容を変更して区別してください。"
            )
        } else {
            // アーカイブ済みの利用者に重複
            val errorTitle = if (isUpdate) "更新エラー（利用修了者と重複）" else "登録エラー（利用修了者に存在）"
            val message = buildString {
                append("入力された内容（$personName 様）は、現在「利用修了者（アーカイブ）」の中に存在します。\n\n")
                append("●その方を復帰させたい場合：\nこの画面を閉じ、設定メニューの「利用終了者の復帰」から操作してください。\n\n")
                append("●別人として新規登録／更新したい場合：\n「同姓同名識別用メモ」を別の内容に変更してから再度保存してください。")
            }
            showError(errorTitle, message)
        }
    }


    fun logicalDeletePerson(person: Person) {
        viewModelScope.launch {
            archivedRepository.logicalDeletePerson(person.id)
            showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんをアーカイブに移動しました")
        }
    }

    fun restorePerson(person: Person) {
        viewModelScope.launch {
            archivedRepository.restorePerson(person.id)
            showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんを一覧に戻しました")
        }
    }

    class Factory(
        private val repository: PersonRepository,
        private val archivedRepository: DeleteOrRestorePersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonListViewModel::class.java)) {
                return PersonListViewModel(repository, archivedRepository, summaryRepository, conditionRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
