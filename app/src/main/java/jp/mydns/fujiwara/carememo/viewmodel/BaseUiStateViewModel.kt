package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel：BaseUiStateViewModel (基底クラス)
 *
 * 【役割】
 * CareMemo アプリ全体の ViewModel における「状態管理」と「実行制御」の共通基盤を提供します。
 * MVI (Model-View-Intent) アーキテクチャの思想を取り入れ、単一の UiState と型安全なイベント通知を実現します。
 *
 * 【設計指針：レイヤー責務】
 * 1. 状態の一元管理：`updateUiState` (MutableStateFlow.update) を通じてのみ状態を変更し、
 *    スレッドセーフかつ原子的な更新を保証します。
 * 2. 非同期制御の標準化：`safeLaunch` および `safeCollect` により、例外ハンドリング、監査ログ、
 *    ローディング表示のボイラープレートを排除し、堅牢な非同期処理を強制します。
 * 3. UI/プラットフォーム非依存：本クラスは Compose UI や Android Context に依存せず、
 *    純粋な状態管理と制御ロジックに専念します。
 */

/**
 * 全体像：ViewModel 階層構造（Inheritance）
 *
 * ViewModel (androidx.lifecycle.ViewModel)
 * └─ [1] BaseUiStateViewModel<S, E> (★本クラス：共通基盤)
 *      ├─ PersonListViewModel (利用者一覧)
 *      ├─ PersonEditViewModel (利用者登録・編集)
 *      ├─ EmergencyContactEditViewModel (緊急連絡先管理)
 *      ├─ SettingsViewModel (設定・保守)
 *      ├─ AuditLogViewModel (操作ログ参照)
 *      ├─ DeleteOrRestorePersonViewModel (利用修了者管理)
 *      ├─ UnassignedPhotoViewModel (未割り当て写真確認)
 *      │
 *      └─ [2] PersonBaseUiStateViewModel<S, E> (利用者コンテキスト同期基盤)
 *           ├─ PersonDetailUiStateViewModel (詳細画面共通：ヘッダー、カテゴリ管理)
 *           ├─ PersonHealthViewModel (専門：健康記録)
 *           ├─ PersonConditionViewModel (専門：所見メモ)
 *           ├─ PersonMedicationViewModel (専門：服薬管理)
 *           └─ BatchInputViewModel (専門：一括入力)
 */
