package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：KanaIndexBar
 *
 * 【役割】
 * 利用者一覧を「あいうえお順」の行（あ行、か行等）でフィルタリングするための、水平インデックス選択バーを提供します。
 *
 * 【主な機能】
 * ・「全」「あ」〜「わ」「他」のセクションリストの水平スクロール表示。
 * ・現在選択されているセクションの強調表示（背景ハイライトおよびアンダーライン）。
 * ・選択されたセクションが画面外にある場合、自動で見える位置まで移動するスクロール機能（LaunchedEffect）。
 *
 * 【想定する利用場所】
 * ・利用者一覧画面（MainScreen）の上部固定エリア。
 *
 * 【このコンポーネントでは行わないこと】
 * ・実際のリストの絞り込み処理（選択されたセクション名を onSectionSelect で通知するのみ）。
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
import jp.mydns.fujiwara.carememo.data.AppSpecifications

/**
 * 全体像：五十音インデックスバー（Kana Index Bar）
 *
 * ■ ui/screens/main/MainScreenContent.kt
 * │
 * └─ [1] KanaIndexBar (★本コンポーネント：コンテナ)
 *      └─ LazyRow (水平スクロールリスト)
 *           └─ items (AppSpecifications.Search.KANA_GROUPS)
 *                └─ Box (個別のインデックスボタン)
 *                     ├─ Text (「あ」「か」等の文字)
 *                     └─ Box (選択中インジケータ：下線)
 */

/**
 * 五十音（カナ）インデックス選択バーを表示します。
 *
 * @param selectedSection 現在選択されているセクション名（「全」または各行の頭文字）
 * @param onSectionSelect セクションが選択（タップ）された際のコールバック
 * @param modifier 修飾子
 */
@Composable
fun KanaIndexBar(
    selectedSection: String,
    onSectionSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 検索に使用するカナグループの定義を取得
    val kanaGroups = AppSpecifications.Search.KANA_GROUPS
    val listState = rememberLazyListState()

    // 選択されたセクションが変更された際、それが画面外にあれば自動スクロールして表示する
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
            val isSelected = selectedSection == title
            Box(
                modifier = Modifier
                    .width(28.dp)   // 幅：48→28に変更
                    .height(32.dp)  // 高さ：48→32に変更
                    // 選択時の背景ハイライト
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
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
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 選択時のみ表示される下部インジケータ（アンダーライン）
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(20.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
