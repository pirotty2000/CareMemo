package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
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
 * ViewModel：PersonEditViewModel
 *
 * 【役割】
 * 利用者の新規登録および既存情報の編集画面における状態管理と実行制御を担当します。
 * 入力バリデーション、和暦変換、重複チェック、およびデータの永続化処理を統合します。
 */
class PersonEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<PersonEditUiState, PersonEditViewEvent>(
    userSettingsRepository,
    PersonEditUiState()
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonEdit"
        /** 監査ログ用：ロード操作名 */
        private const val OP_LOAD = "loadPerson"
        /** 監査ログ用：保存操作名 */
        private const val OP_SAVE = "save"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_PERSON = "person_db"
        /** 引数キー */
        private const val KEY_PERSON_ID = "personId"
    }

    override val featureName: String = FEATURE_NAME

    /** 変更検知の比較元となるロード時の初期データ */
    private var initialPerson: Person? = null

    /** コンストラクタで取得した personId（新規なら null） */
    private val personId: String?

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 引数から ID を取得
        val personIdRaw = savedStateHandle.get<String>(KEY_PERSON_ID)
        personId = if (personIdRaw == "_new") null else personIdRaw
        
        // 初期状態の設定（新規か編集か）
        updateUiState { it.copy(isNew = personId == null) }

        // 編集モードの場合、初期データをロード
        if (personId != null) {
            loadPerson(personId)
        }

        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
    }

    override fun copyWithLoadingState(state: PersonEditUiState, isLoading: Boolean): PersonEditUiState {
        return state.copy(isLoading = isLoading)
    }

    /**
     * 編集対象の利用者情報をロードし、UI 状態を初期化します。
     *
     * @param id 利用者ID
     */
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
                // 誕生日は常に UTC 基準で読み込み、和暦コンポーネントに分解する
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
                    // 初期データロード完了後にバリデーションと変更状態を確定
                    next.copy(
                        isValid = PersonEditLogic.isValid(next),
                        isChanged = PersonEditLogic.isChanged(next, initialPerson)
                    )
                }
            }
        }
    }

    // --- 入力項目更新メソッド群 ---

    fun updateLastName(value: String) = updateState { it.copy(lastName = value) }
    fun updateFirstName(value: String) = updateState { it.copy(firstName = value) }
    fun updateLastNameFurigana(value: String) = updateState { it.copy(lastNameFurigana = value) }
    fun updateFirstNameFurigana(value: String) = updateState { it.copy(firstNameFurigana = value) }
    fun updateNote(value: String) = updateState { it.copy(note = value) }
    fun updateEra(value: BirthEra) = updateState { it.copy(era = value) }
    fun updateYear(value: String) = updateState { it.copy(year = value) }
    fun updateMonth(value: String) = updateState { it.copy(month = value) }
    fun updateDay(value: String) = updateState { it.copy(day = value) }

    private fun updateState(reducer: (PersonEditUiState) -> PersonEditUiState) {
        updateUiState { current ->
            val next = reducer(current)
            next.copy(
                isValid = PersonEditLogic.isValid(next),
                isChanged = PersonEditLogic.isChanged(next, initialPerson)
            )
        }
    }

    /**
     * 入力内容をバリデーションし、DB へ保存（新規登録または更新）します。
     */
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
            val validationResult = PersonEditLogic.validate(state)

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
                throw AppValidationException(R.string.common_error_title_save, messageRes, logMessage = "Validation failed: $validationResult")
            }

            val person = PersonEditLogic.createPerson(state, initialPerson)
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
            sendUiEvent(BaseUiStateViewModel.UiEvent.SaveSuccess(person.id))
            sendViewEvent(PersonEditViewEvent.NavigateBack)
        }
    }

    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(currentState.isNameMaskingEnabled)
        val titleRes = if (isUpdate) R.string.main_err_title_duplicate_archived_update else R.string.main_err_title_duplicate_archived_add
        val messageRes = if (existing.deletedAt == null) R.string.main_err_duplicate_active else R.string.main_err_duplicate_archived

        throw AppValidationException(titleRes, messageRes, args = if (existing.deletedAt == null) emptyList() else listOf(personName), logMessage = "Duplicate person detected (ID: ${existing.id})")
    }

    /**
     * PersonEditViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val repository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonEditViewModel(savedStateHandle, repository, userSettingsRepository, auditLogRepository) as T
        }
    }
}
