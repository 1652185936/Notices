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
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        canvas.translate(-minX + m, -minY + m)
        content.elements.forEach { el ->
            when (el) {
                is StrokeElement -> {
                    val paint = Paint().apply {
                        color = el.color; strokeWidth = el.width; isAntiAlias = true
                        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    }
                    val path = android.graphics.Path()
                    if (el.points.size >= 2) {
                        path.moveTo(el.points[0], el.points[1])
                        var i = 2; while (i + 1 < el.points.size) { path.lineTo(el.points[i], el.points[i + 1]); i += 2 }
                    }
                    canvas.drawPath(path, paint)
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
