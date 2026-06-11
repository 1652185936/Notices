package com.yhx.notices.data.repository

import com.yhx.notices.data.local.dao.TagDao
import com.yhx.notices.data.local.entity.NoteTagCrossRef
import com.yhx.notices.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
) {
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeTags()

    suspend fun create(name: String, color: Int): Long = tagDao.insert(TagEntity(name = name, color = color))

    suspend fun tagsOfNote(noteId: Long): List<TagEntity> = tagDao.tagsOfNote(noteId)

    suspend fun attach(noteId: Long, tagId: Long) = tagDao.attach(NoteTagCrossRef(noteId, tagId))

    suspend fun detach(noteId: Long, tagId: Long) = tagDao.detach(NoteTagCrossRef(noteId, tagId))

    suspend fun delete(tag: TagEntity) = tagDao.delete(tag)
}
