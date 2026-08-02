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
 * AppTextField で扱う入力タイプ定義
 */
enum class AppTextFieldType {
    /** 通常のテキスト（日本語キーボード） */
    TEXT,
    /** 整数のみ（数字キーボード、数字以外をフィルタ） */
    INTEGER,
    /** 小数（小数点対応キーボード、数字とドット以外をフィルタ） */
    DECIMAL,
    /** パスワード（英数キーボード、秘匿表示） */
    PASSWORD,
    /** メールアドレス（英数キーボード、@等の補助付き） */
    EMAIL,
    /** 電話番号（数値キーボード） */
    PHONE
}

/**
 * Component：AppTextField
 *
 * 【役割】
 * CareMemo アプリ全体の入力フィールドの標準基盤を提供し、プロジェクト共通の入力ルールを強制します。
 * Material 3 の OutlinedTextField を拡張し、実務で頻出するバリデーションやフォーカス制御を内包しています。
 *
 * 【主な機能】
 * ・入力タイプ（AppTextFieldType）に応じたキーボード、フィルタ、IMEアクションの自動設定。
 * ・フォーカス取得時のカーソル末尾移動（既存データの誤消去防止と編集性向上）。
 * ・最大桁数（maxLength）到達時の自動フォーカス移動（連続入力の効率化）。
 * ・IMEアクション（Next/Done）実行時の自動フォーカス制御。
 * ・キーボード表示時にフィールドが隠れないよう自動スクロール（BringIntoView）。
 *
 * 【UX標準ルール】
 * 1. 数値入力時は専用キーボードを表示し、不正な文字入力をシステム的に遮断する。
 * 2. 入力完了（Next）時は、直感的に次の項目へフォーカスを移す。
 * 3. 編集開始時はカーソルを末尾に置くことで、追記操作を優先する。
 *
 * @param value 入力値（String）
 * @param onValueChange 値変更時のコールバック
 * @param modifier 修飾子
 * @param type 入力タイプ。キーボードレイアウトや文字フィルタを決定します。
 * @param label ラベル要素
 * @param placeholder ヒントテキスト要素
 * @param leadingIcon 先頭アイコン
 * @param trailingIcon 末尾アイコン
 * @param prefix 接頭辞（単位や通貨記号など）
 * @param suffix 接尾辞（単位など）
 * @param supportingText 補助テキスト（エラーメッセージや注意書き）
 * @param isError エラー状態かどうか
 * @param readOnly 読み取り専用かどうか
 * @param enabled 有効かどうか
 * @param singleLine 単一行入力かどうか（デフォルト: true）
 * @param minLines 最小行数
 * @param maxLines 最大行数
 * @param maxLength 最大入力文字数。到達時に自動で次の項目へフォーカスが移動します。
 * @param imeAction IMEアクション（Next, Done等）。未指定時は singleLine 設定に基づき自動決定。
 * @param autoMoveFocus 自動フォーカス移動を有効にするか（デフォルト: true）
 * @param keyboardActions カスタムのキーボードアクション。未指定時は標準の移動制御を適用。
 * @param visualTransformation 視覚変換（パスワード秘匿やフォーマットなど）。未指定時は type に応じて設定。
 * @param onFocusChanged フォーカス状態変更時のコールバック
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

    // カーソル位置を細かく制御するために TextFieldValue を内部で保持
    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    // 外部からの value (ViewModel等) の変更を内部状態に同期
    LaunchedEffect(value) {
        if (value != textFieldValueState.text) {
            textFieldValueState = textFieldValueState.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    // 入力タイプに応じたキーボードオプションの決定
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

    // 標準のキーボードアクション（自動フォーカス移動を含む）
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

    // タイプに応じた視覚変換（パスワード以外は指定がなければ None）
    val finalVisualTransformation = visualTransformation ?: remember(type) {
        if (type == AppTextFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None
    }

    OutlinedTextField(
        value = textFieldValueState,
        onValueChange = { newValue ->
            // 入力タイプに応じた不正文字のフィルタリング
            var filteredText = when (type) {
                AppTextFieldType.INTEGER -> newValue.text.filter { it.isDigit() }
                AppTextFieldType.DECIMAL -> newValue.text.filter { it.isDigit() || it == '.' }
                else -> newValue.text
            }

            // 最大文字数（maxLength）制限
            if (filteredText.length > maxLength) {
                filteredText = filteredText.take(maxLength)
            }

            // フィルタ適用後の値で状態を生成
            val finalValue = if (filteredText != newValue.text) {
                newValue.copy(text = filteredText)
            } else {
                newValue
            }

            // テキストが実際に変更された場合のみコールバックを発火
            val isTextChanged = finalValue.text != textFieldValueState.text

            textFieldValueState = finalValue
            if (isTextChanged) {
                onValueChange(finalValue.text)
                
                // maxLength 到達時の自動フォーカス移動（ユーザー体験向上）
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
                    // フォーカス取得時にフィールドが画面内に収まるように自動スクロール
                    coroutineScope.launch {
                        requester.bringIntoView()
                    }
                    // フォーカス取得時にカーソルを末尾へ（既存値の後に追記しやすくする）
                    if (textFieldValueState.selection.start != textFieldValueState.text.length) {
                        textFieldValueState = textFieldValueState.copy(
                            selection = TextRange(textFieldValueState.text.length)
                        )
                    }
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
