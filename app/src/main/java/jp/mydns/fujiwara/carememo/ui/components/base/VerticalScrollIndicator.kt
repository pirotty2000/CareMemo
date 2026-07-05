package jp.mydns.fujiwara.carememo.ui.components.base

/**
 * Component：VerticalScrollIndicator
 *
 * 【役割】：
 * 画面のスクロール位置を視覚的に補助するための垂直インジケーターを提供する。
 *
 * 【主な機能】：
 * ・ScrollState（Column用）および LazyListState（LazyColumn用）の両方に対応。
 * ・現在のスクロール位置に応じたスライドバーと、上部/下部のどちらに近いかを示すドット表示。
 * ・スクロール不可な場合は自動的に非表示。
 * ・isCompact 引数により、ダイアログ等に適した小型表示に切り替え可能。
 *
 * 【想定する利用場所】：
 * 詳細画面、履歴リスト、設定画面、および各種入力ダイアログ。
 *
 * 【このコンポーネントでは行わないこと】：
 * スクロール自体の制御（あくまで表示のみを担当）。
 *
 * 【公開composable】：
 * VerticalScrollIndicator
 */

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 垂直スクロールインジケーター (通常の ScrollState 用)
 *
 * @param scrollState スクロール状態
 * @param isCompact ダイアログ内などの狭い領域で使用する場合は true
 */
@Composable
fun BoxScope.VerticalScrollIndicator(
    scrollState: ScrollState,
    isCompact: Boolean = false
) {
    val barHeight = if (isCompact) 32.dp else 60.dp
    val density = LocalDensity.current

    val layoutData by remember {
        derivedStateOf {
            if (scrollState.maxValue <= 0) {
                return@derivedStateOf Triple(0.dp, 0f, false)
            }
            val viewportHeightDp = with(density) { scrollState.viewportSize.toDp() }
            val fraction = scrollState.value.toFloat() / scrollState.maxValue
            val isBottom = scrollState.value > (scrollState.maxValue / 2)
            Triple(viewportHeightDp, fraction, isBottom)
        }
    }

    val (viewportHeight, scrollFraction, isBottomSelected) = layoutData
    if (viewportHeight <= 0.dp) return

    val maxOffset = viewportHeight - barHeight

    IndicatorContent(
        scrollFraction = scrollFraction,
        isBottomSelected = isBottomSelected,
        maxOffset = maxOffset,
        barHeight = barHeight,
        isCompact = isCompact
    )
}

/**
 * 垂直スクロールインジケーター (LazyListState 用)
 *
 * @param lazyListState LazyListのスクロール状態
 * @param isCompact ダイアログ内などの狭い領域で使用する場合は true
 */
@Composable
fun BoxScope.VerticalScrollIndicator(
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    isCompact: Boolean = false
) {
    val canScroll by remember {
        derivedStateOf { lazyListState.canScrollForward || lazyListState.canScrollBackward }
    }
    if (!canScroll) return

    val barHeight = if (isCompact) 32.dp else 60.dp
    val density = LocalDensity.current

    // スクロール位置とビューポートの高さをまとめて計算 (Recompositionを抑制)
    val layoutData by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            
            if (totalItems == 0 || visibleItems.isEmpty()) {
                return@derivedStateOf Triple(0.dp, 0f, false)
            }

            val viewportHeightDp = with(density) { layoutInfo.viewportSize.height.toDp() }
            val firstItem = visibleItems.first()
            
            // 全体量に対する現在位置の概算
            val scrolledItems = firstItem.index.toFloat()
            val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
            val fraction = (scrolledItems / totalScrollableItems).coerceIn(0f, 1f)
            
            Triple(viewportHeightDp, fraction, fraction > 0.5f)
        }
    }

    val (viewportHeight, scrollFraction, isBottomSelected) = layoutData
    val maxOffset = viewportHeight - barHeight

    IndicatorContent(
        scrollFraction = scrollFraction,
        isBottomSelected = isBottomSelected,
        maxOffset = maxOffset,
        barHeight = barHeight,
        isCompact = isCompact
    )
}

@Composable
private fun BoxScope.IndicatorContent(
    scrollFraction: Float,
    isBottomSelected: Boolean,
    maxOffset: androidx.compose.ui.unit.Dp,
    barHeight: androidx.compose.ui.unit.Dp,
    isCompact: Boolean
) {
    val dotSize = if (isCompact) 4.dp else 6.dp
    val dotSpacing = if (isCompact) 4.dp else 8.dp
    val paddingEnd = if (isCompact) 6.dp else 14.dp
    val barWidth = if (isCompact) 2.dp else 4.dp

    // ドット表示 (上/下)
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = paddingEnd),
        verticalArrangement = Arrangement.spacedBy(dotSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) { index ->
            val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
            )
        }
    }

    // スライドバー
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .align(Alignment.TopEnd)
            .offset {
                IntOffset(
                    x = 0,
                    y = (maxOffset * scrollFraction).roundToPx()
                )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    )
}
