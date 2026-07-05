package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：SearchBox
 *
 * 【役割】：
 * リスト内の項目を絞り込むための、アプリ共通の検索バーを提供する。
 *
 * 【主な機能】：
 * ・Material3 の OutlinedTextField をベースにした標準的な検索 UI。
 * ・検索アイコン（leading）と、入力時のクリアボタン（trailing）の自動表示。
 * ・プレースホルダーおよびラベルのカスタマイズ。
 *
 * 【想定する利用場所】：
 * 利用者一覧画面、所見メモの履歴検索、各マスタ検索画面など。
 *
 * 【このコンポーネントでは行わないこと】：
 * フィルタリングロジックの実行（入力値の変化を呼び出し元に通知するのみ）。
 *
 * 【公開composable】：
 * SearchBox
 */

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R

/**
 * アプリ共通の検索ボックス
 */
@Composable
fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = stringResource(R.string.search_memo_hint)
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}
