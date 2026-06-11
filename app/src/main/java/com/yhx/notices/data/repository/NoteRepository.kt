package com.yhx.notices.data.repository

import com.yhx.notices.data.local.dao.AttachmentDao
import com.yhx.notices.data.local.dao.NoteDao
import com.yhx.notices.data.local.entity.NoteEntity
import com.yhx.notices.domain.model.Note
import com.yhx.notices.domain.model.NoteListItem
import com.yhx.notices.domain.model.NoteSort
import com.yhx.notices.domain.richtext.ContentDerive
import com.yhx.notices.domain.richtext.ImageBlock
import com.yhx.notices.domain.richtext.NoteContent
import com.yhx.notices.domain.richtext.RichTextJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val crypto: NoteCrypto,
) {
    fun observeNotes(folderId: Long?, sort: NoteSort): Flow<List<NoteListItem>> =
        noteDao.observeNotes(folderId, sort.key()).map { list -> list.map { it.toListItem() } }

    fun observeFavorites(): Flow<List<NoteListItem>> =
        noteDao.observeFavorites().map { list -> list.map { it.toListItem() } }

    fun observeTrash(): Flow<List<NoteListItem>> =
        noteDao.observeTrash().map { list -> list.map { it.toListItem() } }

    suspend fun getNote(id: Long): Note? {
        val e = noteDao.getById(id) ?: return null
        val raw = if (e.isEncrypted) crypto.decrypt(e.contentJson) else e.contentJson
        val content = RichTextJson.decode(raw ?: "") ?: NoteContent.empty()
        return Note(
            id = e.id, folderId = e.folderId, title = e.title, content = content,
            isPinned = e.isPinned, isFavorite = e.isFavorite, isEncrypted = e.isEncrypted,
            isTodoNote = e.isTodoNote, skin = e.skin,
            createdAt = e.createdAt, updatedAt = e.updatedAt,
        )
    }

    /** 新建空笔记，返回其 id。 */
    suspend fun createNote(folderId: Long?, isTodoNote: Boolean = false, isCanvas: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val contentJson = if (isCanvas)
            com.yhx.notices.domain.canvas.CanvasJson.encode(com.yhx.notices.domain.canvas.CanvasContent())
        else RichTextJson.encode(NoteContent.empty())
        return noteDao.insert(
            NoteEntity(
                folderId = folderId, isTodoNote = isTodoNote, isCanvas = isCanvas,
                contentJson = contentJson,
                createdAt = now, updatedAt = now,
            )
        )
    }

    suspend fun getCanvas(id: Long): com.yhx.notices.domain.canvas.CanvasContent {
        val e = noteDao.getById(id) ?: return com.yhx.notices.domain.canvas.CanvasContent()
        return com.yhx.notices.domain.canvas.CanvasJson.decode(e.contentJson)
    }

    suspend fun saveCanvas(id: Long, title: String, content: com.yhx.notices.domain.canvas.CanvasContent) {
        val e = noteDao.getById(id) ?: return
        val texts = content.elements
            .filterIsInstance<com.yhx.notices.domain.canvas.TextElement>()
            .joinToString(" ") { it.text }
        noteDao.update(
            e.copy(
                title = title,
                contentJson = com.yhx.notices.domain.canvas.CanvasJson.encode(content),
                plainText = texts,
                updatedAt = System.currentTimeMillis(),
                syncDirty = true,
            )
        )
    }

    /** 保存：序列化 + 派生 plainText/首图 + （可选）加密。 */
    suspend fun saveNote(note: Note) {
        val existing = noteDao.getById(note.id) ?: return
        val encrypted = note.isEncrypted
        val json = RichTextJson.encode(note.content)
        val storedJson = if (encrypted) crypto.encrypt(json) else json
        val firstImagePath = firstImagePath(note)
        noteDao.update(
            existing.copy(
                folderId = note.folderId,
                title = note.title,
                contentJson = storedJson,
                plainText = if (encrypted) "" else ContentDerive.plainText(note.content),
                excerptImagePath = firstImagePath,
                isPinned = note.isPinned,
                isFavorite = note.isFavorite,
                isEncrypted = encrypted,
                isTodoNote = note.isTodoNote,
                skin = note.skin,
                updatedAt = System.currentTimeMillis(),
                syncDirty = true,
            )
        )
    }

    private suspend fun firstImagePath(note: Note): String? {
        val imageBlock = note.content.blocks.filterIsInstance<ImageBlock>().firstOrNull() ?: return null
        return attachmentDao.getById(imageBlock.attachmentId)?.fileName
    }

    suspend fun isEncrypted(id: Long): Boolean = noteDao.getById(id)?.isEncrypted == true

    /** 切换加密：解密/重新加密内容并落库。 */
    suspend fun setEncrypted(id: Long, encrypt: Boolean) {
        val e = noteDao.getById(id) ?: return
        if (e.isEncrypted == encrypt) return
        val plainJson = if (e.isEncrypted) (crypto.decrypt(e.contentJson) ?: return) else e.contentJson
        val storedJson = if (encrypt) crypto.encrypt(plainJson) else plainJson
        val content = RichTextJson.decode(plainJson) ?: NoteContent.empty()
        noteDao.update(
            e.copy(
                contentJson = storedJson,
                isEncrypted = encrypt,
                plainText = if (encrypt) "" else ContentDerive.plainText(content),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun moveToTrash(ids: List<Long>) = noteDao.softDelete(ids, System.currentTimeMillis())
    suspend fun restore(ids: List<Long>) = noteDao.restore(ids)
    suspend fun purge(ids: List<Long>) = noteDao.purge(ids)
    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = noteDao.setPinned(ids, pinned)
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = noteDao.setFavorite(ids, favorite)
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) = noteDao.moveToFolder(ids, folderId)

    suspend fun search(query: String): List<NoteListItem> {
        if (query.isBlank()) return emptyList()
        val fts = runCatching { noteDao.searchFts(sanitizeFts(query)) }.getOrDefault(emptyList())
        val combined = if (fts.isEmpty()) noteDao.searchLike(query) else fts
        return combined.map { it.toListItem() }
    }

    private fun sanitizeFts(q: String): String =
        q.trim().split(Regex("\\s+")).joinToString(" ") { "$it*" }

    private fun NoteEntity.toListItem() = NoteListItem(
        id = id,
        title = title.ifBlank { plainText.lineSequence().firstOrNull().orEmpty() }.ifBlank { "无标题" },
        excerpt = if (isEncrypted) "" else plainText.replace("\n", " ").take(120),
        firstImagePath = excerptImagePath,
        isPinned = isPinned, isFavorite = isFavorite, isEncrypted = isEncrypted,
        isTodoNote = isTodoNote, isCanvas = isCanvas, skin = skin, updatedAt = updatedAt,
    )

    private fun NoteSort.key() = when (this) {
        NoteSort.CREATED -> "created"
        else -> "updated"
    }
}
