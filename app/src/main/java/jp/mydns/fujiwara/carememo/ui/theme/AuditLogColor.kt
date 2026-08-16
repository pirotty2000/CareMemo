package jp.mydns.fujiwara.carememo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Component：AuditLogColor
 *
 * 【役割】
 * 監査ログ画面において、操作種別（INSERT, DELETE 等）や実行結果（SUCCESS, ERROR）に応じた
 * 意味論的な配色（セマンティックカラー）を解決するユーティリティです。
 *
 * 【主な機能】
 * ・アクション別配色：操作の重要度や危険度（削除 ➔ 赤、更新 ➔ 黄/青等）に応じた色の提供。
 * ・結果別配色：成否に応じたバッジ用カラーの解決。
 */

/**
 * 操作種別 (actionType) に応じた表示色を解決します。
 */
@Composable
fun getAuditActionColor(actionType: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (actionType) {
        "INSERT" -> MaterialTheme.colorScheme.primary
        "UPDATE" -> MaterialTheme.colorScheme.secondary
        "DELETE", "PERMANENT_DELETE", "CLEAR_ALL_ARCHIVED" -> MaterialTheme.colorScheme.error
        
        // 利用終了（論理削除）は WARNING(橙) と色彩セマンティクスを統一
        "LOGICAL_DELETE" -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        
        "RESTORE" -> MaterialTheme.colorScheme.tertiary
        "ERROR" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * 実行結果 (resultType) に応じたベースカラーを解決します。
 * バッジの枠線や背景のソースとして使用します。
 */
@Composable
fun getAuditResultMainColor(resultType: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (resultType) {
        "SUCCESS" -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        "UNKNOWN" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }
}
