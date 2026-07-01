package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

data class ChartPoint(val x: Double, val y: Double, val note: String? = null)
data class ChartLineData(val label: String, val points: List<ChartPoint>, val color: Color)
data class ChartLimitLine(val label: String, val value: Double, val color: Color, val isLabelAbove: Boolean)
data class ChartRangeHighlight(val startValue: Double, val endValue: Double, val color: Color)
data class SelectedPoint(val x: Double, val y: Double, val color: Color, val label: String, val note: String? = null)

@Composable
fun LineChart(
    dataList: List<ChartLineData>,
    stepY: Double = 5.0,
    limits: List<ChartLimitLine> = emptyList(),
    ranges: List<ChartRangeHighlight> = emptyList(),
    minYConstraint: Double? = null,
    maxYConstraint: Double? = null,
    fixedMinX: Double? = null,
    fixedMaxX: Double? = null,
    showDecimal: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val limitLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
    val legendStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var selectedPoint by remember { mutableStateOf<SelectedPoint?>(null) }

    val density = LocalDensity.current
    val paddingLeft = 40.dp
    val paddingTop = 20.dp
    val paddingBottom = 20.dp

    val paddingTopPx = with(density) { paddingTop.toPx() }
    val paddingBottomPx = with(density) { paddingBottom.toPx() }
    val leftBufferPx = with(density) { 8.dp.toPx() }
    val rightBufferPx = with(density) { 8.dp.toPx() }
    val horizontalPaddingPx = with(density) { 20.dp.toPx() }

    val allPoints = dataList.flatMap { it.points }
    if (allPoints.isEmpty() && (fixedMinX == null || fixedMaxX == null)) return

    val minX = fixedMinX ?: (allPoints.minOfOrNull { it.x } ?: 0.0)
    val maxX = fixedMaxX ?: (allPoints.maxOfOrNull { it.x } ?: 0.0)
    val duration = if (maxX - minX == 0.0) 1.0 else maxX - minX
    
    val allYValues = if (allPoints.isNotEmpty()) {
        allPoints.map { it.y } + limits.map { it.value }
    } else {
        limits.map { it.value }
    }

    if (allYValues.isEmpty() && minYConstraint == null && maxYConstraint == null) return

    var minYInput = allYValues.minOrNull() ?: minYConstraint ?: 0.0
    var maxYInput = allYValues.maxOrNull() ?: maxYConstraint ?: 100.0
    minYConstraint?.let { minYInput = minOf(minYInput, it) }
    maxYConstraint?.let { maxYInput = maxOf(maxYInput, it) }
    val minY = floor(minYInput / stepY) * stepY
    val maxY = ceil(maxYInput / stepY) * stepY
    val yRange = if (maxY - minY == 0.0) stepY else maxY - minY
    val yStepsCount = (yRange / stepY).toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        if (dataList.size > 1) {
            Box(modifier = Modifier.padding(start = paddingLeft, top = 4.dp, bottom = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    dataList.forEach { lineData ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(lineData.color) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lineData.label, style = legendStyle.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.width(paddingLeft).fillMaxHeight()) {
                val chartHeight = size.height - paddingTopPx - paddingBottomPx
                for (i in 0..yStepsCount) {
                    val yVal = minY + stepY * i
                    val py = paddingTopPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                    val label = if (showDecimal || stepY <= 1.0) "%.1f".format(yVal) else yVal.toInt().toString()
                    val textLayout = textMeasurer.measure(label, labelStyle)
                    drawText(textLayout, topLeft = Offset(size.width - textLayout.size.width - 4.dp.toPx(), py - textLayout.size.height / 2))
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleX = (scaleX * zoom).coerceAtLeast(1f)
                            val maxOffsetX = 0f
                            val minOffsetX = -(size.width * (scaleX - 1f))
                            offsetX = (offsetX + pan.x).coerceIn(minOffsetX, maxOffsetX)
                            selectedPoint = null
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scaleX = 1f
                                offsetX = 0f
                                selectedPoint = null
                            },
                            onTap = { tapOffset ->
                                val chartWidth = (size.width - leftBufferPx - rightBufferPx) * scaleX
                                val chartHeight = size.height - paddingTopPx - paddingBottomPx
                                val startX = leftBufferPx + offsetX
                                val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                                var closest: SelectedPoint? = null
                                var minDistance = with(density) { 24.dp.toPx() }

                                dataList.forEach { lineData ->
                                    lineData.points.forEach { point ->
                                        val px = startX + horizontalPaddingPx + ((point.x - minX) / duration).toFloat() * effectiveWidth
                                        val py = paddingTopPx + chartHeight - ((point.y - minY) / yRange).toFloat() * chartHeight

                                        val dx = tapOffset.x - px
                                        val dy = tapOffset.y - py
                                        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                                        if (dist < minDistance) {
                                            minDistance = dist
                                            closest = SelectedPoint(point.x, point.y, lineData.color, lineData.label, point.note)
                                        }
                                    }
                                }

                                // 操作モデルの実装:
                                // 1. 同じ点をタップ -> 閉じる (nullにする)
                                // 2. 別の点をタップ -> 切り替え
                                // 3. グラフ外（どの点からも遠い場所）をタップ -> 閉じる (nullにする)
                                selectedPoint = if (closest != null &&
                                    selectedPoint?.x == closest.x &&
                                    selectedPoint?.y == closest.y &&
                                    selectedPoint?.label == closest.label) {
                                    null
                                } else {
                                    closest
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartWidth = (size.width - leftBufferPx - rightBufferPx) * scaleX
                    val chartHeight = size.height - paddingTopPx - paddingBottomPx
                    val startX = leftBufferPx + offsetX
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    clipRect(left = leftBufferPx, top = 0f, right = size.width - rightBufferPx, bottom = size.height) {
                        ranges.forEach { range ->
                            val pyStart = paddingTopPx + chartHeight - ((range.startValue - minY) / yRange).toFloat() * chartHeight
                            val pyEnd = paddingTopPx + chartHeight - ((range.endValue - minY) / yRange).toFloat() * chartHeight
                            val top = pyEnd.coerceIn(paddingTopPx, paddingTopPx + chartHeight)
                            val bottom = pyStart.coerceIn(paddingTopPx, paddingTopPx + chartHeight)
                            if (bottom > top) {
                                drawRect(
                                    color = range.color,
                                    topLeft = Offset(startX, top),
                                    size = Size(chartWidth, bottom - top)
                                )
                            }
                        }

                        for (i in 0..yStepsCount) {
                            val py = paddingTopPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(
                                color = gridColor.copy(alpha = 0.5f),
                                start = Offset(leftBufferPx, py),
                                end = Offset(size.width - rightBufferPx, py),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        
                        val baseLabelCount = 4
                        val labelCount = (baseLabelCount * scaleX).toInt().coerceAtMost(20)
                        for (i in 0 until labelCount) {
                            val currentX = minX + (duration * i / (labelCount - 1))
                            val px = startX + horizontalPaddingPx + ((currentX - minX) / duration).toFloat() * effectiveWidth
                            
                            if (px in (leftBufferPx - 100f)..(size.width - rightBufferPx + 100f)) {
                                drawLine(
                                    color = gridColor.copy(alpha = 0.3f),
                                    start = Offset(px, paddingTopPx),
                                    end = Offset(px, paddingTopPx + chartHeight),
                                    strokeWidth = 1.dp.toPx()
                                )
                                val dateStr = DateTimeFormatter.ofPattern("yy/MM/dd")
                                    .withLocale(Locale.JAPAN)
                                    .format(Instant.ofEpochMilli(currentX.toLong()).atZone(ZoneId.systemDefault()))
                                val textLayout = textMeasurer.measure(dateStr, labelStyle)
                                drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, paddingTopPx + chartHeight + 4.dp.toPx()))
                            }
                        }

                        limits.forEach { limit ->
                            val py = paddingTopPx + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
                            if (py in paddingTopPx..(paddingTopPx + chartHeight)) {
                                drawLine(
                                    color = limit.color.copy(alpha = 0.6f),
                                    start = Offset(startX, py),
                                    end = Offset(startX + chartWidth, py),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                                val labelLayout = textMeasurer.measure(limit.label, limitLabelStyle.copy(color = limit.color))
                                drawText(labelLayout, topLeft = Offset(startX + 4.dp.toPx(), if (limit.isLabelAbove) py - labelLayout.size.height - 2.dp.toPx() else py + 2.dp.toPx()))
                            }
                        }

                        dataList.forEach { lineData ->
                            val path = Path()
                            val sortedPoints = lineData.points.sortedBy { it.x }
                            sortedPoints.forEachIndexed { index, point ->
                                val px = startX + horizontalPaddingPx + ((point.x - minX) / duration).toFloat() * effectiveWidth
                                val py = paddingTopPx + chartHeight - ((point.y - minY) / yRange).toFloat() * chartHeight
                                
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                
                                if (px in leftBufferPx..(size.width - rightBufferPx)) {
                                    drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                                    
                                    // 選択された点を強調
                                    if (selectedPoint?.x == point.x && selectedPoint?.y == point.y && selectedPoint?.color == lineData.color) {
                                        drawCircle(
                                            color = lineData.color,
                                            radius = 6.dp.toPx(),
                                            center = Offset(px, py),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }

                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(point.y) else point.y.toInt().toString()
                                    val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                                    drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLayout.size.height - 2.dp.toPx()))
                                }
                            }
                            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    
                    drawLine(axisColor, Offset(leftBufferPx, paddingTopPx), Offset(leftBufferPx, paddingTopPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                    drawLine(axisColor, Offset(leftBufferPx, paddingTopPx + chartHeight), Offset(size.width - rightBufferPx, paddingTopPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                }

                // 吹き出し（ツールチップ）の表示
                selectedPoint?.let { point ->
                    val chartWidth = (constraints.maxWidth.toFloat() - leftBufferPx - rightBufferPx) * scaleX
                    val chartHeight = constraints.maxHeight.toFloat() - paddingTopPx - paddingBottomPx
                    val startX = leftBufferPx + offsetX
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    val px = startX + horizontalPaddingPx + ((point.x - minX) / duration).toFloat() * effectiveWidth
                    val py = paddingTopPx + chartHeight - ((point.y - minY) / yRange).toFloat() * chartHeight

                    if (px in leftBufferPx..(constraints.maxWidth.toFloat() - rightBufferPx)) {
                        val dateStr = DateTimeFormatter.ofPattern("yy/MM/dd")
                            .withLocale(Locale.JAPAN)
                            .format(Instant.ofEpochMilli(point.x.toLong()).atZone(ZoneId.systemDefault()))

                        Surface(
                            modifier = Modifier
                                .offset {
                                    // 吹き出しの幅を想定してクランプ（見切れ防止）
                                    // 複数系列の場合は幅広になるため、右端のガードを強めにする
                                    val tooltipWidth = if (dataList.size > 1) 120.dp.toPx() else 80.dp.toPx()
                                    val xOffset = (px - tooltipWidth / 2).toInt().coerceIn(
                                        0, 
                                        (constraints.maxWidth.toFloat() - tooltipWidth).toInt()
                                    )
                                    val yOffset = (py - 60.dp.toPx()).toInt().coerceAtLeast(0)
                                    IntOffset(xOffset, yOffset)
                                }
                                .shadow(4.dp, RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                // 同一時刻(X軸)のデータを抽出して表示
                                val pointsAtX = dataList.mapNotNull { lineData ->
                                    lineData.points.find { it.x == point.x }?.let { 
                                        Triple(lineData.label, it, lineData.color)
                                    }
                                }

                                pointsAtX.forEach { (label, p, color) ->
                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(p.y) else p.y.toInt().toString()
                                    val unit = when {
                                        label.contains("血圧") -> "mmHg"
                                        label.contains("脈拍") -> "bpm"
                                        label.contains("体温") -> "℃"
                                        label.contains("血糖値") -> "mg/dL"
                                        label.contains("HbA1c") -> "%"
                                        label.contains("体重") -> "kg"
                                        else -> ""
                                    }
                                    
                                    // 判定結果がある場合は、値の横に括弧書きで表示
                                    val noteSuffix = if (!p.note.isNullOrBlank()) " (${p.note})" else ""
                                    
                                    val displayText = when {
                                        // 血圧の場合はラベルを省いてコンパクトにする
                                        label.contains("血圧") -> "$valueStr $unit$noteSuffix"
                                        // それ以外の複数系列はラベルを表示
                                        dataList.size > 1 -> "$label: $valueStr $unit$noteSuffix"
                                        // 単一系列
                                        else -> "$valueStr $unit"
                                    }

                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = color
                                    )
                                    
                                    // 単一データかつ判定結果がある場合は、次行に表示
                                    if (dataList.size == 1 && !p.note.isNullOrBlank()) {
                                        Text(
                                            text = p.note,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
