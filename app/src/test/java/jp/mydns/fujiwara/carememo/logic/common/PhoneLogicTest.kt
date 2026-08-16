package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Logic層テスト：PhoneLogic
 *
 * 電話番号のハイフン挿入ロジック（0120/0800/0570対応）を検証します。
 */
class PhoneLogicTest {

    @Test
    fun PL_01_format_mobile11Digits() {
        assertEquals("090-1234-5678", PhoneLogic.formatPhoneNumber("09012345678"))
    }

    @Test
    fun PL_02_format_tokyoFixed() {
        assertEquals("03-1234-5678", PhoneLogic.formatPhoneNumber("0312345678"))
    }

    @Test
    fun PL_03_format_osakaFixed() {
        assertEquals("06-1234-5678", PhoneLogic.formatPhoneNumber("0612345678"))
    }

    @Test
    fun PL_04_format_generalFixed() {
        assertEquals("048-123-4567", PhoneLogic.formatPhoneNumber("0481234567"))
    }

    @Test
    fun PL_05_format_freeDial_0120() {
        // 0120 は 4-3-3 形式
        assertEquals("0120-000-000", PhoneLogic.formatPhoneNumber("0120000000"))
    }

    @Test
    fun PL_06_format_freeCall_0800() {
        // 0800 は 10桁のフリーコールで 4-3-3 形式
        assertEquals("0800-000-000", PhoneLogic.formatPhoneNumber("0800000000"))
    }

    @Test
    fun PL_07_format_naviDial_0570() {
        // 0570 も 10桁で 4-3-3 形式が一般的
        assertEquals("0570-000-000", PhoneLogic.formatPhoneNumber("0570000000"))
    }

    @Test
    fun PL_08_getHyphenPositions_mobile() {
        assertEquals(listOf(2, 6), PhoneLogic.getHyphenPositions("09012345678"))
    }

    @Test
    fun PL_09_getHyphenPositions_freeDial() {
        assertEquals(listOf(3, 6), PhoneLogic.getHyphenPositions("0120000000"))
    }
}
