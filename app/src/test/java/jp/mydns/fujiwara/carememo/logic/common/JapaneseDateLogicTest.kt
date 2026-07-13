@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * JapaneseDateLogic の単体テスト。
 * 仕様書 (TEST_SPEC_JapaneseDateLogic.md) に基づき検証を行う。
 */
class JapaneseDateLogicTest {

    // --- 西暦 → 和暦変換 (toJapaneseDate) ---

    @Test
    fun `JD_01_昭和開始前日はAD`() {
        val date = LocalDate.of(1926, 12, 24)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.AD, era)
        assertEquals(1926, year)
    }

    @Test
    fun `JD_02_昭和開始日は昭和1年`() {
        val date = LocalDate.of(1926, 12, 25)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.SHOWA, era)
        assertEquals(1, year)
    }

    @Test
    fun `JD_03_昭和終了日は昭和64年`() {
        val date = LocalDate.of(1989, 1, 7)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.SHOWA, era)
        assertEquals(64, year)
    }

    @Test
    fun `JD_04_平成開始日は平成1年`() {
        val date = LocalDate.of(1989, 1, 8)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.HEISEI, era)
        assertEquals(1, year)
    }

    @Test
    fun `JD_05_平成終了日は平成31年`() {
        val date = LocalDate.of(2019, 4, 30)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.HEISEI, era)
        assertEquals(31, year)
    }

    @Test
    fun `JD_06_令和開始日は令和1年`() {
        val date = LocalDate.of(2019, 5, 1)
        val (era, year) = JapaneseDateLogic.toJapaneseDate(date)
        assertEquals(BirthEra.REIWA, era)
        assertEquals(1, year)
    }

    // --- 和暦 → 西暦変換 (toLocalDate) ---

    @Test
    fun `LD_01_昭和初日の変換`() {
        val actual = JapaneseDateLogic.toLocalDate(BirthEra.SHOWA, 1, 12, 25)
        assertEquals(LocalDate.of(1926, 12, 25), actual)
    }

    @Test
    fun `LD_02_平成初日の変換`() {
        val actual = JapaneseDateLogic.toLocalDate(BirthEra.HEISEI, 1, 1, 8)
        assertEquals(LocalDate.of(1989, 1, 8), actual)
    }

    @Test
    fun `LD_03_令和初日の変換`() {
        val actual = JapaneseDateLogic.toLocalDate(BirthEra.REIWA, 1, 5, 1)
        assertEquals(LocalDate.of(2019, 5, 1), actual)
    }

    @Test
    fun `LD_04_うるう年の変換`() {
        val actual = JapaneseDateLogic.toLocalDate(BirthEra.AD, 2024, 2, 29)
        assertEquals(LocalDate.of(2024, 2, 29), actual)
    }

    @Test
    fun `LD_05_令和開始前の日付はエラー`() {
        val actual = JapaneseDateLogic.toLocalDate(BirthEra.REIWA, 1, 4, 30)
        assertNull(actual)
    }

    // --- 日付妥当性判定 (isValid) ---

    @Test
    fun `VL_01_正常な日付判定`() {
        assertTrue(JapaneseDateLogic.isValid(BirthEra.REIWA, 1, 5, 1))
    }

    @Test
    fun `VL_02_存在しない日付判定`() {
        assertFalse(JapaneseDateLogic.isValid(BirthEra.AD, 2023, 2, 29))
    }

    @Test
    fun `VL_03_範囲外の和暦年判定`() {
        assertFalse(JapaneseDateLogic.isValid(BirthEra.HEISEI, 32, 1, 1))
    }

    @Test
    fun `VL_04_元号切り替わり日の厳密判定`() {
        assertFalse(JapaneseDateLogic.isValid(BirthEra.SHOWA, 64, 1, 8))
    }

    @Test
    fun `VL_05_1900年以前は無効`() {
        assertFalse(JapaneseDateLogic.isValid(BirthEra.AD, 1899, 12, 31))
    }

    // --- 表示形式変換 (formatYear) ---

    @Test
    fun `FY_01_1年は元年と表記`() {
        assertEquals("元年", JapaneseDateLogic.formatYear(1))
    }

    @Test
    fun `FY_02_2年以上は数値表記`() {
        assertEquals("2", JapaneseDateLogic.formatYear(2))
    }
}
