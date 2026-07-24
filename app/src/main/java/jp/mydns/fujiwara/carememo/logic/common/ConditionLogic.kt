package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit

/**
 * 所見メモのバリデーション結果（事実）
 */
enum class ConditionValidationResult {
    SUCCESS,
    DUPLICATE_TIME
}

/**
 * 所見メモに関する共通ドメインロジック。
 */
object ConditionLogic {

    /**
     * 所見メモのリストを検索クエリでフィルタリングします。
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
    fun validateDuplicate(current: ConditionAtVisit, existing: ConditionAtVisit?): ConditionValidationResult {
        if (existing == null) return ConditionValidationResult.SUCCESS
        
        // 取得されたデータのIDが自分と異なれば、別のレコードが存在するため重複とみなす
        val isDuplicate = current.id != existing.id

        return if (isDuplicate) ConditionValidationResult.DUPLICATE_TIME else ConditionValidationResult.SUCCESS
    }
}
