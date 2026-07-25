package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications

/**
 * IDに関する共通ロジック.
 */
object IdLogic {
    /**
     * 指定されたIDが新規レコード用かどうかを判定する.
     *
     * @param id 判定対象のID
     * @return null, 空文字、または [AppSpecifications.Id.NEW_RECORD_ID] の場合に true
     */
    fun isNew(id: String?): Boolean {
        return id.isNullOrEmpty() || id == AppSpecifications.Id.NEW_RECORD_ID
    }
}
