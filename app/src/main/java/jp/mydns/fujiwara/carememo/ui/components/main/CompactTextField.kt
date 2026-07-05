package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：CompactTextField
 *
 * 【役割】：
 * 数値を入力する際など、省スペースかつ整った見た目が必要な場面で使用される共通の入力フィールドを提供する。
 *
 * 【主な機能】：
 * ・Material3 の OutlinedTextField をベースに、余白（padding）や文字配置（中央揃え）を最適化した軽量なテキスト入力。
 * ・読み取り専用モード（readOnly）への対応。
 * ・エラー表示（isError）およびサフィックス（単位表示等）のサポート。
 *
 * 【想定する利用場所】：
 * 生年月日入力、記録日時入力、および各種数値入力フィールド。
 *
 * 【このコンポーネントでは行わないこと】：
 * 入力値のバリデーションや数値変換ロジック。
 *
 * 【公開composable】：
 * CompactTextField
 */

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: (FocusState) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged(onFocusChanged),
        interactionSource = interactionSource,
        enabled = true,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                suffix = suffix,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape,
                    )
                }
            )
        }
    )
}
