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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel：PersonListViewModel
 *
 * 【役割】
 * 利用者一覧画面における状態管理と実行制御を担当します。
 * 全利用者のリスト表示、五十音順セクション切り替え、検索（氏名および経過記録内容）、
 * アーカイブ（論理削除）・復元、およびクイックアクションメニューの制御を行います。
 *
 * 【主要な機能】
 * ・利用者リストの継続的な購読と、検索・セクションフィルタの動的適用。
 * ・経過記録のキーワード検索結果に基づく利用者絞り込み（リレーショナル検索）。
 * ・未読件数や最終記録日などのサマリー情報の統合。
 * ・利用者の追加、アーカイブ（論理削除）、復元。
 * ・緊急連絡先のオンデマンド取得と表示制御。
 *
 * 【依存している Repository】
 * ・PersonRepository: 利用者情報の取得と追加。
 * ・DeleteOrRestorePersonRepository: 利用者の論理削除および復元。
 * ・PersonSummaryRepository: カテゴリ別の未読数等のサマリー情報取得。
 * ・ConditionRepository: 経過記録の内容に基づいた利用者検索。
 * ・EmergencyContactRepository: 緊急連絡先の取得。
 * ・AuditLogRepository: 操作ログの記録。
 * ・UserSettingsRepository: 共通設定（マスキング）および表示モードの管理。
 *
 * 【設計指針】
 * 1. リアクティブなリスト構築：リポジトリの Flow、UI 状態（検索クエリ等）、外部検索結果、サマリーを `combine` し、
 *    いずれかが変更された際に整合性の取れたリストを自動再生成する。
 * 2. 多角的な検索：氏名だけでなく、経過記録の内容からも関連する利用者を特定し、透過的にリストへ反映する。
 * 3. 即時性の確保：アーカイブや復元などのデータ操作後は、スナックバーによるフィードバックを即座に行う。
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
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonList"
        /** 監査ログ用：追加操作名 */
        private const val OP_ADD = "addPerson"
        /** 監査ログ用：論理削除操作名 */
        private const val OP_DELETE = "logicalDeletePerson"
        /** 監査ログ用：復元操作名 */
        private const val OP_RESTORE = "restorePerson"
        /** 監査ログ用：緊急連絡先取得操作名 */
        private const val OP_LOAD_CONTACTS = "loadEmergencyContacts"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    /** 特定の利用者の緊急連絡先ロード状態を管理する内部 Flow */
    private val emergencyContactLoadingState = MutableStateFlow(false)

    /**
     * 検索クエリに基づき、経過記録の内容が一致する利用者の ID リストを抽出します。
     * 
     * クエリ入力のたびに最新の検索を実行し、ヒットした利用者 ID のリストを返します。
     * クエリが空の場合は null を返し、フィルタリングを無効にします。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val personsWithMatchedConditions: StateFlow<List<String>?> = uiState
        .flatMapLatest { state ->
            val query = state.searchQuery
            if (query.isBlank()) flowOf(null)
            else conditionRepository.getPersonIdsByConditionKeyword(query)
                .catch<List<String>?> { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 全利用者のカテゴリ別サマリー情報（未読数等）を保持するストリーム */
    private val categorySummaries: StateFlow<Map<String, PersonCategorySummary>> = summaryRepository.getPersonCategorySummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

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

        // 緊急連絡先のロード状態を UI State へ反映（ボトムシート表示用）
        scope.launch {
            emergencyContactLoadingState.collect { loading ->
                updateUiState { it.copy(isEmergencyContactLoading = loading) }
            }
        }

        // 利用者リストの購読と統合フィルタリングフロー
        // 各種ソース（DB、UIフィルタ、検索結果、サマリー）を統合して表示用リストを作成する
        safeCollect(
            operation = "userListFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = {
                combine(
                    repository.getAllPersons(),
                    uiState,
                    personsWithMatchedConditions,
                    categorySummaries
                ) { allPersons, state, matchedIds, summaries ->
                    // 1. ロジック層でセクション・検索条件によるフィルタリングを実行
                    // matchedIds は経過記録のキーワード検索にヒットした利用者ID群
                    val filtered = PersonListLogic.filterPersons(allPersons, state.selectedSection, matchedIds)
                    
                    // 2. 表示用の各 UI State オブジェクト（サマリー込み）へ変換
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

    /**
     * 表示対象の五十音セクションを設定します。
     *
     * @param section セクション名（"あ", "か", "全" など）
     */
    fun setSelectedSection(section: String) {
        updateUiState { it.copy(selectedSection = section) }
    }

    /**
     * 検索クエリを更新します。
     * 検索実行時は、自動的にセクションを「全」にリセットして全範囲からのヒットを優先します。
     *
     * @param query 検索キーワード
     */
    fun setSearchQuery(query: String) {
        updateUiState { 
            it.copy(
                searchQuery = query,
                selectedSection = if (query.isNotBlank()) "全" else it.selectedSection
            )
        }
    }

    /**
     * 新しい利用者を登録します。
     *
     * @param person 登録する利用者データ
     */
    fun addPerson(person: Person) {
        safeLaunch(
            operation = OP_ADD,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id
            }
        ) {
            val isMasking = currentState.isNameMaskingEnabled
            
            // 1. 重複チェック（既存またはアーカイブ済み利用者との照合）
            val existing = repository.findExistingPerson(person)
            val duplicateResult = PersonListLogic.validateDuplicate(person, existing)
            
            // 2. 重複がある場合はエラー例外を送出
            translateDuplicateResult(duplicateResult, person, isMasking)

            // 3. 保存実行
            repository.insertPerson(person, featureName, OP_ADD)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isMasking))
        }
    }

    /**
     * 重複判定の結果を UI 通知用の例外に変換します。
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

    /**
     * 利用者をアーカイブ（論理削除）します。
     *
     * @param person 対象の利用者
     */
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

    /**
     * アーカイブ済みの利用者を有効状態に復元します。
     *
     * @param person 対象の利用者
     */
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
     * 対象の利用者に対するクイックアクションメニュー（詳細、編集、緊急連絡先など）を表示します。
     *
     * @param person 対象の利用者
     */
    fun showQuickMenu(person: Person) {
        updateUiState { 
            it.copy(
                selectedPersonForQuickMenu = person,
                isQuickActionMenuExpanded = true
            ) 
        }
    }

    /** クイックアクションメニューを閉じます。 */
    fun dismissQuickMenu() {
        updateUiState { it.copy(isQuickActionMenuExpanded = false) }
    }

    /** 緊急連絡先表示ボトムシート等の関連状態をリセットします。 */
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
     * 指定された利用者の緊急連絡先情報を非同期でロードします。
     * ロード完了後、ボトムシート等での表示用に UI 状態を更新します。
     *
     * @param personId 利用者ID
     */
    fun loadEmergencyContacts(personId: String) {
        // メニューは先に閉じる
        dismissQuickMenu()

        safeLaunch(
            operation = OP_LOAD_CONTACTS,
            loadingState = emergencyContactLoadingState,
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
     * 詳細画面へ遷移する前の準備処理（設定のリセット等）を行います。
     */
    fun prepareDetailNavigation() {
        scope.launch {
            // 詳細画面へ行く際は、健康記録等の表示モードをデフォルト（履歴）に戻しておく
            userSettingsRepository.setHealthDisplayModeIsHistory(true)
        }
    }

    /**
     * PersonListViewModel を生成するための Factory クラス。
     */
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
