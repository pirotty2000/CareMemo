package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test：AlertLogicTest
 */
class AlertLogicTest {

    @Test
    fun WTL_01_evaluateWeightLoss_exactThreshold_returnsAlert() {
        // 60.0 -> 57.0 (ちょうど3kg減少)
        val (level, diff) = AlertLogic.evaluateWeightLoss(57.0, 60.0)
        assertEquals(HealthAlertLevel.ALERT, level)
        assertEquals(-3.0, diff, 0.0)
    }

    @Test
    fun WTL_02_evaluateWeightLoss_overThreshold_returnsAlert() {
        // 60.0 -> 56.5 (3.5kg減少)
        val (level, diff) = AlertLogic.evaluateWeightLoss(56.5, 60.0)
        assertEquals(HealthAlertLevel.ALERT, level)
        assertEquals(-3.5, diff, 0.0)
    }

    @Test
    fun WTL_03_evaluateWeightLoss_underThreshold_returnsNormal() {
        // 60.0 -> 57.1 (2.9kg減少)
        val (level, _) = AlertLogic.evaluateWeightLoss(57.1, 60.0)
        assertEquals(HealthAlertLevel.NORMAL, level)
    }

    @Test
    fun WTL_04_evaluateWeightLoss_weightGain_returnsNormal() {
        val (level, _) = AlertLogic.evaluateWeightLoss(61.0, 60.0)
        assertEquals(HealthAlertLevel.NORMAL, level)
    }

    @Test
    fun WTL_05_evaluateWeightLoss_nullInput_returnsNormal() {
        val (level1, _) = AlertLogic.evaluateWeightLoss(null, 60.0)
        val (level2, _) = AlertLogic.evaluateWeightLoss(60.0, null)
        assertEquals(HealthAlertLevel.NORMAL, level1)
        assertEquals(HealthAlertLevel.NORMAL, level2)
    }
}
