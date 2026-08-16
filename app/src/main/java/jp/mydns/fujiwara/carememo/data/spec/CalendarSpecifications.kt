package jp.mydns.fujiwara.carememo.data.spec

import java.time.LocalDate

/**
 * Spec：CalendarSpecifications
 *
 * 【役割】
 * 日本の暦（和暦）に関連する改元日、西暦オフセット、およびアプリがサポートする
 * 生年月日の下限値（明治期以前を切り捨てる等）を定義します。
 */
object CalendarSpecifications {
    /** アプリがサポートする生年月日の下限 (1900年1月1日) */
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    
    /** 西暦入力時の上限年 */
    const val MAX_WESTERN_YEAR = 2100

    /** 各元号の定義 */
    object Era {
        /** 昭和 **/
        object Showa {
            val START_DATE: LocalDate = LocalDate.of(1926, 12, 25)
            const val OFFSET_YEAR = 1925
            const val MAX_YEAR = 64
        }
        /** 平成 **/
        object Heisei {
            val START_DATE: LocalDate = LocalDate.of(1989, 1, 8)
            const val OFFSET_YEAR = 1988
            const val MAX_YEAR = 31
        }
        /** 令和 **/
        object Reiwa {
            val START_DATE: LocalDate = LocalDate.of(2019, 5, 1)
            const val OFFSET_YEAR = 2018
            const val MAX_YEAR = 99 // 便宜上の最大値
        }
    }
}
