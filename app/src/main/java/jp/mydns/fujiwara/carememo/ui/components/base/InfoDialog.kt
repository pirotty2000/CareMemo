package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：InfoDialog
 *
 * 【役割】：
 * ユーザーに重要な情報やエラーメッセージを通知するための共通ダイアログを提供する。
 *
 * 【主な機能】：
 * ・タイトル（任意）と本文の表示。
 * ・閉じボタン（確定ボタン）のみのシンプルな構成。
 * ・多目的（情報、警告、成功報告、エラー）に利用可能。
 *
 * 【想定する利用場所】：
 * 登録成功時の報告、入力エラーの通知、システムからのメッセージ表示等、アプリ全般。
 *
 * 【このコンポーネントでは行わないこと】：
 * 選択肢（Yes/No）による分岐（選択が必要な場合は AlertDialog や専用ダイアログを使用する）。
 *
 * 【公開composable】：
 * InfoDialog
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    confirmButtonText: String = stringResource(R.string.common_close)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            val scrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text(message)
                }
                VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmButtonText)
            }
        }
    )
}
