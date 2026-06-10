package com.yhx.notices.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yhx.notices.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val noteCount: Int,
)

@Dao
interface FolderDao {

    @Query(
        """SELECT folders.*, COUNT(notes.id) AS noteCount FROM folders
           LEFT JOIN notes ON notes.folderId = folders.id AND notes.deletedAt IS NULL
           GROUP BY folders.id ORDER BY folders.sortOrder, folders.createdAt"""
    )
    fun observeFoldersWithCount(): Flow<List<FolderWithCount>>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}
