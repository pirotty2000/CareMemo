package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R

/**
 * Component：LoadingScreen
 *
 * 【役割】
 * データの読み込み中やバックグラウンド処理中に表示される、アプリ共通の待機画面を提供します。
 *
 * 【主な機能】
 * ・中心に配置された円形プログレスインジケーター（CircularProgressIndicator）の表示。
 * ・待機メッセージ（デフォルトは「読み込み中...」）の表示。
 * ・画面中央への自動レイアウト。
 * ・UIテスト用のテストタグ（AppLoadingIndicator）の付与。
 *
 * 【想定する利用場所】
 * DBからの初期データ取得時、ネットワーク通信時（将来用）、大量データの加工処理時など。
 *
 * 【このコンポーネントでは行わないこと】
 * 処理の実行制御（処理が完了したら Composable 自体が消えるように親側で制御する）。
 */

/**
 * 全体像：共通ローディング画面（LoadingScreen）
 *
 * ■ LoadingScreen (コンテナ：Box)
 * │
 * └─ Column (中央配置)
 *      ├─ CircularProgressIndicator (くるくる)
 *      └─ Text (メッセージ：デフォルト「読み込み中...」)
 */
@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.common_loading)
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("AppLoadingIndicator"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // プログレスインジケーターの表示
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                // 待機メッセージの表示
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
