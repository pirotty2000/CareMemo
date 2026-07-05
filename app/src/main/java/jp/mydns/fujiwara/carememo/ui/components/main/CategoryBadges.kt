package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：CategoryBadges
 *
 * 【役割】：
 * 利用者の各カテゴリ（健康記録、所見メモ、服薬管理）における記録の有無を、色付きのバッジ（漢字一文字）で視覚的に表現する。
 *
 * 【主な機能】：
 * ・「身」「バ」「糖」「メ」「薬」の各バッジを 2x2+1 のグリッド形式で表示。
 * ・記録データが存在する場合（isActive）は鮮やかなアクセントカラー、存在しない場合はグレーアウトして表示。
 *
 * 【想定する利用場所】：
 * 利用者一覧画面（MainScreen）の各リストアイテム内。
 *
 * 【このコンポーネントでは行わないこと】：
 * 個別の記録データの詳細表示や、バッジ自体のクリックイベント処理。
 *
 * 【公開composable】：
 * CategoryBadges
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary

@Composable
fun CategoryBadges(
    summary: PersonCategorySummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 身 -> 身長・体重, バ -> バイタル
            BadgeChar(text = "身", isActive = summary.hasHeightWeight, color = Color(0xFFE91E63))
            BadgeChar(text = "バ", isActive = summary.hasBpAndPulse, color = Color(0xFF2196F3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 糖 -> 血糖・HbA1c, メ -> 所見メモ
            BadgeChar(text = "糖", isActive = summary.hasGlucoseAndHbA1c, color = Color(0xFFFF9800))
            BadgeChar(text = "メ", isActive = summary.hasCondition, color = Color(0xFF4CAF50))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 薬 -> 服薬
            BadgeChar(text = "薬", isActive = summary.hasMedication, color = Color(0xFF673AB7))
        }
    }
}

@Composable
fun BadgeChar(text: String, isActive: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (isActive) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.size(18.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 10.sp
                )
            )
        }
    }
}
