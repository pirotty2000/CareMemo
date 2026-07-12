package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * プロジェクト全体の ViewModel の基底クラス。
 * 共通の設定値保持や、UI通知（Snackbar/Dialog）の仕組みを提供します。
 */
abstract class BaseViewModel(
    protected val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    /**
     * 監査ログ等で使用する機能名。子クラスで必ず実装（定数を返す）する。
     */
    protected abstract val featureName: String

    /**
     * エラーハンドラ。初期化時に適切なハンドラをセットすること。
     */
    protected lateinit var coroutineErrorHandler: CoroutineErrorHandler

    /**
     * UIに対する一回限りの通知イベント
     */
    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
        data class ShowSnackbarRes(val resId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowInfoDialog(val title: String, val message: String) : UiEvent
        data class ShowInfoDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowErrorDialog(val title: String, val message: String) : UiEvent
        data class ShowErrorDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowOverwriteConfirm(val onConfirm: () -> Unit) : UiEvent
        object RequestPassword : UiEvent
        object SaveSuccess : UiEvent
    }

    protected val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    /**
     * 氏名伏せ字設定（全画面共通）
     */
    val isNameMaskingEnabled: StateFlow<Boolean> = userSettingsRepository.isNameMaskingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * デフォルト記録者名（全画面共通）
     */
    val defaultRecorderName: StateFlow<String> = userSettingsRepository.defaultRecorderName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /**
     * 共通のメッセージ送信ヘルパー
     */
    protected fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEventFlow.emit(event)
        }
    }

    protected fun showSnackbar(message: String) = sendUiEvent(UiEvent.ShowSnackbar(message))
    protected fun showSnackbar(resId: Int, vararg args: Any) = sendUiEvent(UiEvent.ShowSnackbarRes(resId, args.toList()))
    
    protected fun showError(titleResId: Int, messageResId: Int, vararg args: Any) = 
        sendUiEvent(UiEvent.ShowErrorDialogRes(titleResId, messageResId, args.toList()))

    protected fun showError(title: String, message: String) =
        sendUiEvent(UiEvent.ShowErrorDialog(title, message))

    protected fun showInfo(titleResId: Int, messageResId: Int, vararg args: Any) =
        sendUiEvent(UiEvent.ShowInfoDialogRes(titleResId, messageResId, args.toList()))

    /**
     * 外部アプリ（ファイルピッカー等）呼び出しのために、次回のフォアグラウンド復帰時のロックを一時的にスキップさせる
     */
    fun setLockBypassEnabled(enabled: Boolean) {
        userSettingsRepository.isLockBypassed = enabled
    }

    /**
     * コルーチン実行に使用するスコープ。
     * デフォルトは viewModelScope ですが、テスト時に差し替え可能です。
     */
    protected open val scope: CoroutineScope get() = viewModelScope

    /**
     * コルーチンを安全に実行し、例外ハンドリングとローディング制御を自動化します。
     *
     * @param operation 操作名（監査ログ用）
     * @param loadingState ローディング状態を保持する MutableStateFlow
     * @param contextBuilder ErrorContext を構築する DSL ブロック
     * @param block 実行するサスペンド関数
     */
    open fun safeLaunch(
        operation: String,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val context = ErrorContextBuilder(featureName, operation)
            .apply { contextBuilder?.invoke(this) }
            .build()

        return scope.launch {
            loadingState?.value = true
            try {
                block()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                // ハンドラへ委譲（Error系も含む）
                coroutineErrorHandler.handleException(t, context)

                // Error系はログ記録後に再スローしてシステムに委ねる
                if (t is Error) throw t
            } finally {
                loadingState?.value = false
            }
        }
    }

    /**
     * Flow を安全に購読し、例外ハンドリングとローディング制御を自動化します。
     *
     * @param operation 操作名（監査ログ用）
     * @param mode 購読モード (INITIAL: 初回ロード用 / MONITORING: 監視用)
     * @param loadingState ローディング状態を保持する MutableStateFlow
     * @param contextBuilder ErrorContext を構築する DSL ブロック
     * @param flowProvider 購読対象の Flow を提供する関数
     * @param action データ受信時に実行するサスペンド関数
     */
    open fun <T> safeCollect(
        operation: String,
        mode: CollectMode = CollectMode.INITIAL,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        flowProvider: () -> Flow<T>,
        action: suspend (T) -> Unit
    ): Job {
        val context = ErrorContextBuilder(featureName, operation)
            .apply { contextBuilder?.invoke(this) }
            .build()

        return scope.launch {
            if (mode == CollectMode.INITIAL) loadingState?.value = true
            try {
                flowProvider().collect { value ->
                    if (mode == CollectMode.INITIAL) loadingState?.value = false
                    action(value)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                coroutineErrorHandler.handleException(t, context)

                if (t is Error) throw t
            } finally {
                if (mode == CollectMode.INITIAL) loadingState?.value = false
            }
        }
    }
}
