package jp.mydns.fujiwara.carememo.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.spec.*
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel.*
import jp.mydns.fujiwara.carememo.ui.components.common.ExportOrder
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import jp.mydns.fujiwara.carememo.ui.components.common.ExportRange
import jp.mydns.fujiwara.carememo.ui.components.health.HealthChartConfig
import jp.mydns.fujiwara.carememo.ui.components.health.HealthChartHelper
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatDateShort
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatShortDayOfWeek
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatYearMonthHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/**
 * アプリ内の各種データをA4サイズのPDFとして出力・共有するためのユーティリティ。
 */
object PdfExporter {
    private val layoutSpec = ExportSpecifications.Pdf.Layout
    private val styleSpec = ExportSpecifications.Pdf.Style
    private val colorSpec = ExportSpecifications.Pdf.Colors
    private val tableSpec = ExportSpecifications.Pdf.TableConfig

    /**
     * PDF作成時の描画コンテキストを保持する内部クラス。
     * 座標管理や改ページ処理を隠蔽する。
     */
    private class PdfPageContext(
        val context: Context,
        val document: PdfDocument,
        val person: Person,
        val category: Category
    ) {
        var pageNumber = 0
        var currentY = 0f
        lateinit var canvas: Canvas
        private var currentPage: PdfDocument.Page? = null

        /**
         * 新しいページを開始する。
         */
        fun nextPage() {
            finishPage()
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(layoutSpec.PAGE_WIDTH.toInt(), layoutSpec.PAGE_HEIGHT.toInt(), pageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            currentY = drawHeader(this)
        }

        /**
         * 現在のページを確定する。
         */
        fun finishPage() {
            currentPage?.let { document.finishPage(it) }
            currentPage = null
        }

        /**
         * 指定された高さが現在のページに収まるか確認し、不足していれば改ページする。
         */
        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > layoutSpec.PAGE_HEIGHT - layoutSpec.MARGIN) {
                nextPage()
            }
        }
    }

    /**
     * PDFを作成し、共有インテントを呼び出す。
     * @throws IllegalArgumentException 出力対象データが空の場合
     * @throws IOException ファイル操作に失敗した場合
     */
    suspend fun exportAndShare(
        context: Context,
        person: Person,
        category: Category,
        records: List<Any>,
        allPhotos: List<ConditionPhoto> = emptyList(),
        range: ExportRange = ExportRange.ALL,
        order: ExportOrder = ExportOrder.NEWEST_FIRST,
        customStartDate: Instant? = null,
        customEndDate: Instant? = null,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        clearOldExports(context)

        val filteredRecords = filterAnyRecords(category, records, range, customStartDate, customEndDate)
        if (filteredRecords.isEmpty()) {
            throw IllegalArgumentException("出力対象のデータが存在しません。")
        }

        val document = PdfDocument()
        val pageContext = PdfPageContext(context, document, person, category)
        
        // 最初のページ作成
        pageContext.nextPage()

        try {
            when (category) {
                Category.HEIGHT_AND_WEIGHT, Category.BP_AND_PULSE, Category.GLUCOSE_AND_HBA1C -> {
                    drawHealthContent(pageContext, category, filteredRecords, order)
                }
                Category.CONDITION_AT_VISIT -> {
                    val casted = filteredRecords.filterIsInstance<ConditionAtVisit>()
                    val sorted = if (order == ExportOrder.NEWEST_FIRST) casted.sortedByDescending { it.recordTime } else casted.sortedBy { it.recordTime }
                    drawConditionContent(pageContext, sorted, allPhotos)
                }
                Category.MEDICATION -> {
                    val casted = filteredRecords.filterIsInstance<MedicationRecord>()
                    drawMedicationContent(pageContext, casted)
                }
            }

            pageContext.finishPage()

            val fileName = "CareMemo_${category.name}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out -> document.writeTo(out) }

            if (!password.isNullOrEmpty()) {
                encryptPdf(file, password)
            }

            withContext(Dispatchers.Main) {
                shareFile(context, file)
            }
        } finally {
            document.close()
        }
    }

    /**
     * 健康記録系のコンテンツ（グラフ＋テーブル）を描画する。
     */
    private fun drawHealthContent(
        pageContext: PdfPageContext,
        category: Category,
        records: List<Any>,
        order: ExportOrder
    ) {
        val (globalMinX, globalMaxX) = HealthChartHelper.calculateGlobalXRange(records)
        
        // 1. グラフ描画 (時系列順)
        val sortedForGraph = records.filterIsInstance<HistoryRecord>().sortedBy { it.recordTime }
        if (sortedForGraph.isNotEmpty()) {
            repeat(HealthChartHelper.getGraphCount(category)) { index ->
                val config = HealthChartHelper.getChartConfig(pageContext.context, category, index, records)
                if (config != null && config.dataList.any { it.points.isNotEmpty() }) {
                    val graphHeight = if (category == Category.BP_AND_PULSE && index == 0) 170f else layoutSpec.SINGLE_GRAPH_HEIGHT
                    pageContext.ensureSpace(graphHeight + 30f)
                    pageContext.currentY = drawSingleGraphFromConfig(pageContext, config, graphHeight, globalMinX, globalMaxX)
                    pageContext.currentY += 15f
                }
            }
            pageContext.currentY += 15f
        }

        // 2. テーブル描画
        val displayRecords = if (order == ExportOrder.NEWEST_FIRST) {
            records.filterIsInstance<HistoryRecord>().sortedByDescending { it.recordTime }
        } else {
            records.filterIsInstance<HistoryRecord>().sortedBy { it.recordTime }
        }

        when (category) {
            Category.HEIGHT_AND_WEIGHT -> drawHeightAndWeightTable(pageContext, displayRecords.filterIsInstance<HeightAndWeight>())
            Category.BP_AND_PULSE -> drawBpAndPulseTable(pageContext, displayRecords.filterIsInstance<BpAndPulse>())
            Category.GLUCOSE_AND_HBA1C -> drawGlucoseAndHbA1cTable(pageContext, displayRecords.filterIsInstance<GlucoseAndHbA1c>())
            else -> {}
        }
    }

    /**
     * 所見メモのコンテンツを描画する。
     */
    private fun drawConditionContent(
        pageContext: PdfPageContext,
        records: List<ConditionAtVisit>,
        allPhotos: List<ConditionPhoto>
    ) {
        val attrPaint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_BODY; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_BODY; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val captionPaint = Paint().apply { color = Color.DKGRAY; textSize = styleSpec.FONT_SIZE_CAPTION; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val bgPaint = Paint().apply { color = colorSpec.BACKGROUND_LIGHT; style = Paint.Style.FILL }
        
        val contentWidth = layoutSpec.PAGE_WIDTH - (layoutSpec.MARGIN * 2)
        val photoMaxCount = ConstraintSpecifications.Condition.Photo.MAX_COUNT
        val photoSize = (contentWidth - 20f) / photoMaxCount.toFloat()

        records.forEach { record ->
            val photos = allPhotos.filter { it.conditionId == record.id }.take(photoMaxCount)
            val memoLines = splitTextIntoLines(record.condition ?: "", bodyPaint, contentWidth - 10f)
            
            // ブロックの高さを計算
            val blockHeight = 25f + (memoLines.size * layoutSpec.LINE_SPACING) + (if (photos.isNotEmpty()) photoSize + 25f else 0f) + 15f

            pageContext.ensureSpace(blockHeight)

            // ヘッダー（属性行）
            pageContext.canvas.drawRect(layoutSpec.MARGIN, pageContext.currentY - 12f, layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN, pageContext.currentY + 4f, bgPaint)
            val dateStr = formatRecordTime(record.recordTime)
            val attrText = "$dateStr (${record.author}) : ${record.title ?: ""}"
            pageContext.canvas.drawText(attrText, layoutSpec.MARGIN + 5f, pageContext.currentY, attrPaint)
            pageContext.currentY += 20f

            // 本文
            memoLines.forEach { line ->
                pageContext.canvas.drawText(line, layoutSpec.MARGIN + 10f, pageContext.currentY, bodyPaint)
                pageContext.currentY += layoutSpec.LINE_SPACING
            }

            // 写真
            if (photos.isNotEmpty()) {
                pageContext.currentY += 10f
                var currentX = layoutSpec.MARGIN + 5f
                photos.forEach { photo ->
                    val photoFile = ImageUtils.getPhotoFile(pageContext.context, photo.photoFileName)
                    if (photoFile.exists()) {
                        val bitmap = loadOptimizedBitmap(photoFile.absolutePath, photoSize.toInt())
                        if (bitmap != null) {
                            val rect = RectF(currentX, pageContext.currentY, currentX + photoSize - 5f, pageContext.currentY + photoSize - 5f)
                            drawBitmapCenterInside(pageContext.canvas, bitmap, rect)
                            pageContext.canvas.drawText(photo.caption, currentX + (photoSize / 2f), pageContext.currentY + photoSize + 5f, captionPaint)
                            bitmap.recycle()
                        }
                    }
                    currentX += photoSize
                }
                pageContext.currentY += photoSize + 15f
            }
            pageContext.currentY += 15f
        }
    }

    /**
     * 服薬記録のコンテンツを描画する（月間マトリックス）。
     */
    private fun drawMedicationContent(
        pageContext: PdfPageContext,
        records: List<MedicationRecord>
    ) {
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_HEADER; isFakeBoldText = true; isAntiAlias = true }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_TABLE_BODY; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val labelPaint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_TABLE_BODY; isFakeBoldText = true; isAntiAlias = true }
        val linePaint = Paint().apply { color = colorSpec.TABLE_LINE; strokeWidth = 0.5f; style = Paint.Style.STROKE }

        // ステータス記号
        val medColors = ExportSpecifications.Pdf.Colors.Medication
        val statusPaints = mapOf(
            2 to Paint().apply { color = medColors.STATUS_TAKEN; textSize = styleSpec.FONT_SIZE_MED_STATUS; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER },
            1 to Paint().apply { color = medColors.STATUS_ASSIST; textSize = styleSpec.FONT_SIZE_MED_STATUS; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER },
            0 to Paint().apply { color = medColors.STATUS_NONE; textSize = styleSpec.FONT_SIZE_MED_STATUS + 1f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        )

        val recordsByMonth = records.groupBy {
            val date = try { LocalDate.parse(it.dosageDate) } catch (_: Exception) { LocalDate.now() }
            YearMonth.from(date)
        }.toSortedMap(compareByDescending { it })

        val rowLabels = listOf(
            pageContext.context.getString(R.string.slot_morning),
            pageContext.context.getString(R.string.slot_lunch),
            pageContext.context.getString(R.string.slot_dinner),
            pageContext.context.getString(R.string.slot_bedtime)
        )
        val labelWidth = ExportSpecifications.Pdf.TableConfig.Medication.LABEL_WIDTH
        val colWidth = (layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN * 2 - labelWidth) / 31f
        val rowHeight = ExportSpecifications.Pdf.TableConfig.Medication.ROW_HEIGHT
        val tableHeight = rowHeight * 6 + 40f

        recordsByMonth.forEach { (yearMonth, monthRecords) ->
            pageContext.ensureSpace(tableHeight)

            // 月タイトル
            pageContext.canvas.drawText(formatYearMonthHeader(yearMonth), layoutSpec.MARGIN, pageContext.currentY, titlePaint)
            pageContext.currentY += 15f

            val startX = layoutSpec.MARGIN
            val tableTop = pageContext.currentY
            val daysInMonth = yearMonth.lengthOfMonth()
            
            // 土日背景
            val sunBg = Paint().apply { color = colorSpec.SUN_BACKGROUND; style = Paint.Style.FILL }
            val satBg = Paint().apply { color = colorSpec.SAT_BACKGROUND; style = Paint.Style.FILL }
            for (day in 1..daysInMonth) {
                val date = yearMonth.atDay(day)
                if (date.dayOfWeek == java.time.DayOfWeek.SUNDAY || date.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                    val x = startX + labelWidth + (day - 1) * colWidth
                    pageContext.canvas.drawRect(RectF(x, tableTop, x + colWidth, tableTop + rowHeight * 6), if (date.dayOfWeek == java.time.DayOfWeek.SUNDAY) sunBg else satBg)
                }
            }

            // グリッド
            for (i in 0..6) pageContext.canvas.drawLine(startX, tableTop + i * rowHeight, startX + labelWidth + colWidth * daysInMonth, tableTop + i * rowHeight, linePaint)
            pageContext.canvas.drawLine(startX, tableTop, startX, tableTop + rowHeight * 6, linePaint)
            pageContext.canvas.drawLine(startX + labelWidth, tableTop, startX + labelWidth, tableTop + rowHeight * 6, linePaint)
            for (i in 1..daysInMonth) {
                val x = startX + labelWidth + i * colWidth
                pageContext.canvas.drawLine(x, tableTop, x, tableTop + rowHeight * 6, linePaint)
            }

            // 日付・曜日
            for (day in 1..daysInMonth) {
                val x = startX + labelWidth + (day - 0.5f) * colWidth
                pageContext.canvas.drawText(day.toString(), x, tableTop + rowHeight * 0.65f, headerPaint)
                val date = yearMonth.atDay(day)
                val color = when(date.dayOfWeek) {
                    java.time.DayOfWeek.SUNDAY -> colorSpec.SUN_TEXT
                    java.time.DayOfWeek.SATURDAY -> colorSpec.SAT_TEXT
                    else -> Color.BLACK
                }
                pageContext.canvas.drawText(formatShortDayOfWeek(date), x, tableTop + rowHeight * 1.65f, Paint(headerPaint).apply { this.color = color })
            }

            // データ
            val mapped = monthRecords.associateBy { it.dosageDate to it.timeSlot }
            rowLabels.forEachIndexed { rowIndex, label ->
                val y = tableTop + (rowIndex + 2) * rowHeight
                pageContext.canvas.drawText(label, startX + 5f, y + rowHeight * 0.65f, labelPaint)
                for (day in 1..daysInMonth) {
                    val rec = mapped[yearMonth.atDay(day).toString() to rowIndex]
                    val x = startX + labelWidth + (day - 0.5f) * colWidth
                    val mark = when (rec?.status) { 2 -> "〇"; 1 -> "△"; 0 -> "×"; else -> "－" }
                    pageContext.canvas.drawText(mark, x, y + rowHeight * 0.7f, statusPaints[rec?.status] ?: bodyPaint)
                }
            }
            pageContext.currentY += rowHeight * 6 + 30f
        }
    }

    // --- テーブル描画の各論 (Health) ---

    private fun drawHeightAndWeightTable(ctx: PdfPageContext, records: List<HeightAndWeight>) {
        val hwSpec = ExportSpecifications.Pdf.TableConfig.HeightWeight
        val columns = listOf(
            TableColumn<HeightAndWeight>("日付", tableSpec.DATE_COL_WIDTH) { rec, _ ->
                "${formatDateShort(rec.recordTime)} ${formatTime(rec.recordTime)}"
            },
            TableColumn("${ctx.context.getString(R.string.health_label_height)}(cm)", hwSpec.HEIGHT_WIDTH) { rec, _ -> rec.height?.toString() ?: "---" },
            TableColumn("${ctx.context.getString(R.string.health_label_weight)}(kg)", hwSpec.WEIGHT_WIDTH) { rec, idx ->
                val prev = if (idx < records.size - 1) records[idx + 1] else null
                rec.weight?.let { cur ->
                    prev?.weight?.let { p ->
                        val df = cur - p
                        "%.1f (%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df))
                    } ?: cur.toString()
                } ?: "---"
            },
            TableColumn(ctx.context.getString(R.string.health_label_bmi), hwSpec.BMI_WIDTH) { rec, _ ->
                val bmi = rec.calculateBMI()
                if (bmi > 0) "%.1f".format(bmi) else "---"
            },
            TableColumn(
                header = ctx.context.getString(R.string.health_label_status),
                width = tableSpec.STATUS_COL_WIDTH_BASE,
                getBackgroundColor = { rec -> HealthDisplayMapper.getPdfBgColor(rec.getBmiResult(ctx.context).second) }
            ) { rec, _ -> rec.getBmiResult(ctx.context).first }
        )
        drawGenericTable(ctx, records, columns)
    }

    private fun drawBpAndPulseTable(ctx: PdfPageContext, records: List<BpAndPulse>) {
        val bpSpec = ExportSpecifications.Pdf.TableConfig.BpPulse
        val columns = listOf(
            TableColumn<BpAndPulse>("日付", tableSpec.DATE_COL_WIDTH) { rec, _ ->
                "${formatDateShort(rec.recordTime)} ${formatTime(rec.recordTime)}"
            },
            TableColumn(ctx.context.getString(R.string.health_label_systolic_short), bpSpec.SYS_WIDTH) { rec, _ -> rec.bpSystolic?.toString() ?: "---" },
            TableColumn(ctx.context.getString(R.string.health_label_diastolic_short), bpSpec.DIA_WIDTH) { rec, _ -> rec.bpDiastolic?.toString() ?: "---" },
            TableColumn("SAT", bpSpec.SAT_WIDTH) { rec, _ -> rec.sat?.toString() ?: "---" },
            TableColumn(ctx.context.getString(R.string.health_label_pulse_short), bpSpec.PULSE_WIDTH) { rec, _ -> rec.pulse?.toString() ?: "---" },
            TableColumn(ctx.context.getString(R.string.health_label_body_temp), bpSpec.TEMP_WIDTH) { rec, _ -> rec.bodyTemperature?.let { "%.1f".format(it) } ?: "---" },
            TableColumn(
                header = ctx.context.getString(R.string.health_label_status),
                width = 170f,
                getBackgroundColor = { rec -> HealthDisplayMapper.getPdfBgColor(rec.getWorstAlertLevel()) }
            ) { rec, _ ->
                val results = rec.getVitalResults(ctx.context)
                if (results.all { it.second == NORMAL }) ctx.context.getString(R.string.vital_label_normal)
                else results.filter { it.second != NORMAL }.joinToString("・") { it.first }
            }
        )
        drawGenericTable(ctx, records, columns)
    }

    private fun drawGlucoseAndHbA1cTable(ctx: PdfPageContext, records: List<GlucoseAndHbA1c>) {
        val glSpec = ExportSpecifications.Pdf.TableConfig.Glucose
        val columns = listOf(
            TableColumn<GlucoseAndHbA1c>("日付", tableSpec.DATE_COL_WIDTH) { rec, _ ->
                "${formatDateShort(rec.recordTime)} ${formatTime(rec.recordTime)}"
            },
            TableColumn("${ctx.context.getString(R.string.health_label_glucose)}(mg/dL)", glSpec.GLUCOSE_WIDTH) { rec, idx ->
                val pr = if (idx < records.size - 1) records[idx + 1] else null
                rec.glucose?.let { cur -> pr?.glucose?.let { p -> "$cur(${if (cur-p >= 0) "+${cur-p}" else cur-p})" } ?: cur.toString() } ?: "---"
            },
            TableColumn("${ctx.context.getString(R.string.health_label_hba1c)}(%)", glSpec.HBA1C_WIDTH) { rec, idx ->
                val pr = if (idx < records.size - 1) records[idx + 1] else null
                rec.hba1c?.let { cur -> pr?.hba1c?.let { p -> val df = cur-p; "%.1f(%s)".format(cur, if (df >= 0) "+%.1f".format(df) else "%.1f".format(df)) } ?: "%.1f".format(cur) } ?: "---"
            },
            TableColumn(
                header = ctx.context.getString(R.string.health_label_status),
                width = 155f,
                getBackgroundColor = { rec -> HealthDisplayMapper.getPdfBgColor(rec.getWorstAlertLevel()) }
            ) { rec, _ -> rec.getCombinedResultText(ctx.context) }
        )
        drawGenericTable(ctx, records, columns)
    }

    // --- 共通部品とユーティリティ ---

    @Suppress("SameReturnValue")
    private fun drawHeader(ctx: PdfPageContext): Float {
        val paint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_PAGE_TITLE; isFakeBoldText = true }
        
        // PDFは外部共有前提のため、アプリの設定に関わらず常にマスキングを適用する
        val name = ctx.person.getMaskedName(isEnabled = true)

        val date = DateTimeUtils.getCurrentPhotoCaption()
        ctx.canvas.drawText(ctx.context.getString(R.string.pdf_title, ctx.context.getString(ctx.category.displayNameRes)), layoutSpec.MARGIN, 50f, paint)
        paint.textSize = styleSpec.FONT_SIZE_HEADER; paint.isFakeBoldText = false
        ctx.canvas.drawText(ctx.context.getString(R.string.pdf_user_name, name), layoutSpec.MARGIN, 80f, paint)
        ctx.canvas.drawText(ctx.context.getString(R.string.pdf_output_date, date), layoutSpec.MARGIN, 100f, paint)
        ctx.canvas.drawText(ctx.context.getString(R.string.pdf_page_number, ctx.pageNumber), layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN - 50f, 100f, paint)
        ctx.canvas.drawLine(layoutSpec.MARGIN, 110f, layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN, 110f, paint)
        return layoutSpec.HEADER_HEIGHT
    }

    private data class TableColumn<T>(val header: String, val width: Float, val getBackgroundColor: ((T) -> Int?)? = null, val getValue: (T, Int) -> String)

    private fun <T> drawGenericTable(ctx: PdfPageContext, records: List<T>, columns: List<TableColumn<T>>) {
        val paint = Paint().apply { color = Color.BLACK; textSize = styleSpec.FONT_SIZE_TABLE_BODY; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val hp = Paint().apply { color = Color.BLACK; isFakeBoldText = true; textSize = styleSpec.FONT_SIZE_TABLE_HEADER; isAntiAlias = true }

        fun drawHeaderRow() {
            var cx = layoutSpec.MARGIN
            columns.forEach { col -> ctx.canvas.drawText(col.header, cx, ctx.currentY, hp); cx += col.width }
            ctx.currentY += 5f
            ctx.canvas.drawLine(layoutSpec.MARGIN, ctx.currentY, layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN, ctx.currentY, paint)
            ctx.currentY += 20f
        }

        drawHeaderRow()
        records.forEachIndexed { index, record ->
            ctx.ensureSpace(20f)
            if (ctx.currentY < layoutSpec.HEADER_HEIGHT + 10f) drawHeaderRow() // 改ページ直後の場合ヘッダー再描画

            columns.firstNotNullOfOrNull { it.getBackgroundColor?.invoke(record) }?.let { color ->
                ctx.canvas.drawRect(RectF(layoutSpec.MARGIN, ctx.currentY - 12f, layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN, ctx.currentY + 4f), Paint().apply { this.color = color })
            }
            var cx = layoutSpec.MARGIN
            columns.forEach { col -> ctx.canvas.drawText(col.getValue(record, index), cx, ctx.currentY, paint); cx += col.width }
            ctx.currentY += 20f
        }
    }

    private fun drawSingleGraphFromConfig(ctx: PdfPageContext, config: HealthChartConfig, height: Float, minX: Double?, maxX: Double?): Float {
        val lineDataList = config.dataList.map { line -> (line.points.map { it.x.toLong() to it.y }) to line.color.toArgb() }
        val ranges = config.ranges.map { (it.startValue to it.endValue) to it.color.toArgb() }
        val limits = config.limits.map { Triple(it.value, DashPathEffect(floatArrayOf(5f, 5f), 0f), it.label) }
        return drawSingleGraph(ctx.canvas, "${config.title} 推移", lineDataList, ctx.currentY, height, config.stepY, ranges, limits, !config.showDecimal, config.getSubtitleLines(), config.dataList.firstOrNull()?.unit ?: "", minX, maxX)
    }

    private fun drawSingleGraph(canvas: Canvas, title: String, lineDataList: List<Pair<List<Pair<Long, Double>>, Int>>, startY: Float, height: Float, yStep: Double, ranges: List<Pair<Pair<Double, Double>, Int>>, limitLines: List<Triple<Double, DashPathEffect, String>>, isInteger: Boolean, subtitles: List<String>, unit: String, fixedMinX: Double?, fixedMaxX: Double?): Float {
        val paint = Paint().apply { isAntiAlias = true }
        paint.color = Color.BLACK; paint.textSize = 10f; paint.isFakeBoldText = true
        canvas.drawText(if (unit.isNotEmpty()) "$title ($unit)" else title, layoutSpec.MARGIN, startY + 10f, paint)
        var currentSubY = startY + 22f
        paint.isFakeBoldText = false; paint.textSize = 8f; paint.color = Color.DKGRAY
        subtitles.forEach { canvas.drawText(it, layoutSpec.MARGIN, currentSubY, paint); currentSubY += 12f }
        val graphTop = if (subtitles.isEmpty()) startY + 20f else currentSubY + 5f
        val graphArea = RectF(layoutSpec.MARGIN + 35f, graphTop, layoutSpec.PAGE_WIDTH - layoutSpec.MARGIN - 10f, graphTop + height - 20f)
        paint.color = Color.rgb(240, 240, 240); paint.style = Paint.Style.FILL
        canvas.drawRect(graphArea, paint)
        if (lineDataList.all { it.first.isEmpty() } && (fixedMinX == null || fixedMaxX == null)) {
            paint.color = Color.LTGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f
            canvas.drawRect(graphArea, paint); return graphArea.bottom
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
        for (i in 0..4) {
            val yVal = minYVal + (yRange / 4) * i
            val yPos = graphArea.bottom - (i.toFloat() / 4) * graphArea.height()
            canvas.drawText(if (isInteger) yVal.toInt().toString() else "%.1f".format(yVal), layoutSpec.MARGIN, yPos + 3f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f; paint.alpha = 40
            canvas.drawLine(graphArea.left, yPos, graphArea.right, yPos, paint)
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }
        val minX = fixedMinX ?: (lineDataList.flatMap { it.first }.minOfOrNull { it.first }?.toDouble() ?: 0.0)
        val maxX = fixedMaxX ?: (lineDataList.flatMap { it.first }.maxOfOrNull { it.first }?.toDouble() ?: 0.0)
        val xRange = if (maxX == minX) 1.0 else maxX - minX
        val xLabelPaint = Paint().apply { color = Color.DKGRAY; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        for (i in 0..3) {
            val xTime = minX + (xRange / 3.0) * i
            val xPos = graphArea.left + (i.toFloat() / 3.0f) * graphArea.width()
            canvas.drawText(formatDateShort(Instant.ofEpochMilli(xTime.toLong())), xPos, graphArea.bottom + 12f, xLabelPaint)
        }
        lineDataList.forEach { (records, color) ->
            val path = Path(); paint.color = color; paint.strokeWidth = 1.5f; paint.style = Paint.Style.STROKE
            records.forEachIndexed { idx, rec ->
                val px = graphArea.left + ((rec.first - minX) / xRange).toFloat() * graphArea.width()
                val py = graphArea.bottom - ((rec.second - minYVal) / yRange).toFloat() * graphArea.height()
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
            records.forEach { rec ->
                val px = graphArea.left + ((rec.first - minX) / xRange).toFloat() * graphArea.width()
                val py = graphArea.bottom - ((rec.second - minYVal) / yRange).toFloat() * graphArea.height()
                canvas.drawCircle(px, py, 2f, paint)
                val textPaint = Paint().apply { this.color = Color.BLACK; textSize = 7f; isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
                canvas.drawText(if (isInteger) rec.second.toInt().toString() else "%.1f".format(rec.second), px, py - 6f, textPaint)
            }
        }
        return graphArea.bottom + 15f
    }

    private fun loadOptimizedBitmap(path: String, maxSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var inSampleSize = 1
        if (options.outHeight > maxSize || options.outWidth > maxSize) {
            val halfHeight = options.outHeight / 2; val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= maxSize && halfWidth / inSampleSize >= maxSize) inSampleSize *= 2
        }
        options.inJustDecodeBounds = false; options.inSampleSize = inSampleSize
        return BitmapFactory.decodeFile(path, options)
    }

    private fun drawBitmapCenterInside(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val scale = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val left = rect.left + (rect.width() - w) / 2f; val top = rect.top + (rect.height() - h) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    @Suppress("SameParameterValue")
    private fun splitTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) { result.add(""); continue }
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                result.add(remaining.substring(0, count))
                remaining = remaining.substring(count)
            }
        }
        return result
    }

    private fun filterAnyRecords(category: Category, records: List<Any>, range: ExportRange, customStart: Instant?, customEnd: Instant?): List<Any> {
        if (range == ExportRange.ALL) return records
        val items = records.filterIsInstance<HistoryRecord>()
        if (items.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val getEffectiveTime: (HistoryRecord) -> Instant = { rec -> if (rec is MedicationRecord) { try { LocalDate.parse(rec.dosageDate).atStartOfDay(zone).toInstant() } catch (_: Exception) { rec.recordTime } } else rec.recordTime }
        val sortedByEffective = items.sortedByDescending { getEffectiveTime(it) }
        if (range == ExportRange.LATEST) {
            val latest = sortedByEffective.firstOrNull() ?: return emptyList()
            return if (category == Category.CONDITION_AT_VISIT) {
                // 所見メモの場合は、最新の「年月日」の全データを対象とする
                val latestDate = latest.recordTime.atZone(zone).toLocalDate()
                items.filter { it.recordTime.atZone(zone).toLocalDate() == latestDate }
            } else {
                listOf(latest)
            }
        }
        val startInclusive: Instant?; val endInclusive: Instant?
        if (range == ExportRange.CUSTOM) {
            startInclusive = customStart?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()?.atStartOfDay(zone)?.toInstant()
            endInclusive = customEnd?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()?.atTime(23, 59, 59, 999_999_999)?.atZone(zone)?.toInstant()
        } else {
            // カレンダーベース（月初から）の範囲計算
            val today = LocalDate.now(zone)
            startInclusive = when (range) {
                ExportRange.ONE_MONTH -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant() // 当月
                ExportRange.THREE_MONTHS -> today.minusMonths(2).withDayOfMonth(1).atStartOfDay(zone).toInstant() // 過去3ヶ月（今月含め）
                ExportRange.SIX_MONTHS -> today.minusMonths(5).withDayOfMonth(1).atStartOfDay(zone).toInstant() // 過去6ヶ月（今月含め）
                else -> null
            }
            endInclusive = null
        }
        return items.filter { val t = getEffectiveTime(it); (startInclusive == null || !t.isBefore(startInclusive)) && (endInclusive == null || !t.isAfter(endInclusive)) }
    }

    private fun encryptPdf(file: File, password: String) {
        PDDocument.load(file).use { document ->
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            document.save(file)
        }
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.pdf_share_chooser_title))
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            throw IOException("PDFファイルを共有できるアプリが見つかりません。", e)
        }
    }

    fun clearOldExports(context: Context) {
        try { context.cacheDir.listFiles()?.forEach { if (it.name.startsWith("CareMemo") && it.name.endsWith(".pdf")) it.delete() } } catch (e: Exception) { e.printStackTrace() }
    }
}
