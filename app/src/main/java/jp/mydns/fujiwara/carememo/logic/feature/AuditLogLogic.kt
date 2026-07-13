package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AuditLog

/**
 * 監査ログの閲覧・検索に関するドメインロジック。
 */
object AuditLogLogic {

    /**
     * 監査ログを条件に応じてフィルタリングおよび並び替えします。
     */
    fun filterAuditLogs(
        logs: List<AuditLog>,
        feature: String?,
        result: String?,
        ascending: Boolean
    ): List<AuditLog> {
        val filtered = logs.filter { log ->
            ((feature == null) || (log.featureName == feature)) &&
                    ((result == null) || (log.resultType == result))
        }
        return if (ascending) filtered.reversed() else filtered
    }

    /**
     * ログリストから存在する機能名の一覧を重複なく抽出します。
     */
    fun extractAvailableFeatures(logs: List<AuditLog>): List<String> {
        return logs.asSequence().map { it.featureName }.distinct().sorted().toList()
    }

    /**
     * ログリストから存在する結果種別の一覧を重複なく抽出します。
     */
    fun extractAvailableResults(logs: List<AuditLog>): List<String> {
        return logs.asSequence().map { it.resultType }.distinct().sorted().toList()
    }
}
