package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：AppTopAppBarColors
 *
 * 【役割】：
 * アプリ共通の TopAppBar 配色設定を管理する。
 *
 * 【主な機能】：
 * Material3 の TopAppBarDefaults.topAppBarColors を用いて、
 * アプリのデザインシステムに合致した標準的な配色（primaryContainer 等）を生成して提供する。
 *
 * 【想定する利用場所】：
 * 各画面（MainScreen, PersonHealthScreen 等）の Scaffold 内の topBar。
 *
 * 【このコンポーネントでは行わないこと】：
 * TopAppBar 自体の構造定義や、ボタンの配置などのレイアウト管理。
 *
 * 【公開composable】：
 * appTopAppBarColors
 */

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)
