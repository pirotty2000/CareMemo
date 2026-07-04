package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * 情報通知またはエラー通知用の共通ダイアログ
 */
@Composable
fun InfoDialog(
    title: String?,
    message: String,
    onDismiss: () -> Unit,
    confirmButtonText: String = stringResource(R.string.close)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmButtonText)
            }
        }
    )
}
