package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Logic層テスト：HealthLogic
 */
class HealthLogicTest {

    // region 1. BMI判定テスト (evaluateBMI)

    @Test
    fun BMI_01_evaluateBMI_underweight() {
        val (status, alert) = HealthLogic.evaluateBMI(18.4)
        assertEquals(BmiStatus.UNDERWEIGHT, status)
        assertEquals(HealthAlertLevel.INFO, alert)
    }

    @Test
    fun BMI_02_evaluateBMI_normal() {
        val (status, alert) = HealthLogic.evaluateBMI(22.0)
        assertEquals(BmiStatus.NORMAL, status)
        assertEquals(HealthAlertLevel.NORMAL, alert)
    }

    @Test
    fun BMI_03_evaluateBMI_obesity1() {
        val (status, alert) = HealthLogic.evaluateBMI(25.0)
        assertEquals(BmiStatus.OBESITY_1, status)
        assertEquals(HealthAlertLevel.INFO, alert)
    }

    @Test
    fun BMI_04_evaluateBMI_obesity4() {
        val (status, alert) = HealthLogic.evaluateBMI(40.0)
        assertEquals(BmiStatus.OBESITY_4, status)
        assertEquals(HealthAlertLevel.ALERT, alert)
    }

    // endregion

    // region 2. バイタル判定テスト (evaluateVital)

    @Test
    fun VTL_01_evaluateVital_allNormal() {
        val results = HealthLogic.evaluateVitalItems(120, 80, 98, 70, 36.5)
        val worst = HealthAlertLevel.worst(results.map { it.second })
        assertEquals(HealthAlertLevel.NORMAL, worst)
    }

    @Test
    fun VTL_02_evaluateVital_highBp() {
        val results = HealthLogic.evaluateVitalItems(140, 80, 98, 70, 36.5)
        val worst = HealthAlertLevel.worst(results.map { it.second })
        assertEquals(HealthAlertLevel.ALERT, worst)
    }

    @Test
    fun VTL_03_evaluateVital_lowSat() {
        val results = HealthLogic.evaluateVitalItems(120, 80, 90, 70, 36.5)
        val worst = HealthAlertLevel.worst(results.map { it.second })
        assertEquals(HealthAlertLevel.ALERT, worst)
    }

    @Test
    fun VTL_04_evaluateVital_fever() {
        val results = HealthLogic.evaluateVitalItems(120, 80, 98, 70, 37.5)
        val worst = HealthAlertLevel.worst(results.map { it.second })
        assertEquals(HealthAlertLevel.ALERT, worst)
    }

    @Test
    fun VTL_05_evaluateVital_combinedWarning() {
        val results = HealthLogic.evaluateVitalItems(95, 55, 98, 45, 35.0)
        val worst = HealthAlertLevel.worst(results.map { it.second })
        assertEquals(HealthAlertLevel.WARNING, worst)
    }

    // endregion

    // region 3. 血糖値・HbA1c判定テスト (evaluateGlucose / evaluateHbA1c)

    @Test
    fun GLC_01_evaluateGlucose_normal() {
        val (status, alert) = HealthLogic.evaluateGlucose(90)
        assertEquals(GlucoseStatus.NORMAL, status)
        assertEquals(HealthAlertLevel.NORMAL, alert)
    }

    @Test
    fun GLC_02_evaluateGlucose_warning() {
        val (status, alert) = HealthLogic.evaluateGlucose(110)
        assertEquals(GlucoseStatus.PREDIABETES, status)
        assertEquals(HealthAlertLevel.WARNING, alert)
    }

    @Test
    fun GLC_03_evaluateGlucose_high() {
        val (status, alert) = HealthLogic.evaluateGlucose(126)
        assertEquals(GlucoseStatus.DIABETES, status)
        assertEquals(HealthAlertLevel.ALERT, alert)
    }

    @Test
    fun GLC_04_evaluateGlucose_low() {
        val (status, alert) = HealthLogic.evaluateGlucose(69)
        assertEquals(GlucoseStatus.LOW, status)
        assertEquals(HealthAlertLevel.INFO, alert)
    }

