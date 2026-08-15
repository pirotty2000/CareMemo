package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Component：EmptyState
 *
 * 【役割】
 * データが存在しない場合（空の状態）に、ユーザーに対して状況を伝えるための共通プレースホルダーUIを提供します。
 *
 * 【主な機能】
 * ・中心に配置された大きなアイコンとタイトルメッセージの表示。
 * ・オプションでの詳細説明（description）の表示。
 * ・画面中央への自動レイアウト。
 *
 * 【想定する利用場所】
 * 利用者一覧が空の時、履歴データがない時、検索結果が0件の時、アーカイブが空の時など。
 *
 * 【このコンポーネントでは行わないこと】
 * データの取得判定（親コンポーネントがデータが空であることを判定した上で呼び出す）。
 *
 * @param message 表示するメインメッセージ（例：「記録がありません」）
 * @param icon 表示するアイコン
 * @param modifier 修飾子
 * @param description 補足説明メッセージ（任意）
 */
@Composable
fun EmptyState(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // アイコン表示（控えめな配色）
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // メインメッセージ
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            // 補足説明がある場合のみ表示
            if (description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
