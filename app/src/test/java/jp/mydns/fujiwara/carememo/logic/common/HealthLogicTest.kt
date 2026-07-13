@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.*
import org.junit.Test

/**
 * HealthLogic のロジックを検証する単体テスト
 */
class HealthLogicTest {

    // --- 入力妥当性判定のテスト ---
    @Test
    fun isValidHeightAndWeight_体重が必須で形式が正しいこと() {
        // 正常
        assertTrue(HealthLogic.isValidHeightAndWeight("170.0", "60.5"))
        assertTrue(HealthLogic.isValidHeightAndWeight("", "60.5")) // 身長は空でも可

        // 異常
        assertFalse(HealthLogic.isValidHeightAndWeight("170.0", "")) // 体重が空はNG
        assertFalse(HealthLogic.isValidHeightAndWeight("1700.0", "60.5")) // 身長の桁数オーバー
    }

    @Test
    fun isValidBpAndPulse_いずれか入力が必要で形式が正しいこと() {
        // 正常
        assertTrue(HealthLogic.isValidBpAndPulse("120", "", "", "", "")) // 上血圧のみ
        assertTrue(HealthLogic.isValidBpAndPulse("", "", "", "", "36.5")) // 体温のみ

        // 異常
        assertFalse(HealthLogic.isValidBpAndPulse("", "", "", "", "")) // 全て空はNG
        assertFalse(HealthLogic.isValidBpAndPulse("1200", "", "", "", "")) // 桁数オーバー
    }

    @Test
    fun isValidGlucoseAndHbA1c_いずれか入力が必要で形式が正しいこと() {
        // 正常
        assertTrue(HealthLogic.isValidGlucoseAndHbA1c("100", ""))
        assertTrue(HealthLogic.isValidGlucoseAndHbA1c("", "6.0"))

        // 異常
        assertFalse(HealthLogic.isValidGlucoseAndHbA1c("", ""))
    }

    // --- 判定ロジックのテスト ---
    @Test
    fun evaluateBMI_境界値の判定() {
        // 低体重 (< 18.5)
        val (statusUnder, alertUnder) = HealthLogic.evaluateBMI(18.4)
        assertEquals(BmiStatus.UNDERWEIGHT, statusUnder)
        assertEquals(HealthAlertLevel.INFO, alertUnder)

        // 普通体重 (18.5 <= BMI < 25.0)
        val (statusNormal, alertNormal) = HealthLogic.evaluateBMI(24.9)
        assertEquals(BmiStatus.NORMAL, statusNormal)
        assertEquals(HealthAlertLevel.NORMAL, alertNormal)

        // 肥満度4 (>= 40.0)
        val (statusObesity, alertObesity) = HealthLogic.evaluateBMI(40.0)
        assertEquals(BmiStatus.OBESITY_4, statusObesity)
        assertEquals(HealthAlertLevel.ALERT, alertObesity)
    }

    @Test
    fun evaluateVitalItems_複数異常の検出() {
        // 高血圧かつ頻脈
        val results = HealthLogic.evaluateVitalItems(
            systolic = 150, // 高血圧(上) >= 140
            diastolic = 80,
            sat = 98,
            pulse = 110,    // 頻脈 >= 100
            temp = 36.5
        )
        
        val statuses = results.map { it.first }
        assertTrue(statuses.contains(VitalStatus.HIGH_BP))
        assertTrue(statuses.contains(VitalStatus.TACHYCARDIA))
        assertEquals(2, results.size)
    }

    @Test
    fun evaluateVitalItems_正常時は正常ラベルを返す() {
        val results = HealthLogic.evaluateVitalItems(120, 80, 98, 70, 36.5)
        assertEquals(1, results.size)
        assertEquals(VitalStatus.NORMAL, results[0].first)
        assertEquals(HealthAlertLevel.NORMAL, results[0].second)
    }

    @Test
    fun calculateBMI_正常に計算されること() {
        val bmi = HealthLogic.calculateBMI(170.0, 65.0)
        assertEquals(22.49, bmi, 0.1)
    }

    @Test
    fun calculateBMI_0除算の考慮() {
        assertEquals(0.0, HealthLogic.calculateBMI(0.0, 60.0), 0.0)
        assertEquals(0.0, HealthLogic.calculateBMI(null, 60.0), 0.0)
    }
}