    @Test
    fun HBA_01_evaluateHbA1c_normal() {
        val (status, alert) = HealthLogic.evaluateHbA1c(5.5)
        assertEquals(HbA1cStatus.NORMAL, status)
        assertEquals(HealthAlertLevel.NORMAL, alert)
    }

    @Test
    fun HBA_02_evaluateHbA1c_warning() {
        val (status, alert) = HealthLogic.evaluateHbA1c(6.0)
        assertEquals(HbA1cStatus.WARNING, status)
        assertEquals(HealthAlertLevel.WARNING, alert)
    }

    @Test
    fun HBA_03_evaluateHbA1c_diabetes() {
        val (status, alert) = HealthLogic.evaluateHbA1c(6.5)
        assertEquals(HbA1cStatus.DIABETES, status)
        assertEquals(HealthAlertLevel.ALERT, alert)
    }

    // endregion

    // region 4. 計算ロジックテスト (calculateBMI)

    @Test
    fun CAL_01_calculateBMI_normal() {
        assertEquals(20.76, HealthLogic.calculateBMI(170.0, 60.0), 0.01)
    }

    @Test
    fun CAL_02_calculateBMI_zeroHeight() {
        assertEquals(0.0, HealthLogic.calculateBMI(0.0, 60.0), 0.0)
    }

    @Test
    fun CAL_03_calculateBMI_nullHeight() {
        assertEquals(0.0, HealthLogic.calculateBMI(null, 60.0), 0.0)
    }

    // endregion

    // region 5. 入力バリデーションテスト (validateInput)

    @Test
    fun VLD_01_validateInput_success() {
        assertEquals(HealthInputValidationResult.SUCCESS, HealthLogic.validateHeightAndWeight("170", "60"))
    }

    @Test
    fun VLD_02_validateInput_empty() {
        assertEquals(HealthInputValidationResult.EMPTY, HealthLogic.validateHeightAndWeight("", ""))
    }

    @Test
    fun VLD_03_validateInput_invalidFormat() {
        assertEquals(HealthInputValidationResult.INVALID_FORMAT, HealthLogic.validateHeightAndWeight("abc", "60"))
    }

    @Test
    fun VLD_04_validateInput_outOfRangeMax() {
        assertEquals(HealthInputValidationResult.OUT_OF_RANGE, HealthLogic.validateHeightAndWeight("300", "60"))
    }

    @Test
    fun VLD_05_validateInput_outOfRangeMin() {
        assertEquals(HealthInputValidationResult.OUT_OF_RANGE, HealthLogic.validateHeightAndWeight("170", "0"))
    }

    // endregion

    // region 6. 形式チェック・フォーマッタテスト

    @Test
    fun FMT_01_isWithinFormat_valid() {
        // 整数3桁、小数なし
        assertTrue(HealthLogic.isWithinFormat("123", 3, 0))
        assertTrue(HealthLogic.isWithinFormat("0", 3, 0))
        assertTrue(HealthLogic.isWithinFormat("", 3, 0))

        // 整数3桁、小数1桁
        assertTrue(HealthLogic.isWithinFormat("123.4", 3, 1))
        assertTrue(HealthLogic.isWithinFormat("12.3", 3, 1))
        assertTrue(HealthLogic.isWithinFormat("1", 3, 1))
    }

    @Test
    fun FMT_02_isWithinFormat_invalid() {
        // 桁数オーバー
        assertFalse(HealthLogic.isWithinFormat("1234", 3, 0))
        assertFalse(HealthLogic.isWithinFormat("123.45", 3, 1))

        // 記号・文字
        assertFalse(HealthLogic.isWithinFormat("1.2.3", 3, 1))
        assertFalse(HealthLogic.isWithinFormat("12a", 3, 0))
        assertFalse(HealthLogic.isWithinFormat("-10", 3, 0))
    }

    @Test
    fun FMT_03_formatHeight() {
        assertEquals("170.0", HealthLogic.formatHeight(170.0))
        assertEquals("---", HealthLogic.formatHeight(null))
    }

    @Test
    fun FMT_04_formatBmi() {
        assertEquals("22.5", HealthLogic.formatBmi(22.49))
        assertEquals("---", HealthLogic.formatBmi(0.0))
        assertEquals("---", HealthLogic.formatBmi(null))
    }

    // endregion
}
