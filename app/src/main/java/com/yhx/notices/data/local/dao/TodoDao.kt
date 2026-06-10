package com.yhx.notices.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yhx.notices.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query(
        """SELECT * FROM todos WHERE deletedAt IS NULL AND isDone = 0 AND parentId IS NULL
           ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt, sortOrder"""
    )
    fun observeActive(): Flow<List<TodoEntity>>

    @Query(
        """SELECT * FROM todos WHERE deletedAt IS NULL AND isDone = 1 AND parentId IS NULL
           ORDER BY doneAt DESC LIMIT 100"""
    )
    fun observeDone(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND parentId = :parentId ORDER BY sortOrder")
    fun observeChildren(parentId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND isDone = 0 AND dueAt IS NOT NULL")
    suspend fun activeWithDue(): List<TodoEntity>

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Query("UPDATE todos SET isDone = :done, doneAt = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)

    @Query("UPDATE todos SET deletedAt = :now WHERE id = :id OR parentId = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT * FROM todos")
    suspend fun getAllForBackup(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForBackup(todo: TodoEntity)
}
