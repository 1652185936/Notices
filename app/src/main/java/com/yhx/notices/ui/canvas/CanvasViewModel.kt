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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var background by mutableStateOf("blank"); private set

    private val undoStack = ArrayDeque<List<CanvasElement>>()
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
        }
    }

    fun cycleBackground() {
        val order = listOf("blank", "grid", "lines", "dots")
        background = order[(order.indexOf(background) + 1) % order.size]
        markDirty()
    }

    private fun pushUndo() {
        undoStack.addLast(elements.toList())
        if (undoStack.size > 80) undoStack.removeFirst()
        canUndo = true
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        elements.clear(); elements.addAll(prev)
        canUndo = undoStack.isNotEmpty()
        markDirty()
    }

    fun onTitleChange(t: String) { title = t; markDirty() }

    fun addStroke(stroke: StrokeElement) {
        pushUndo()
        elements.add(stroke)
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

    private fun markDirty() {
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
