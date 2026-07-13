@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data

import org.junit.Assert.*
import org.junit.Test

/**
 * AppThresholds の定数やフォーマッタを検証する単体テスト
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

    // --- フォーマッタのテスト ---
    @Test
    fun formatHeight_正常な表示() {
        assertEquals("170.0", AppThresholds.formatHeight(170.0))
        assertEquals("---", AppThresholds.formatHeight(null))
    }

    @Test
    fun formatBmi_正常な表示() {
        assertEquals("22.5", AppThresholds.formatBmi(22.49))
        assertEquals("---", AppThresholds.formatBmi(0.0))
        assertEquals("---", AppThresholds.formatBmi(null))
    }
}
