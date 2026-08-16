package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * Component：AppInfoDialog
 *
 * 【役割】
 * ユーザーに重要な情報や操作の結果（成功・失敗）、エラーメッセージを通知するための共通ダイアログを提供します。
 *
 * 【主な機能】
 * ・タイトル（任意）と本文メッセージの表示。
 * ・「閉じる」アクションのみのシンプルな構成。
 * ・AppDialogContent による本文のスクロールサポート。
 *
 * 【想定する利用場所】
 * 保存完了の報告、入力チェックエラーの通知、システムエラーの表示など、
 * ユーザーの選択（Yes/No）を必要としない通知全般。
 *
 * 【このコンポーネントでは行わないこと】
 * 複雑な業務フォームの表示（AppDialog を直接使用するか、専用の入力ダイアログを作成する）。
 */
@Composable
fun AppInfoDialog(
    title: String?,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButtonText: String = stringResource(R.string.common_close)
) {
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = title?.let { { Text(it) } },
        text = {
            // 本文を表示（長いメッセージの場合はスクロール可能）
            AppDialogContent(text = message)
        },
        confirmButton = {
            // 通知用ダイアログのため、強調しすぎない AppDialogDismissButton を使用
            AppDialogDismissButton(
                text = confirmButtonText,
                onClick = onDismiss
            )
        }
    )
}
