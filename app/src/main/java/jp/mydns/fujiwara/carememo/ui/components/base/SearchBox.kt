package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import jp.mydns.fujiwara.carememo.R

/**
 * Component：SearchBox
 *
 * 【役割】
 * リスト内の項目をキーワードで絞り込むための、アプリ共通の検索バーUIを提供します。
 *
 * 【主な機能】
 * ・Material 3 のデザインに基づいた標準的な検索 UI。
 * ・検索アイコン（leading）の常時表示。
 * ・入力値がある場合のみ、一括消去用のクリアボタン（trailing）を自動表示。
 * ・プレースホルダーおよびラベルのカスタマイズ。
 *
 * 【設計指針】
 * 内部で汎用入力コンポーネント `AppTextField` をラップしており、プロジェクト共通の入力ルール（フォーカス制御等）を自動的に継承します。
 *
 * 【想定する利用場所】
 * 利用者一覧画面、所見メモの履歴検索、各種マスタ検索画面など。
 *
 * 【このコンポーネントでは行わないこと】
 * 実際のフィルタリングロジックの実行（入力値の変化を onQueryChange で通知するのみ）。
 */
@Composable
fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = stringResource(R.string.main_search_hint)
) {
    // プロジェクト共通の AppTextField をラップして構成
    AppTextField(
        value = query,
        onValueChange = onQueryChange,
        type = AppTextFieldType.TEXT,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            // 文字が入力されている場合のみ、一括消去用のクリアボタンを表示
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.common_clear)
                    )
                }
            }
        },
        singleLine = true
    )
}
