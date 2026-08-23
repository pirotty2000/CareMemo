package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import jp.mydns.fujiwara.carememo.ui.navigation.EditResult
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel：PersonEditViewModel
 *
 * 【役割】
 * 利用者の新規登録および情報編集画面における状態管理と実行制御を担当します。
 * 入力バリデーション、和暦変換、重複チェック、およびデータの永続化処理を統合します。
 *
 * 【設計指針：UI 境界の責務】
 * 1. リアルタイム・バリデーション：ユーザーの入力ごとに、保存の妥当性 (`isValid`) および
 *    初期状態からの変更の有無 (`isChanged`) を ViewModel 側で即座に判定し、UI のボタン活性制御や
 *    「変更破棄警告」の表示判定に反映します。
 * 2. データの正規化：UI 上での和暦入力等を、保存に適した標準的なデータ型（Instant 等）に変換する責務を負います。
 * 3. State Restoration：Process Death 対策として、未保存のユーザー入力と編集開始時の比較基準 (baseline) を
 *    SavedStateHandle に保持し、画面復帰時に状態を正確に再構築します。
 *
 * 【この ViewModel では行わないこと】
 * ・和暦・西暦の相互変換ロジック（JapaneseDateLogic が担当）。
 * ・氏名のマスキング計算（Person 共通拡張メソッドまたは Logic が担当）。
 */
class PersonEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<PersonEditUiState, PersonEditViewEvent>(
    userSettingsRepository,
    securitySession,
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
        /** 引数キー（Navigation Argument） */
        private const val KEY_PERSON_ID = "personId"

        // --- Restoration State Keys ---
        /** 復元用：バージョン（存在確認用） */
        private const val KEY_RESTORE_VERSION = "restoration_version"
        private const val RESTORE_VERSION = 1

        /** 復元用：Baseline (比較基準) */
        private const val KEY_BASE_LAST_NAME = "baseline_last_name"
        private const val KEY_BASE_FIRST_NAME = "baseline_first_name"
        private const val KEY_BASE_LAST_NAME_KANA = "baseline_last_name_furigana"
        private const val KEY_BASE_FIRST_NAME_KANA = "baseline_first_name_furigana"
        private const val KEY_BASE_BIRTHDAY_EPOCH = "baseline_birthday_epoch"
        private const val KEY_BASE_NOTE = "baseline_note"

        /** 復元用：Current Input (ユーザー入力) */
        private const val KEY_INPUT_LAST_NAME = "input_last_name"
        private const val KEY_INPUT_FIRST_NAME = "input_first_name"
        private const val KEY_INPUT_LAST_NAME_KANA = "input_last_name_furigana"
        private const val KEY_INPUT_FIRST_NAME_KANA = "input_first_name_furigana"
        private const val KEY_INPUT_NOTE = "input_note"
        private const val KEY_INPUT_ERA = "input_era"
        private const val KEY_INPUT_YEAR = "input_year"
        private const val KEY_INPUT_MONTH = "input_month"
        private const val KEY_INPUT_DAY = "input_day"
        private const val KEY_INPUT_IS_NEW = "input_is_new"
    }

    override val featureName: String = FEATURE_NAME

    /** 変更検知の比較元となるロード時の初期データ (SSOT: SavedStateHandle for restoration) */
    private var initialPerson: Person? = null

    /** コンストラクタで取得した personId（新規なら null） */
    private val personId: String?

    /** 保存処理の実行状態を管理する Job */
    private var saveJob: Job? = null

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 引数から ID を取得
        val personIdRaw = savedStateHandle.get<String>(KEY_PERSON_ID)
        personId = if (IdLogic.isNew(personIdRaw) || personIdRaw == "_new") null else personIdRaw
        
        // --- State Restoration フロー ---
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            restoreState()
        } else {
            // 通常起動：初期状態の設定（新規か編集か）
            updateUiState { it.copy(isNew = IdLogic.isNew(personId)) }

            // 既存編集モードの場合、初期データをロード
            if (!IdLogic.isNew(personId)) {
                loadPerson(personId!!)
            }
        }

        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
    }

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val isNew = savedStateHandle.get<Boolean>(KEY_INPUT_IS_NEW) ?: true

        // 1. Baseline (initialPerson) の復元
        // 修正：DB から再取得せず、SavedStateHandle の値から直接 Baseline を再構成する
        if (!isNew) {
            val baseLastName = savedStateHandle.get<String>(KEY_BASE_LAST_NAME) ?: ""
            val baseFirstName = savedStateHandle.get<String>(KEY_BASE_FIRST_NAME) ?: ""
            val baseLastKana = savedStateHandle.get<String>(KEY_BASE_LAST_NAME_KANA) ?: ""
            val baseFirstKana = savedStateHandle.get<String>(KEY_BASE_FIRST_NAME_KANA) ?: ""
            val baseEpoch = savedStateHandle.get<Long>(KEY_BASE_BIRTHDAY_EPOCH) ?: 0L
            val baseNote = savedStateHandle.get<String>(KEY_BASE_NOTE) ?: ""

            initialPerson = Person(
                id = personId ?: "", // Nav Arg から取得
                lastName = baseLastName,
                firstName = baseFirstName,
                lastNameFurigana = baseLastKana,
                firstNameFurigana = baseFirstKana,
                birthday = Instant.ofEpochMilli(baseEpoch),
                note = baseNote,
                updatedAt = Instant.ofEpochMilli(baseEpoch), // 更新日時は Baseline 構築時の値を使用
                isSynced = true // 既存データとして扱う
            )
        }

        // 2. Current Input の復元
        val eraName = savedStateHandle.get<String>(KEY_INPUT_ERA)
        val era = BirthEra.entries.find { it.name == eraName } ?: BirthEra.SHOWA

        updateUiState { current ->
            val next = current.copy(
                isNew = isNew,
                lastName = savedStateHandle.get<String>(KEY_INPUT_LAST_NAME) ?: "",
                firstName = savedStateHandle.get<String>(KEY_INPUT_FIRST_NAME) ?: "",
                lastNameFurigana = savedStateHandle.get<String>(KEY_INPUT_LAST_NAME_KANA) ?: "",
                firstNameFurigana = savedStateHandle.get<String>(KEY_INPUT_FIRST_NAME_KANA) ?: "",
                note = savedStateHandle.get<String>(KEY_INPUT_NOTE) ?: "",
                era = era,
                year = savedStateHandle.get<String>(KEY_INPUT_YEAR) ?: "",
                month = savedStateHandle.get<String>(KEY_INPUT_MONTH) ?: "",
                day = savedStateHandle.get<String>(KEY_INPUT_DAY) ?: ""
            )
            // 復元された原始データから Derived State を再計算
            next.copy(
                isValid = PersonEditLogic.isValid(next),
                isChanged = PersonEditLogic.isChanged(next, initialPerson)
            )
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
                
                // baseline を SavedStateHandle に保存（編集開始時の固定値）
                saveBaseline(person)

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
                    val finalState = next.copy(
                        isValid = PersonEditLogic.isValid(next),
                        isChanged = PersonEditLogic.isChanged(next, initialPerson)
                    )
                    // ロード直後の入力値をバックアップ
                    saveCurrentInput(finalState)
                    finalState
                }
            }
        }
    }

    /** baseline フィールドを SavedStateHandle へ退避します。 */
    private fun saveBaseline(person: Person) {
        savedStateHandle[KEY_BASE_LAST_NAME] = person.lastName
        savedStateHandle[KEY_BASE_FIRST_NAME] = person.firstName
        savedStateHandle[KEY_BASE_LAST_NAME_KANA] = person.lastNameFurigana
        savedStateHandle[KEY_BASE_FIRST_NAME_KANA] = person.firstNameFurigana
        savedStateHandle[KEY_BASE_BIRTHDAY_EPOCH] = person.birthday.toEpochMilli()
        savedStateHandle[KEY_BASE_NOTE] = person.note
    }

    /** 現在の入力値を SavedStateHandle へ退避します。 */
    private fun saveCurrentInput(state: PersonEditUiState) {
        savedStateHandle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        savedStateHandle[KEY_INPUT_LAST_NAME] = state.lastName
        savedStateHandle[KEY_INPUT_FIRST_NAME] = state.firstName
        savedStateHandle[KEY_INPUT_LAST_NAME_KANA] = state.lastNameFurigana
        savedStateHandle[KEY_INPUT_FIRST_NAME_KANA] = state.firstNameFurigana
        savedStateHandle[KEY_INPUT_NOTE] = state.note
        savedStateHandle[KEY_INPUT_ERA] = state.era.name
        savedStateHandle[KEY_INPUT_YEAR] = state.year
        savedStateHandle[KEY_INPUT_MONTH] = state.month
        savedStateHandle[KEY_INPUT_DAY] = state.day
        savedStateHandle[KEY_INPUT_IS_NEW] = state.isNew
    }

    /** Restoration State を削除します。 */
    private fun clearRestorationState() {
        savedStateHandle.remove<Int>(KEY_RESTORE_VERSION)
        // Baseline
        savedStateHandle.remove<String>(KEY_BASE_LAST_NAME)
        savedStateHandle.remove<String>(KEY_BASE_FIRST_NAME)
        savedStateHandle.remove<String>(KEY_BASE_LAST_NAME_KANA)
        savedStateHandle.remove<String>(KEY_BASE_FIRST_NAME_KANA)
        savedStateHandle.remove<Long>(KEY_BASE_BIRTHDAY_EPOCH)
        savedStateHandle.remove<String>(KEY_BASE_NOTE)
        // Input
        savedStateHandle.remove<String>(KEY_INPUT_LAST_NAME)
        savedStateHandle.remove<String>(KEY_INPUT_FIRST_NAME)
        savedStateHandle.remove<String>(KEY_INPUT_LAST_NAME_KANA)
        savedStateHandle.remove<String>(KEY_INPUT_FIRST_NAME_KANA)
        savedStateHandle.remove<String>(KEY_INPUT_NOTE)
        savedStateHandle.remove<String>(KEY_INPUT_ERA)
        savedStateHandle.remove<String>(KEY_INPUT_YEAR)
        savedStateHandle.remove<String>(KEY_INPUT_MONTH)
        savedStateHandle.remove<String>(KEY_INPUT_DAY)
        savedStateHandle.remove<Boolean>(KEY_INPUT_IS_NEW)
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

    /**
     * UiState の更新と同時に、バリデーション (isValid) および 変更検知 (isChanged) を実行するヘルパー。
     * あわせて、復元用のバックアップを SavedStateHandle に同期します。
     */
    private fun updateState(reducer: (PersonEditUiState) -> PersonEditUiState) {
        updateUiState { current ->
            val next = reducer(current)
            val finalState = next.copy(
                isValid = PersonEditLogic.isValid(next),
                isChanged = PersonEditLogic.isChanged(next, initialPerson)
            )
            // 通常動作中は UiState が SSOT だが、退避用バックアップとして同期
            saveCurrentInput(finalState)
            finalState
        }
    }

    /**
     * 入力内容をバリデーションし、DB へ保存（新規登録または更新）します。
     */
    fun save() {
        // 二重保存防止：既に保存処理が実行中の場合は何もしない
        if (saveJob?.isActive == true) return

        saveJob = safeLaunch(
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
            val maskedName = person.getMaskedName(state.isNameMaskingEnabled)
            val existing = repository.findExistingPerson(person)
            if (existing != null && (IdLogic.isNew(personId) || existing.id != personId)) {
                handleDuplicateError(existing, person, isUpdate = !IdLogic.isNew(personId))
            }

            if (IdLogic.isNew(personId)) {
                repository.insertPerson(person, featureName, OP_SAVE)
                // 保存が正常に完了したことが確定した場合のみクリーンアップ
                clearRestorationState()
                sendUiEvent(UiEvent.SaveSuccess(person.id))
                sendViewEvent(PersonEditViewEvent.NavigateBack(EditResult.ADDED, maskedName))
            } else {
                repository.updatePerson(person, featureName, OP_SAVE)
                // 保存が正常に完了したことが確定した場合のみクリーンアップ
                clearRestorationState()
                sendUiEvent(UiEvent.SaveSuccess(person.id))
                sendViewEvent(PersonEditViewEvent.NavigateBack(EditResult.UPDATED, maskedName))
            }
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
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonEditViewModel(
                savedStateHandle,
                repository,
                userSettingsRepository,
                securitySession,
                auditLogRepository
            ) as T
        }
    }
}