abstract class BaseUiStateViewModel<S, E>(
    protected val userSettingsRepository: UserSettingsRepository,
    protected val securitySession: SecuritySession,
    initialState: S
) : ViewModel() {

    /** 監査ログの記録に使用する機能名。子クラスで定数（"PersonDetail" 等）を指定すること。 */
    protected abstract val featureName: String

    /** 
     * 例外発生時にUI通知とログ記録を行うハンドラ。
     * 初期化時に適切な具体的実装（ViewModelCoroutineErrorHandler 等）をセットすること。
     */
    protected lateinit var coroutineErrorHandler: CoroutineErrorHandler

    // --- 1. 状態管理 (UiState) ---

    private val _uiState = MutableStateFlow(initialState)
    /** 外部（Composable）から購読可能な UI 状態のストリーム */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    /** 現在の最新状態（参照専用。スナップショット的な利用に使用） */
    protected val currentState: S get() = _uiState.value

    /**
     * UiState を原子的に更新します。
     * 内部で MutableStateFlow.update を呼び出し、最新状態に基づくコピーを生成します。
     *
     * @param reducer 現在の状態を受け取り、新しい状態を返すラムダ
     */
    protected fun updateUiState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    // --- 2. イベント通知 (UiEvent & ViewEvent) ---

    /** 
     * 全画面共通で使用される一過性の通知イベント定義。
     * UI（Scaffold 等）はこの Flow を購読し、適切なダイアログやスナックバーを表示します。
     */
    sealed interface UiEvent {
        /** スナックバー表示（直接文字列） */
        data class ShowSnackbar(val message: String) : UiEvent
        /** スナックバー表示（リソースID） */
        data class ShowSnackbarRes(val resId: Int, val args: List<Any> = emptyList()) : UiEvent
        /** 情報ダイアログ表示（直接文字列） */
        data class ShowInfoDialog(val title: String, val message: String) : UiEvent
        /** 情報ダイアログ表示（リソースID） */
        data class ShowInfoDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        /** エラーダイアログ表示（直接文字列） */
        data class ShowErrorDialog(val title: String, val message: String) : UiEvent
        /** エラーダイアログ表示（リソースID） */
        data class ShowErrorDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        /** 上書き確認等のコンファーム表示（コールバック付き） */
        data class ShowOverwriteConfirm(val onConfirm: () -> Unit) : UiEvent
        /** 
         * 保存成功などの標準的な完了通知
         * @param id 保存されたデータの ID（遷移等に使用する場合）
         */
        data class SaveSuccess(val id: String? = null) : UiEvent
    }

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    /** ダイアログ表示等の共通通知イベントのストリーム */
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _viewEvent = MutableSharedFlow<E>()
    /** 画面遷移等の画面固有イベントのストリーム */
    val viewEvent = _viewEvent.asSharedFlow()

    /** 共通通知イベントを UI へ送出します。 */
    protected fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch { _uiEventFlow.emit(event) }
    }

    /** 画面固有イベントを UI へ送出します。 */
    protected fun sendViewEvent(event: E) {
        viewModelScope.launch { _viewEvent.emit(event) }
    }

    // 各種通知のショートカットメソッド
    protected fun showSnackbar(message: String) = sendUiEvent(UiEvent.ShowSnackbar(message))
    protected fun showSnackbar(resId: Int, vararg args: Any) = sendUiEvent(UiEvent.ShowSnackbarRes(resId, args.toList()))
    protected fun showError(message: String, title: String = "エラー") = sendUiEvent(UiEvent.ShowErrorDialog(title, message))
    protected fun showError(titleResId: Int, messageResId: Int, vararg args: Any) = sendUiEvent(UiEvent.ShowErrorDialogRes(titleResId, messageResId, args.toList()))

    // --- 3. 共通設定の提供 ---

    /** 氏名伏せ字設定：リポジトリの設定値を自動購読し、StateFlow として提供 */
    val isNameMaskingEnabled: StateFlow<Boolean> = userSettingsRepository.isNameMaskingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** デフォルト記録者名：新規入力時の初期値として利用可能 */
    val defaultRecorderName: StateFlow<String> = userSettingsRepository.defaultRecorderName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /** アプリロックを一時的にバイパスするかどうかを設定します（PDF共有時や外部アプリ連携時に使用） */
    fun setLockBypassEnabled(enabled: Boolean) {
        securitySession.isLockBypassed = enabled
    }

    // --- 4. コルーチン実行制御 (safeLaunch / safeCollect) ---

    /** ViewModelScope または 拡張用の子クラス提供スコープ */
    protected open val scope: CoroutineScope get() = viewModelScope

    /** 
     * 子クラスにおいて、UiState 内の loading フラグを更新するための抽象メソッド。
     * 基盤側からローディングの開始・終了を通知するために使用します。
     */
    protected abstract fun copyWithLoadingState(state: S, isLoading: Boolean): S

    /** 
     * safeLaunch 等で利用するローディング状態管理プロキシ。
     * この Flow の値が変化すると、自動的に copyWithLoadingState が呼び出され UiState へ反映されます。
     */
    protected val loadingStateProxy: MutableStateFlow<Boolean> by lazy {
        val proxy = MutableStateFlow(false)
        scope.launch {
            proxy.collect { isLoading ->
                updateUiState { copyWithLoadingState(it, isLoading) }
            }
        }
        proxy
    }

    /**
     * 安全にコルーチンを起動します。
     */
    open fun safeLaunch(
        operation: String,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        // featureName が未初期化(null)の場合は安全なデフォルト値を使用する
        val safeFeatureName = try { featureName } catch (_: Exception) { "Unknown" }
        val context = ErrorContextBuilder(safeFeatureName, operation)
            .apply { contextBuilder?.invoke(this) }
            .build()

        val actualLoadingState = loadingState ?: loadingStateProxy

        return scope.launch {
            actualLoadingState.value = true
            try {
                block()
            } catch (t: Throwable) {
                // キャンセル例外は上位へ伝播させる（コルーチン基盤の標準ルールに従う）
                if (t is CancellationException) throw t
                // それ以外の例外はハンドラ（ErrorHandler）に委譲して通知とログ記録を行う
                coroutineErrorHandler.handleException(t, context)
                // 致命的な Error は再送出
                if (t is Error) throw t
            } finally {
                actualLoadingState.value = false
            }
        }
    }

    /**
     * Flow を安全に購読（収集）します。
     * safeLaunch と同様の例外保護と、モードに応じたローディング制御を提供します。
     *
     * @param operation 操作名（監査ログ用）
     * @param mode コレクションモード（INITIAL: 開始時にローディング表示, MONITORING: 表示なし）
     * @param loadingState ローディング状態管理用 Flow
     * @param contextBuilder 追加のログコンテキスト
     * @param retryCount エラー発生時の再試行回数（デフォルト 0）
     * @param retryDelayMillis 再試行間の待機時間（ミリ秒）
     * @param flowProvider 購読対象の Flow を生成するラムダ
     * @param action 値を受け取った際の処理本体（suspend 関数）
     */
    open fun <T> safeCollect(
        operation: String,
        mode: CollectMode,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        retryCount: Int = 0,
        retryDelayMillis: Long = 1000L,
        flowProvider: () -> Flow<T>,
        action: suspend (T) -> Unit
    ): Job {
        val safeFeatureName = try { featureName } catch (_: Exception) { "Unknown" }
        val context = ErrorContextBuilder(safeFeatureName, operation)
            .apply { contextBuilder?.invoke(this) }
            .build()

        val actualLoadingState = loadingState ?: loadingStateProxy

        return scope.launch {
            var currentRetry = 0
            while (true) {
                if (mode == CollectMode.INITIAL) actualLoadingState.value = true
                try {
                    flowProvider().collect { value ->
                        // INITIAL モードの場合、最初のデータを受信した時点でローディングを解除する
                        if (mode == CollectMode.INITIAL) actualLoadingState.value = false
                        action(value)
                    }
                    // 正常終了（有限の Flow の場合）
                    break
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t

                    // 再試行が設定されている場合
                    if (currentRetry < retryCount) {
                        currentRetry++
                        delay(retryDelayMillis.milliseconds)
                        continue
                    }

                    // 規定回数の試行が失敗した後に例外をハンドル
                    coroutineErrorHandler.handleException(t, context)
                    if (t is Error) throw t
                    break
                } finally {
                    if (mode == CollectMode.INITIAL) actualLoadingState.value = false
                }
            }
        }
    }
}
