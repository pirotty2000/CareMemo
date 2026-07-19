package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AuditLog

/**
 * 監査ログ画面全体の表示状態
 */
data class AuditLogUiState(
    val auditLogs: List<AuditLog> = emptyList(),
    val filteredLogs: List<AuditLog> = emptyList(),
    val isLoading: Boolean = true,
    val selectedFeature: String? = null,
    val selectedResult: String? = null,
    val isAscending: Boolean = false,
    val availableFeatures: List<String> = emptyList(),
    val availableResults: List<String> = emptyList()
)

/**
 * 監査ログ画面固有のイベント
 */
sealed interface AuditLogViewEvent {
    // 将来的な拡張用
}

/**
 * 監査ログのフィルタリングとソートに関するドメインロジック。
 */
object AuditLogLogic {

    /**
     * 条件に基づいてログリストをフィルタリングおよびソートします。
     */
    fun filterAndSortLogs(
        logs: List<AuditLog>,
        feature: String?,
        result: String?,
        ascending: Boolean
    ): List<AuditLog> {
        var filtered = logs

        // 機能で絞り込み
        if (feature != null) {
            filtered = filtered.filter { it.featureName == feature }
        }

        // 結果で絞り込み
        if (result != null) {
            filtered = filtered.filter { it.resultType == result }
        }

        // ソート
        return if (ascending) {
            filtered.sortedBy { it.timestamp }
        } else {
            filtered.sortedByDescending { it.timestamp }
        }
    }

    /**
     * ログリストから、選択可能な機能一覧を抽出します。
     */
    fun extractAvailableFeatures(logs: List<AuditLog>): List<String> {
        return logs.map { it.featureName }.distinct().sorted()
    }

    /**
     * ログリストから、選択可能な結果一覧を抽出します。
     */
    fun extractAvailableResults(logs: List<AuditLog>): List<String> {
        return logs.map { it.resultType }.distinct().sorted()
    }
}
