package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
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
     * UIに対する一回限りの通知イベント
     */
    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
        data class ShowInfoDialog(val title: String, val message: String) : UiEvent
        data class ShowErrorDialog(val title: String, val message: String) : UiEvent
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
            initialValue = false
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
    protected fun showError(title: String, message: String) = sendUiEvent(UiEvent.ShowErrorDialog(title, message))

    /**
     * 外部アプリ（ファイルピッカー等）呼び出しのために、次回のフォアグラウンド復帰時のロックを一時的にスキップさせる
     */
    fun setLockBypassEnabled(enabled: Boolean) {
        userSettingsRepository.isLockBypassed = enabled
    }
}
