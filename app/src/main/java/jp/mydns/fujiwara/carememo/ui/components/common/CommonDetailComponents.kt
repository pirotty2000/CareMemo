package jp.mydns.fujiwara.carememo.ui.components.common

/**
 * Component：CommonDetailComponents
 *
 * 【役割】：
 * 詳細表示画面などで頻繁に使用される、小さな共通UIパーツ群を提供する。
 *
 * 【主な機能】：
 * ・DetailItem: ラベルと値のペアを左右に配置して表示する。ラベルは控えめに、値は強調して描画される。
 *
 * 【想定する利用場所】：
 * 健康記録の詳細表示カード、利用者情報のサマリー表示など。
 *
 * 【このコンポーネントでは行わないこと】：
 * 複雑な入力フォームや、カード自体の外枠（Surface/Card）の定義。
 *
 * 【公開composable】：
 * DetailItem
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * 詳細表示カード内の各項目（ラベルと値のペア）を描画する補助コンポーネント。
 * ラベルを左側に控えめに、値を right 側に強調して配置する。
 */
@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
