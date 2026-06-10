package com.yhx.notices.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yhx.notices.data.local.entity.AttachmentEntity
import com.yhx.notices.data.local.entity.NoteTagCrossRef
import com.yhx.notices.data.local.entity.ReminderEntity
import com.yhx.notices.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: NoteTagCrossRef)

    @Delete
    suspend fun detach(ref: NoteTagCrossRef)

    @Query(
        """SELECT tags.* FROM tags JOIN note_tag_cross_ref r ON r.tagId = tags.id
           WHERE r.noteId = :noteId ORDER BY tags.name"""
    )
    suspend fun tagsOfNote(noteId: Long): List<TagEntity>
}

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    suspend fun getByNote(noteId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    suspend fun activeReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE targetType = :type AND targetId = :targetId")
    suspend fun byTarget(type: String, targetId: Long): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
