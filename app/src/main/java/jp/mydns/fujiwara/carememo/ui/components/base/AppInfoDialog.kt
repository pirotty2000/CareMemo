package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：AppInfoDialog
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
 * 選択肢（Yes/No）による分岐（選択が必要な場合は AppDialog や専用ダイアログを使用する）。
 *
 * 【公開composable】：
 * AppInfoDialog
 */

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * 情報通知またはエラー通知用の共通ダイアログ
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
            AppDialogContent(text = message)
        },
        confirmButton = {
            AppDialogDismissButton(
                text = confirmButtonText,
                onClick = onDismiss
            )
        }
    )
}
