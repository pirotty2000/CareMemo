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
 * 利用者コンテキストを持つ UiState が実装すべきインターフェース
 */
interface PersonAwareState {
    val currentCategory: Category? get() = null
    val isLoading: Boolean
}

/**
 * 利用者情報を扱う ViewModel の共通基底クラス。
 *
 * 取得した利用者情報を原子的に UiState へ反映する仕組みを提供します。
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
        private const val OP_LOAD_PERSON = "loadPerson"
        private const val TABLE_PERSON = "person_db"
    }

    init {
        // エラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    private var loadPersonJob: Job? = null

    /**
     * 指定された利用者IDの情報をロードし、UiState に反映します。
     *
     * @param personId ロード対象の利用者ID
     */
    open fun loadPerson(personId: String) {
        // 現在ロードされている利用者と同じならスキップ
        if (getPersonId(currentState) == personId) return

        // ロード開始前に状態をクリア（サブクラスでリセットロジックを実装可能にする）
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
     * UiState から現在の利用者IDを抽出します。
     * 二重ロード防止判定に使用します。
     */
    protected abstract fun getPersonId(state: S): String?


    /**
     * ロードした利用者データを UiState に反映した新しい状態を返します。
     * 派生クラスはこのメソッドを実装して、自身の UiState の各プロパティを更新してください。
     */
    protected abstract fun updateWithPersonData(state: S, person: Person, summary: PersonCategorySummary?): S

    /**
     * ロード開始前の UiState リセット処理。
     * 派生クラスでオーバーライドして、検索クエリのクリアなどを行ってください。
     */
    protected open fun onPrepareLoadPerson(state: S): S = state
}
