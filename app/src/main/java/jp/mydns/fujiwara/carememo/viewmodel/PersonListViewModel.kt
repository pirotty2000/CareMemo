package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * ViewModel：PersonListViewModel
 *
 * 【役割】
 * 利用者一覧画面における状態管理と実行制御を担当します。
 * 全利用者のリスト表示、五十音順セクション切り替え、検索（氏名および経過記録内容）、
 * アーカイブ（論理削除）・復元、およびクイックアクションメニューの制御を行います。
 *
 * 【設計指針：UI 境界の責務】
 * 1. 状態の不変化：UI に公開する利用者リストはすべて ImmutableList に変換し、リスト更新時の
 *    Compose 側の再描画コストを最適化します。
 * 2. 検索ロジックの統合：氏名検索と経過記録検索の検索結果を ViewModel で統合し、
 *    UI に対しては単一のフィルタリング済みリストとして透過的に提供します。
 *
 * 【この ViewModel では行わないこと】
 * ・検索ロジックの具体的な実装（PersonListLogic が担当）。
 * ・個別の健康記録や服薬情報の詳細管理（各専門 ViewModel が担当）。
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

    /** データ更新処理（追加・削除・復元）用の Job */
    private var actionJob: Job? = null

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
        .map { it.searchQuery }
        .distinctUntilChanged()
        .flatMapLatest { query ->
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
            // UI 境界において ImmutableList へ変換し、安定したリスト表示を保証する
            updateUiState { it.copy(userList = newList.toImmutableList()) }
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
        // 二重実行防止
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(
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
            sendUiEvent(UiEvent.SaveSuccess(person.id))
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
        // 二重実行防止
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(
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
        // 二重実行防止
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(
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
                updateUiState { it.copy(emergencyContactsForSheet = contacts.toImmutableList()) }
            }
        }
    }

    /**
     * 詳細画面（各カテゴリ）へ遷移します。
     *
     * @param personId 利用者ID
     * @param category 遷移先のカテゴリ
     */
    fun navigateToDetail(personId: String, category: Category) {
        scope.launch {
            // 詳細画面へ行く際は、健康記録等の表示モードをデフォルト（履歴）に戻しておく
            userSettingsRepository.setHealthDisplayModeIsHistory(true)

            // 現在の検索クエリを取得（空文字の場合は null とする）
            val query = uiState.value.searchQuery.ifBlank { null }

            sendViewEvent(PersonListViewEvent.NavigateToDetail(personId, category, query))
        }
    }

    /** 一括入力画面へ遷移します。 */
    fun navigateToBatchInput(personId: String) {
        sendViewEvent(PersonListViewEvent.NavigateToBatchInput(personId))
    }

    /** 利用者追加画面へ遷移します。 */
    fun navigateToAddPerson() {
        sendViewEvent(PersonListViewEvent.NavigateToAddPerson)
    }

    /** 利用者編集画面へ遷移します。 */
    fun navigateToEditPerson(personId: String) {
        sendViewEvent(PersonListViewEvent.NavigateToEditPerson(personId))
    }

    /** 設定画面へ遷移します。 */
    fun navigateToSettings() {
        sendViewEvent(PersonListViewEvent.NavigateToSettings)
    }

    /** 緊急連絡先管理画面へ遷移します。 */
    fun navigateToMedicalContacts(personId: String) {
        sendViewEvent(PersonListViewEvent.NavigateToMedicalContacts(personId))
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
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
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
