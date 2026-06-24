package jp.mydns.fujiwara.carememo.ui.components

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
