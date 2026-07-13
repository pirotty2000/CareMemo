package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic

/**
 * 健康記録画面固有のドメインロジック
 */
object PersonHealthLogic {

    /**
     * レコードが新規登録（ID=0）かどうかを判定します。
     */
    fun isNew(record: Any): Boolean {
        return (record as? HistoryRecord)?.id == 0
    }

    /**
     * 保存しようとしているレコードが、自分自身（既存レコード）以外と重複しているか判定するための基準を返します。
     * 重複がある場合は true を返すべき状態と判断します。
     */
    fun isDuplicate(current: HistoryRecord, existing: HistoryRecord?): Boolean {
        if (existing == null) return false
        // IDが0（新規）なら、同じ時間のデータが存在する時点で重複
        if (current.id == 0) return true
        // IDが0以外（更新）なら、取得されたデータのIDが自分と異なれば重複（別のレコードが既にその時間にある）
        return current.id != existing.id
    }
}
