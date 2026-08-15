package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：LineChart
 *
 * 【役割】
 * Canvas を使用して、時系列データの折れ線グラフを低レイヤーで描画する汎用グラフエンジンを提供します。
 * 標準のグラフライブラリに依存せず、アプリ固有のズーム・スクロール挙動とアクセシビリティを実現します。
 *
 * 【主な機能】
 * ・複数のデータ系列（dataList）の同時描画。
 * ・ピンチズームによるX軸（時間軸）の拡大・縮小、およびスワイプによるスクロール。
 * ・グラフ上の点をタップした際の詳細情報（ツールチップ）のオーバーレイ表示。
 * ・判定基準に基づく背景ハイライト（ranges）および目標値等の補助線（limits）の描画。
 * ・動的なY軸目盛りの計算と、軸外はみ出しのクリッピング制御。
 * ・UIテスト検証用のセマンティクス（contentDescription）への数値出力。
 *
 * 【想定する利用場所】
 * HealthGraphView（健康記録履歴）、GraphExpansionScreen（グラフ拡大表示）、PDF出力プレビュー。
 *
 * 【このコンポーネントでは行わないこと】
 * データの加工ロジックや、カテゴリに応じた特定の設定生成（HealthChartHelper が担当）。
 */

/**
 * 全体像：折れ線グラフエンジン（Line Chart Engine）
 *
 * ■ HealthGraphView 等 (親コンポーネント)
 * │
 * └─ [1] LineChart (★本コンポーネント)
 *      ├─ Column (凡例表示)
 *      └─ Row
 *           ├─ Canvas (Y軸目盛りラベル)
 *           └─ BoxWithConstraints (ジェスチャー・クリッピング領域)
 *                ├─ Canvas (描画本体：背景、グリッド、折れ線、点、補助線)
 *                └─ Surface (ツールチップ：タップ時にオーバーレイ表示)
 */

import android.annotation.SuppressLint
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** グラフ上の1点を表すデータ */
data class ChartPoint(val x: Double, val y: Double, val note: String? = null)
/** 1つの折れ線（データ系列）を構成するデータ */
data class ChartLineData(val label: String, val points: List<ChartPoint>, val color: Color, val unit: String = "")
/** グラフ上に描画する水平補助線（目標値など） */
data class ChartLimitLine(val label: String, val value: Double, val color: Color = Color.Gray, val isLabelAbove: Boolean)
/** グラフ背景に描画するハイライト範囲 */
data class ChartRangeHighlight(val startValue: Double, val endValue: Double, val color: Color)
/** ユーザーに選択された点の詳細情報 */
data class SelectedPoint(val x: Double, val y: Double, val color: Color, val label: String, val note: String? = null)

