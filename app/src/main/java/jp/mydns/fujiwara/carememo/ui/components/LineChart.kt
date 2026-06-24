package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

data class ChartLineData(val label: String, val points: List<Pair<Double, Double>>, val color: Color)
data class ChartLimitLine(val label: String, val value: Double, val color: Color, val isLabelAbove: Boolean)
data class ChartRangeHighlight(val startValue: Double, val endValue: Double, val color: Color)

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
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val valueLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val limitLabelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
    val legendStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val allPoints = dataList.flatMap { it.points }
    if (allPoints.isEmpty() && (fixedMinX == null || fixedMaxX == null)) return
    
    val minX = fixedMinX ?: (allPoints.map { it.first }.minOrNull() ?: 0.0)
    val maxX = fixedMaxX ?: (allPoints.map { it.first }.maxOrNull() ?: 0.0)
    val duration = if (maxX - minX == 0.0) 1.0 else maxX - minX
    
    val allYValues = if (allPoints.isNotEmpty()) {
        allPoints.map { it.second } + limits.map { it.value }
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
    
    val density = LocalDensity.current
    val paddingLeft = 40.dp
    val paddingTop = 20.dp
    val paddingBottom = 20.dp

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
                val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                val topPx = paddingTop.toPx()
                for (i in 0..yStepsCount) {
                    val yVal = minY + stepY * i
                    val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                    val label = if (showDecimal || stepY <= 1.0) "%.1f".format(yVal) else yVal.toInt().toString()
                    val textLayout = textMeasurer.measure(label, labelStyle)
                    drawText(textLayout, topLeft = Offset(size.width - textLayout.size.width - 4.dp.toPx(), py - textLayout.size.height / 2))
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleX = (scaleX * zoom).coerceAtLeast(1f)
                            val maxOffsetX = 0f
                            val minOffsetX = -(size.width * (scaleX - 1f))
                            offsetX = (offsetX + pan.x).coerceIn(minOffsetX, maxOffsetX)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scaleX = 1f
                            offsetX = 0f
                        })
                    }
            ) {
                val leftBufferPx = with(density) { 8.dp.toPx() }
                val rightBufferPx = with(density) { 8.dp.toPx() }
                val horizontalPaddingPx = with(density) { 20.dp.toPx() }
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartWidth = (size.width - leftBufferPx - rightBufferPx) * scaleX
                    val chartHeight = size.height - paddingTop.toPx() - paddingBottom.toPx()
                    val topPx = paddingTop.toPx()
                    val startX = leftBufferPx + offsetX
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    clipRect(left = leftBufferPx, top = 0f, right = size.width - rightBufferPx, bottom = size.height) {
                        ranges.forEach { range ->
                            val pyStart = topPx + chartHeight - ((range.startValue - minY) / yRange).toFloat() * chartHeight
                            val pyEnd = topPx + chartHeight - ((range.endValue - minY) / yRange).toFloat() * chartHeight
                            val top = pyEnd.coerceIn(topPx, topPx + chartHeight)
                            val bottom = pyStart.coerceIn(topPx, topPx + chartHeight)
                            if (bottom > top) {
                                drawRect(
                                    color = range.color,
                                    topLeft = Offset(startX, top),
                                    size = Size(chartWidth, bottom - top)
                                )
                            }
                        }

                        for (i in 0..yStepsCount) {
                            val py = topPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.5f),
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
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    start = Offset(px, topPx),
                                    end = Offset(px, topPx + chartHeight),
                                    strokeWidth = 1.dp.toPx()
                                )
                                val dateStr = DateTimeFormatter.ofPattern("yy/MM/dd")
                                    .withLocale(Locale.JAPAN)
                                    .format(Instant.ofEpochMilli(currentX.toLong()).atZone(ZoneId.systemDefault()))
                                val textLayout = textMeasurer.measure(dateStr, labelStyle)
                                drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, topPx + chartHeight + 4.dp.toPx()))
                            }
                        }

                        limits.forEach { limit ->
                            val py = topPx + chartHeight - ((limit.value - minY) / yRange).toFloat() * chartHeight
                            if (py in topPx..(topPx + chartHeight)) {
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
                            val sortedPoints = lineData.points.sortedBy { it.first }
                            sortedPoints.forEachIndexed { index, (x, y) ->
                                val px = startX + horizontalPaddingPx + ((x - minX) / duration).toFloat() * effectiveWidth
                                val py = topPx + chartHeight - ((y - minY) / yRange).toFloat() * chartHeight
                                
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                
                                if (px in leftBufferPx..(size.width - rightBufferPx)) {
                                    drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(y) else y.toInt().toString()
                                    val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                                    drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLayout.size.height - 2.dp.toPx()))
                                }
                            }
                            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    
                    drawLine(Color.Gray, Offset(leftBufferPx, topPx), Offset(leftBufferPx, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                    drawLine(Color.Gray, Offset(leftBufferPx, topPx + chartHeight), Offset(size.width - rightBufferPx, topPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                }
            }
        }
    }
}
