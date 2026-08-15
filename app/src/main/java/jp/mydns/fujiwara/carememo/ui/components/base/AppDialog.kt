package jp.mydns.fujiwara.carememo.ui.components.base

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
 * Component：AppDialog
 *
 * 【役割】
 * アプリ全体のダイアログの「器（コンテナ）」となる共通コンポーネントです。
 * Material 3 の AlertDialog をラップし、CareMemo 標準のデザイン（形状、配色、スクロール制御）を適用します。
 *
 * 【主な機能】
 * ・標準的なタイトル、アイコン、ボタン配置の提供。
 * ・コンテンツエリアの自動スクロール対応（AppDialogContent）。
 * ・業務固有の複雑な UI を content 引数として受け入れ可能。
 * ・ポジティブアクション（確定）とネガティブアクション（キャンセル）の視覚的差異の明示。
 *
 * 【標準ルール（UX）】
 * 1. ポジティブアクション（保存、削除、実行等）：右下に配置し、塗りつぶしボタンで目立たせる。
 * 2. ネガティブアクション（キャンセル、閉じる等）：左側に配置し、文字のみのボタンとする。
 * 3. ポジティブアクションの色分け：保存系＝Primary、削除系＝Error、その他実行系＝Tertiary。
 *
 * 【想定する利用場所】
 * アプリ内のすべてのダイアログ（AppInfoDialog, AppDeleteConfirmDialog, 各種入力ダイアログ等）の基盤。
 */

/**
 * 全体像：共通ダイアログ基盤（AppDialog）
 *
 * ■ AppDialog (最外位：AlertDialog ラッパー)
 * │
 * ├─ title (スロット：タイトル領域)
 * ├─ text (スロット：コンテンツ領域)
 * │    └─ [1] AppDialogContent (スクロール・余白制御用コンテナ)
 * │         └─ <実際のコンテンツ> (テキスト、TextField、写真等)
 * └─ buttons (Bottom Area)
 *      ├─ [2] AppDialogDismissButton (キャンセル・閉じる：通常は左側)
 *      └─ [3] AppDialogConfirmButton (保存・実行・削除：通常は右側)
 */

/**
 * ダイアログの確定ボタンの種類。
 * 操作の性質（セマンティクス）に応じて色を決定します。
 */
enum class AppDialogActionType {
    /** 保存・確定系：テーマの Primary を使用 */
    SAVE,
    /** 削除・破棄系：テーマの Error を使用 */
    DELETE,
    /** 実行・出力系：テーマの Tertiary を使用 */
    ACTION
}

/**
 * CareMemo 標準ダイアログコンテナ
 *
 * @param onDismissRequest ダイアログの外側をタップしたり戻るボタンを押した際のコールバック
 * @param confirmButton 右側に配置されるポジティブアクションボタン（AppDialogConfirmButton 推奨）
 * @param modifier 修飾子
 * @param dismissButton 左側に配置されるネガティブアクションボタン（AppDialogDismissButton 推奨）
 * @param icon タイトルの上に表示されるアイコン
 * @param title ダイアログのタイトル
 * @param text ダイアログの本文（AppDialogContent を使用することでスクロールに対応可能）
 * @param shape ダイアログの形状
 * @param containerColor 背景色
 * @param iconContentColor アイコンの色
 * @param titleContentColor タイトルの色
 * @param textContentColor 本文の色
 * @param tonalElevation トーンの高さ
 * @param properties ダイアログのプロパティ（dismissOnClickOutside 等）
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
 * 常に塗りつぶしボタン（Button）を使用し、視覚的に目立たせます。
 *
 * @param text ボタンのラベル
 * @param onClick クリック時のコールバック
 * @param modifier 修飾子
 * @param enabled 有効状態
 * @param type ボタンの種類（色分けに影響）
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
 * 常に文字のみ（TextButton）とし、ポジティブアクションと対比させます。
 *
 * @param text ボタンのラベル
 * @param onClick クリック時のコールバック
 * @param modifier 修飾子
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
 *
 * 縦方向に長いコンテンツが含まれる場合でも、ボタンエリアを固定したまま
 * 内容のみをスクロール可能にします。
 *
 * @param modifier 修飾子
 * @param text 本文として表示する文字列（簡易的な場合に使用）
 * @param content 任意の Composable コンテンツ（複雑なレイアウトが必要な場合に使用）
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
        // スクロール可能なことを示すインジケータ（共通コンポーネント）を表示
        VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
    }
}
