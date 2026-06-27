package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.HealthChartConfig
import jp.mydns.fujiwara.carememo.ui.components.HealthChartHelper
import jp.mydns.fujiwara.carememo.ui.components.ExportOrder
import jp.mydns.fujiwara.carememo.ui.components.ExportRange
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateShort
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatYearMonthHeader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

object PdfExporter {
    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 50f
    private const val SINGLE_GRAPH_HEIGHT = 140f

    /**
     * PDFを作成し、共有インテントを呼び出す
     * @return PDFが生成された場合はtrue、対象データがなく中断した場合はfalse
     */
    fun exportAndShare(
        context: Context,
        person: Person,
        isNameMaskingEnabled: Boolean,
        category: Category,
        records: List<Any>,
        allPhotos: List<ConditionPhoto> = emptyList(), // 写真データを追加
        range: ExportRange = ExportRange.ALL,
        order: ExportOrder = ExportOrder.NEWEST_FIRST,
        customStartDate: Instant? = null,
        customEndDate: Instant? = null,
        password: String? = null // パスワード保護用
    ): Boolean {
        // PDFBoxの初期化
        PDFBoxResourceLoader.init(context)

        // 新しく作る前に古いキャッシュを掃除（常に1世代しか残らないようにする）
        clearOldExports(context)

        // 共通フィルタリング処理を先に行う
        val filteredRecords = filterAnyRecords(records, range, customStartDate, customEndDate)
        if (filteredRecords.isEmpty()) {
            return false
        }

        val document = PdfDocument()
        var pageNumber = 1
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val currentPage = document.startPage(pageInfo)
        val canvas = currentPage.canvas
        
        var currentY = drawHeader(canvas, person, isNameMaskingEnabled, category, pageNumber)

        // カテゴリ内の全データを通じた共通のX軸（時間軸）の範囲を計算
        val (globalMinX, globalMaxX) = HealthChartHelper.calculateGlobalXRange(filteredRecords)

        when (category) {
            Category.HEIGHT_AND_WEIGHT -> {
                val castedRecords = filteredRecords.asSequence().filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }.toList()
                if (castedRecords.isNotEmpty()) {
                    // グラフ描画
                    repeat(HealthChartHelper.getGraphCount(category)) { index ->
                        val config = HealthChartHelper.getChartConfig(category, index, filteredRecords)
                        if ((config != null) && config.dataList.any { it.points.isNotEmpty() }) {
                            currentY = drawSingleGraphFromConfig(canvas, config, currentY, SINGLE_GRAPH_HEIGHT, globalMinX, globalMaxX)
                            currentY += 25f
                        }
                    }
                    currentY += 15f
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawHeightAndWeightTable(document, currentPage, displayRecords, currentY) { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, isNameMaskingEnabled, category, pageNumber)
                }
            }
            Category.BP_AND_PULSE -> {
                val castedRecords = filteredRecords.asSequence().filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }.toList()
                if (castedRecords.isNotEmpty()) {
                    // グラフ描画
                    repeat(HealthChartHelper.getGraphCount(category)) { index ->
                        val config = HealthChartHelper.getChartConfig(category, index, filteredRecords)
                        if ((config != null) && config.dataList.any { it.points.isNotEmpty() }) {
                            val graphHeight = if (index == 0) 180f else 120f
                            currentY = drawSingleGraphFromConfig(canvas, config, currentY, graphHeight, globalMinX, globalMaxX)
                            currentY += 25f
                        }
                    }
                    currentY += 15f
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawBpAndPulseTable(document, currentPage, displayRecords, currentY) { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, isNameMaskingEnabled, category, pageNumber)
                }
            }
            Category.GLUCOSE_AND_HBA1C -> {
                val castedRecords = filteredRecords.asSequence().filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }.toList()
                if (castedRecords.isNotEmpty()) {
                    // グラフ描画
                    repeat(HealthChartHelper.getGraphCount(category)) { index ->
                        val config = HealthChartHelper.getChartConfig(category, index, filteredRecords)
                        if ((config != null) && config.dataList.any { it.points.isNotEmpty() }) {
                            currentY = drawSingleGraphFromConfig(canvas, config, currentY, SINGLE_GRAPH_HEIGHT, globalMinX, globalMaxX)
                            currentY += 25f
                        }
                    }
                    currentY += 15f
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawGlucoseAndHbA1cTable(document, currentPage, displayRecords, currentY) { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, isNameMaskingEnabled, category, pageNumber)
                }
            }
            Category.CONDITION_AT_VISIT -> {
                val castedRecords = filteredRecords.filterIsInstance<ConditionAtVisit>()
                if (castedRecords.isNotEmpty()) {
                    val sortedRecords = if (order == ExportOrder.NEWEST_FIRST) {
                        castedRecords.sortedByDescending { it.recordTime }
                    } else {
                        castedRecords.sortedBy { it.recordTime }
                    }
                    drawConditionAtVisitContent(context, document, currentPage, sortedRecords, allPhotos, currentY) { newCanvas, newPageNum ->
                        pageNumber = newPageNum
                        drawHeader(newCanvas, person, isNameMaskingEnabled, category, pageNumber)
                    }
                } else {
                    document.finishPage(currentPage)
                }
            }
            Category.MEDICATION -> {
                val castedRecords = filteredRecords.asSequence().filterIsInstance<MedicationRecord>().toList()
                if (castedRecords.isNotEmpty()) {
                    drawMedicationContent(document, currentPage, castedRecords, currentY) { newCanvas, newPageNum ->
                        pageNumber = newPageNum
                        drawHeader(newCanvas, person, isNameMaskingEnabled, category, pageNumber)
                    }
                } else {
                    document.finishPage(currentPage)
                }
            }
        }

        val fileName = "CareMemo_${category.name}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        try {
            FileOutputStream(file).use { out -> document.writeTo(out) }

            // パスワード保護の適用
            if (!password.isNullOrEmpty()) {
                encryptPdf(file, password)
            }

            shareFile(context, file)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            document.close()
        }
    }

    /**
     * PDFファイルをパスワードで暗号化する
     */
    private fun encryptPdf(file: File, password: String) {
        val document = PDDocument.load(file)
        try {
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            document.save(file)
        } finally {
            document.close()
        }
    }

    private fun filterAnyRecords(records: List<Any>, range: ExportRange, customStart: Instant?, customEnd: Instant?): List<Any> {
        if (range == ExportRange.ALL) return records
        
        // HistoryRecord インターフェースを利用して汎用的に recordTime を取得
        val sorted = records.asSequence().filterIsInstance<HistoryRecord>()
            .sortedByDescending { it.recordTime }
            .toList()
        
        if (sorted.isEmpty()) return emptyList()

        if (range == ExportRange.LATEST) return listOf(sorted.first())
        
        if (range == ExportRange.CUSTOM && (customStart != null || customEnd != null)) {
            var filtered = sorted.asSequence()
            if (customStart != null) {
                filtered = filtered.filter { it.recordTime.isAfter(customStart) || it.recordTime == customStart }
            }
            if (customEnd != null) {
                // 終了日の23:59:59まで含めるための調整
                val endInclusive = customEnd.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)
                filtered = filtered.filter { it.recordTime.isBefore(endInclusive) || it.recordTime == endInclusive }
            }
            return filtered.toList()
        }

        val latestTime = sorted.first().recordTime.atZone(ZoneId.systemDefault())
        val startTime = when (range) {
            ExportRange.ONE_MONTH -> latestTime.minusMonths(1)
            ExportRange.THREE_MONTHS -> latestTime.minusMonths(3)
            ExportRange.SIX_MONTHS -> latestTime.minusMonths(6)
            else -> latestTime.minusYears(100)
        }.toInstant()
        
        return sorted.filter { it.recordTime.isAfter(startTime) || it.recordTime == startTime }
    }

    private fun drawHeader(canvas: Canvas, person: Person, isNameMaskingEnabled: Boolean, category: Category, pageNumber: Int): Float {
        val paint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val name = person.getMaskedName(isNameMaskingEnabled)
        val date = DateTimeUtils.getCurrentPhotoCaption()
        canvas.drawText("利用者記録 (${category.displayName})", MARGIN, 50f, paint)
        paint.textSize = 12f; paint.isFakeBoldText = false
        canvas.drawText("利用者名: $name 様", MARGIN, 80f, paint)
        canvas.drawText("出力日時: $date", MARGIN, 100f, paint)
        canvas.drawText("ページ: $pageNumber", PAGE_WIDTH - MARGIN - 50f, 100f, paint)
        canvas.drawLine(MARGIN, 110f, PAGE_WIDTH - MARGIN, 110f, paint)
        return 140f
    }

    private fun drawConditionAtVisitContent(
        context: Context,
        document: PdfDocument,
        initialPage: PdfDocument.Page,
        records: List<ConditionAtVisit>,
        allPhotos: List<ConditionPhoto>,
        startY: Float,
        onNewPage: (Canvas, Int) -> Float
    ) {
        var currentPage = initialPage
        var canvas = currentPage.canvas
        var currentY = startY
        var pageNum = 1

        val attrPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val captionPaint = Paint().apply { color = Color.DKGRAY; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val bgPaint = Paint().apply { color = Color.rgb(245, 245, 245); style = Paint.Style.FILL }
        
        val contentWidth = PAGE_WIDTH - (MARGIN * 2)
        val photoSize = (contentWidth - 20f) / 3f // 写真の最大幅（余白込）

        records.forEach { record ->
            val photos = allPhotos.filter { it.conditionId == record.id }.take(3)
            val memoLines = splitTextIntoLines(record.condition ?: "", bodyPaint, contentWidth - 10f)
            
            // 1レコード分の高さを計算（ページ跨ぎ判定用）
            var recordHeight = 25f + (memoLines.size * 15f)
            if (photos.isNotEmpty()) {
                recordHeight += photoSize + 25f // 写真＋キャプションの高さ
            }
            recordHeight += 15f // 下部のマージン

            // ページ内に収まらない場合は改ページ
            if (currentY + recordHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(currentPage); pageNum++; val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create(); currentPage = document.startPage(pi); canvas = currentPage.canvas
                currentY = onNewPage(canvas, pageNum)
            }

            // 属性行
            canvas.drawRect(MARGIN, currentY - 12f, PAGE_WIDTH - MARGIN, currentY + 4f, bgPaint)
            val dateStr = formatRecordTime(record.recordTime)
            val attrText = "$dateStr (${record.author}) : ${record.title ?: ""}"
            canvas.drawText(attrText, MARGIN + 5f, currentY, attrPaint)
            currentY += 20f

            // 本文
            memoLines.forEach { line ->
                canvas.drawText(line, MARGIN + 10f, currentY, bodyPaint)
                currentY += 15f
            }

            // 写真
            if (photos.isNotEmpty()) {
                currentY += 10f
                var currentX = MARGIN + 5f
                photos.forEach { photo ->
                    val photoFile = ImageUtils.getPhotoFile(context, photo.photoFileName)
                    if (photoFile.exists()) {
                        val bitmap = loadOptimizedBitmap(photoFile.absolutePath, photoSize.toInt())
                        if (bitmap != null) {
                            // 正方形枠内に Center Inside で描画
                            val rect = RectF(currentX, currentY, currentX + photoSize - 5f, currentY + photoSize - 5f)
                            drawBitmapCenterInside(canvas, bitmap, rect)
                            
                            // キャプション
                            val captionY = currentY + photoSize + 5f
                            canvas.drawText(photo.caption, currentX + (photoSize / 2f), captionY, captionPaint)
                            
                            bitmap.recycle()
                        }
                    }
                    currentX += photoSize
                }
                currentY += photoSize + 15f
            }

            currentY += 15f // メモ間の最終余白
        }
        document.finishPage(currentPage)
    }

    private fun loadOptimizedBitmap(path: String, maxSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        
        var inSampleSize = 1
        if (options.outHeight > maxSize || options.outWidth > maxSize) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= maxSize && halfWidth / inSampleSize >= maxSize) {
                inSampleSize *= 2
            }
        }
        
        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        return BitmapFactory.decodeFile(path, options)
    }

    private fun drawBitmapCenterInside(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val scale = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = rect.left + (rect.width() - w) / 2f
        val top = rect.top + (rect.height() - h) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun splitTextIntoLines(text: String, paint: Paint, @Suppress("SameParameterValue") maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                result.add("")
                continue
            }
            
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                result.add(remaining.substring(0, count))
                remaining = remaining.substring(count)
            }
        }
        return result
    }

    /**
     * HealthChartConfig を元にグラフを描画する
     */
    private fun drawSingleGraphFromConfig(
        canvas: Canvas,
        config: HealthChartConfig,
        startY: Float,
        height: Float,
        fixedMinX: Double?,
        fixedMaxX: Double?
    ): Float {
        // LineChartConfig から drawSingleGraph 用のデータに変換
        val lineDataList = config.dataList.map { lineData ->
            lineData.points.map { it.first.toLong() to it.second } to lineData.color.toArgb()
        }
        
        val rangeList = config.ranges.map { range ->
            (range.startValue to range.endValue) to range.color.toArgb()
        }

        return drawSingleGraph(
            canvas = canvas,
            title = "${config.title} 推移",
            lineDataList = lineDataList,
            startY = startY,
            height = height,
            yStep = config.stepY,
            ranges = rangeList,
            limitLines = emptyList(), // しきい値線は現在 range（背景色）に統合
            isInteger = !config.showDecimal,
            subtitles = config.getSubtitleLines(),
            fixedMinX = fixedMinX,
            fixedMaxX = fixedMaxX
        )
    }

    private fun drawSingleGraph(
        canvas: Canvas,
        title: String,
        lineDataList: List<Pair<List<Pair<Long, Double>>, Int>>,
        startY: Float,
        height: Float,
        yStep: Double,
        ranges: List<Pair<Pair<Double, Double>, Int>> = emptyList(),
        limitLines: List<Triple<Double, DashPathEffect, String>> = emptyList(),
        isInteger: Boolean = false,
        subtitles: List<String> = emptyList(),
        fixedMinX: Double? = null,
        fixedMaxX: Double? = null
    ): Float {
        val paint = Paint().apply { isAntiAlias = true }
        
        // 1. タイトルの描画
        paint.color = Color.BLACK; paint.textSize = 10f; paint.isFakeBoldText = true
        canvas.drawText(title, MARGIN, startY + 10f, paint)
        
        // 2. サブタイトル（目安・ヒント）の描画
        var currentSubY = startY + 22f
        paint.isFakeBoldText = false
        paint.textSize = 8f
        paint.color = Color.DKGRAY
        subtitles.forEach { line ->
            canvas.drawText(line, MARGIN, currentSubY, paint)
            currentSubY += 12f
        }
        
        // 3. グラフエリアの計算 (目安などの有無に合わせて位置を調整)
        val graphTop = if (subtitles.isEmpty()) startY + 20f else currentSubY + 5f
        val graphArea = RectF(MARGIN + 35f, graphTop, PAGE_WIDTH - MARGIN - 10f, graphTop + height - 20f)

        paint.color = Color.rgb(248, 248, 248); paint.style = Paint.Style.FILL
        canvas.drawRect(graphArea, paint)

        if (lineDataList.all { it.first.isEmpty() } && (fixedMinX == null || fixedMaxX == null)) {
            paint.color = Color.LTGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f
            canvas.drawRect(graphArea, paint)
            return graphArea.bottom
        }

        val allValues = lineDataList.flatMap { it.first }.map { it.second } + limitLines.map { it.first }
        val minYVal = if (allValues.isEmpty()) 0.0 else floor((allValues.minOf { it } - (yStep / 2)) / yStep) * yStep
        val maxYVal = if (allValues.isEmpty()) 100.0 else ceil((allValues.maxOf { it } + (yStep / 2)) / yStep) * yStep
        val yRange = if (maxYVal == minYVal) yStep else maxYVal - minYVal

        ranges.forEach { (range, color) ->
            val (s, e) = range
            val yStart = graphArea.bottom - ((s - minYVal) / yRange).toFloat() * graphArea.height()
            val yEnd = graphArea.bottom - ((e - minYVal) / yRange).toFloat() * graphArea.height()
            val t = yEnd.coerceIn(graphArea.top, graphArea.bottom); val b = yStart.coerceIn(graphArea.top, graphArea.bottom)
            if (b > t) { paint.color = color; canvas.drawRect(graphArea.left, t, graphArea.right, b, paint) }
        }

        val dashPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f; pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f); isAntiAlias = true }
        limitLines.forEach { (valPos, effect, label) ->
            if (valPos > minYVal && valPos < maxYVal) {
                val py = graphArea.bottom - ((valPos - minYVal) / yRange).toFloat() * graphArea.height()
                dashPaint.pathEffect = effect; canvas.drawLine(graphArea.left, py, graphArea.right, py, dashPaint)
                if (label.isNotEmpty()) { canvas.drawText(label, graphArea.left + 4f, py - 2f, Paint().apply { color = Color.GRAY; textSize = 6f; isAntiAlias = true }) }
            }
        }

        paint.pathEffect = null; paint.style = Paint.Style.FILL; paint.textSize = 8f; paint.color = Color.GRAY
        val steps = 4
        for (i in 0..steps) {
            val yVal = minYVal + (yRange / steps) * i
            val yPos = graphArea.bottom - (i.toFloat() / steps) * graphArea.height()
            canvas.drawText(if (isInteger) yVal.toInt().toString() else "%.1f".format(yVal), MARGIN, yPos + 3f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f; paint.alpha = 40
            canvas.drawLine(graphArea.left, yPos, graphArea.right, yPos, paint)
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }

        paint.color = Color.LTGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f; canvas.drawRect(graphArea, paint)
        
        val minX = fixedMinX ?: (lineDataList.flatMap { it.first }.minOfOrNull { it.first }?.toDouble() ?: 0.0)
        val maxX = fixedMaxX ?: (lineDataList.flatMap { it.first }.maxOfOrNull { it.first }?.toDouble() ?: 0.0)
        val xRange = if (maxX == minX) 1.0 else maxX - minX

        val xLabelPaint = Paint().apply { color = Color.DKGRAY; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        for (i in 0..3) {
            val xTime = minX + (xRange / 3.0) * i
            val xPos = graphArea.left + (i.toFloat() / 3.0f) * graphArea.width()
            val ds = formatDateShort(Instant.ofEpochMilli(xTime.toLong()))
            canvas.drawText(ds, xPos, graphArea.bottom + 12f, xLabelPaint)
        }

        val lp = Paint().apply { color = Color.BLACK; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        lineDataList.forEach { (records, color) ->
            val path = Path(); paint.color = color; paint.strokeWidth = 1.5f; paint.style = Paint.Style.STROKE
            records.forEachIndexed { idx, rec ->
                val px = graphArea.left + ((rec.first - minX) / xRange).toFloat() * graphArea.width()
                val py = graphArea.bottom - ((rec.second - minYVal) / yRange).toFloat() * graphArea.height()
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            records.forEach { rec ->
                val px = graphArea.left + ((rec.first - minX) / xRange).toFloat() * graphArea.width()
                val py = graphArea.bottom - ((rec.second - minYVal) / yRange).toFloat() * graphArea.height()
                canvas.drawCircle(px, py, 2f, paint)
                canvas.drawText(if (isInteger) rec.second.toInt().toString() else "%.1f".format(rec.second), px, py - 6f, lp)
            }
        }
        return graphArea.bottom + 15f
    }

    private fun drawHeightAndWeightTable(document: PdfDocument, initialPage: PdfDocument.Page, records: List<HeightAndWeight>, startY: Float, onNewPage: (Canvas, Int) -> Float) {
        val columns = listOf(
            TableColumn<HeightAndWeight>("日付", 160f) { rec, _ -> formatRecordTime(rec.recordTime) },
            TableColumn<HeightAndWeight>("${HealthThresholds.HEALTH_LABEL_HEIGHT}(cm)", 70f) { rec, _ -> rec.height?.toString() ?: "---" },
            TableColumn<HeightAndWeight>("${HealthThresholds.HEALTH_LABEL_WEIGHT}(kg)", 75f) { rec, idx ->
                if (rec.weight != null) {
                    val cur = rec.weight
                    val prev = if (idx < records.size - 1) records[idx + 1] else null
                    if (prev?.weight != null) {
                        val df = cur - prev.weight
                        "%.1f (%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df))
                    } else "$cur"
                } else "---"
            },
            TableColumn<HeightAndWeight>(HealthThresholds.HEALTH_LABEL_BMI, 60f) { rec, _ ->
                val bmi = rec.calculateBMI()
                if (bmi > 0) "%.1f".format(bmi) else "---"
            },
            TableColumn<HeightAndWeight>(
                header = HealthThresholds.HEALTH_LABEL_STATUS,
                width = 130f,
                getBackgroundColor = { rec -> rec.getBmiResult().second.pdfBgColor },
                getValue = { rec, _ -> rec.getBmiResult().first }
            )
        )
        drawGenericTable(document, initialPage, records, columns, startY, onNewPage)
    }

    private fun drawBpAndPulseTable(document: PdfDocument, initialPage: PdfDocument.Page, records: List<BpAndPulse>, startY: Float, onNewPage: (Canvas, Int) -> Float) {
        val columns = listOf(
            TableColumn<BpAndPulse>("日付", 150f) { rec, _ -> formatRecordTime(rec.recordTime) },
            TableColumn<BpAndPulse>(HealthThresholds.HEALTH_LABEL_SYSTOLIC_SHORT, 55f) { rec, _ -> rec.bpSystolic?.toString() ?: "---" },
            TableColumn<BpAndPulse>(HealthThresholds.HEALTH_LABEL_DIASTOLIC_SHORT, 55f) { rec, _ -> rec.bpDiastolic?.toString() ?: "---" },
            TableColumn<BpAndPulse>(HealthThresholds.HEALTH_LABEL_PULSE_SHORT, 50f) { rec, _ -> rec.pulse?.toString() ?: "---" },
            TableColumn<BpAndPulse>(HealthThresholds.HEALTH_LABEL_BODY_TEMP, 55f) { rec, _ -> rec.bodyTemperature?.let { "%.1f".format(it) } ?: "---" },
            TableColumn<BpAndPulse>(HealthThresholds.HEALTH_LABEL_STATUS, 130f,
                getBackgroundColor = { rec -> rec.getWorstAlertLevel().pdfBgColor },
                getValue = { rec, _ -> rec.getVitalResults().joinToString("・") { it.first } }
            )
        )
        drawGenericTable(document, initialPage, records, columns, startY, onNewPage)
    }

    private fun drawGlucoseAndHbA1cTable(document: PdfDocument, initialPage: PdfDocument.Page, records: List<GlucoseAndHbA1c>, startY: Float, onNewPage: (Canvas, Int) -> Float) {
        val columns = listOf(
            TableColumn<GlucoseAndHbA1c>("日付", 155f) { rec, _ -> formatRecordTime(rec.recordTime) },
            TableColumn<GlucoseAndHbA1c>("${HealthThresholds.HEALTH_LABEL_GLUCOSE}(mg/dL)", 95f) { rec, idx ->
                val pr = if (idx < records.size - 1) records[idx + 1] else null
                if (rec.glucose != null) {
                    val cur = rec.glucose
                    if (pr?.glucose != null) {
                        val df = cur - pr.glucose
                        "$cur(${if (df >= 0) "+$df" else df})"
                    } else "$cur"
                } else "---"
            },
            TableColumn<GlucoseAndHbA1c>("${HealthThresholds.HEALTH_LABEL_HBA1C}(%)", 95f) { rec, idx ->
                val pr = if (idx < records.size - 1) records[idx + 1] else null
                if (rec.hba1c != null) {
                    val cur = rec.hba1c
                    if (pr?.hba1c != null) {
                        val df = cur - pr.hba1c
                        "%.1f(%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df))
                    } else "%.1f".format(cur)
                } else "---"
            },
            TableColumn<GlucoseAndHbA1c>(HealthThresholds.HEALTH_LABEL_STATUS, 150f,
                getBackgroundColor = { rec -> rec.getWorstAlertLevel().pdfBgColor },
                getValue = { rec, _ -> rec.getCombinedResultText() }
            )
        )
        drawGenericTable(document, initialPage, records, columns, startY, onNewPage)
    }

    /**
     * テーブルのカラム定義
     */
    private data class TableColumn<T>(
        val header: String,
        val width: Float,
        val getBackgroundColor: ((T) -> Int?)? = null,
        val getValue: (T, Int) -> String
    )

    /**
     * A4用紙に最適化された共通テーブル描画ロジック
     */
    private fun <T> drawGenericTable(
        document: PdfDocument,
        initialPage: PdfDocument.Page,
        records: List<T>,
        columns: List<TableColumn<T>>,
        startY: Float,
        onNewPage: (Canvas, Int) -> Float
    ) {
        var currentPage = initialPage
        var canvas = currentPage.canvas
        var currentY = startY
        var pageNum = 1

        val paint = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val hp = Paint().apply { color = Color.BLACK; isFakeBoldText = true; textSize = 11f; isAntiAlias = true }
        val fp = Paint().apply { style = Paint.Style.FILL }

        fun drawTableHeader(c: Canvas, y: Float): Float {
            var cx = MARGIN
            columns.forEach { col ->
                c.drawText(col.header, cx, y, hp)
                cx += col.width
            }
            val ly = y + 5f
            c.drawLine(MARGIN, ly, PAGE_WIDTH - MARGIN, ly, paint)
            return ly + 20f
        }

        currentY = drawTableHeader(canvas, currentY)

        records.forEachIndexed { index, record ->
            // 改ページ判定
            if (currentY > PAGE_HEIGHT - MARGIN - 20f) {
                document.finishPage(currentPage)
                pageNum++
                val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                currentPage = document.startPage(pi)
                canvas = currentPage.canvas
                currentY = drawTableHeader(canvas, onNewPage(canvas, pageNum))
            }

            // 行の背景色
            val bgColor = columns.firstNotNullOfOrNull { it.getBackgroundColor?.invoke(record) }
            if (bgColor != null) {
                fp.color = bgColor
                canvas.drawRect(RectF(MARGIN, currentY - 12f, PAGE_WIDTH - MARGIN, currentY + 4f), fp)
            }

            // データの描画
            var cx = MARGIN
            columns.forEach { col ->
                canvas.drawText(col.getValue(record, index), cx, currentY, paint)
                cx += col.width
            }
            currentY += 20f
        }
        document.finishPage(currentPage)
    }

    private fun drawMedicationContent(
        document: PdfDocument,
        initialPage: PdfDocument.Page,
        records: List<MedicationRecord>,
        startY: Float,
        onNewPage: (Canvas, Int) -> Float
    ) {
        var currentPage = initialPage
        var canvas = currentPage.canvas
        var currentY = startY
        var pageNum = 1

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val labelPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE }

        // 曜日の色
        val sundayPaint = Paint().apply { color = Color.rgb(211, 47, 47); textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val saturdayPaint = Paint().apply { color = Color.rgb(25, 118, 210); textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        // ステータスの色・記号設定
        val statusPaints = mapOf(
            2 to Paint().apply { color = Color.rgb(103, 58, 183); textSize = 10f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }, // Taken (Purple)
            1 to Paint().apply { color = Color.rgb(126, 87, 194); textSize = 10f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }, // Assisted (Medium Purple)
            0 to Paint().apply { color = Color.rgb(211, 47, 47); textSize = 11f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }  // Not taken (Red)
        )
        val grayPaint = Paint().apply { color = Color.LTGRAY; textSize = 9f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        // 月ごとにグループ化し、降順（新しい月が上）にソート
        val recordsByMonth = records.groupBy {
            val date = try { LocalDate.parse(it.dosageDate) } catch (e: Exception) { LocalDate.now() }
            YearMonth.from(date)
        }.toSortedMap(compareByDescending { it })

        val rowLabels = listOf("朝", "昼", "夕", "寝る前")
        val labelWidth = 60f
        val colWidth = (PAGE_WIDTH - MARGIN * 2 - labelWidth) / 31f
        val rowHeight = 22f
        val tableHeight = rowHeight * 6 + 40f // ヘッダー2行 + データ4行 + タイトル・余白

        recordsByMonth.forEach { (yearMonth, monthRecords) ->
            // ページ跨ぎ判定
            if (currentY + tableHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(currentPage)
                pageNum++
                val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                currentPage = document.startPage(pi)
                canvas = currentPage.canvas
                currentY = onNewPage(canvas, pageNum)
            }

            // 月タイトル描画 (和暦)
            val titleText = formatYearMonthHeader(yearMonth)
            canvas.drawText(titleText, MARGIN, currentY, titlePaint)
            currentY += 15f

            val startX = MARGIN
            val tableTop = currentY
            val daysInMonth = yearMonth.lengthOfMonth()
            
            // 土日の背景色塗りつぶし
            val sunBgPaint = Paint().apply { color = Color.rgb(255, 240, 240); style = Paint.Style.FILL }
            val satBgPaint = Paint().apply { color = Color.rgb(240, 248, 255); style = Paint.Style.FILL }
            for (day in 1..daysInMonth) {
                val date = yearMonth.atDay(day)
                val dayOfWeek = date.dayOfWeek
                if (dayOfWeek == java.time.DayOfWeek.SUNDAY || dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                    val xStart = startX + labelWidth + (day - 1) * colWidth
                    val rect = RectF(xStart, tableTop, xStart + colWidth, tableTop + rowHeight * 6)
                    canvas.drawRect(rect, if (dayOfWeek == java.time.DayOfWeek.SUNDAY) sunBgPaint else satBgPaint)
                }
            }

            // 表のグリッド（横線）
            for (i in 0..6) {
                val y = tableTop + i * rowHeight
                canvas.drawLine(startX, y, startX + labelWidth + colWidth * daysInMonth, y, linePaint)
            }
            // 縦線
            canvas.drawLine(startX, tableTop, startX, tableTop + rowHeight * 6, linePaint) // 左端
            canvas.drawLine(startX + labelWidth, tableTop, startX + labelWidth, tableTop + rowHeight * 6, linePaint) // ラベルとデータの境界
            for (i in 1..daysInMonth) {
                val x = startX + labelWidth + i * colWidth
                canvas.drawLine(x, tableTop, x, tableTop + rowHeight * 6, linePaint)
            }

            // ヘッダー行 (日付)
            for (day in 1..daysInMonth) {
                val x = startX + labelWidth + (day - 0.5f) * colWidth
                canvas.drawText(day.toString(), x, tableTop + rowHeight * 0.65f, headerPaint)
                
                // 曜日
                val date = yearMonth.atDay(day)
                val dowStr = formatShortDayOfWeek(date)
                val dowPaint = when (date.dayOfWeek) {
                    java.time.DayOfWeek.SUNDAY -> sundayPaint
                    java.time.DayOfWeek.SATURDAY -> saturdayPaint
                    else -> headerPaint
                }
                canvas.drawText(dowStr, x, tableTop + rowHeight * 1.65f, dowPaint)
            }

            // データ行
            val recordsByDayAndSlot = monthRecords.associateBy { it.dosageDate to it.timeSlot }
            rowLabels.forEachIndexed { rowIndex, label ->
                val y = tableTop + (rowIndex + 2) * rowHeight
                canvas.drawText(label, startX + 5f, y + rowHeight * 0.65f, labelPaint)
                
                for (day in 1..daysInMonth) {
                    val dateStr = yearMonth.atDay(day).toString()
                    val record = recordsByDayAndSlot[dateStr to rowIndex]
                    val x = startX + labelWidth + (day - 0.5f) * colWidth
                    if (record != null) {
                        val mark = when (record.status) {
                            2 -> "〇"
                            1 -> "△"
                            0 -> "×"
                            else -> "－"
                        }
                        val paint = statusPaints[record.status] ?: bodyPaint
                        canvas.drawText(mark, x, y + rowHeight * 0.7f, paint)
                    } else {
                        canvas.drawText("－", x, y + rowHeight * 0.7f, grayPaint)
                    }
                }
            }

            currentY += rowHeight * 6 + 30f // 次の表までの余白
        }
        document.finishPage(currentPage)
    }

    private fun shareFile(context: Context, file: File) {
        val auth = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, auth, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "PDFを共有"))
    }

    /**
     * 過去に生成したPDFキャッシュをすべて削除する
     */
    fun clearOldExports(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("CareMemo") && file.name.endsWith(".pdf")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
