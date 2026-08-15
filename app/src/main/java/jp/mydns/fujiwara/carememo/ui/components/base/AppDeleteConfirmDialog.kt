package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * Component：AppDeleteConfirmDialog
 *
 * 【役割】
 * データの削除等の破壊的な操作を行う前に、ユーザーに最終確認を求める共通ダイアログを提供します。
 *
 * 【主な機能】
 * ・タイトル、メッセージ、ボタンテキストのカスタマイズ。
 * ・削除ボタン（確定ボタン）への警告色（error）の適用（AppDialogActionType.DELETE を使用）。
 * ・確定およびキャンセルアクションのコールバック。
 *
 * 【想定する利用場所】
 * 健康記録の削除、所見写真の削除、全データの消去、アーカイブ抹消等の確認。
 *
 * 【このコンポーネントでは行わないこと】
 * 実際の削除処理（引数で渡された onDelete ラムダを通じて親側で実行する）。
 */
@Composable
fun AppDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.p_detail_dialog_title_delete),
    message: String = stringResource(R.string.p_detail_dialog_msg_delete_confirm),
    confirmButtonText: String = stringResource(R.string.common_delete),
    dismissButtonText: String = stringResource(R.string.common_cancel),
) {
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            // 本文メッセージの表示
            AppDialogContent(text = message)
        },
        confirmButton = {
            // 削除実行ボタン（DELETEタイプを指定して警告色を適用）
            AppDialogConfirmButton(
                text = confirmButtonText,
                onClick = {
                    onDelete()
                    onDismiss()
                },
                type = AppDialogActionType.DELETE
            )
        },
        dismissButton = {
            // キャンセルボタン
            AppDialogDismissButton(
                text = dismissButtonText,
                onClick = onDismiss
            )
        }
    )
}
