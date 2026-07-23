package jp.mydns.fujiwara.carememo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel

/**
 * HealthAlertLevel (NORMAL, WARNING, ALERT, INFO) を、
 * 現在のテーマ（ColorScheme）に基づいた具体的な表示色に変換します。
 */
@Composable
fun HealthAlertLevel.getDisplayColor(): Color {
    val isDark = isSystemInDarkTheme()
    return when (this) {
        HealthAlertLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        
        // 注意・予備群：オレンジ系（M3に適切なスロットがないため独自定義し、モードで明度調整）
        HealthAlertLevel.WARNING -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        
        HealthAlertLevel.ALERT -> MaterialTheme.colorScheme.error
        
        // 情報・低値：青系（M3に適切なスロットがないため独自定義し、モードで明度調整）
        HealthAlertLevel.INFO -> if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1)
        
        HealthAlertLevel.NONE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }
}

/**
 * グラフ背景等のハイライト用カラーを取得します（非Composableコンテキスト用）。
 * HealthChartHelper などから呼び出されることを想定しています。
 */
fun HealthAlertLevel.getHighlightColor(isDark: Boolean): Color {
    return when (this) {
        HealthAlertLevel.ALERT -> if (isDark) Color(0xFF3B1010) else Color(0xFFFFEBEE)
        HealthAlertLevel.WARNING -> if (isDark) Color(0xFF2E2A00) else Color(0xFFFFF3E0) // 極薄オレンジ
        HealthAlertLevel.INFO -> if (isDark) Color(0xFF0D1C33) else Color(0xFFE1F5FE)    // 極薄青
        HealthAlertLevel.NORMAL -> if (isDark) Color.Transparent else Color.White
        HealthAlertLevel.NONE -> Color.Transparent
    }
}
