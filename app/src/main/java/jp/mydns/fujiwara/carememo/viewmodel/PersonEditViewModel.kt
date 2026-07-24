package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneOffset

/**
 * 利用者の新規登録・編集画面用の ViewModel
 */
class PersonEditViewModel(
    private val personId: String?,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<PersonEditUiState, PersonEditViewEvent>(
    userSettingsRepository,
    PersonEditUiState(isNew = personId == null)
) {

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

        // 共通設定の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        if (personId != null) {
            loadPerson(personId)
        }
    }

    // ロードされた初期データ（変更検知用）
    private var initialPerson: Person? = null

    override fun copyWithLoadingState(state: PersonEditUiState, isLoading: Boolean): PersonEditUiState {
        return state.copy(isLoading = isLoading)
    }

    private fun loadPerson(id: String) {
        safeLaunch(
            operation = OP_LOAD,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = id
            }
        ) {
            repository.getPersonById(id).filterNotNull().first().let { person ->
                initialPerson = person
                // 誕生日は常に UTC 基準で読み込む
                val date = person.birthday.atZone(ZoneOffset.UTC).toLocalDate()
                val (initialEra, initialYear) = JapaneseDateLogic.toJapaneseDate(date)

                updateUiState { current ->
                    val next = current.copy(
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
                    // 初期データロード時にバリデーションと変更状態を確定させる
                    next.copy(
                        isValid = PersonEditLogic.isValid(next),
                        isChanged = PersonEditLogic.isChanged(next, initialPerson)
                    )
                }
            }
        }
    }

    // 更新用メソッド群: 原子的に状態を更新し、派生状態も同時に計算する
    fun updateLastName(value: String) = updateState { it.copy(lastName = value) }
    fun updateFirstName(value: String) = updateState { it.copy(firstName = value) }
    fun updateLastNameFurigana(value: String) = updateState { it.copy(lastNameFurigana = value) }
    fun updateFirstNameFurigana(value: String) = updateState { it.copy(firstNameFurigana = value) }
    fun updateNote(value: String) = updateState { it.copy(note = value) }
    fun updateEra(value: BirthEra) = updateState { it.copy(era = value) }
    fun updateYear(value: String) = updateState { it.copy(year = value) }
    fun updateMonth(value: String) = updateState { it.copy(month = value) }
    fun updateDay(value: String) = updateState { it.copy(day = value) }

    /**
     * 値の更新と同時に派生状態（isValid, isChanged）を再計算するヘルパー
     */
    private fun updateState(reducer: (PersonEditUiState) -> PersonEditUiState) {
        updateUiState { current ->
            val next = reducer(current)
            next.copy(
                isValid = PersonEditLogic.isValid(next),
                isChanged = PersonEditLogic.isChanged(next, initialPerson)
            )
        }
    }

    fun save() {
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId ?: ""
            }
        ) {
            val state = currentState

            // 1. バリデーション（事実の判定）
            val validationResult = PersonEditLogic.validate(state)

            // 2. 翻訳
            if (validationResult != PersonEditValidationResult.SUCCESS) {
                val messageRes = when (validationResult) {
                    PersonEditValidationResult.EMPTY_LAST_NAME -> R.string.main_err_edit_empty_last_name
                    PersonEditValidationResult.EMPTY_FIRST_NAME -> R.string.main_err_edit_empty_first_name
                    PersonEditValidationResult.INVALID_BIRTHDAY -> R.string.main_err_edit_invalid_birthday
                    PersonEditValidationResult.NAME_TOO_LONG -> R.string.main_err_name_too_long
                    PersonEditValidationResult.FURIGANA_TOO_LONG -> R.string.main_err_furigana_too_long
                    PersonEditValidationResult.NOTE_TOO_LONG -> R.string.main_err_note_too_long
                    else -> R.string.common_error_save
                }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = messageRes,
                    logMessage = "Validation failed: $validationResult"
                )
            }

            // Entity の構築
            val person = PersonEditLogic.createPerson(state, initialPerson)

            // 重複チェック
            val existing = repository.findExistingPerson(person)
            if (existing != null && (personId == null || existing.id != personId)) {
                handleDuplicateError(existing, person, isUpdate = personId != null)
            }

            if (personId == null) {
                repository.insertPerson(person, featureName, OP_SAVE)
                showSnackbar(R.string.main_msg_user_added, person.getMaskedName(state.isNameMaskingEnabled))
            } else {
                repository.updatePerson(person, featureName, OP_SAVE)
                showSnackbar(R.string.main_msg_user_updated)
            }
            sendUiEvent(UiEvent.SaveSuccess)
        }
    }

    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(currentState.isNameMaskingEnabled)
        val titleRes = if (isUpdate) R.string.main_err_title_duplicate_archived_update else R.string.main_err_title_duplicate_archived_add
        
        val messageRes = if (existing.deletedAt == null) {
            R.string.main_err_duplicate_active
        } else {
            R.string.main_err_duplicate_archived
        }

        throw AppValidationException(
            titleResId = titleRes,
            messageResId = messageRes,
            args = if (existing.deletedAt == null) emptyList() else listOf(personName),
            logMessage = "Duplicate person detected (ID: ${existing.id})"
        )
    }

    class Factory(
        private val personId: String?,
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
