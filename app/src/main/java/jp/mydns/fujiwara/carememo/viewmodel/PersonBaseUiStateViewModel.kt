package jp.mydns.fujiwara.carememo.viewmodel

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

/**
 * Interface：PersonAwareState
 *
 * 【役割】
 * 特定の「利用者（Person）」のコンテキストを持つ UI 状態が実装すべき共通インターフェースです。
 * 画面遷移やデータ取得の際に、どの利用者を対象としているかを統一的に扱うために使用します。
 *
 * @property personId 現在対象となっている利用者の識別子。未ロード時は null。
 * @property currentCategory 現在選択されているカテゴリ。
 * @property isLoading ローディング中かどうか。
 */
interface PersonAwareState {
    val personId: String?
    val currentCategory: Category?
    val isLoading: Boolean
}

/**
 * ViewModel：PersonBaseUiStateViewModel (基底クラス)
 *
 * 【役割】
 * 利用者情報（基本情報・サマリー情報）を扱う全ての ViewModel に共通する基盤機能を提供します。
 * `BaseUiStateViewModel` を継承し、特定の利用者 ID に基づくデータの自動取得と UiState への反映を標準化します。
 *
 * 【主要な機能】
 * ・指定された利用者 ID に基づく基本情報（Person）およびカテゴリ別サマリー（PersonCategorySummary）の取得。
 * ・取得データの原子的な UiState 反映プロトコル（updateWithPersonData）。
 * ・ロード開始前の状態リセット（onPrepareLoadPerson）のフック提供。
 * ・利用者 ID が存在することを前提とした安全なアクセス（requiredPersonId）。
 * ・標準的な例外ハンドラ（ViewModelCoroutineErrorHandler）のセットアップ。
 *
 * 【依存している Repository】
 * ・PersonRepository: 利用者の基本情報の取得。
 * ・PersonSummaryRepository: カテゴリごとの未読件数や最終記録日などのサマリー情報の取得。
 * ・AuditLogRepository: 操作ログの記録（例外ハンドラ経由）。
 * ・UserSettingsRepository: 共通設定の参照（BaseUiStateViewModel 経由）。
 *
 * 【設計指針】
 * 1. コンテキストの明示：`personId` を状態の軸とし、関連するデータの整合性を維持する。
 * 2. ライフサイクル管理：新しい利用者をロードする際は、以前のロードジョブを適切にキャンセルし、二重実行やデータの混線（Race Condition）を防ぐ。
 * 3. 型安全性：`PersonAwareState` を強制することで、利用者情報の存在を前提とした処理を安全に記述できるようにする。
 *
 * @param S UI状態の型。PersonAwareState を実装していること。
 * @param E 画面固有イベントの型
 */
abstract class PersonBaseUiStateViewModel<S : PersonAwareState, E>(
    protected val personRepository: PersonRepository,
    protected val summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    protected val auditLogRepository: AuditLogRepository,
    initialState: S
) : BaseUiStateViewModel<S, E>(userSettingsRepository, initialState) {

    companion object {
        /** 監査ログ用：利用者ロード操作名 */
        private const val OP_LOAD_PERSON = "loadPerson"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_PERSON = "person_db"
    }

    init {
        // 標準のエラーハンドラをセットアップ
        // 例外発生時は監査ログを記録し、共通のエラーダイアログを表示する
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    /** 非同期での利用者情報ロードジョブを保持 */
    private var loadPersonJob: Job? = null

    /**
     * 指定された利用者IDの情報をロードし、UiState に反映します。
     *
     * 内部で `personRepository` と `summaryRepository` を順次呼び出し、
     * 両方のデータが揃った時点で `updateWithPersonData` を介して UiState を更新します。
     *
     * @param personId ロード対象の利用者ID
     */
    open fun loadPerson(personId: String) {
        // 現在ロードされている利用者と同じなら、不要な再ロードをスキップ
        if (currentState.personId == personId) return

        // ロード開始前に状態をクリア（サブクラスでクエリのリセットなどを行う機会を提供）
        updateUiState { onPrepareLoadPerson(it) }

        // 進行中のジョブがあればキャンセルして最新の要求を優先する
        loadPersonJob?.cancel()
        loadPersonJob = safeLaunch(
            operation = OP_LOAD_PERSON,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId
            }
        ) {
            // 利用者基本情報の取得（Flowの最新値を取得）
            val person = personRepository.getPersonById(personId).filterNotNull().first()
            
            // カテゴリ別サマリー情報の取得
            val summary = summaryRepository.getPersonCategorySummaryById(personId).first()

            // 取得したデータを原子的に UiState へ反映
            updateUiState { current ->
                updateWithPersonData(current, person, summary)
            }
        }
    }

    /**
     * 現在の利用者IDを安全に取得します。
     * 
     * 保存処理など「利用者がロードされていること」がロジック上の前提条件となる箇所で使用します。
     * ロードされていない場合は IllegalStateException をスローし、開発時の考慮漏れを早期発見します。
     */
    protected val requiredPersonId: String
        get() = currentState.personId ?: throw IllegalStateException("Person information is not loaded.")

    /**
     * ロードした利用者データを UiState に反映した新しい状態を返します。
     * 
     * 派生クラスはこのメソッドを実装し、自身の UiState（S）に含まれる
     * `person` や `summary` などのプロパティを適切に更新してください。
     *
     * @param state 現在の状態
     * @param person 取得された利用者基本情報
     * @param summary 取得されたサマリー情報
     * @return 更新後の状態
     */
    protected abstract fun updateWithPersonData(state: S, person: Person, summary: PersonCategorySummary?): S

    /**
     * ロード開始前の UiState リセット処理フック。
     * 
     * 別の利用者に切り替わる際、検索クエリのクリアや以前の利用者情報の破棄などを行いたい場合に
     * 派生クラスでオーバーライドして実装します。
     *
     * @param state 現在の状態
     * @return リセット後の状態
     */
    protected open fun onPrepareLoadPerson(state: S): S = state
}
