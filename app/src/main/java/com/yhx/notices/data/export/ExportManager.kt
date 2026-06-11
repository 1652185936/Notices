package com.yhx.notices.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.yhx.notices.data.repository.AttachmentRepository
import com.yhx.notices.domain.canvas.CanvasContent
import com.yhx.notices.domain.canvas.ImageElement
import com.yhx.notices.domain.canvas.StrokeElement
import com.yhx.notices.domain.canvas.TextElement
import com.yhx.notices.domain.richtext.ChecklistBlock
import com.yhx.notices.domain.richtext.DividerBlock
import com.yhx.notices.domain.richtext.ImageBlock
import com.yhx.notices.domain.richtext.NoteContent
import com.yhx.notices.domain.richtext.SpanOps
import com.yhx.notices.domain.richtext.TableBlock
import com.yhx.notices.domain.richtext.TextBlock
import com.yhx.notices.domain.richtext.TextKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 导出渲染：分页笔记 / 无界笔记 → 长图 / PDF；保存相册或分享。见 docs/01 N-18。 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentRepo: AttachmentRepository,
) {
    private val pageWidth = 1080
    private val pad = 48f

    // ---------- 分页笔记 → 长图 ----------
    suspend fun renderPaged(title: String, content: NoteContent): Bitmap = withContext(Dispatchers.Default) {
        val contentWidth = (pageWidth - pad * 2).toInt()
        val ops = ArrayList<(Canvas, Float) -> Float>()

        fun textPaint(size: Float, bold: Boolean = false): TextPaint = TextPaint().apply {
            isAntiAlias = true; color = 0xFF182431.toInt(); textSize = size
            if (bold) isFakeBoldText = true
        }

        fun addText(text: String, size: Float, bold: Boolean) {
            ops.add { canvas, y ->
                val layout = StaticLayout.Builder
                    .obtain(text.ifEmpty { " " }, 0, text.ifEmpty { " " }.length, textPaint(size, bold), contentWidth)
                    .build()
                canvas.save(); canvas.translate(pad, y); layout.draw(canvas); canvas.restore()
                y + layout.height + 12f
            }
        }

        if (title.isNotBlank()) addText(title, 56f, true)
        ops.add { _, y -> y + 8f }

        for (block in content.blocks) {
            when (block) {
                is TextBlock -> {
                    val t = SpanOps.text(block.spans)
                    val size = when (block.kind) {
                        TextKind.H1 -> 48f; TextKind.H2 -> 40f; TextKind.H3 -> 34f; else -> 30f
                    }
                    val prefix = when (block.kind) {
                        TextKind.BULLET -> "•  "; TextKind.NUMBERED -> "–  "; TextKind.QUOTE -> "丨  "; else -> ""
                    }
                    addText(prefix + t, size, block.kind in listOf(TextKind.H1, TextKind.H2, TextKind.H3))
                }
                is ChecklistBlock -> addText((if (block.checked) "☑  " else "☐  ") + SpanOps.text(block.spans), 30f, false)
                is ImageBlock -> {
                    val path = attachmentRepo.resolvePath(block.attachmentId)?.absolutePath
                    if (path != null) {
                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp != null) {
                            ops.add { canvas, y ->
                                val scale = contentWidth.toFloat() / bmp.width
                                val h = bmp.height * scale
                                val dst = android.graphics.RectF(pad, y, pad + contentWidth, y + h)
                                canvas.drawBitmap(bmp, null, dst, null)
                                y + h + 16f
                            }
                        }
                    }
                }
                is DividerBlock -> ops.add { canvas, y ->
                    canvas.drawLine(pad, y + 8f, pad + contentWidth, y + 8f, Paint().apply { color = 0x22000000 })
                    y + 24f
                }
                is TableBlock -> {
                    val text = block.rows.joinToString("\n") { it.joinToString("  |  ") }
                    addText(text, 28f, false)
                }
                else -> {}
            }
        }

        // 两遍：测高 + 绘制
        var total = pad
        val measureCanvas = Canvas(Bitmap.createBitmap(pageWidth, 4, Bitmap.Config.ARGB_8888))
        for (op in ops) total = op(measureCanvas, total)
        total += pad

        val bmp = Bitmap.createBitmap(pageWidth, total.toInt().coerceAtLeast(200), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = pad
        for (op in ops) y = op(canvas, y)
        bmp
    }

    // ---------- 无界笔记 → 图 ----------
    suspend fun renderCanvas(content: CanvasContent): Bitmap = withContext(Dispatchers.Default) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun acc(x: Float, y: Float) { minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y) }
        content.elements.forEach { el ->
            when (el) {
                is StrokeElement -> { var i = 0; while (i + 1 < el.points.size) { acc(el.points[i], el.points[i + 1]); i += 2 } }
                is TextElement -> { acc(el.x, el.y); acc(el.x + 200, el.y + el.fontSize) }
                is ImageElement -> { acc(el.x, el.y); acc(el.x + el.width, el.y + el.height) }
            }
        }
        if (minX == Float.MAX_VALUE) { minX = 0f; minY = 0f; maxX = 1080f; maxY = 720f }
        val m = 40f
        val w = (maxX - minX + m * 2).coerceAtLeast(100f)
        val h = (maxY - minY + m * 2).coerceAtLeast(100f)
        val maxDim = 3000f
        val scale = if (maxOf(w, h) > maxDim) maxDim / maxOf(w, h) else 1f
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(com.yhx.notices.domain.canvas.PaperStyles.paperBaseColor(content.background))
        canvas.scale(scale, scale)
        canvas.translate(-minX + m, -minY + m)
        // 世界坐标下绘制底纹（与屏幕端 drawCanvasBackground 一致），可见世界区 [minX-m..maxX+m]
        drawCanvasBackgroundNative(canvas, content.background, minX - m, minY - m, maxX + m, maxY + m)
        content.elements.forEach { el ->
            when (el) {
                is StrokeElement -> {
                    // 与画布端同一墨迹引擎：平滑变宽轮廓填充
                    val n = el.points.size / 2
                    val radii = if (el.widths.size == n) el.widths.map { it / 2f } else List(n) { el.width / 2f }
                    val outline = com.yhx.notices.domain.canvas.InkGeometry.strokeOutline(
                        el.points, radii, roundCaps = el.tool != "highlighter",
                    )
                    if (outline.size >= 6) {
                        val path = android.graphics.Path()
                        path.moveTo(outline[0], outline[1])
                        var i = 2; while (i + 1 < outline.size) { path.lineTo(outline[i], outline[i + 1]); i += 2 }
                        path.close()
                        if (el.tool == "highlighter") {
                            // 与屏幕端一致：Multiply 真叠色 + 两侧略深沉积边
                            val a = Color.alpha(el.color)
                            val fill = Paint().apply {
                                color = el.color; isAntiAlias = true; style = Paint.Style.FILL
                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
                            }
                            canvas.drawPath(path, fill)
                            val edgeA = (a + 31).coerceAtMost(255) // +0.12 alpha
                            val edge = Paint().apply {
                                color = (el.color and 0x00FFFFFF) or (edgeA shl 24)
                                isAntiAlias = true; style = Paint.Style.STROKE
                                strokeWidth = (el.width * 0.08f).coerceIn(0.6f, 2.2f)
                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
                            }
                            canvas.drawPath(path, edge)
                        } else {
                            val paint = Paint().apply { color = el.color; isAntiAlias = true; style = Paint.Style.FILL }
                            canvas.drawPath(path, paint)
                        }
                    }
                }
                is ImageElement -> {
                    val path = attachmentRepo.resolvePath(el.attachmentId)?.absolutePath
                    val ib = path?.let { BitmapFactory.decodeFile(it) }
                    if (ib != null) {
                        val dst = android.graphics.RectF(el.x, el.y, el.x + el.width, el.y + el.height)
                        canvas.drawBitmap(ib, null, dst, null)
                    }
                }
                is TextElement -> {
                    val paint = Paint().apply { color = el.color; textSize = el.fontSize; isAntiAlias = true }
                    canvas.drawText(el.text, el.x, el.y + el.fontSize, paint)
                }
            }
        }
        bmp
    }

    /** 世界坐标下绘制纸张底纹（与 CanvasScreen.drawCanvasBackground 一致），导出所见即所得。 */
    private fun drawCanvasBackgroundNative(
        canvas: Canvas, style: String, x0: Float, y0: Float, x1: Float, y1: Float,
    ) {
        if (style == "blank") return
        val P = com.yhx.notices.domain.canvas.PaperStyles
        val line = Paint().apply { color = P.LINE; isAntiAlias = true; strokeWidth = 1f }
        val major = Paint().apply { color = P.LINE_MAJOR; isAntiAlias = true; strokeWidth = 1f }
        val margin = Paint().apply { color = P.MARGIN_RED; isAntiAlias = true; strokeWidth = 1.5f }
        val dot = Paint().apply { color = P.LINE; isAntiAlias = true; this.style = Paint.Style.FILL }
        val sp = P.BASE_SPACING
        val big = P.GRID_SPACING

        fun firstAtOrAfter(coord: Float, step: Float): Float =
            Math.ceil((coord / step).toDouble()).toFloat() * step
        fun hLines(step: Float, p: Paint, lx0: Float = x0, lx1: Float = x1) {
            var y = firstAtOrAfter(y0, step); while (y <= y1) { canvas.drawLine(lx0, y, lx1, y, p); y += step }
        }
        fun vLines(step: Float, p: Paint, ly0: Float = y0, ly1: Float = y1) {
            var x = firstAtOrAfter(x0, step); while (x <= x1) { canvas.drawLine(x, ly0, x, ly1, p); x += step }
        }

        when (style) {
            "grid" -> { hLines(sp, line); vLines(sp, line) }
            "lines" -> hLines(sp, line)
            "cornell" -> { hLines(sp, line); vLines(sp * 5f, major) }
            "dots", "cream-dots" -> {
                var y = firstAtOrAfter(y0, sp)
                while (y <= y1) {
                    var x = firstAtOrAfter(x0, sp)
                    while (x <= x1) { canvas.drawCircle(x, y, 2f, dot); x += sp }
                    y += sp
                }
            }
            "legal" -> {
                hLines(sp, line)
                vLines(big, margin)  // 每 96 一条暖色竖边距线（无限画布无单页概念）
            }
            "graph" -> {
                hLines(sp, line); vLines(sp, line)
                hLines(big, major); vLines(big, major)
            }
            "tianzige" -> {
                hLines(big, line); vLines(big, line)
                val dash = Paint().apply {
                    color = (P.MARGIN_RED and 0x00FFFFFF) or 0x30000000
                    isAntiAlias = true; strokeWidth = 1f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 8f), 0f)
                }
                val half = big / 2f
                var hy = firstAtOrAfter(y0 - half, big) + half
                while (hy <= y1) { canvas.drawLine(x0, hy, x1, hy, dash); hy += big }
                var vx = firstAtOrAfter(x0 - half, big) + half
                while (vx <= x1) { canvas.drawLine(vx, y0, vx, y1, dash); vx += big }
            }
            "staff" -> {
                val ln = P.STAFF_LINE
                val group = ln * 4f + P.STAFF_GROUP_GAP
                var top = firstAtOrAfter(y0, group) - group
                while (top <= y1) {
                    for (k in 0..4) { val y = top + k * ln; if (y in y0..y1) canvas.drawLine(x0, y, x1, y, line) }
                    top += group
                }
            }
        }
    }

    // ---------- 输出 ----------
    fun saveToGallery(bmp: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Notices")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }

    fun bitmapToPdf(bmp: Bitmap, name: String): Uri? {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
        val page = doc.startPage(pageInfo)
        page.canvas.drawBitmap(bmp, 0f, 0f, null)
        doc.finishPage(page)
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "$name.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareImage(bmp: Bitmap, name: String): Uri? {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
