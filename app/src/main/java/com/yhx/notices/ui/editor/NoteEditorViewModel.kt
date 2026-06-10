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
import com.yhx.notices.data.repository.AttachmentRepository
import com.yhx.notices.data.repository.NoteRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepo: NoteRepository,
    private val attachmentRepo: AttachmentRepository,
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

    private var loaded: Note? = null
    private var dirty = false
    private var saveJob: Job? = null

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastTextSnapshotAt = 0L

    private data class Snapshot(val title: String, val blocks: List<Block>)

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val note = noteRepo.getNote(noteId)
            if (note != null) {
                loaded = note
                title = note.title
                isPinned = note.isPinned
                isFavorite = note.isFavorite
                blocks.clear()
                blocks.addAll(note.content.blocks)
                focusedBlockId = blocks.firstOrNull()?.id
            }
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
