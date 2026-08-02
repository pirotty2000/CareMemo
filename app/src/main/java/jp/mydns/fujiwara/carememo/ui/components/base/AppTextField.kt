package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch

/**
 * AppTextField で扱う入力タイプ
 */
enum class AppTextFieldType {
    TEXT,       // 通常のテキスト（日本語）
    INTEGER,    // 整数のみ（数字キーボード、数字以外フィルタ）
    DECIMAL,    // 小数（小数点対応キーボード、数字とドット以外フィルタ）
    PASSWORD,   // パスワード（英数モード、秘匿表示）
    EMAIL,      // メールアドレス（英数モード）
    PHONE       // 電話番号（数値モード）
}

/**
 * Component：AppTextField
 *
 * 【役割】：
 * CareMemo アプリ全体の入力フィールドの基盤を提供し、プロジェクト共通の入力ルールを強制する。
 *
 * 【主な機能】：
 * ・フォーカス取得時のカーソル末尾移動（既存データの保持と編集性向上）。
 * ・入力タイプ（AppTextFieldType）に応じたキーボード、フィルタ、IMEアクションの自動設定。
 * ・最大桁数（maxLength）到達時の自動フォーカス移動。
 * ・IMEアクション（Next/Done）実行時の自動フォーカス制御。
 *
 * 【引数リファレンス】：
 * @param value 入力値（String）。
 * @param onValueChange 値変更時のコールバック。
 * @param modifier 修飾子。
 * @param type 入力タイプ（TEXT, INTEGER, DECIMAL, PASSWORD等）。キーボードやフィルタが決定される。
 * @param label ラベル（Composable）。
 * @param placeholder ヒントテキスト（Composable）。
 * @param leadingIcon 先頭アイコン。
 * @param trailingIcon 末尾アイコン。
 * @param prefix 接頭辞。
 * @param suffix 接尾辞。
 * @param supportingText 補助テキスト（エラーメッセージ等）。
 * @param isError エラー状態かどうか。
 * @param readOnly 読み取り専用かどうか。
 * @param enabled 有効かどうか。
 * @param singleLine 単一行入力かどうか（デフォルト: true）。
 * @param minLines 最小行数。
 * @param maxLines 最大行数。
 * @param maxLength 最大入力桁数。指定すると、到達時に自動で次の項目へフォーカスが移動する。
 * @param imeAction IMEアクション（Next, Done等）。指定しない場合は singleLine に応じて自動設定。
 * @param autoMoveFocus 自動フォーカス移動を有効にするか（デフォルト: true）。
 * @param keyboardActions カスタムのキーボードアクション。指定しない場合は自動移動が適用される。
 * @param onFocusChanged フォーカス状態変更時のコールバック。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: AppTextFieldType = AppTextFieldType.TEXT,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    maxLength: Int = Int.MAX_VALUE,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    autoMoveFocus: Boolean = true,
    keyboardActions: KeyboardActions? = null,
    visualTransformation: VisualTransformation? = null,
    onFocusChanged: (FocusState) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }

    // カーソル位置を制御するために TextFieldValue を内部で保持
    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    // 外部からの value の変更を内部状態に同期
    LaunchedEffect(value) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueState.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    // タイプに応じたキーボード設定の決定
    val keyboardOptions = remember(type, imeAction) {
        when (type) {
            AppTextFieldType.TEXT -> KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = imeAction)
            AppTextFieldType.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction)
            AppTextFieldType.DECIMAL -> KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction)
            AppTextFieldType.PASSWORD -> KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction)
            AppTextFieldType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction)
            AppTextFieldType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction)
        }
    }

    // デフォルトのキーボードアクション（自動フォーカス移動を含む）
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

    // タイプに応じた視覚変換の決定
    val finalVisualTransformation = visualTransformation ?: remember(type) {
        if (type == AppTextFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None
    }

    OutlinedTextField(
        value = textFieldValueState,
        onValueChange = { newValue ->
            // 入力タイプに応じたフィルタリング
            var filteredText = when (type) {
                AppTextFieldType.INTEGER -> newValue.text.filter { it.isDigit() }
                AppTextFieldType.DECIMAL -> newValue.text.filter { it.isDigit() || it == '.' }
                else -> newValue.text
            }

            // maxLength による制限
            if (filteredText.length > maxLength) {
                filteredText = filteredText.take(maxLength)
            }

            // フィルタ後の値で状態を更新
            val finalValue = if (filteredText != newValue.text) {
                newValue.copy(text = filteredText)
            } else {
                newValue
            }

            // テキストが実際に変更されたか（再帰呼び出しや onBlur 時の重複処理を防止）
            val isTextChanged = finalValue.text != textFieldValueState.text

            textFieldValueState = finalValue
            if (isTextChanged) {
                onValueChange(finalValue.text)
                
                // maxLength 到達時の自動移動
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
                    coroutineScope.launch {
                        requester.bringIntoView()
                    }
                }
                // フォーカス取得時にのみ実行し、かつ現在のカーソル位置が末尾でない場合のみ移動
                if (focusState.isFocused && textFieldValueState.selection.start != textFieldValueState.text.length) {
                textFieldValueState = textFieldValueState.copy(
                    selection = TextRange(textFieldValueState.text.length)
                )
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        readOnly = readOnly,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: defaultKeyboardActions,
        visualTransformation = finalVisualTransformation,
        colors = OutlinedTextFieldDefaults.colors()
    )
}
