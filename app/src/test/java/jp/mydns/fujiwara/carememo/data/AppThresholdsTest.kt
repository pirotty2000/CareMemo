@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data

import org.junit.Assert.*
import org.junit.Test

/**
 * AppThresholds のロジックを検証する単体テスト
 */
class AppThresholdsTest {

    // --- isWithinFormat のテスト ---
    @Test
    fun isWithinFormat_正常な形式はtrueを返す() {
        // 整数3桁、小数なし
        assertTrue(AppThresholds.isWithinFormat("123", 3, 0))
        assertTrue(AppThresholds.isWithinFormat("0", 3, 0))
        assertTrue(AppThresholds.isWithinFormat("", 3, 0))

        // 整数3桁、小数1桁
        assertTrue(AppThresholds.isWithinFormat("123.4", 3, 1))
        assertTrue(AppThresholds.isWithinFormat("12.3", 3, 1))
        assertTrue(AppThresholds.isWithinFormat("1", 3, 1))
    }

    @Test
    fun isWithinFormat_不正な形式はfalseを返す() {
        // 桁数オーバー
        assertFalse(AppThresholds.isWithinFormat("1234", 3, 0))
        assertFalse(AppThresholds.isWithinFormat("123.45", 3, 1))

        // 記号・文字
        assertFalse(AppThresholds.isWithinFormat("1.2.3", 3, 1))
        assertFalse(AppThresholds.isWithinFormat("12a", 3, 0))
        assertFalse(AppThresholds.isWithinFormat("-10", 3, 0))
    }

    // --- 入力妥当性判定のテスト ---
    @Test
    fun isValidHeightAndWeight_体重が必須で形式が正しいこと() {
        // 正常
        assertTrue(AppThresholds.isValidHeightAndWeight("170.0", "60.5"))
        assertTrue(AppThresholds.isValidHeightAndWeight("", "60.5")) // 身長は空でも可

        // 異常
        assertFalse(AppThresholds.isValidHeightAndWeight("170.0", "")) // 体重が空はNG
        assertFalse(AppThresholds.isValidHeightAndWeight("1700.0", "60.5")) // 身長の桁数オーバー
    }

    @Test
    fun isValidBpAndPulse_いずれか入力が必要で形式が正しいこと() {
        // 正常
        assertTrue(AppThresholds.isValidBpAndPulse("120", "", "", "", "")) // 上血圧のみ
        assertTrue(AppThresholds.isValidBpAndPulse("", "", "", "", "36.5")) // 体温のみ

        // 異常
        assertFalse(AppThresholds.isValidBpAndPulse("", "", "", "", "")) // 全て空はNG
        assertFalse(AppThresholds.isValidBpAndPulse("1200", "", "", "", "")) // 桁数オーバー
    }

    // --- 判定ロジックのテスト ---
    @Test
    fun evaluateBMI_境界値の判定() {
        // 低体重 (< 18.5)
        val resultUnder = AppThresholds.evaluateBMI(18.4)
        assertEquals(AppThresholds.BMI_LABEL_UNDERWEIGHT, resultUnder.first)
        assertEquals(AppThresholds.AlertLevel.WARNING, resultUnder.second)

        // 普通体重 (18.5 <= BMI < 25.0)
        val resultNormal = AppThresholds.evaluateBMI(24.9)
        assertEquals(AppThresholds.BMI_LABEL_NORMAL, resultNormal.first)
        assertEquals(AppThresholds.AlertLevel.NORMAL, resultNormal.second)

        // 肥満度4 (>= 40.0)
        val resultObesity = AppThresholds.evaluateBMI(40.0)
        assertEquals(AppThresholds.BMI_LABEL_OBESITY_4, resultObesity.first)
        assertEquals(AppThresholds.AlertLevel.ALERT, resultObesity.second)
    }

    @Test
    fun evaluateVital_複数異常の検出() {
        // 高血圧かつ頻脈
        val results = AppThresholds.evaluateVital(
            systolic = 150, // 高血圧(上) >= 140
            diastolic = 80,
            sat = 98,
            pulse = 110,    // 頻脈 >= 100
            temp = 36.5
        )
        
        val labels = results.map { it.first }
        assertTrue(labels.contains(AppThresholds.VITAL_LABEL_HIGH_BP))
        assertTrue(labels.contains(AppThresholds.VITAL_LABEL_TACHYCARDIA))
        assertEquals(2, results.size)
    }

    @Test
    fun evaluateVital_正常時は正常ラベルを返す() {
        val results = AppThresholds.evaluateVital(120, 80, 98, 70, 36.5)
        assertEquals(1, results.size)
        assertEquals(AppThresholds.VITAL_LABEL_NORMAL, results[0].first)
        assertEquals(AppThresholds.AlertLevel.NORMAL, results[0].second)
    }
}