/**
 * 汎用折れ線グラフコンポーネント
 *
 * @param dataList 描画する系列データのリスト
 * @param modifier 修飾子
 * @param stepY Y軸のメモリ間隔
 * @param limits 水平補助線のリスト
 * @param ranges 背景ハイライトのリスト
 * @param minYConstraint Y軸の最小表示値（nullの場合は自動計算）
 * @param maxYConstraint Y軸の最大表示値（nullの場合は自動計算）
 * @param fixedMinX X軸（時間）の最小値。複数グラフの軸を同期させる場合に使用。
 * @param fixedMaxX X軸（時間）の最大値。複数グラフの軸を同期させる場合に使用。
 * @param showDecimal Y軸の目盛りに小数を表示するかどうか
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LineChart(
    dataList: List<ChartLineData>,
    modifier: Modifier = Modifier,
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

    // UIテストでのデータ検証用に、グラフに含まれる全数値をセマンティクスに統合する
    val allLabelContent = remember(dataList, fixedMinX, fixedMaxX) {
        val labels = mutableListOf<String>()
        dataList.forEach { line ->
            line.points.forEach { p ->
                labels.add(if (showDecimal || stepY <= 1.0) "%.1f".format(p.y) else p.y.toInt().toString())
            }
        }
        labels.joinToString(", ")
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    
    // ズーム（scaleX）とスクロール（offsetX）の状態管理
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var selectedPoint by remember { mutableStateOf<SelectedPoint?>(null) }

    val density = LocalDensity.current
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yy/MM/dd")
            .withLocale(Locale.JAPAN)
            .withZone(ZoneId.systemDefault())
    }

    // 軸ラベル用の余白設定
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

    // X軸の範囲計算
    val minX = fixedMinX ?: (allPoints.minOfOrNull { it.x } ?: 0.0)
    val maxX = fixedMaxX ?: (allPoints.maxOfOrNull { it.x } ?: 0.0)
    val duration = if (maxX - minX == 0.0) 1.0 else maxX - minX
    
    // Y軸の範囲計算（入力制約とデータの最大/最小を統合し、stepY 単位で丸める）
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { 
                // テスト用に描画内容の文字情報を公開
                this.contentDescription = allLabelContent
            }
    ) {
        // 凡例の表示（複数系列ある場合のみ）
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
            // Y軸ラベルの描画エリア
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
            // グラフ本体の描画エリア（ジェスチャー対応）
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        // ズームとパンのジェスチャー処理
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleX = (scaleX * zoom).coerceAtLeast(1f)
                            val maxOffsetX = 0f
                            val minOffsetX = -(size.width * (scaleX - 1f))
                            offsetX = (offsetX + pan.x).coerceIn(minOffsetX, maxOffsetX)
                            selectedPoint = null
                        }
                    }
                    .pointerInput(Unit) {
                        // タップ操作による詳細（ツールチップ）表示
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
                                var minDistance = with(density) { 24.dp.toPx() } // タップ判定の許容範囲

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
                                // トグル動作：同じ点をタップしたら閉じる
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
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()

                Canvas(modifier = Modifier.size(maxWidth, maxHeight)) {
                    val chartWidth = (size.width - leftBufferPx - rightBufferPx) * scaleX
                    val chartHeight = size.height - paddingTopPx - paddingBottomPx
                    val startX = leftBufferPx + offsetX
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    // clipRect により、スクロール時にグラフが軸の外側（ラベルエリアなど）に描画されないように制限
                    clipRect(left = leftBufferPx, top = 0f, right = size.width - rightBufferPx, bottom = size.height) {
                        // 1. 背景ハイライトの描画
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

                        // 2. グリッド線（水平）の描画
                        for (i in 0..yStepsCount) {
                            val py = paddingTopPx + chartHeight - (i.toFloat() / yStepsCount) * chartHeight
                            drawLine(
                                color = gridColor.copy(alpha = 0.5f),
                                start = Offset(leftBufferPx, py),
                                end = Offset(size.width - rightBufferPx, py),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        
                        // 3. X軸ラベルとグリッド線（垂直）の描画
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
                                val dateStr = dateFormatter.format(Instant.ofEpochMilli(currentX.toLong()))
                                val textLayout = textMeasurer.measure(dateStr, labelStyle)
                                drawText(textLayout, topLeft = Offset(px - textLayout.size.width / 2, paddingTopPx + chartHeight + 4.dp.toPx()))
                            }
                        }

                        // 4. 水平補助線（limits）の描画
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

                        // 5. データ系列（折れ線と点）の描画
                        dataList.forEach { lineData ->
                            val path = Path()
                            val sortedPoints = lineData.points.sortedBy { it.x }
                            sortedPoints.forEachIndexed { index, point ->
                                val px = startX + horizontalPaddingPx + ((point.x - minX) / duration).toFloat() * effectiveWidth
                                val py = paddingTopPx + chartHeight - ((point.y - minY) / yRange).toFloat() * chartHeight
                                
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                
                                if (px in leftBufferPx..(size.width - rightBufferPx)) {
                                    drawCircle(lineData.color, radius = 3.dp.toPx(), center = Offset(px, py))
                                    
                                    // 選択された点を強調表示
                                    if (selectedPoint?.x == point.x && selectedPoint?.y == point.y && selectedPoint?.color == lineData.color) {
                                        drawCircle(
                                            color = lineData.color,
                                            radius = 6.dp.toPx(),
                                            center = Offset(px, py),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }

                                    // データラベル（数値）を各点の近傍に描画
                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(point.y) else point.y.toInt().toString()
                                    val valueLayout = textMeasurer.measure(valueStr, valueLabelStyle.copy(color = lineData.color))
                                    drawText(valueLayout, topLeft = Offset(px - valueLayout.size.width / 2, py - valueLabelStyle.fontSize.toPx() - 2.dp.toPx()))
                                }
                            }
                            drawPath(path, color = lineData.color, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    
                    // L字型の軸線の描画
                    drawLine(axisColor, Offset(leftBufferPx, paddingTopPx), Offset(leftBufferPx, paddingTopPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                    drawLine(axisColor, Offset(leftBufferPx, paddingTopPx + chartHeight), Offset(size.width - rightBufferPx, paddingTopPx + chartHeight), strokeWidth = 1.5.dp.toPx())
                }

                // 吹き出し（ツールチップ）の表示制御
                selectedPoint?.let { point ->
                    val chartWidth = (wPx - leftBufferPx) * scaleX
                    val chartHeight = hPx - paddingTopPx - paddingBottomPx
                    val startX = leftBufferPx + offsetX
                    val effectiveWidth = chartWidth - (horizontalPaddingPx * 2)

                    val px = startX + horizontalPaddingPx + ((point.x - minX) / duration).toFloat() * effectiveWidth
                    val py = paddingTopPx + chartHeight - ((point.y - minY) / yRange).toFloat() * chartHeight

                    if (px in leftBufferPx..(wPx - rightBufferPx)) {
                        val dateStr = dateFormatter.format(Instant.ofEpochMilli(point.x.toLong()))

                        Surface(
                            modifier = Modifier
                                .offset {
                                    // 吹き出しの幅と高さを考慮して配置位置を調整（画面端で切れないように coerceIn を使用）
                                    val tooltipWidth = if (dataList.size > 1) 120.dp.toPx() else 80.dp.toPx()
                                    val xOffset = (px - tooltipWidth / 2).toInt().coerceIn(
                                        0, 
                                        (wPx - tooltipWidth).toInt()
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
                                
                                // 同一時刻(X軸)のデータを抽出して表示（血圧など）
                                val pointsAtX = dataList.mapNotNull { lineData ->
                                    lineData.points.find { it.x == point.x }?.let { 
                                        Triple(lineData.label, it, lineData.color)
                                    }
                                }

                                pointsAtX.forEach { (label, p, color) ->
                                    val lineData = dataList.find { it.label == label && it.color == color }
                                    val unit = lineData?.unit ?: ""
                                    val valueStr = if (showDecimal || stepY <= 1.0) "%.1f".format(p.y) else p.y.toInt().toString()
                                    
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
                                    
                                    // 単一データかつ判定結果(note)がある場合は、次行に詳細を表示
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
