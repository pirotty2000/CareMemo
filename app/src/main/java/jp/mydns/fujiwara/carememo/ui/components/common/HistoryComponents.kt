package jp.mydns.fujiwara.carememo.ui.components.common

/**
 * Component：HistoryComponents
 *
 * 【役割】：
 * 時系列の履歴リストを表示するための、アプリ共通の基盤コンポーネントを提供する。
 *
 * 【主な機能】：
 * ・日付ごとの自動グルーピングと、粘着ヘッダー（stickyHeader）による視認性の向上。
 * ・スワイプによる削除操作（SwipeToDismissBox）の標準実装。
 * ・選択状態に応じた背景色の変更管理。
 * ・リストアイテムの内容を外部から自由に定義できるスロット（itemContent）の提供。
 *
 * 【想定する利用場所】：
 * 健康記録、所見メモなどの履歴リスト表示箇所。
 *
 * 【このコンポーネントでは行わないこと】：
 * 個別の記録データの具体的な描画（具体的な表示内容は呼び出し側が Composable として提供する）。
 *
 * 【公開composable】：
 * PersonHistoryList, HistoryItemWrapper
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateHeader
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import java.time.ZoneId

/**
 * 利用者情報の履歴リストを表示する汎用コンポーネント
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonHistoryList(
    records: List<HistoryRecord>,
    selectedRecordId: Int = -1,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    isAnyDialogOpen: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    itemContent: @Composable (HistoryRecord) -> Unit
) {
    val groupedRecords = remember(records) {
        records.groupBy { it.recordTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { entry -> entry.value.sortedBy { it.recordTime } }
            .toSortedMap(compareByDescending { it })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("PersonHistoryList"),
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        groupedRecords.forEach { (date, items) ->
            val isSingle = items.size == 1
            // スティッキー・ヘッダー
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("HistoryList_Header_${date}"),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ヘッダー：日付
                        Text(
                            text = formatDateHeader(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSingle) {
                            // ヘッダー右側：時刻
                            Text(
                                text = formatTime(items.first().recordTime),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            // アイテム
            items(items.size, key = { items[it].id }) { index ->
                val record = items[index]
                val isSelected = record.id == selectedRecordId
                HistoryItemWrapper(
                    record = record,
                    showTime = !isSingle,
                    isSelected = isSelected,
                    onItemClick = { onItemClick(record) },
                    onDeleteSwipe = { onDeleteSwipe(record) },
                    isAnyDialogOpen = isAnyDialogOpen,
                    modifier = Modifier.testTag("HistoryItem_${record.id}")
                ) {
                    itemContent(record)
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * 履歴アイテムの枠組み（スワイプ削除、選択状態の背景管理）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWrapper(
    record: HistoryRecord,
    showTime: Boolean,
    isSelected: Boolean = false,
    onItemClick: () -> Unit,
    onDeleteSwipe: () -> Unit,
    isAnyDialogOpen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDeleteSwipe()
    }
    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen && (dismissState.currentValue != SwipeToDismissBoxValue.Settled)) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onItemClick)
                .padding(vertical = 1.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
            ) {
                if (showTime) {
                    Text(
                        text = formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                content()
            }
        }
    }
}
