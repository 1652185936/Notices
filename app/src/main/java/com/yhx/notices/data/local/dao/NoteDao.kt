package com.yhx.notices.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yhx.notices.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        """SELECT * FROM notes WHERE deletedAt IS NULL
           AND (:folderId IS NULL OR folderId = :folderId)
           ORDER BY isPinned DESC,
           CASE WHEN :sort = 'created' THEN createdAt ELSE updatedAt END DESC"""
    )
    fun observeNotes(folderId: Long?, sort: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        """SELECT n.* FROM notes n JOIN notes_fts f ON n.id = f.rowid
           WHERE notes_fts MATCH :query AND n.deletedAt IS NULL
           ORDER BY n.updatedAt DESC"""
    )
    suspend fun searchFts(query: String): List<NoteEntity>

    @Query(
        """SELECT * FROM notes WHERE deletedAt IS NULL
           AND (title LIKE '%' || :kw || '%' OR plainText LIKE '%' || :kw || '%')
           ORDER BY updatedAt DESC LIMIT 200"""
    )
    suspend fun searchLike(kw: String): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("UPDATE notes SET deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun purge(ids: List<Long>)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeExpired(before: Long): Int

    @Query("UPDATE notes SET isPinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE notes SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE notes SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)
}
