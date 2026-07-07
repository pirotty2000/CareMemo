package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：AppDialog
 *
 * 【役割】：
 * アプリ全体のダイアログの「器（コンテナ）」となる共通コンポーネント。
 * Material 3 の AlertDialog をラップし、CareMemo 標準のデザイン（形状、配色、スクロール制御）を適用する。
 *
 * 【主な機能】：
 * ・標準的なタイトル、アイコン、ボタン配置の提供。
 * ・コンテンツエリアの自動スクロール対応（AppDialogContent）。
 * ・業務固有の複雑な UI を content 引数として受け入れ可能。
 * ・ポジティブアクション（確定）とネガティブアクション（キャンセル）の視覚的差異の明示。
 *
 * 【標準ルール（UX）】：
 * 1. ポジティブアクション（保存、削除、実行等）は、右下に配置し、塗りつぶしボタンで目立たせる。
 * 2. ネガティブアクション（キャンセル、閉じる等）は、左側に配置し、文字のみのボタンとする。
 * 3. ポジティブアクションの色分け：保存系＝Primary、削除系＝Error、その他実行系＝Tertiary。
 *
 * 【想定する利用場所】：
 * アプリ内のすべてのダイアログ（InfoDialog, DeleteConfirmDialog, 各種入力ダイアログ等）の基盤。
 *
 * 【公開composable】：
 * ・AppDialog : ダイアログの外枠（コンテナ）
 * ・AppDialogContent : ダイアログ内部の標準的なコンテンツ（スクロール補助付き）
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * ダイアログの確定ボタンの種類。
 * 操作の性質（セマンティクス）に応じて色を決定する。
 */
enum class AppDialogActionType {
    SAVE,   // 保存・確定系：テーマの Primary
    DELETE, // 削除・破棄系：テーマの Error
    ACTION  // 実行・出力系：テーマの Tertiary
}

/**
 * CareMemo 標準ダイアログコンテナ
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

/**
 * ダイアログ用の共通確定ボタン（ポジティブアクション）
 * 常に塗りつぶしボタン（Button）を使用し、視覚的に目立たせる。
 */
@Composable
fun AppDialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    type: AppDialogActionType = AppDialogActionType.SAVE
) {
    // タイプに応じた色の決定（テーマのセマンティクスを優先）
    val buttonColors = when (type) {
        AppDialogActionType.SAVE -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        AppDialogActionType.DELETE -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
        AppDialogActionType.ACTION -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = buttonColors,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(text)
    }
}

/**
 * ダイアログ用の共通キャンセル・閉じるボタン（ネガティブアクション）
 * 常に文字のみ（TextButton）とし、ポジティブアクションと対比させる。
 */
@Composable
fun AppDialogDismissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(text)
    }
}

/**
 * ダイアログ内部の標準的なコンテンツエリア（スクロール補助付き）
 */
@Composable
fun AppDialogContent(
    modifier: Modifier = Modifier,
    text: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            if (text != null) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
            content?.invoke()
        }
        VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
    }
}
