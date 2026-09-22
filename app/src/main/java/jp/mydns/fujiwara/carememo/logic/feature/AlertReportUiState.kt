package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AlertItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * UI State：AlertReportUiState
 *
 * 【役割】
 * アラート・レポート画面の状態を保持します。
 */
@Immutable
data class AlertReportUiState(
    val screenState: AlertReportScreenState = AlertReportScreenState.Loading,
    val alerts: ImmutableList<AlertItem> = persistentListOf(),
    val filteredAlerts: ImmutableList<AlertItem> = persistentListOf(),
    val filterType: AlertFilterType = AlertFilterType.ALL,
    val operation: AlertReportOperation = AlertReportOperation.Idle
)

/**
 * 構造的状態
 */
sealed interface AlertReportScreenState {
    data object Loading : AlertReportScreenState
    data object Active : AlertReportScreenState
    data class Error(val throwable: Throwable) : AlertReportScreenState
}

/**
 * 操作状態
 */
sealed interface AlertReportOperation {
    data object Idle : AlertReportOperation
    data object Scanning : AlertReportOperation
}

/**
 * フィルタ種別
 */
enum class AlertFilterType {
    ALL, VITAL, WEIGHT, GLUCOSE
}

/**
 * View Event
 */
sealed interface AlertReportViewEvent {
    data class NavigateToDetail(
        val personId: String,
        val category: jp.mydns.fujiwara.carememo.data.Category
    ) : AlertReportViewEvent
}
