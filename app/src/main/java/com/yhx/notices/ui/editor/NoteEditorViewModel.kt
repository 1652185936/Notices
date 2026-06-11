package com.yhx.notices.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.export.ExportManager
import com.yhx.notices.data.repository.AttachmentRepository
import com.yhx.notices.data.repository.AudioEngine
import com.yhx.notices.data.repository.NoteRepository
import com.yhx.notices.data.repository.OcrEngine
import com.yhx.notices.data.repository.TagRepository
import com.yhx.notices.domain.model.Note
import com.yhx.notices.domain.richtext.Block
import com.yhx.notices.domain.richtext.BlockOps
import com.yhx.notices.domain.richtext.ChecklistBlock
import com.yhx.notices.domain.richtext.ContentDerive
import com.yhx.notices.domain.richtext.ImageBlock
import com.yhx.notices.domain.richtext.NoteContent
import com.yhx.notices.domain.richtext.Span
import com.yhx.notices.domain.richtext.SpanOps
import com.yhx.notices.domain.richtext.TextBlock
import com.yhx.notices.domain.richtext.TextKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioInfo(val durationMs: Long)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepo: NoteRepository,
    private val attachmentRepo: AttachmentRepository,
    private val audioEngine: AudioEngine,
    private val exportManager: ExportManager,
    private val ocrEngine: OcrEngine,
    private val tagRepo: TagRepository,
) : ViewModel() {

    val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: -1L

    var title by mutableStateOf("")
        private set
    val blocks: SnapshotStateList<Block> = mutableStateListOf()

    var focusedBlockId by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf(TextRange.Zero)
        private set
    var stickyStyles by mutableStateOf<Set<String>>(emptySet())
        private set

    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    var isPinned by mutableStateOf(false); private set
    var isFavorite by mutableStateOf(false); private set
    var isRecording by mutableStateOf(false); private set
    var skin by mutableStateOf("default"); private set

    private var loaded: Note? = null
    private var dirty = false
    private var saveJob: Job? = null

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastTextSnapshotAt = 0L

    private data class Snapshot(val title: String, val blocks: List<Block>)

    var isLocked by mutableStateOf(false); private set
    var encrypted by mutableStateOf(false); private set

    init { boot() }

    private fun boot() {
        viewModelScope.launch {
            encrypted = noteRepo.isEncrypted(noteId)
            if (encrypted) isLocked = true else loadContent()
        }
    }

    val tags = mutableStateListOf<com.yhx.notices.data.local.entity.TagEntity>()
    val allTags: kotlinx.coroutines.flow.StateFlow<List<com.yhx.notices.data.local.entity.TagEntity>> =
        tagRepo.observeTags().stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private fun reloadTags() {
        viewModelScope.launch { tags.clear(); tags.addAll(tagRepo.tagsOfNote(noteId)) }
    }

    fun addTag(tagId: Long) {
        viewModelScope.launch { tagRepo.attach(noteId, tagId); reloadTags() }
    }

    fun removeTag(tagId: Long) {
        viewModelScope.launch { tagRepo.detach(noteId, tagId); reloadTags() }
    }

    fun createAndAddTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = tagRepo.create(name.trim(), 0xFF007DFF.toInt())
            if (id > 0) tagRepo.attach(noteId, id)
            reloadTags()
        }
    }

    fun unlock() {
        isLocked = false
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            val note = noteRepo.getNote(noteId) ?: return@launch
            loaded = note
            title = note.title
            isPinned = note.isPinned
            isFavorite = note.isFavorite
            encrypted = note.isEncrypted
            skin = note.skin
            blocks.clear()
            blocks.addAll(note.content.blocks)
            focusedBlockId = blocks.firstOrNull()?.id
            reloadTags()
        }
    }

    fun cycleSkin() {
        val order = listOf("default", "lines", "grid", "dots")
        skin = order[(order.indexOf(skin).coerceAtLeast(0) + 1) % order.size]
        loaded = loaded?.copy(skin = skin)
        markDirty()
    }

    fun toggleEncryption() {
        viewModelScope.launch {
            ensureSaved()
            val target = !encrypted
            noteRepo.setEncrypted(noteId, target)
            encrypted = target
            loaded = loaded?.copy(isEncrypted = target)
        }
    }

    // ---------- 撤销 / 重做 ----------
    private fun snapshot() = Snapshot(title, blocks.toList())

    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        refreshUndoState()
    }

    private fun pushUndoCoalesced() {
        val now = System.currentTimeMillis()
        if (now - lastTextSnapshotAt > 600) {
            pushUndo()
            lastTextSnapshotAt = now
        }
    }

    private fun refreshUndoState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(prev); refreshUndoState(); markDirty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next); refreshUndoState(); markDirty()
    }

    private fun restore(s: Snapshot) {
        title = s.title
        blocks.clear(); blocks.addAll(s.blocks)
    }

    // ---------- 编辑操作 ----------
    fun onTitleChange(new: String) {
        if (new == title) return
        pushUndoCoalesced()
        title = new
        markDirty()
    }

    fun onFocus(blockId: String, sel: TextRange) {
        focusedBlockId = blockId
        selection = sel
        stickyStyles = emptySet()
    }

    fun onSelectionChange(sel: TextRange) { selection = sel }

    fun onTextChange(blockId: String, newText: String, newSelection: TextRange) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = blocks[index]
        val oldSpans = spansOf(block) ?: return
        if (SpanOps.text(oldSpans) == newText) {
            selection = newSelection
            return
        }
        pushUndoCoalesced()
        val newSpans = SpanOps.applyTextChange(oldSpans, newText, stickyStyles)
        blocks[index] = withSpans(block, newSpans)
        selection = newSelection
        stickyStyles = emptySet()

        // Markdown 式自动转换
        (blocks[index] as? TextBlock)?.let { tb ->
            BlockOps.autoConvert(tb)?.let { converted ->
                blocks[index] = converted
                selection = TextRange.Zero
            }
        }
        markDirty()
    }

    /** 在当前焦点块光标处插入文字（语音转写用）。 */
    fun insertText(text: String) {
        if (text.isEmpty()) return
        val blockId = focusedBlockId ?: blocks.firstOrNull()?.id ?: return
        val i = blocks.indexOfFirst { it.id == blockId }
        if (i < 0) return
        val spans = spansOf(blocks[i]) ?: return
        val cur = SpanOps.text(spans)
        val at = selection.min.coerceIn(0, cur.length)
        val newText = cur.substring(0, at) + text + cur.substring(at)
        pushUndo()
        blocks[i] = withSpans(blocks[i], SpanOps.applyTextChange(spans, newText, stickyStyles))
        selection = TextRange(at + text.length)
        markDirty()
    }

    fun applyStyle(style: String) {
        val blockId = focusedBlockId ?: return
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = blocks[index]
        val spans = spansOf(block) ?: return
        if (selection.collapsed) {
            // 粘性样式切换
            stickyStyles = if (stickyStyles.contains(style)) stickyStyles - style else stickyStyles + style
            return
        }
        pushUndo()
        val newSpans = SpanOps.applyStyle(spans, selection.min, selection.max, style)
        blocks[index] = withSpans(block, newSpans)
        markDirty()
    }

    fun isStyleActive(style: String): Boolean {
        val blockId = focusedBlockId ?: return false
        val block = blocks.firstOrNull { it.id == blockId } ?: return false
        val spans = spansOf(block) ?: return false
        return if (selection.collapsed) stickyStyles.contains(style)
        else SpanOps.hasStyle(spans, selection.min, selection.max, style)
    }

    fun changeKind(kind: TextKind) {
        val blockId = focusedBlockId ?: return
        pushUndo()
        val newBlocks = BlockOps.changeKind(blocks.toList(), blockId, kind)
        replaceAll(newBlocks); markDirty()
    }

    fun toggleChecklistKind() {
        val blockId = focusedBlockId ?: return
        pushUndo()
        replaceAll(BlockOps.toChecklist(blocks.toList(), blockId)); markDirty()
    }

    fun onEnter(blockId: String, offset: Int) {
        pushUndo()
        val r = BlockOps.splitBlock(blocks.toList(), blockId, offset)
        replaceAll(r.blocks)
        focusedBlockId = r.focusBlockId
        selection = TextRange(r.focusOffset)
        markDirty()
    }

    fun onBackspaceAtStart(blockId: String) {
        pushUndo()
        val r = BlockOps.backspaceAtStart(blocks.toList(), blockId)
        replaceAll(r.blocks)
        focusedBlockId = r.focusBlockId
        selection = TextRange(r.focusOffset)
        markDirty()
    }

    fun toggleChecked(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = blocks[index] as? ChecklistBlock ?: return
        pushUndo()
        blocks[index] = block.copy(checked = !block.checked)
        markDirty()
    }

    fun removeBlock(blockId: String) {
        pushUndo()
        replaceAll(BlockOps.removeBlock(blocks.toList(), blockId)); markDirty()
    }

    fun insertDivider() {
        pushUndo()
        val anchor = focusedBlockId ?: blocks.lastOrNull()?.id
        val divider = com.yhx.notices.domain.richtext.DividerBlock()
        val newBlocks = if (anchor != null) BlockOps.insertAfter(blocks.toList(), anchor, divider)
        else blocks.toList() + divider
        replaceAll(newBlocks + TextBlock(kind = TextKind.PARAGRAPH))
        markDirty()
    }

    fun insertTable() {
        pushUndo()
        val anchor = focusedBlockId ?: blocks.lastOrNull()?.id
        val table = com.yhx.notices.domain.richtext.TableBlock(
            rows = listOf(listOf("", ""), listOf("", ""))
        )
        val newBlocks = if (anchor != null) BlockOps.insertAfter(blocks.toList(), anchor, table)
        else blocks.toList() + table
        replaceAll(newBlocks + TextBlock(kind = TextKind.PARAGRAPH))
        markDirty()
    }

    fun updateTableCell(blockId: String, row: Int, col: Int, value: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val table = blocks[index] as? com.yhx.notices.domain.richtext.TableBlock ?: return
        val newRows = table.rows.mapIndexed { r, cells ->
            if (r == row) cells.mapIndexed { c, v -> if (c == col) value else v } else cells
        }
        blocks[index] = table.copy(rows = newRows)
        markDirty()
    }

    fun tableAddRow(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val table = blocks[index] as? com.yhx.notices.domain.richtext.TableBlock ?: return
        val cols = table.rows.firstOrNull()?.size ?: 2
        pushUndo()
        blocks[index] = table.copy(rows = table.rows + listOf(List(cols) { "" }))
        markDirty()
    }

    fun tableAddColumn(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val table = blocks[index] as? com.yhx.notices.domain.richtext.TableBlock ?: return
        pushUndo()
        blocks[index] = table.copy(rows = table.rows.map { it + "" })
        markDirty()
    }

    // ---------- 录音 ----------
    fun startRecording() {
        if (audioEngine.startRecording()) isRecording = true
    }

    fun stopRecordingAndInsert() {
        val result = audioEngine.stopRecording()
        isRecording = false
        if (result != null) {
            viewModelScope.launch {
                ensureSaved()
                val (file, dur) = result
                val attachmentId = attachmentRepo.saveAudio(noteId, file, dur)
                pushUndo()
                val anchor = focusedBlockId ?: blocks.lastOrNull()?.id
                val audio = com.yhx.notices.domain.richtext.AudioBlock(attachmentId = attachmentId)
                val newBlocks = if (anchor != null) BlockOps.insertAfter(blocks.toList(), anchor, audio)
                else blocks.toList() + audio
                replaceAll(newBlocks + TextBlock(kind = TextKind.PARAGRAPH))
                markDirty()
            }
        }
    }

    fun cancelRecording() {
        audioEngine.stopRecording()
        isRecording = false
    }

    suspend fun audioInfo(id: Long): AudioInfo = AudioInfo(attachmentRepo.audioDuration(id))

    fun playAudio(id: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            val f = attachmentRepo.resolvePath(id)
            if (f != null && f.exists()) audioEngine.play(f, onComplete) else onComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopPlayback()
    }

    fun insertSketch(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            ensureSaved()
            val attachmentId = attachmentRepo.saveSketch(noteId, bitmap)
            pushUndo()
            val anchor = focusedBlockId ?: blocks.lastOrNull()?.id
            val sketch = com.yhx.notices.domain.richtext.SketchBlock(attachmentId = attachmentId)
            val newBlocks = if (anchor != null) {
                BlockOps.insertAfter(blocks.toList(), anchor, sketch)
            } else blocks.toList() + sketch
            replaceAll(newBlocks + TextBlock(kind = TextKind.PARAGRAPH))
            markDirty()
        }
    }

    fun insertImage(uri: Uri) {
        viewModelScope.launch {
            ensureSaved()
            val attachmentId = attachmentRepo.importImage(noteId, uri)
            pushUndo()
            val anchor = focusedBlockId ?: blocks.lastOrNull()?.id
            val newBlocks = if (anchor != null) {
                BlockOps.insertAfter(blocks.toList(), anchor, ImageBlock(attachmentId = attachmentId))
            } else {
                blocks.toList() + ImageBlock(attachmentId = attachmentId)
            }
            // 图片后补一个空段落便于继续输入
            val withTrailing = newBlocks + TextBlock(kind = TextKind.PARAGRAPH)
            replaceAll(withTrailing)
            markDirty()
        }
    }

    // ---------- 保存 ----------
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
        val base = loaded ?: return
        val content = NoteContent(blocks = blocks.toList())
        noteRepo.saveNote(
            base.copy(title = title, content = content)
        )
        dirty = false
    }

    /** 退出页面时调用：保存或丢弃空笔记。 */
    fun onExit() {
        viewModelScope.launch {
            saveJob?.cancel()
            val content = NoteContent(blocks = blocks.toList())
            if (title.isBlank() && ContentDerive.isEmpty(content)) {
                noteRepo.purge(listOf(noteId)) // 空笔记自动丢弃
            } else if (dirty) {
                persist()
            }
        }
    }

    fun togglePin() {
        val note = loaded ?: return
        isPinned = !isPinned
        loaded = note.copy(isPinned = isPinned)
        viewModelScope.launch { noteRepo.setPinned(listOf(noteId), isPinned) }
    }

    fun toggleFavorite() {
        val note = loaded ?: return
        isFavorite = !isFavorite
        loaded = note.copy(isFavorite = isFavorite)
        viewModelScope.launch { noteRepo.setFavorite(listOf(noteId), isFavorite) }
    }

    fun deleteNote(onDone: () -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            noteRepo.moveToTrash(listOf(noteId))
            onDone()
        }
    }

    // ---------- 导出 / 分享 ----------
    private fun exportName() =
        (title.ifBlank { "笔记" }) + "_" + System.currentTimeMillis()

    fun exportImageToGallery(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderPaged(title, NoteContent(blocks = blocks.toList()))
            onDone(exportManager.saveToGallery(bmp, exportName()) != null)
        }
    }

    fun shareAsImage(onUri: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderPaged(title, NoteContent(blocks = blocks.toList()))
            onUri(exportManager.shareImage(bmp, exportName()))
        }
    }

    fun exportPdf(onUri: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            ensureSaved()
            val bmp = exportManager.renderPaged(title, NoteContent(blocks = blocks.toList()))
            onUri(exportManager.bitmapToPdf(bmp, exportName()))
        }
    }

    fun ocr(path: String, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(ocrEngine.recognize(path)) }
    }

    suspend fun attachmentPath(id: Long): String? =
        attachmentRepo.resolvePath(id)?.absolutePath

    private fun replaceAll(newBlocks: List<Block>) {
        blocks.clear(); blocks.addAll(newBlocks)
    }

    private fun spansOf(b: Block): List<Span>? = when (b) {
        is TextBlock -> b.spans
        is ChecklistBlock -> b.spans
        else -> null
    }

    private fun withSpans(b: Block, spans: List<Span>): Block = when (b) {
        is TextBlock -> b.copy(spans = spans)
        is ChecklistBlock -> b.copy(spans = spans)
        else -> b
    }
}
