package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit

/**
 * 所見メモのバリデーション結果
 */
enum class ConditionValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 同一利用者の同一日時に既に別のレコードが存在する */
    DUPLICATE_TIME
}

/**
 * Logic：ConditionLogic
 *
 * 【役割】
 * 所見メモ（カテゴリB）に関する共通のドメインロジックを提供します。
 *
 * 【設計指針：Pure Kotlin / Android 非依存】
 * 1. UI や DB アクセスから独立し、純粋なデータ操作と妥当性検証のルールを定義します。
 * 2. 検索フィルタリングは大文字小文字を区別せず、タイトルと内容の両方を対象とする「ユーザー中心の検索ルール」を提供します。
 * 3. 同一時時の重複チェックでは、ID 比較による「自己更新の許容」というドメインルールを厳密に適用します。
 */
object ConditionLogic {

    /**
     * 所見メモのリストを検索クエリでフィルタリングします。
     * 
     * クエリが空の場合は全レコードを返し、入力がある場合はタイトルまたは所見内容に
     * クエリが含まれるレコード（部分一致、大文字小文字区別なし）を抽出します。
     *
     * @param records フィルタリング対象のレコードリスト
     * @param query 検索キーワード
     * @return フィルタリング後のレコードリスト
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
     *
     * DBから取得された「指定日時に存在する既存レコード」と比較し、
     * IDが異なる場合にのみ重複（DUPLICATE_TIME）とみなします。
     * これにより、既存レコードの「日時を変更しない更新」が重複エラーになるのを防ぎます。
     *
     * @param current 現在編集中のレコード情報
     * @param existing DBから取得された同一日時の既存レコード（存在しない場合は null）
     * @return バリデーション結果（SUCCESS または DUPLICATE_TIME）
     */
    fun validateDuplicate(current: ConditionAtVisit, existing: ConditionAtVisit?): ConditionValidationResult {
        if (existing == null) return ConditionValidationResult.SUCCESS
        
        // 取得されたデータのIDが自分と異なれば、別のレコードが存在するため重複とみなす
        val isDuplicate = current.id != existing.id

        return if (isDuplicate) ConditionValidationResult.DUPLICATE_TIME else ConditionValidationResult.SUCCESS
    }
}
