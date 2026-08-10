package jp.mydns.fujiwara.carememo.ui.components.common

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
import kotlinx.collections.immutable.ImmutableList
import java.time.ZoneId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme

/**
 * Component：HistoryComponents
 *
 * 【役割】
 * 時系列の履歴リストを表示するための、アプリ共通の基盤コンポーネントを提供します。
 *
 * 【主な機能】
 * ・日付ごとの自動グルーピングと、粘着ヘッダー（stickyHeader）による視認性の向上。
 * ・スワイプによる削除操作（SwipeToDismissBox）の標準実装。
 * ・選択状態に応じた背景色の変更管理。
 * ・リストアイテムの内容を外部から自由に定義できるスロット（itemContent）の提供。
 *
 * 【想定する利用場所】
 * 健康記録、所見メモ、服薬記録などの履歴リスト表示箇所。
 *
 * 【このコンポーネントでは行わないこと】
 * 個別の記録データの具体的な描画（具体的な表示内容は呼び出し側が Composable として提供する）。
 */

/**
 * 利用者情報の履歴リストを表示する汎用コンポーネント
 *
 * 内部でデータを日付単位にグルーピングし、日付見出し（stickyHeader）を付けて表示します。
 *
 * @param records 表示対象の履歴レコードリスト
 * @param selectedRecordId 現在選択（強調）されているレコードのID
 * @param onItemClick アイテムがタップされた際のコールバック
 * @param onDeleteSwipe アイテムがスワイプ削除された際のコールバック
 * @param isAnyDialogOpen ダイアログが開いているかどうか。開いた際にスワイプ状態をリセットするために使用。
 * @param lazyListState リストのスクロール状態
 * @param itemContent 各レコードの具体的な表示内容を定義する Composable スロット
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonHistoryList(
    records: ImmutableList<HistoryRecord>,
    modifier: Modifier = Modifier,
    selectedRecordId: String? = null,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    isAnyDialogOpen: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
    itemContent: @Composable (HistoryRecord) -> Unit
) {
    // 記録を日付ごとにグループ化し、日付の降順（新しい順）かつ同一日は時刻の昇順でソート
    val groupedRecords = remember(records) {
        records.groupBy { it.recordTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { entry -> entry.value.sortedBy { it.recordTime } }
            .toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = modifier.testTag("PersonHistoryList"),
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 80.dp) // 下部のFABと重ならないよう余白を確保
    ) {
        groupedRecords.forEach { (date, items) ->
            val isSingle = items.size == 1
            
            // 日付区切り（スクロール時も画面上部に固定されるスティッキー・ヘッダー）
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
                        // 同一日に1件しかない場合は、ヘッダーに時刻を表示して一覧性を高める
                        if (isSingle) {
                            Text(
                                text = formatTime(items.first().recordTime),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 個別の履歴アイテム
            items(items.size, key = { items[it].id }) { index ->
                val record = items[index]
                val isSelected = record.id == selectedRecordId
                
                HistoryItemWrapper(
                    record = record,
                    showTime = !isSingle, // 同一日に複数ある場合のみ、アイテム内に時刻を表示
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
 * 履歴アイテムの枠組み（スワイプ削除、選択状態の背景管理、共通レイアウト）
 *
 * @param record 対象のレコード
 * @param showTime アイテム内に時刻を表示するかどうか
 * @param isSelected 選択状態かどうか
 * @param onItemClick クリック時のコールバック
 * @param onDeleteSwipe スワイプ削除時のコールバック
 * @param isAnyDialogOpen 他のダイアログが表示された際に、スワイプ状態を閉じるために使用
 * @param modifier 修飾子
 * @param content アイテム内部のコンテンツ（itemContent から渡される）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWrapper(
    record: HistoryRecord,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
    isSelected: Boolean = false,
    onItemClick: () -> Unit = {},
    onDeleteSwipe: () -> Unit = {},
    isAnyDialogOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // スワイプ完了時に削除処理を実行
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDeleteSwipe()
        }
    }

    // 他のダイアログ（削除確認など）が開いた際、または閉じられた際、スワイプが開いたままなら閉じる
    // これにより、削除キャンセル後にアイテムが「スワイプされたまま」になるのを防ぐ
    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen && (dismissState.currentValue != SwipeToDismissBoxValue.Settled)) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    // 選択状態に応じたコンテナ色の決定
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false, // 左から右へのスワイプは無効
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
                // 必要に応じて時刻を表示
                if (showTime) {
                    Text(
                        text = formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // 外部から渡された具体的な内容を描画
                content()
            }
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Previews
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true)
@Composable
private fun PreviewPersonHistoryList(
    @PreviewParameter(HistoryPreviewParameterProvider::class) records: ImmutableList<HistoryRecord>
) {
    CareMemoTheme {
        PersonHistoryList(
            records = records,
            onItemClick = {},
            onDeleteSwipe = {}
        ) { record ->
            Text(text = "Record ID: ${record.id}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
