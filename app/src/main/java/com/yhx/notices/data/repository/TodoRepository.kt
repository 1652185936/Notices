package com.yhx.notices.data.repository

import com.yhx.notices.data.local.dao.TodoDao
import com.yhx.notices.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
) {
    fun observeActive(): Flow<List<TodoEntity>> = todoDao.observeActive()
    fun observeDone(): Flow<List<TodoEntity>> = todoDao.observeDone()

    suspend fun add(content: String, dueAt: Long? = null): Long =
        todoDao.insert(TodoEntity(content = content, dueAt = dueAt))

    suspend fun setDone(id: Long, done: Boolean) =
        todoDao.setDone(id, done, if (done) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = todoDao.softDelete(id, System.currentTimeMillis())

    suspend fun get(id: Long): TodoEntity? = todoDao.getById(id)

    suspend fun update(todo: TodoEntity) = todoDao.update(todo)
}
