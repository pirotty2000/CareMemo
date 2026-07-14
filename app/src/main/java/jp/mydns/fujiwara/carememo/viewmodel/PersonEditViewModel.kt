package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneOffset

/**
 * 利用者の新規登録・編集画面用の ViewModel
 */
class PersonEditViewModel(
    private val personId: Int,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonEdit"
        private const val OP_LOAD = "loadPerson"
        private const val OP_SAVE = "save"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    // UI状態の一括管理
    private val _uiState = MutableStateFlow(PersonEditUiState())
    val uiState: StateFlow<PersonEditUiState> = _uiState.asStateFlow()

    // ロードされた初期データ（変更検知用）
    private var initialPerson: Person? = null

    private val _isLoading = MutableStateFlow(personId != -1)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isNew: Boolean = personId == -1

    init {
        if (personId != -1) {
            loadPerson(personId)
        }
    }

    private fun loadPerson(id: Int) {
        safeLaunch(
            operation = OP_LOAD,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = id.toString()
            }
        ) {
            repository.getPersonById(id).filterNotNull().first().let { person ->
                initialPerson = person
                // 誕生日は常に UTC 基準で読み込む
                val date = person.birthday.atZone(ZoneOffset.UTC).toLocalDate()
                val (initialEra, initialYear) = JapaneseDateLogic.toJapaneseDate(date)

                _uiState.value = PersonEditUiState(
                    lastName = person.lastName,
                    firstName = person.firstName,
                    lastNameFurigana = person.lastNameFurigana,
                    firstNameFurigana = person.firstNameFurigana,
                    note = person.note,
                    era = initialEra,
                    year = initialYear.toString(),
                    month = date.monthValue.toString(),
                    day = date.dayOfMonth.toString()
                )
            }
        }
    }

    // 更新用メソッド群
    fun updateLastName(value: String) { _uiState.update { it.copy(lastName = value) } }
    fun updateFirstName(value: String) { _uiState.update { it.copy(firstName = value) } }
    fun updateLastNameFurigana(value: String) { _uiState.update { it.copy(lastNameFurigana = value) } }
    fun updateFirstNameFurigana(value: String) { _uiState.update { it.copy(firstNameFurigana = value) } }
    fun updateNote(value: String) { _uiState.update { it.copy(note = value) } }
    fun updateEra(value: BirthEra) { _uiState.update { it.copy(era = value) } }
    fun updateYear(value: String) { _uiState.update { it.copy(year = value) } }
    fun updateMonth(value: String) { _uiState.update { it.copy(month = value) } }
    fun updateDay(value: String) { _uiState.update { it.copy(day = value) } }

    /**
     * 現在の入力内容が初期状態から変更されているかどうか
     */
    val isChanged: StateFlow<Boolean> = uiState.map { current ->
        PersonEditLogic.isChanged(current, initialPerson)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 保存可能かどうか（バリデーション）
     */
    val isValid: StateFlow<Boolean> = uiState.map { current ->
        PersonEditLogic.isValid(current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun save() {
        val person = PersonEditLogic.createPerson(_uiState.value, initialPerson) ?: return

        safeLaunch(
            operation = OP_SAVE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id.toString()
            }
        ) {
            // 重複チェック
            val existing = repository.findExistingPerson(person)
            if (existing != null && (personId == -1 || existing.id != personId)) {
                handleDuplicateError(existing, person, isUpdate = personId != -1)
                return@safeLaunch
            }

            try {
                if (personId == -1) {
                    repository.insertPerson(person, featureName, OP_SAVE)
                    showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isNameMaskingEnabled.value))
                } else {
                    repository.updatePerson(person, featureName, OP_SAVE)
                    showSnackbar(R.string.main_msg_user_updated)
                }
                sendUiEvent(UiEvent.SaveSuccess)
            } catch (e: SQLiteConstraintException) {
                // 重複の可能性が高いが、一応ログとエラー表示
                showError(R.string.common_error_title_save, R.string.common_error_save, e.localizedMessage ?: "Unknown error")
                throw e // 再スローしてハンドラに記録させる
            }
        }
    }

    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(isNameMaskingEnabled.value)
        val titleRes = if (isUpdate) R.string.main_err_title_duplicate_archived_update else R.string.main_err_title_duplicate_archived_add
        
        if (existing.deletedAt == null) {
            showError(titleRes, R.string.main_err_duplicate_active)
        } else {
            showError(titleRes, R.string.main_err_duplicate_archived, personName)
        }
    }

    class Factory(
        private val personId: Int,
        private val repository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonEditViewModel(personId, repository, userSettingsRepository, auditLogRepository) as T
        }
    }
}
