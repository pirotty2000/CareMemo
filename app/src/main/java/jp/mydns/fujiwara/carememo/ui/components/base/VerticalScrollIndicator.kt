package jp.mydns.fujiwara.carememo.ui.components.base

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Component：VerticalScrollIndicator
 *
 * 【役割】
 * 画面のスクロール位置を視覚的に補助するための垂直インジケーターを提供します。
 * 標準のスクロールバーよりも情報量が多く、かつ控えめなデザインで現在の位置（上部/下部）をユーザーに伝えます。
 *
 * 【主な機能】
 * ・ScrollState（Column用）および LazyListState（LazyColumn用）の両方に対応。
 * ・現在のスクロール位置に応じたスライドバーと、上部/下部のどちらに近いかを示すドット表示。
 * ・スクロール不可な場合は自動的に非表示。
 * ・isCompact 引数により、ダイアログ内などの狭い領域に適した小型表示に切り替え可能。
 *
 * 【想定する利用場所】
 * 詳細画面、履歴リスト、設定画面、および各種入力ダイアログ（AppDialogContent等で使用）。
 *
 * 【このコンポーネントでは行わないこと】
 * スクロール自体の制御（あくまで表示のみを担当）。
 */

/**
 * 垂直スクロールインジケーター (通常の ScrollState / Column 用)
 *
 * @param scrollState スクロール状態
 * @param isCompact ダイアログ内などの狭い領域で使用する場合は true
 */
@Composable
fun BoxScope.VerticalScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val barHeight = if (isCompact) 32.dp else 60.dp
    val density = LocalDensity.current

    // スクロール位置とビューポートの高さをまとめて計算 (Recompositionを抑制)
    val layoutData by remember {
        derivedStateOf {
            // スクロール不可なら表示しない
            if (scrollState.maxValue <= 0) {
                return@derivedStateOf Triple(0.dp, 0f, false)
            }
            val viewportHeightDp = with(density) { scrollState.viewportSize.toDp() }
            val fraction = scrollState.value.toFloat() / scrollState.maxValue
            // 中間地点より下なら bottomSelected とする
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
        isCompact = isCompact,
        modifier = modifier
    )
}

/**
 * 垂直スクロールインジケーター (LazyListState / LazyColumn 用)
 *
 * @param lazyListState LazyListのスクロール状態
 * @param isCompact ダイアログ内などの狭い領域で使用する場合は true
 */
@Composable
fun BoxScope.VerticalScrollIndicator(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    // スクロール可能かどうかを判定
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
            
            // アイコン総数に対する現在位置の概算
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
        isCompact = isCompact,
        modifier = modifier
    )
}

/**
 * インジケーターの描画本体
 */
@Composable
private fun BoxScope.IndicatorContent(
    scrollFraction: Float,
    isBottomSelected: Boolean,
    maxOffset: Dp,
    barHeight: Dp,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    // 表示サイズの設定
    val dotSize = if (isCompact) 4.dp else 6.dp
    val dotSpacing = if (isCompact) 4.dp else 8.dp
    val paddingEnd = if (isCompact) 6.dp else 14.dp
    val barWidth = if (isCompact) 2.dp else 4.dp

    // 上部/下部ドット表示
    Column(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .padding(end = paddingEnd),
        verticalArrangement = Arrangement.spacedBy(dotSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) { index ->
            // 現在の位置（上半分か下半分か）に応じてドットをハイライト
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

    // 移動するスライドバー
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
