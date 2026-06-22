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
import androidx.core.content.FileProvider
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.screens.ExportOrder
import jp.mydns.fujiwara.carememo.ui.screens.ExportRange
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        @Suppress("UNUSED_PARAMETER") isNameMaskingEnabled: Boolean,
        category: Category,
        records: List<Any>,
        allPhotos: List<ConditionPhoto> = emptyList(), // 写真データを追加
        range: ExportRange = ExportRange.ALL,
        order: ExportOrder = ExportOrder.NEWEST_FIRST,
        customStartDate: Instant? = null,
        customEndDate: Instant? = null
    ): Boolean {
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
        
        var currentY = drawHeader(canvas, person, true, category, pageNumber)

        when (category) {
            Category.HEIGHT_AND_WEIGHT -> {
                val castedRecords = filteredRecords.filterIsInstance<HeightAndWeight>().sortedBy { it.recordTime }
                if (castedRecords.isNotEmpty()) {
                    // 体重データがある場合のみ描画
                    val weightData = castedRecords.filter { it.weight != null }.map { it.recordTime.toEpochMilli() to it.weight!! }
                    if (weightData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "体重(kg) 推移",
                            lineDataList = listOf(weightData to Color.rgb(0, 100, 200)),
                            startY = currentY,
                            height = SINGLE_GRAPH_HEIGHT,
                            yStep = 10.0,
                            ranges = emptyList(),
                            limitLines = emptyList(),
                            isInteger = false
                        )
                        currentY += 25f
                    }

                    // BMI計算可能なデータ（身長と体重が両方ある）がある場合のみ描画
                    val bmiData = castedRecords.filter { it.height != null && it.weight != null }
                        .map { it.recordTime.toEpochMilli() to it.calculateBMI() }
                    
                    if (bmiData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "BMI 推移",
                            lineDataList = listOf(bmiData to Color.rgb(200, 50, 50)),
                            startY = currentY,
                            height = SINGLE_GRAPH_HEIGHT,
                            yStep = 2.0,
                            ranges = listOf((0.0 to HealthThresholds.BMI_NORMAL_LOW) to 0xFFF0F0F0.toInt(), (HealthThresholds.BMI_NORMAL_HIGH to 100.0) to 0xFFD8D8D8.toInt()),
                            limitLines = emptyList(),
                            isInteger = false,
                            subtitles = listOf(
                                "【判定基準(WHO)】",
                                "・${HealthThresholds.BMI_NORMAL_LOW}未満：低体重",
                                "・${HealthThresholds.BMI_NORMAL_LOW}〜${HealthThresholds.BMI_NORMAL_HIGH}未満：普通体重",
                                "・${HealthThresholds.BMI_NORMAL_HIGH}以上：肥満"
                            )
                        )
                        currentY += 40f
                    }
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawHeightAndWeightTable(document, currentPage, displayRecords, currentY, { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, true, category, pageNumber)
                })
            }
            Category.BP_AND_PULSE -> {
                val castedRecords = filteredRecords.filterIsInstance<BpAndPulse>().sortedBy { it.recordTime }
                if (castedRecords.isNotEmpty()) {
                    val highDash = DashPathEffect(floatArrayOf(8f, 4f), 0f)
                    val lowDash = DashPathEffect(floatArrayOf(2f, 2f), 0f)

                    // 血圧データ（上または下）がある場合のみ描画
                    val bpData = mutableListOf<Pair<List<Pair<Long, Double>>, Int>>()
                    val sysData = castedRecords.filter { it.bpSystolic != null }.map { it.recordTime.toEpochMilli() to it.bpSystolic!!.toDouble() }
                    val diaData = castedRecords.filter { it.bpDiastolic != null }.map { it.recordTime.toEpochMilli() to it.bpDiastolic!!.toDouble() }
                    if (sysData.isNotEmpty()) bpData.add(sysData to Color.rgb(200, 50, 50))
                    if (diaData.isNotEmpty()) bpData.add(diaData to Color.rgb(0, 100, 200))

                    if (bpData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "血圧 (mmHg) 推移",
                            lineDataList = bpData,
                            startY = currentY,
                            height = 180f,
                            yStep = 20.0,
                            ranges = emptyList(),
                            limitLines = listOf(
                                Triple(HealthThresholds.BP_HIGH_SYSTOLIC, highDash, "高血圧目安"),
                                Triple(HealthThresholds.BP_HIGH_DIASTOLIC, highDash, ""),
                                Triple(HealthThresholds.BP_LOW_SYSTOLIC, lowDash, "低血圧目安"),
                                Triple(HealthThresholds.BP_LOW_DIASTOLIC, lowDash, "")
                            ),
                            isInteger = true,
                            subtitles = listOf(
                                "高血圧目安：上 ${HealthThresholds.BP_HIGH_SYSTOLIC.toInt()}mmHg以上 / 下 ${HealthThresholds.BP_HIGH_DIASTOLIC.toInt()}mmHg以上",
                                "低血圧目安：上 ${HealthThresholds.BP_LOW_SYSTOLIC.toInt()}mmHg未満 / 下 ${HealthThresholds.BP_LOW_DIASTOLIC.toInt()}mmHg未満"
                            )
                        )
                        currentY += 25f
                    }

                    // 脈拍データがある場合のみ描画
                    val pulseData = castedRecords.filter { it.pulse != null }.map { it.recordTime.toEpochMilli() to it.pulse!!.toDouble() }
                    if (pulseData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "脈拍 (bpm) 推移",
                            lineDataList = listOf(pulseData to Color.rgb(50, 150, 50)),
                            startY = currentY,
                            height = 120f,
                            yStep = 20.0,
                            ranges = listOf(
                                (0.0 to HealthThresholds.PULSE_LOW) to 0xFFF0F0F0.toInt(),
                                (HealthThresholds.PULSE_HIGH to 200.0) to 0xFFD8D8D8.toInt()
                            ),
                            limitLines = emptyList(),
                            isInteger = true,
                            subtitles = listOf(
                                "脈拍目安：${HealthThresholds.PULSE_LOW.toInt()} 〜 ${HealthThresholds.PULSE_HIGH.toInt()} bpm (正常範囲)"
                            )
                        )
                        currentY += 40f
                    }
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawBpAndPulseTable(document, currentPage, displayRecords, currentY, { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, true, category, pageNumber)
                })
            }
            Category.GLUCOSE_AND_HBA1C -> {
                val castedRecords = filteredRecords.filterIsInstance<GlucoseAndHbA1c>().sortedBy { it.recordTime }
                if (castedRecords.isNotEmpty()) {
                    // 血糖値データがある場合のみ描画
                    val glucoseData = castedRecords.filter { it.glucose != null }.map { it.recordTime.toEpochMilli() to it.glucose!!.toDouble() }
                    if (glucoseData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "血糖値 (mg/dL) 推移",
                            lineDataList = listOf(glucoseData to Color.rgb(150, 0, 150)),
                            startY = currentY,
                            height = SINGLE_GRAPH_HEIGHT,
                            yStep = 20.0,
                            ranges = listOf(
                                (0.0 to HealthThresholds.GLUCOSE_NORMAL_LOW) to 0xFFF0F0F0.toInt(),
                                (HealthThresholds.GLUCOSE_NORMAL_HIGH to 500.0) to 0xFFD8D8D8.toInt()
                            ),
                            isInteger = true,
                            subtitles = listOf(
                                "血糖値（良好）：${HealthThresholds.GLUCOSE_NORMAL_LOW.toInt()} 〜 ${HealthThresholds.GLUCOSE_NORMAL_HIGH.toInt()} mg/dL"
                            )
                        )
                        currentY += 25f
                    }

                    // HbA1cデータがある場合のみ描画
                    val hba1cData = castedRecords.filter { it.hba1c != null }.map { it.recordTime.toEpochMilli() to it.hba1c!! }
                    if (hba1cData.isNotEmpty()) {
                        currentY = drawSingleGraph(
                            canvas = canvas,
                            title = "HbA1c (%) 推移",
                            lineDataList = listOf(hba1cData to Color.rgb(200, 50, 50)),
                            startY = currentY,
                            height = SINGLE_GRAPH_HEIGHT,
                            yStep = 1.0,
                            ranges = listOf(
                                (HealthThresholds.HBA1C_PREDIABETES to HealthThresholds.HBA1C_DIABETES) to 0xFFF0F0F0.toInt(),
                                (HealthThresholds.HBA1C_DIABETES to 20.0) to 0xFFD8D8D8.toInt()
                            ),
                            isInteger = false,
                            subtitles = listOf(
                                "HbA1c判定：正常値(${HealthThresholds.HBA1C_GOOD}%以下) / 正常高値 / 予備軍(${HealthThresholds.HBA1C_PREDIABETES}%以上) / 強い疑い(${HealthThresholds.HBA1C_DIABETES}%以上)"
                            )
                        )
                        currentY += 40f
                    }
                }
                val displayRecords = if (order == ExportOrder.NEWEST_FIRST) castedRecords.sortedByDescending { it.recordTime } else castedRecords
                drawGlucoseAndHbA1cTable(document, currentPage, displayRecords, currentY, { newCanvas, newPageNum ->
                    pageNumber = newPageNum
                    drawHeader(newCanvas, person, true, category, pageNumber)
                })
            }
            Category.CONDITION_AT_VISIT -> {
                val castedRecords = filteredRecords.filterIsInstance<ConditionAtVisit>()
                if (castedRecords.isNotEmpty()) {
                    val sortedRecords = if (order == ExportOrder.NEWEST_FIRST) {
                        castedRecords.sortedByDescending { it.recordTime }
                    } else {
                        castedRecords.sortedBy { it.recordTime }
                    }
                    drawConditionAtVisitContent(context, document, currentPage, sortedRecords, allPhotos, currentY, { newCanvas, newPageNum ->
                        pageNumber = newPageNum
                        drawHeader(newCanvas, person, true, category, pageNumber)
                    })
                } else {
                    document.finishPage(currentPage)
                }
            }
        }

        val fileName = "CareMemo_${category.name}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        try { FileOutputStream(file).use { out -> document.writeTo(out) } } catch (e: Exception) { e.printStackTrace() } finally { document.close() }
        shareFile(context, file)
        return true
    }

    private fun filterAnyRecords(records: List<Any>, range: ExportRange, customStart: Instant?, customEnd: Instant?): List<Any> {
        if (range == ExportRange.ALL) return records
        
        // 汎用的にrecordTimeを取得するためのヘルパー
        fun getRecordTime(record: Any): Instant? = when (record) {
            is HeightAndWeight -> record.recordTime
            is BpAndPulse -> record.recordTime
            is GlucoseAndHbA1c -> record.recordTime
            is ConditionAtVisit -> record.recordTime
            else -> null
        }

        val sorted = records.mapNotNull { rec -> getRecordTime(rec)?.let { it to rec } }
            .sortedByDescending { it.first }
        
        if (sorted.isEmpty()) return emptyList()

        if (range == ExportRange.LATEST) return listOf(sorted.first().second)
        
        if (range == ExportRange.CUSTOM && (customStart != null || customEnd != null)) {
            var filtered = sorted
            if (customStart != null) {
                filtered = filtered.filter { it.first.isAfter(customStart) || it.first.equals(customStart) }
            }
            if (customEnd != null) {
                // 終了日の23:59:59まで含めるための調整
                val endInclusive = customEnd.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)
                filtered = filtered.filter { it.first.isBefore(endInclusive) || it.first.equals(endInclusive) }
            }
            return filtered.map { it.second }
        }

        val latestTime = sorted.first().first.atZone(ZoneId.systemDefault())
        val startTime = when (range) {
            ExportRange.ONE_MONTH -> latestTime.minusMonths(1)
            ExportRange.THREE_MONTHS -> latestTime.minusMonths(3)
            ExportRange.SIX_MONTHS -> latestTime.minusMonths(6)
            else -> latestTime.minusYears(100)
        }.toInstant()
        
        return sorted.filter { it.first.isAfter(startTime) || it.first.equals(startTime) }.map { it.second }
    }

    private fun drawHeader(canvas: Canvas, person: Person, isNameMaskingEnabled: Boolean, category: Category, pageNumber: Int): Float {
        val paint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val name = person.getMaskedName(isNameMaskingEnabled)
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        canvas.drawText("利用者記録報告書 (${category.displayName})", MARGIN, 50f, paint)
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

    private fun splitTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
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
        subtitles: List<String> = emptyList()
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

        if (lineDataList.all { it.first.isEmpty() }) {
            paint.color = Color.LTGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f
            canvas.drawRect(graphArea, paint)
            return graphArea.bottom
        }

        val allValues = lineDataList.flatMap { it.first }.map { it.second } + limitLines.map { it.first }
        val minYVal = floor((allValues.minOf { it } - (yStep / 2)) / yStep) * yStep
        val maxYVal = ceil((allValues.maxOf { it } + (yStep / 2)) / yStep) * yStep
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
        val minX = lineDataList.flatMap { it.first }.minOf { it.first }.toDouble()
        val maxX = lineDataList.flatMap { it.first }.maxOf { it.first }.toDouble()
        val xRange = if (maxX == minX) 1.0 else maxX - minX
        val xLabelPaint = Paint().apply { color = Color.DKGRAY; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        for (i in 0..3) {
            val xTime = minX + (xRange / 3.0) * i
            val xPos = graphArea.left + (i.toFloat() / 3.0f) * graphArea.width()
            val ds = Instant.ofEpochMilli(xTime.toLong()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd"))
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
        var currentPage = initialPage; var canvas = currentPage.canvas; var currentY = startY; var pageNum = 1
        val paint = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val hp = Paint().apply { color = Color.BLACK; isFakeBoldText = true; textSize = 11f; isAntiAlias = true }
        val fp = Paint().apply { style = Paint.Style.FILL }
        val cw = floatArrayOf(160f, 70f, 75f, 60f, 130f); val hd = arrayOf("日付", "身長(cm)", "体重(kg)", "BMI", "判定")
        fun dh(c: Canvas, y: Float): Float {
            var cx = MARGIN; hd.forEachIndexed { i, s -> c.drawText(s, cx, y, hp); cx += cw[i] }
            val ly = y + 5f; c.drawLine(MARGIN, ly, PAGE_WIDTH - MARGIN, ly, paint); return ly + 20f
        }
        currentY = dh(canvas, currentY)
        records.forEachIndexed { idx, rec ->
            if (currentY > PAGE_HEIGHT - MARGIN - 20f) {
                document.finishPage(currentPage); pageNum++; val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create(); currentPage = document.startPage(pi); canvas = currentPage.canvas; currentY = dh(canvas, onNewPage(canvas, pageNum))
            }
            val bmi = rec.calculateBMI()
            val bg = when { bmi <= 0.0 -> null; bmi < HealthThresholds.BMI_NORMAL_LOW -> 0xFFF0F0F0.toInt(); bmi >= HealthThresholds.BMI_NORMAL_HIGH -> 0xFFD8D8D8.toInt(); else -> null }
            if (bg != null) { fp.color = bg; canvas.drawRect(RectF(MARGIN, currentY - 12f, PAGE_WIDTH - MARGIN, currentY + 4f), fp) }
            var cx = MARGIN; canvas.drawText(formatRecordTime(rec.recordTime), cx, currentY, paint); cx += cw[0]
            canvas.drawText(rec.height?.toString() ?: "---", cx, currentY, paint); cx += cw[1]
            val ws = if (rec.weight != null) { val cur = rec.weight; val prev = if (idx < records.size - 1) records[idx + 1] else null; if (prev?.weight != null) { val df = cur - prev.weight; "%.1f (%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df)) } else "%.1f".format(cur) } else "---"
            canvas.drawText(ws, cx, currentY, paint); cx += cw[2]
            canvas.drawText(if (bmi > 0) "%.1f".format(bmi) else "---", cx, currentY, paint); cx += cw[3]
            canvas.drawText(if (bmi > 0) bmi.evaluateBMI() else "---", cx, currentY, paint); currentY += 20f
        }
        document.finishPage(currentPage)
    }

    private fun drawBpAndPulseTable(document: PdfDocument, initialPage: PdfDocument.Page, records: List<BpAndPulse>, startY: Float, onNewPage: (Canvas, Int) -> Float) {
        var currentPage = initialPage; var canvas = currentPage.canvas; var currentY = startY; var pageNum = 1
        val paint = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val hp = Paint().apply { color = Color.BLACK; isFakeBoldText = true; textSize = 11f; isAntiAlias = true }
        val fp = Paint().apply { style = Paint.Style.FILL }
        val cw = floatArrayOf(160f, 65f, 65f, 65f, 139f); val hd = arrayOf("日付", "上(mmHg)", "下(mmHg)", "脈(bpm)", "判定")
        fun dh(c: Canvas, y: Float): Float {
            var cx = MARGIN; hd.forEachIndexed { i, s -> c.drawText(s, cx, y, hp); cx += cw[i] }
            val ly = y + 5f; c.drawLine(MARGIN, ly, PAGE_WIDTH - MARGIN, ly, paint); return ly + 20f
        }
        currentY = dh(canvas, currentY)
        records.forEach { record ->
            if (currentY > PAGE_HEIGHT - MARGIN - 20f) {
                document.finishPage(currentPage); pageNum++; val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create(); currentPage = document.startPage(pi); canvas = currentPage.canvas; currentY = dh(canvas, onNewPage(canvas, pageNum))
            }
            val status = record.checkStatus(); val bg = if (status != "正常") 0xFFF0F0F0.toInt() else null
            if (bg != null) { fp.color = bg; canvas.drawRect(RectF(MARGIN, currentY - 12f, PAGE_WIDTH - MARGIN, currentY + 4f), fp) }
            var cx = MARGIN; canvas.drawText(formatRecordTime(record.recordTime), cx, currentY, paint); cx += cw[0]
            canvas.drawText(record.bpSystolic?.toString() ?: "---", cx, currentY, paint); cx += cw[1]
            canvas.drawText(record.bpDiastolic?.toString() ?: "---", cx, currentY, paint); cx += cw[2]
            canvas.drawText(record.pulse?.toString() ?: "---", cx, currentY, paint); cx += cw[3]
            canvas.drawText(status, cx, currentY, paint); currentY += 20f
        }
        document.finishPage(currentPage)
    }

    private fun drawGlucoseAndHbA1cTable(document: PdfDocument, initialPage: PdfDocument.Page, records: List<GlucoseAndHbA1c>, startY: Float, onNewPage: (Canvas, Int) -> Float) {
        var currentPage = initialPage; var canvas = currentPage.canvas; var currentY = startY; var pageNum = 1
        val paint = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val hp = Paint().apply { color = Color.BLACK; isFakeBoldText = true; textSize = 11f; isAntiAlias = true }
        val fp = Paint().apply { style = Paint.Style.FILL }
        val cw = floatArrayOf(155f, 95f, 95f, 150f); val hd = arrayOf("日付", "血糖値(mg/dL)", "HbA1c(%)", "判定(血糖値・HbA1c)")
        fun dh(c: Canvas, y: Float): Float {
            var cx = MARGIN; hd.forEachIndexed { i, s -> c.drawText(s, cx, y, hp); cx += cw[i] }
            val ly = y + 5f; c.drawLine(MARGIN, ly, PAGE_WIDTH - MARGIN, ly, paint); return ly + 20f
        }
        currentY = dh(canvas, currentY)
        records.forEachIndexed { index, record ->
            if (currentY > PAGE_HEIGHT - MARGIN - 20f) {
                document.finishPage(currentPage); pageNum++; val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create(); currentPage = document.startPage(pi); canvas = currentPage.canvas; currentY = dh(canvas, onNewPage(canvas, pageNum))
            }
            val status = record.checkStatus(); val g = record.glucose; val h = record.hba1c; val isGNormal = g != null && g >= HealthThresholds.GLUCOSE_NORMAL_LOW && g <= HealthThresholds.GLUCOSE_NORMAL_HIGH; val isHGood = h != null && h <= HealthThresholds.HBA1C_GOOD
            val bg = when { g != null && h != null -> { when { isGNormal && isHGood -> null; isGNormal || isHGood -> 0xFFF0F0F0.toInt(); else -> 0xFFD8D8D8.toInt() } }; g != null -> if (isGNormal) null else 0xFFF0F0F0.toInt(); h != null -> if (isHGood) null else 0xFFF0F0F0.toInt(); else -> null }
            if (bg != null) { fp.color = bg; canvas.drawRect(RectF(MARGIN, currentY - 12f, PAGE_WIDTH - MARGIN, currentY + 4f), fp) }
            val pr = if (index < records.size - 1) records[index + 1] else null
            val gs = if (record.glucose != null) { val cur = record.glucose; if (pr?.glucose != null) { val df = cur - pr.glucose; "$cur(${if (df >= 0) "+$df" else "$df"})" } else "$cur" } else "---"
            val hs = if (record.hba1c != null) { val cur = record.hba1c; if (pr?.hba1c != null) { val df = cur - pr.hba1c; "%.1f(%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df)) } else "%.1f".format(cur) } else "---"
            var cx = MARGIN; canvas.drawText(formatRecordTime(record.recordTime), cx, currentY, paint); cx += cw[0]
            canvas.drawText(gs, cx, currentY, paint); cx += cw[1]
            canvas.drawText(hs, cx, currentY, paint); cx += cw[2]
            canvas.drawText(status, cx, currentY, paint); currentY += 20f
        }
        document.finishPage(currentPage)
    }

    private fun formatRecordTime(instant: Instant): String {
        val zoneId = ZoneId.systemDefault()
        val localDateTime = instant.atZone(zoneId).toLocalDateTime()
        val localDate = localDateTime.toLocalDate()
        val eraDate = JapaneseDate.from(localDate)
        val eraYearFormatter = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        val eraName = eraDate.format(eraYearFormatter)
        return "%d(%s%d)年%d月%d日 %02d:%02d".format(localDate.year, eraName, eraYear, localDate.monthValue, localDate.dayOfMonth, localDateTime.hour, localDateTime.minute)
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
