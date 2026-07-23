package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.data.spec.*

/**
 * 所見メモの表示用マッパー。
 */
object ConditionDisplayMapper {

    /**
     * 写真の最大枚数の説明文を取得します。
     */
    fun getPhotoCountLabel(current: Int): String {
        return "写真 ($current/${ConstraintSpecifications.Condition.Photo.MAX_COUNT})"
    }
}
