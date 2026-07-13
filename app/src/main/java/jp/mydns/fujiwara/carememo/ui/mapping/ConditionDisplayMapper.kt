package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.data.AppThresholds

/**
 * 所見メモの表示用マッパー。
 */
object ConditionDisplayMapper {

    /**
     * 写真の最大枚数の説明文を取得します。
     */
    fun getPhotoCountLabel(current: Int): String {
        return "写真 ($current/${AppThresholds.CONDITION_PHOTO_MAX_COUNT})"
    }
}
