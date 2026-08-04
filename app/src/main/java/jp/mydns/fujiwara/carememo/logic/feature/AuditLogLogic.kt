package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AuditLog

/**
 * 監査ログ画面全体の表示状態を管理するデータクラス。
 *
 * @param auditLogs 全ログのリスト
 * @param filteredLogs 現在の条件でフィルタリングされたログのリスト
 * @param isLoading 読み込み中フラグ
 * @param selectedFeature 選択されている機能フィルタ（null は全機能）
 * @param selectedResult 選択されている結果フィルタ（null は全結果）
 * @param isAscending 日時の昇順ソートかどうか
 * @param availableFeatures ログ内に存在する、フィルタ選択可能な機能名のリスト
 * @param availableResults ログ内に存在する、フィルタ選択可能な結果タイプのリスト
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
 * 監査ログ画面固有のイベント定義。
 */
sealed interface AuditLogViewEvent {
    /** 前の画面に戻る */
    data object NavigateBack : AuditLogViewEvent
}

/**
 * Logic：AuditLogLogic
 *
 * 【役割】
 * 監査ログ（操作履歴）のフィルタリング、ソート、および集計に関するドメインロジックを提供します。
 *
 * 【主な機能】
 * ・指定された条件（機能名、結果）によるログの動的フィルタリング。
 * ・日時に基づく昇順・降順ソート。
 * ・ログリストからのユニークな機能一覧、結果一覧の抽出。
 *
 * 【設計指針】
 * 1. フィルタリングは AND 条件として動作させる。
 * 2. 大量のログに対しても効率的に動作するよう、List に対する標準の filter/sorted 操作で簡潔に実装する。
 */
object AuditLogLogic {

    /**
     * 条件に基づいてログリストをフィルタリングおよびソートします。
     *
     * @param logs 元のログリスト
     * @param feature 絞り込む機能名（null なら全表示）
     * @param result 絞り込む結果タイプ（null なら全表示）
     * @param ascending true なら古い順、false なら新しい順
     * @return 処理後のログリスト
     */
    fun filterAndSortLogs(
        logs: List<AuditLog>,
        feature: String?,
        result: String?,
        ascending: Boolean
    ): List<AuditLog> {
        var filtered = logs

        // 1. 機能で絞り込み
        if (feature != null) {
            filtered = filtered.filter { it.featureName == feature }
        }

        // 2. 処理結果で絞り込み
        if (result != null) {
            filtered = filtered.filter { it.resultType == result }
        }

        // 3. 日時でソート
        return if (ascending) {
            filtered.sortedBy { it.timestamp }
        } else {
            filtered.sortedByDescending { it.timestamp }
        }
    }

    /**
     * ログリストから、重複を除いた選択可能な機能一覧を抽出します。
     * フィルタドロップダウンの項目生成に使用します。
     *
     * @param logs 全ログリスト
     * @return アルファベット/五十音順にソートされたユニークな機能名リスト
     */
    fun extractAvailableFeatures(logs: List<AuditLog>): List<String> {
        return logs.map { it.featureName }.distinct().sorted()
    }

    /**
     * ログリストから、重複を除いた選択可能な結果一覧を抽出します。
     * フィルタドロップダウンの項目生成に使用します。
     *
     * @param logs 全ログリスト
     * @return アルファベット順にソートされたユニークな結果タイプリスト
     */
    fun extractAvailableResults(logs: List<AuditLog>): List<String> {
        return logs.map { it.resultType }.distinct().sorted()
    }
}
