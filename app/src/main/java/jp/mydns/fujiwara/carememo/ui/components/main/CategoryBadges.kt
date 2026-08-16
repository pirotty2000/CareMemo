package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：CategoryBadges
 *
 * 【役割】
 * 利用者の各カテゴリ（健康記録、所見メモ、服薬管理）における記録の有無を、色付きの小型バッジ（漢字一文字）で視覚的に表現します。
 *
 * 【主な機能】
 * ・「身」「バ」「糖」「メ」「薬」の各バッジを 2x2+1 のグリッド形式でコンパクトに表示。
 * ・記録データが存在する場合（isActive）は各カテゴリ固有のアクセントカラーで強調表示。
 * ・記録データが存在しない場合はグレーアウト（透過表示）し、情報の有無を一目で判別可能にします。
 * ・アクセシビリティ対応として、記録があるバッジには適切な読み上げテキスト（contentDescription）を付与。
 *
 * 【想定する利用場所】
 * ・利用者一覧画面（MainScreen）の各リストアイテム内。
 *
 * 【このコンポーネントでは行わないこと】
 * ・個別の記録データの詳細表示や、バッジ自体のクリックイベント処理。
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary

/**
 * 全体像：カテゴリ記録状況バッジ（Category Badges）
 *
 * ■ UserListItem (利用者カード)
 * │
 * └─ [1] CategoryBadges (★本コンポーネント：2x2+1 グリッド)
 *      ├─ BadgeChar (身：身長体重)
 *      ├─ BadgeChar (バ：バイタル)
 *      ├─ BadgeChar (糖：血糖・HbA1c)
 *      ├─ BadgeChar (メ：所見メモ)
 *      └─ BadgeChar (薬：服薬管理)
 */

/**
 * カテゴリバッジ一覧を表示するコンポーネント。
 *
 * @param summary 各カテゴリの記録有無を保持するサマリーデータ
 * @param modifier 修飾子
 */
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
            BadgeChar(text = "身", isActive = summary.hasHeightWeight, color = Color(0xFFE91E63), contentDescription = stringResource(R.string.health_badge_desc_height_weight))
            BadgeChar(text = "バ", isActive = summary.hasBpAndPulse, color = Color(0xFF2196F3), contentDescription = stringResource(R.string.health_badge_desc_vital))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 糖 -> 血糖・HbA1c, メ -> 所見メモ
            BadgeChar(text = "糖", isActive = summary.hasGlucoseAndHbA1c, color = Color(0xFFFF9800), contentDescription = stringResource(R.string.health_badge_desc_glucose))
            BadgeChar(text = "メ", isActive = summary.hasCondition, color = Color(0xFF4CAF50), contentDescription = stringResource(R.string.health_badge_desc_condition))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // 薬 -> 服薬
            BadgeChar(text = "薬", isActive = summary.hasMedication, color = Color(0xFF673AB7), contentDescription = stringResource(R.string.health_badge_desc_medication))
        }
    }
}

/**
 * 個別のバッジ（漢字一文字）を描画するコンポーネント。
 *
 * @param text 表示する文字（例：「身」「薬」）
 * @param isActive 記録が存在し、バッジを強調表示するかどうか
 * @param color 記録あり（アクティブ）時の背景色
 * @param contentDescription アクセシビリティ用の読み上げテキスト
 */
@Composable
fun BadgeChar(
    text: String,
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        // 非アクティブ時は背景・文字ともに透過・グレーアウトさせて「情報の欠落」を表現
        color = if (isActive) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
            .size(18.dp)
            .then(
                if (isActive && contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
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
