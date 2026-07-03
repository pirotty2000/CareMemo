package jp.mydns.fujiwara.carememo.ui.components

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
 */
@Composable
fun BoxScope.VerticalScrollIndicator(scrollState: ScrollState) {
    if (scrollState.maxValue <= 0) return

    val barHeight = 60.dp
    val density = LocalDensity.current
    val viewportHeight = with(density) { scrollState.viewportSize.toDp() }
    val maxOffset = viewportHeight - barHeight

    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
        }
    }
    val isBottomSelected by remember {
        derivedStateOf { scrollState.value > (scrollState.maxValue / 2) }
    }

    IndicatorContent(
        scrollFraction = scrollFraction,
        isBottomSelected = isBottomSelected,
        maxOffset = maxOffset,
        barHeight = barHeight
    )
}

/**
 * 垂直スクロールインジケーター (LazyListState 用)
 */
@Composable
fun BoxScope.VerticalScrollIndicator(lazyListState: androidx.compose.foundation.lazy.LazyListState) {
    val canScroll = remember {
        derivedStateOf { lazyListState.canScrollForward || lazyListState.canScrollBackward }
    }
    if (!canScroll.value) return

    val barHeight = 60.dp
    val density = LocalDensity.current
    val layoutInfo = lazyListState.layoutInfo
    val viewportHeight = with(density) { layoutInfo.viewportSize.height.toDp() }
    val maxOffset = viewportHeight - barHeight

    val scrollFraction by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf 0f
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f

            val firstItem = visibleItems.first()
            
            // 全体量に対する現在位置の概算
            val scrolledItems = firstItem.index.toFloat()
            val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
            (scrolledItems / totalScrollableItems).coerceIn(0f, 1f)
        }
    }
    
    val isBottomSelected by remember {
        derivedStateOf { scrollFraction > 0.5f }
    }

    IndicatorContent(
        scrollFraction = scrollFraction,
        isBottomSelected = isBottomSelected,
        maxOffset = maxOffset,
        barHeight = barHeight
    )
}

@Composable
private fun BoxScope.IndicatorContent(
    scrollFraction: Float,
    isBottomSelected: Boolean,
    maxOffset: androidx.compose.ui.unit.Dp,
    barHeight: androidx.compose.ui.unit.Dp
) {
    // ドット表示 (上/下)
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) { index ->
            val isSelected = if (index == 0) !isBottomSelected else isBottomSelected
            Box(
                modifier = Modifier
                    .size(6.dp)
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
            .width(4.dp)
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
