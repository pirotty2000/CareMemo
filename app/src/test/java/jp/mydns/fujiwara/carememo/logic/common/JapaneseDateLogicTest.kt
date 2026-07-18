package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Logic層テスト：JapaneseDateLogic
 */
class JapaneseDateLogicTest {

    // region 2. 西暦 ↔ 和暦 変換テスト (toJapaneseDate / toLocalDate)

    @Test
    fun CNV_01_toJapaneseDate_reiwa() {
        val (era, year) = JapaneseDateLogic.toJapaneseDate(LocalDate.of(2019, 5, 1))
        assertEquals(BirthEra.REIWA, era)
        assertEquals(1, year)
    }

    @Test
    fun CNV_02_toJapaneseDate_heisei() {
        val (era, year) = JapaneseDateLogic.toJapaneseDate(LocalDate.of(1989, 1, 8))
        assertEquals(BirthEra.HEISEI, era)
        assertEquals(1, year)
    }

    @Test
    fun CNV_03_toJapaneseDate_showa() {
        val (era, year) = JapaneseDateLogic.toJapaneseDate(LocalDate.of(1926, 12, 25))
        assertEquals(BirthEra.SHOWA, era)
        assertEquals(1, year)
    }

    @Test
    fun CNV_04_toJapaneseDate_ad() {
        val (era, year) = JapaneseDateLogic.toJapaneseDate(LocalDate.of(1926, 12, 24))
        assertEquals(BirthEra.AD, era)
        assertEquals(1926, year)
    }

    @Test
    fun CNV_05_toLocalDate_success() {
        val date = JapaneseDateLogic.toLocalDate(BirthEra.SHOWA, 60, 1, 1)
        assertEquals(LocalDate.of(1985, 1, 1), date)
    }

    // endregion

    // region 3. バリデーションテスト (validate)

    @Test
    fun VAL_01_validate_success() {
        assertEquals(DateValidationResult.SUCCESS, JapaneseDateLogic.validate(BirthEra.REIWA, 5, 10, 27))
    }

    @Test
    fun VAL_02_validate_invalidDay() {
        assertEquals(DateValidationResult.INVALID_DAY, JapaneseDateLogic.validate(BirthEra.SHOWA, 60, 2, 30))
    }

    @Test
    fun VAL_03_validate_invalidMonth() {
        assertEquals(DateValidationResult.INVALID_MONTH, JapaneseDateLogic.validate(BirthEra.SHOWA, 60, 13, 1))
    }

    @Test
    fun VAL_04_validate_invalidEraRange() {
        // 令和は2019/5/1からなので、2019/4/30（令和1年4月30日）は不正
        assertEquals(DateValidationResult.INVALID_ERA_RANGE, JapaneseDateLogic.validate(BirthEra.REIWA, 1, 4, 30))
    }

    @Test
    fun VAL_05_validate_invalidYear() {
        assertEquals(DateValidationResult.INVALID_YEAR, JapaneseDateLogic.validate(BirthEra.REIWA, 0, 1, 1))
    }

    @Test
    fun VAL_06_validate_outOfAppRange() {
        // 1900年未満は制限
        assertEquals(DateValidationResult.OUT_OF_APP_RANGE, JapaneseDateLogic.validate(BirthEra.AD, 1899, 12, 31))
    }

    @Test
    fun VAL_07_validate_leapYear_success() {
        assertEquals(DateValidationResult.SUCCESS, JapaneseDateLogic.validate(BirthEra.HEISEI, 4, 2, 29))
    }

    @Test
    fun VAL_08_validate_notLeapYear_fail() {
        assertEquals(DateValidationResult.INVALID_DAY, JapaneseDateLogic.validate(BirthEra.REIWA, 5, 2, 29))
    }

    // endregion
}
