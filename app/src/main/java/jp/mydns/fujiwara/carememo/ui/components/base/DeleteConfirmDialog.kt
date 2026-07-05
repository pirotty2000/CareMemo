package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：DeleteConfirmDialog
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
 * DeleteConfirmDialog
 */

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * 削除確認用の共通ダイアログ
 */
@Composable
fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    title: String = stringResource(R.string.delete_data_title),
    message: String = stringResource(R.string.delete_confirm_message),
    confirmButtonText: String = stringResource(R.string.delete),
    dismissButtonText: String = stringResource(R.string.cancel),
    isDestructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismiss()
                },
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissButtonText)
            }
        }
    )
}
