package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface PersonAwareState {
    val personId: String?
    val currentCategory: Category?
    val isLoading: Boolean
}

/**
 * ViewModel：PersonBaseUiStateViewModel (利用者コンテキスト基底クラス)
 *
 * 【役割】
 * 特定の利用者に紐付く詳細画面（健康記録、所見メモ、服薬管理等）に共通の「利用者情報の自動ロード」機能を提供します。
 *
 * 【設計指針：レイヤー責務】
 * 1. コンテキストの自動管理：`SavedStateHandle` からの `personId` 取得と、それに基づく利用者情報の初期ロードを自動化します。
 * 2. 重複ロードの防止：ガード節と Job 状態のチェックにより、画面遷移時などの無駄なリロードを抑制し、パフォーマンスを最適化します。
 */

/**
 * 全体像：利用者詳細系 ViewModel 構成（Composition）
 *
 * ■ 利用者詳細画面（各カテゴリ共通）
 * │
 * ├─ [1] PersonDetailUiStateViewModel (基盤：ヘッダー表示、カテゴリ遷移、戻る操作)
 * │
 * └─ [2] 専門 ViewModel (各カテゴリ固有の業務ロジック)
 *      ├─ PersonHealthViewModel (健康記録：バイタル、血糖、グラフ)
 *      ├─ PersonConditionViewModel (所見メモ：テキスト、音声入力、写真)
 *      └─ PersonMedicationViewModel (服薬管理：カレンダー、同期)
 *
 * ※ これら全ての ViewModel は本クラスを継承しており、
 *    SavedStateHandle から同一の personId を自律的にロードすることで、
 *    画面を跨いでも利用者コンテキストが一貫して維持される構造となっています。
 */
abstract class PersonBaseUiStateViewModel<S : PersonAwareState, E>(
    protected val personRepository: PersonRepository,
    protected val summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    protected val auditLogRepository: AuditLogRepository,
    initialState: S,
    protected val savedStateHandle: SavedStateHandle? = null
) : BaseUiStateViewModel<S, E>(userSettingsRepository, initialState) {

    companion object {
        private const val OP_LOAD_PERSON = "loadPerson"
        private const val TABLE_PERSON = "person_db"
        protected const val KEY_PERSON_ID = "personId"
        protected const val KEY_CATEGORY_NAME = "categoryName"
    }

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    /**
     * SavedStateHandle からの自動ロードを開始します。
     * 子クラスの init ブロックの最後で呼び出してください。
     */
    protected fun startObservePersonId() {
        scope.launch {
            savedStateHandle?.getStateFlow<String?>(KEY_PERSON_ID, null)?.collect { personId ->
                if (!personId.isNullOrBlank()) {
                    loadPerson(personId)
                }
            }
        }
    }

    private var loadPersonJob: Job? = null

    open fun loadPerson(personId: String) {
        // 同じIDが既にロード済みであり、かつロード処理が一度でも開始（Jobが生成）されていれば、
        // 無駄なリロードを避けるためにスキップする。
        if (currentState.personId == personId && loadPersonJob != null) {
            // ガードが発動したことをログに残す（診断用）
            scope.launch {
                auditLogRepository.log(
                    featureName = featureName,
                    operation = OP_LOAD_PERSON,
                    tableName = TABLE_PERSON,
                    actionType = "INFO",
                    affectedId = personId,
                    details = "Skip loadPerson: Already loaded and job exists.",
                    resultType = "GUARD_SKIPPED"
                )
            }
            return
        }

        updateUiState { onPrepareLoadPerson(it) }

        loadPersonJob?.cancel()
        loadPersonJob = safeLaunch(
            operation = OP_LOAD_PERSON,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId
            }
        ) {
            val person = personRepository.getPersonById(personId).filterNotNull().first()
            val summary = summaryRepository.getPersonCategorySummaryById(personId).first()

            updateUiState { current ->
                updateWithPersonData(current, person, summary)
            }
        }
    }

    protected val requiredPersonId: String
        get() = currentState.personId ?: throw IllegalStateException("Person information is not loaded.")

    protected abstract fun updateWithPersonData(state: S, person: Person, summary: PersonCategorySummary?): S

    protected open fun onPrepareLoadPerson(state: S): S = state
}
