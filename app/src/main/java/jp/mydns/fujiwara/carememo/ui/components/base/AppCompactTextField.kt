package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Component：AppCompactTextField
 *
 * 【役割】
 * 数値を入力する際など、省スペースかつ整った見た目が必要な場面で使用される共通の入力フィールドを提供します。
 * 内部で AppTextField と同様のロジック（TextFieldValue 管理、フォーカス制御）を実装しており、
 * プロジェクト共通の入力ルールを継承しています。
 *
 * 【主な機能】
 * ・余白（padding）や文字配置（中央揃え）を最適化した軽量なテキスト入力。
 * ・フォーカス取得時のカーソル末尾移動（入力スムーズ化）。
 * ・最大桁数（maxLength）到達時の自動フォーカス移動。
 * ・入力タイプ（AppTextFieldType）に応じたキーボード、フィルタ、IMEアクションの自動設定。
 *
 * 【想定する利用場所】
 * 生年月日入力、記録日時入力、および各種数値入力フィールド。
 */

/**
 * 全体像：小型入力フィールド（AppCompactTextField）
 *
 * ■ AppCompactTextField (最外位：BasicTextField + DecorationBox)
 * │
 * ├─ [1] TextFieldValue (内部状態：カーソル位置管理)
 * ├─ [2] DecorationBox (OutlinedTextField スタイルの外装適用)
 * └─ [3] TextAlign.Center (中央揃え：数値入力への最適化)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: AppTextFieldType = AppTextFieldType.INTEGER,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    suffix: @Composable (() -> Unit)? = null,
    maxLength: Int = Int.MAX_VALUE,
    imeAction: ImeAction = ImeAction.Next,
    autoMoveFocus: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    onFocusChanged: (FocusState) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }

    // カーソル位置を制御するために TextFieldValue を内部で保持
    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    // 外部からの value 変更を内部状態（textFieldValueState）に同期させる
    LaunchedEffect(value) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueState.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    // 入力タイプに応じたキーボード設定の生成
    val keyboardOptions = remember(type, imeAction) {
        when (type) {
            AppTextFieldType.INTEGER -> KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = imeAction)
            AppTextFieldType.DECIMAL -> KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = imeAction)
            else -> KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text, imeAction = imeAction)
        }
    }

    // デフォルトのキーボードアクション設定（Nextなら次へ、Doneならフォーカス解除）
    val defaultKeyboardActions = remember(autoMoveFocus, imeAction) {
        KeyboardActions(
            onNext = {
                if (autoMoveFocus) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            },
            onDone = {
                if (autoMoveFocus) {
                    focusManager.clearFocus()
                }
            }
        )
    }

    BasicTextField(
        value = textFieldValueState,
        onValueChange = { newValue ->
            // タイプに応じたフィルタリング
            var filteredText = when (type) {
                AppTextFieldType.INTEGER -> newValue.text.filter { it.isDigit() }
                AppTextFieldType.DECIMAL -> newValue.text.filter { it.isDigit() || it == '.' }
                else -> newValue.text
            }

            // 最大文字数制限の適用
            if (filteredText.length > maxLength) {
                filteredText = filteredText.take(maxLength)
            }

            val finalValue = if (filteredText != newValue.text) newValue.copy(text = filteredText) else newValue
            
            // テキストが実際に変更されたか（不要なコールバックを防止）
            val isTextChanged = finalValue.text != textFieldValueState.text

            textFieldValueState = finalValue
            
            if (isTextChanged) {
                onValueChange(finalValue.text)

                // maxLength 到達時の自動フォーカス移動
                if (autoMoveFocus && finalValue.text.length == maxLength && imeAction == ImeAction.Next) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            }
        },
        modifier = modifier
            .bringIntoViewRequester(requester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState)
                if (focusState.isFocused) {
                    // キーボード表示時にフィールドが隠れないよう可視領域へ移動
                    coroutineScope.launch {
                        requester.bringIntoView()
                    }
                    // フォーカス取得時にカーソルを末尾に移動
                    if (textFieldValueState.selection.start != textFieldValueState.text.length) {
                        textFieldValueState = textFieldValueState.copy(
                            selection = TextRange(textFieldValueState.text.length)
                        )
                    }
                }
            },
        interactionSource = interactionSource,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: defaultKeyboardActions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = textFieldValueState.text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                suffix = suffix,
                supportingText = supportingText,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
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
