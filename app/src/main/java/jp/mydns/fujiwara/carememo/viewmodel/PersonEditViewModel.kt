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
 * ViewModel：PersonEditViewModel
 *
 * 【役割】
 * 利用者の新規登録および既存情報の編集画面における状態管理と実行制御を担当します。
 * 入力バリデーション、和暦変換、重複チェック、およびデータの永続化処理を統合します。
 *
 * 【主要な機能】
 * ・利用者情報のロード（編集時）と初期状態の保持。
 * ・各入力項目（氏名、フリガナ、生年月日、備考）の更新と、それに伴うバリデーション・変更検知の自動実行。
 * ・和暦（元号・年）と西暦の変換補助。
 * ・保存実行時の重複チェック（既存・削除済み利用者との照合）。
 * ・保存成功時のスナックバー通知と画面遷移イベントの送出。
 *
 * 【依存している Repository】
 * ・PersonRepository: 利用者情報の取得、新規登録、更新。
 * ・AuditLogRepository: 保存・ロード操作の証跡記録。
 * ・UserSettingsRepository: 共通設定（氏名のマスキング設定等）の参照。
 *
 * 【設計指針】
 * 1. 原子的な状態更新：入力項目が変更されるたびに `isValid`（有効性）と `isChanged`（変更有無）を再計算し、UI レイヤが常に正しいボタン活性状態などを把握できるようにする。
 * 2. 入力保護：保存処理には `safeLaunch` を使用し、バリデーションエラーや重複エラーを例外として処理することで、一貫したエラー通知フローを実現する。
 * 3. データの整合性：誕生日は UTC 基準で管理し、UI 上の和暦入力と DB 上の Instant 型の間の変換をロジック層へ委譲する。
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
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonEdit"
        /** 監査ログ用：ロード操作名 */
        private const val OP_LOAD = "loadPerson"
        /** 監査ログ用：保存操作名 */
        private const val OP_SAVE = "save"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // 編集モードの場合、初期データをロード
        if (personId != null) {
            loadPerson(personId)
        }
    }

    /** 変更検知の比較元となるロード時の初期データ */
    private var initialPerson: Person? = null

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
    // これらは UI から直接呼び出され、状態の更新と派生状態の再計算を原子的に行います。

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
     * 常に最新の入力状態に基づいた派生状態を維持します。
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

            // 1. バリデーション実行
            val validationResult = PersonEditLogic.validate(state)

            // 2. エラーがある場合は翻訳して例外を送出（safeLaunch 内で適切にハンドルされる）
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

            // 3. 保存用 Entity の構築
            val person = PersonEditLogic.createPerson(state, initialPerson)

            // 4. 利用者の重複チェック
            val existing = repository.findExistingPerson(person)
            if (existing != null && (personId == null || existing.id != personId)) {
                handleDuplicateError(existing, person, isUpdate = personId != null)
            }

            // 5. 保存の実行
            if (personId == null) {
                repository.insertPerson(person, featureName, OP_SAVE)
                showSnackbar(R.string.main_msg_user_added, person.getMaskedName(state.isNameMaskingEnabled))
            } else {
                repository.updatePerson(person, featureName, OP_SAVE)
                showSnackbar(R.string.main_msg_user_updated)
            }
            // 保存成功イベントを送出
            sendUiEvent(UiEvent.SaveSuccess)
        }
    }

    /**
     * 重複した利用者が検出された場合のエラーハンドリングを行います。
     * 既存の利用者が「有効」か「アーカイブ済み（削除済み）」かによってメッセージを切り分けます。
     */
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

    /**
     * PersonEditViewModel を生成するための Factory クラス。
     */
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
