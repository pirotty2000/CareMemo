package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.BirthEra

/**
 * 元号の表示用マッピングロジック
 */
object BirthEraDisplayMapper {

    /**
     * 元号に対応する表示名のリソースIDを取得します。
     */
    fun getDisplayNameRes(era: BirthEra): Int = when (era) {
        BirthEra.AD -> R.string.common_era_ad
        BirthEra.SHOWA -> R.string.common_era_showa
        BirthEra.HEISEI -> R.string.common_era_heisei
        BirthEra.REIWA -> R.string.common_era_reiwa
    }
}
