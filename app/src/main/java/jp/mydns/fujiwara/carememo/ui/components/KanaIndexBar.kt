package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 五十音（カナ）インデックス選択バー
 */
@Composable
fun KanaIndexBar(
    selectedSection: String,
    onSectionSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val kanaGroups = listOf("全", "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "他")
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
                    .width(44.dp)
                    .height(48.dp)
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
                        color = if (selectedSection == title) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
