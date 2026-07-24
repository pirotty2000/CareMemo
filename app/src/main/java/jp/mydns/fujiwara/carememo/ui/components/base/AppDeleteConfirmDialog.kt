package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：AppDeleteConfirmDialog
 *
 * 【役割】：
 * データの削除等の破壊的な操作を行う前に、ユーザーに最終確認を求める共通ダイアログを提供する。
 *
 * 【主な機能】：
 * ・タイトル、メッセージ、ボタンテキストのカスタマイズ。
 * ・削除ボタン（確定ボタン）への警告色（error）の適用。
 * ・確定およびキャンセルアクションのコールバック。
 *
 * 【想定する利用場所】：
 * 健康記録の削除、所見写真の削除、全データの消去、アーカイブ抹消等の確認。
 *
 * 【このコンポーネントでは行わないこと】：
 * 削除ロジック自体の実行（呼び出し元から渡された onDelete ラムダを実行するのみ）。
 *
 * 【公開composable】：
 * AppDeleteConfirmDialog
 */

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * 削除確認用の共通ダイアログ
 */
@Composable
fun AppDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    title: String = stringResource(R.string.p_detail_dialog_title_delete),
    message: String = stringResource(R.string.p_detail_dialog_msg_delete_confirm),
    confirmButtonText: String = stringResource(R.string.common_delete),
    dismissButtonText: String = stringResource(R.string.common_cancel),
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppDialogContent(text = message)
        },
        confirmButton = {
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
            AppDialogDismissButton(
                text = dismissButtonText,
                onClick = onDismiss
            )
        }
    )
}
