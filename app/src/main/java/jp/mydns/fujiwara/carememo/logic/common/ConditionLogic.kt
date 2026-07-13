package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit

/**
 * 所見メモに関する共通ドメインロジック。
 */
object ConditionLogic {

    /**
     * 所見メモのリストを検索クエリでフィルタリングします。
     * タイトルまたは本文にクエリが含まれるものを抽出します（大文字小文字を区別しません）。
     */
    fun filterRecords(records: List<ConditionAtVisit>, query: String): List<ConditionAtVisit> {
        if (query.isBlank()) return records
        return records.filter { record ->
            val titleMatch = record.title?.contains(query, ignoreCase = true) == true
            val conditionMatch = record.condition?.contains(query, ignoreCase = true) == true
            titleMatch || conditionMatch
        }
    }

    /**
     * 保存しようとしているレコードが、自分自身（既存レコード）以外と重複しているか判定します。
     */
    fun isDuplicate(current: ConditionAtVisit, existing: ConditionAtVisit?): Boolean {
        if (existing == null) return false
        // IDが0（新規）なら、同じ時間のデータが存在する時点で重複
        if (current.id == 0) return true
        // IDが0以外（更新）なら、取得されたデータのIDが自分と異なれば重複
        return current.id != existing.id
    }
}
