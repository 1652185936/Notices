package com.yhx.notices.ui.canvas

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.export.ExportManager
import com.yhx.notices.data.repository.AttachmentRepository
import com.yhx.notices.data.repository.NoteRepository
import com.yhx.notices.domain.canvas.CanvasContent
import com.yhx.notices.domain.canvas.CanvasElement
import com.yhx.notices.domain.canvas.ImageElement
import com.yhx.notices.domain.canvas.StrokeElement
import com.yhx.notices.domain.canvas.TextElement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CanvasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepo: NoteRepository,
    private val attachmentRepo: AttachmentRepository,
    private val exportManager: ExportManager,
) : ViewModel() {

    val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: -1L

    var title by mutableStateOf("")
        private set
    val elements: SnapshotStateList<CanvasElement> = mutableStateListOf()

    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var background by mutableStateOf("blank"); private set

    /** 内容修订号：元素/纸张任何变化 +1，供渲染层缓存失效判断。 */
    var revision by mutableStateOf(0L); private set

    private val undoStack = ArrayDeque<List<CanvasElement>>()
    private val redoStack = ArrayDeque<List<CanvasElement>>()
    private var dirty = false
    private var saveJob: Job? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val note = noteRepo.getNote(noteId)
            title = note?.title ?: ""
            val content = noteRepo.getCanvas(noteId)
            background = content.background
            elements.clear()
            elements.addAll(content.elements)
            revision++
        }
    }

    /** 设置纸张样式（blank/grid/lines/dots）。 */
    fun changeBackground(style: String) {
        background = style
        markDirty()
    }

    private fun pushUndo() {
        undoStack.addLast(elements.toList())
        if (undoStack.size > 80) undoStack.removeFirst()
        canUndo = true
        redoStack.clear()
        canRedo = false
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(elements.toList())
        canRedo = true
        elements.clear(); elements.addAll(prev)
        canUndo = undoStack.isNotEmpty()
        markDirty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(elements.toList())
        canUndo = true
        elements.clear(); elements.addAll(next)
        canRedo = redoStack.isNotEmpty()
        markDirty()
    }

    fun onTitleChange(t: String) { title = t; markDirty() }

    fun addStroke(stroke: StrokeElement) {
        pushUndo()
        elements.add(stroke)
        markDirty()
    }

    fun addSticker(emoji: String, x: Float, y: Float) {
        pushUndo()
        elements.add(TextElement(x = x, y = y, text = emoji, fontSize = 96f))
        markDirty()
    }

    fun addText(x: Float, y: Float): String {
        pushUndo()
        val t = TextElement(x = x, y = y, text = "")
        elements.add(t)
        markDirty()
        return t.id
    }

    fun updateText(id: String, text: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = elements[i] as? TextElement ?: return
        elements[i] = t.copy(text = text)
        markDirty()
    }

    fun moveElement(id: String, dx: Float, dy: Float) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        when (val el = elements[i]) {
            is TextElement -> elements[i] = el.copy(x = el.x + dx, y = el.y + dy)
            is ImageElement -> elements[i] = el.copy(x = el.x + dx, y = el.y + dy)
            else -> {}
        }
        markDirty()
    }

    /** 橡皮：移除与擦除路径相交的笔迹。 */
    fun eraseStrokes(erasePoints: List<Float>, radius: Float) {
        if (erasePoints.size < 2) return
        val r2 = radius * radius
        val toRemove = elements.filterIsInstance<StrokeElement>().filter { s ->
            var hit = false
            var i = 0
            while (i + 1 < s.points.size && !hit) {
                var j = 0
                while (j + 1 < erasePoints.size) {
                    val dx = s.points[i] - erasePoints[j]
                    val dy = s.points[i + 1] - erasePoints[j + 1]
                    if (dx * dx + dy * dy <= r2) { hit = true; break }
                    j += 2
                }
                i += 2
            }
            hit
        }
        if (toRemove.isNotEmpty()) {
            pushUndo()
            elements.removeAll(toRemove.toSet())
            markDirty()
        }
    }

    /**
     * 局部（像素）擦除：沿橡皮路径 erasePoints（扁平 x,y），把每条笔迹被覆盖的点切除，
     * 连续保留段各生成一个新 StrokeElement（同步切片 points 与 widths），从中间断开成多段。
     * 完全没被擦到的笔迹保持原对象不动（避免缓存抖动）；整条擦光的直接消失。整次拖动一次撤销。
     */
    fun erasePixels(erasePoints: List<Float>, radius: Float) {
        if (erasePoints.size < 2) return
        var changed = false
        val newList = ArrayList<CanvasElement>(elements.size)
        for (el in elements) {
            if (el !is StrokeElement) { newList.add(el); continue }
            val n = el.points.size / 2
            if (n == 0) { newList.add(el); continue }
            val hasWidths = el.widths.size == n
            val reach = radius + el.width / 2f
            // 逐点判断是否被橡皮路径覆盖
            val erased = BooleanArray(n)
            var any = false
            for (i in 0 until n) {
                val px = el.points[i * 2]
                val py = el.points[i * 2 + 1]
                if (distToPolyline(px, py, erasePoints) <= reach) { erased[i] = true; any = true }
            }
            if (!any) { newList.add(el); continue } // 未被擦到，原对象保留
            changed = true
            // 把连续保留的点切成多段 run
            var i = 0
            while (i < n) {
                if (erased[i]) { i++; continue }
                var j = i
                while (j < n && !erased[j]) j++
                val len = j - i
                if (len >= 2) {
                    val segPts = ArrayList<Float>(len * 2)
                    val segWs = if (hasWidths) ArrayList<Float>(len) else null
                    for (k in i until j) {
                        segPts.add(el.points[k * 2])
                        segPts.add(el.points[k * 2 + 1])
                        segWs?.add(el.widths[k])
                    }
                    newList.add(
                        el.copy(
                            id = com.yhx.notices.domain.canvas.newId(),
                            points = segPts,
                            widths = segWs ?: emptyList(),
                        )
                    )
                }
                i = j
            }
        }
        if (changed) {
            pushUndo()
            elements.clear(); elements.addAll(newList)
            markDirty()
        }
    }

    /** 套索：按 id 集合整体平移（笔迹平移所有点）。 */
    fun moveElementsBy(ids: Set<String>, dx: Float, dy: Float) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val i = elements.indexOfFirst { it.id == id }
            if (i < 0) return@forEach
            elements[i] = when (val el = elements[i]) {
                is StrokeElement -> el.copy(points = el.points.mapIndexed { idx, v -> if (idx % 2 == 0) v + dx else v + dy })
                is TextElement -> el.copy(x = el.x + dx, y = el.y + dy)
                is ImageElement -> el.copy(x = el.x + dx, y = el.y + dy)
                else -> el
            }
        }
        markDirty()
    }

    /** 在世界坐标 atY 处插入纵向空白：其下方元素整体下移 amount。 */
    fun insertVerticalSpace(atY: Float, amount: Float) {
        pushUndo()
        for (i in elements.indices) {
            val el = elements[i]
            val minY = when (el) {
                is StrokeElement -> { var m = Float.MAX_VALUE; var j = 1; while (j < el.points.size) { m = minOf(m, el.points[j]); j += 2 }; if (m == Float.MAX_VALUE) null else m }
                is TextElement -> el.y
                is ImageElement -> el.y
                else -> null
            }
            if (minY != null && minY >= atY) {
                elements[i] = when (el) {
                    is StrokeElement -> el.copy(points = el.points.mapIndexed { idx, v -> if (idx % 2 == 1) v + amount else v })
                    is TextElement -> el.copy(y = el.y + amount)
                    is ImageElement -> el.copy(y = el.y + amount)
                    else -> el
                }
            }
        }
        markDirty()
    }

    fun deleteElements(ids: Set<String>) {
        if (ids.isEmpty()) return
        pushUndo()
        elements.removeAll { it.id in ids }
        markDirty()
    }

    /** 复制选中元素（偏移 +40），返回副本 id 集合。 */
    fun duplicateElements(ids: Set<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        pushUndo()
        val copies = elements.filter { it.id in ids }.map { el ->
            when (el) {
                is StrokeElement -> el.copy(id = com.yhx.notices.domain.canvas.newId(), points = el.points.map { it + 40f })
                is TextElement -> el.copy(id = com.yhx.notices.domain.canvas.newId(), x = el.x + 40f, y = el.y + 40f)
                is ImageElement -> el.copy(id = com.yhx.notices.domain.canvas.newId(), x = el.x + 40f, y = el.y + 40f)
                else -> el
            }
        }
        elements.addAll(copies)
        markDirty()
        return copies.map { it.id }.toSet()
    }

    fun deleteElement(id: String) {
        pushUndo()
        elements.removeAll { it.id == id }
        markDirty()
    }

    fun insertImage(uri: Uri, x: Float, y: Float) {
        viewModelScope.launch {
            ensureSaved()
            val attachmentId = attachmentRepo.importImage(noteId, uri)
            val file = attachmentRepo.resolvePath(attachmentId)
            val (w, h) = file?.let { imageSize(it.absolutePath) } ?: (600f to 400f)
            pushUndo()
            elements.add(ImageElement(x = x, y = y, width = w, height = h, attachmentId = attachmentId))
            markDirty()
        }
    }

    /** 多图导入：逐张导入并级联偏移排列，整批一次撤销。 */
    fun insertImages(uris: List<Uri>, baseX: Float, baseY: Float) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            ensureSaved()
            val added = ArrayList<ImageElement>(uris.size)
            uris.forEachIndexed { i, uri ->
                val attachmentId = attachmentRepo.importImage(noteId, uri)
                val file = attachmentRepo.resolvePath(attachmentId)
                val (w, h) = file?.let { imageSize(it.absolutePath) } ?: (600f to 400f)
                added.add(
                    ImageElement(
                        x = baseX + i * 36f,
                        y = baseY + i * 36f,
                        width = w,
                        height = h,
                        attachmentId = attachmentId,
                    )
                )
            }
            if (added.isNotEmpty()) {
                pushUndo()
                elements.addAll(added)
                markDirty()
            }
        }
    }

    /** PDF 导入：系统 PdfRenderer（无 GMS）逐页转图，纵向铺排，可在其上书写批注。整份一次撤销。 */
    fun importPdf(uri: Uri, baseX: Float, baseY: Float) {
        viewModelScope.launch {
            ensureSaved()
            val pageWidth = 760f
            val added = ArrayList<ImageElement>()
            withContext(Dispatchers.IO) {
                val bitmaps = attachmentRepo.renderPdfToBitmaps(uri)
                var y = baseY
                for (bmp in bitmaps) {
                    val attachmentId = attachmentRepo.importBitmap(noteId, bmp)
                    val ratio = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                    val h = pageWidth * ratio
                    added.add(
                        ImageElement(
                            x = baseX,
                            y = y,
                            width = pageWidth,
                            height = h,
                            attachmentId = attachmentId,
                        )
                    )
                    y += h + 24f
                    bmp.recycle()
                }
            }
            if (added.isNotEmpty()) {
                pushUndo()
                elements.addAll(added)
                markDirty()
            }
        }
    }

    suspend fun attachmentPath(id: Long): String? = attachmentRepo.resolvePath(id)?.absolutePath

    private fun exportName() = (title.ifBlank { "无界笔记" }) + "_" + System.currentTimeMillis()

    private fun content() = CanvasContent(background = background, elements = elements.toList())

    fun exportImageToGallery(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderCanvas(content())
            onDone(exportManager.saveToGallery(bmp, exportName()) != null)
        }
    }

    fun shareAsImage(onUri: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderCanvas(content())
            onUri(exportManager.shareImage(bmp, exportName()))
        }
    }

    fun exportPdf(onUri: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderCanvas(content())
            onUri(exportManager.bitmapToPdf(bmp, exportName()))
        }
    }

    private fun imageSize(path: String): Pair<Float, Float> {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        val w = opts.outWidth.coerceAtLeast(1)
        val h = opts.outHeight.coerceAtLeast(1)
        val maxW = 900f
        val scale = if (w > maxW) maxW / w else 1f
        return w * scale to h * scale
    }

    /** 点 (px,py) 到橡皮路径（扁平 x,y 折线）的最近距离；单点路径退化为点距。 */
    private fun distToPolyline(px: Float, py: Float, poly: List<Float>): Float {
        if (poly.size < 2) return Float.MAX_VALUE
        if (poly.size == 2) {
            val dx = px - poly[0]; val dy = py - poly[1]
            return kotlin.math.hypot(dx, dy)
        }
        var best = Float.MAX_VALUE
        var i = 0
        while (i + 3 < poly.size) {
            val ax = poly[i]; val ay = poly[i + 1]
            val bx = poly[i + 2]; val by = poly[i + 3]
            val vx = bx - ax; val vy = by - ay
            val len2 = vx * vx + vy * vy
            val t = if (len2 <= 1e-6f) 0f else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
            val dx = px - (ax + vx * t)
            val dy = py - (ay + vy * t)
            val d = kotlin.math.hypot(dx, dy)
            if (d < best) best = d
            i += 2
        }
        return best
    }

    private fun markDirty() {
        revision++
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000)
            persist()
        }
    }

    private suspend fun ensureSaved() {
        saveJob?.cancel()
        if (dirty) persist()
    }

    private suspend fun persist() {
        noteRepo.saveCanvas(noteId, title, CanvasContent(background = background, elements = elements.toList()))
        dirty = false
    }

    fun onExit() {
        viewModelScope.launch {
            saveJob?.cancel()
            if (title.isBlank() && elements.isEmpty()) {
                noteRepo.purge(listOf(noteId))
            } else if (dirty) persist()
        }
    }
}
