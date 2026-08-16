package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/**
 * Component：AppTopAppBarColors
 *
 * 【役割】
 * アプリ全体で統一された TopAppBar の配色設定を提供します。
 *
 * 【主な機能】
 * ・Material 3 の TopAppBarDefaults.topAppBarColors をラップし、CareMemo のブランドカラーを適用します。
 * ・コンテナ色、タイトル色、アイコン色の一貫性を保証します。
 *
 * 【想定する利用場所】
 * 各画面の Scaffold 内に配置される TopAppBar (CenterAlignedTopAppBar 等) の colors 引数。
 *
 * 【デザイン指針】
 * 背景に primaryContainer を使用し、コンテンツ（文字・アイコン）に onPrimaryContainer を使用することで、
 * アプリの上部エリアとしての視認性と階層構造を明確にします。
 *
 * 【このコンポーネントでは行わないこと】
 * TopAppBar 自体のレイアウト構築。
 */

/**
 * アプリ共通の TopAppBar 配色を生成します。
 *
 * @return Material 3 標準の TopAppBarColors オブジェクト
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)
