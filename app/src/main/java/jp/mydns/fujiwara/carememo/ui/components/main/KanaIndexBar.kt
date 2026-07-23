package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：KanaIndexBar
 *
 * 【役割】：
 * 利用者一覧を「あいうえお順」の行（あ行、か行等）でフィルタリングするための、水平インデックス選択バーを提供する。
 *
 * 【主な機能】：
 * ・「全」「あ」〜「わ」「他」のセクションリストの水平表示。
 * ・現在選択されているセクションの強調表示（アンダーライン）。
 * ・選択されたセクションへの自動スクロール（LaunchedEffect）。
 *
 * 【想定する利用場所】：
 * 利用者一覧画面（MainScreen）の上部。
 *
 * 【このコンポーネントでは行わないこと】：
 * 実際のフィルタリングロジック（選択されたセクション名を呼び出し元に通知するのみ）。
 *
 * 【公開composable】：
 * KanaIndexBar
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.spec.SearchSpecifications

/**
 * 五十音（カナ）インデックス選択バー
 */
@Composable
fun KanaIndexBar(
    selectedSection: String,
    onSectionSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val kanaGroups = SearchSpecifications.KANA_GROUPS
    val listState = rememberLazyListState()

    // 選択されたセクションが画面外にある場合、自動スクロールして表示
    LaunchedEffect(selectedSection) {
        val index = kanaGroups.indexOf(selectedSection)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(kanaGroups) { title ->
            Box(
                modifier = Modifier
                    // 「全」「あ」・・・の間隔（文字幅を指定し、中央に配置しているだけで、
                    // paddingを指定しているわけではない。
                    .width(28.dp)   // 幅を48→28にして横を詰めた
                    .height(32.dp)  // 高さを48→32にして上下を詰めた
                    // ----- 2026/07/06 start
                    // ------ 選択されたら背景色を変える
                    .background(
                        color =
                            if (selectedSection == title)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent,
                                shape = RoundedCornerShape(4.dp) // 少し角を丸くすると馴染みます
                    )
                    // ----- 2026/07/06 end
                    .clickable { onSectionSelect(title) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedSection == title)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedSection == title) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(24.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
