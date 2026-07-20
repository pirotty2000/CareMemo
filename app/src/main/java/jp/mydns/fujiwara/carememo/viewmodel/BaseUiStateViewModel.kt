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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * (B)系統：UiState と ViewEvent を用いた画面状態管理の基底クラス。
 *
 * BaseViewModel (A系統) から完全に独立し、単一の状態管理と原子的な更新を提供します。
 *
 * @param S UI状態の型 (Data Class)
 * @param E 画面固有イベントの型
 */
abstract class BaseUiStateViewModel<S, E>(
    protected val userSettingsRepository: UserSettingsRepository,
    initialState: S
) : ViewModel() {

    /** 監査ログ等で使用する機能名。子クラスで実装する。 */
    protected abstract val featureName: String

    /** エラーハンドラ。初期化時に適切なハンドラをセットすること。 */
    protected lateinit var coroutineErrorHandler: CoroutineErrorHandler

    // --- 1. 状態管理 (UiState) ---

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected val currentState: S get() = _uiState.value

    protected fun updateUiState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    // --- 2. イベント通知 (UiEvent & ViewEvent) ---

    /** UIに対する一回限りの共通通知イベント (A系統の UiEvent と同等の機能を保持) */
    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
        data class ShowSnackbarRes(val resId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowInfoDialog(val title: String, val message: String) : UiEvent
        data class ShowInfoDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowErrorDialog(val title: String, val message: String) : UiEvent
        data class ShowErrorDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
        data class ShowOverwriteConfirm(val onConfirm: () -> Unit) : UiEvent
        object SaveSuccess : UiEvent
    }

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _viewEvent = MutableSharedFlow<E>()
    val viewEvent = _viewEvent.asSharedFlow()

    protected fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch { _uiEventFlow.emit(event) }
    }

    protected fun sendViewEvent(event: E) {
        viewModelScope.launch { _viewEvent.emit(event) }
    }

    protected fun showSnackbar(message: String) = sendUiEvent(UiEvent.ShowSnackbar(message))
    protected fun showSnackbar(resId: Int, vararg args: Any) = sendUiEvent(UiEvent.ShowSnackbarRes(resId, args.toList()))
    protected fun showError(message: String, title: String = "エラー") = sendUiEvent(UiEvent.ShowErrorDialog(title, message))
    protected fun showError(titleResId: Int, messageResId: Int, vararg args: Any) = sendUiEvent(UiEvent.ShowErrorDialogRes(titleResId, messageResId, args.toList()))

    // --- 3. 共通設定 ---

    /** 氏名伏せ字設定 */
    val isNameMaskingEnabled: StateFlow<Boolean> = userSettingsRepository.isNameMaskingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** デフォルト記録者名 */
    val defaultRecorderName: StateFlow<String> = userSettingsRepository.defaultRecorderName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun setLockBypassEnabled(enabled: Boolean) {
        userSettingsRepository.isLockBypassed = enabled
    }

    // --- 4. コルーチン制御 (safeLaunch / safeCollect) ---

    protected open val scope: CoroutineScope get() = viewModelScope

    /** UiState 内の loading フラグを更新するための抽象メソッド */
    protected abstract fun copyWithLoadingState(state: S, isLoading: Boolean): S

    /** safeLaunch 等で利用するローディング状態プロキシ */
    protected val loadingStateProxy: MutableStateFlow<Boolean> by lazy {
        val proxy = MutableStateFlow(false)
        scope.launch {
            proxy.collect { isLoading ->
                updateUiState { copyWithLoadingState(it, isLoading) }
            }
        }
        proxy
    }

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
                coroutineErrorHandler.handleException(t, context)
                if (t is Error) throw t
            } finally {
                loadingState?.value = false
            }
        }
    }

    open fun <T> safeCollect(
        operation: String,
        mode: CollectMode,
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
