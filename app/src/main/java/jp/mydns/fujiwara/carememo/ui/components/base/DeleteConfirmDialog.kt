package jp.mydns.fujiwara.carememo.ui.components.base

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
