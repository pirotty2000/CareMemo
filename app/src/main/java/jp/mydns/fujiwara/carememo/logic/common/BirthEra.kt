package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.R

/**
 * 生年月日の元号定義
 */
enum class BirthEra(val displayNameRes: Int) {
    AD(R.string.common_era_ad),
    SHOWA(R.string.common_era_showa),
    HEISEI(R.string.common_era_heisei),
    REIWA(R.string.common_era_reiwa)
}
