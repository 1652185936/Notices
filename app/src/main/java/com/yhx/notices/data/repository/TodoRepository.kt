package com.yhx.notices.data.repository

import com.yhx.notices.data.local.dao.TodoDao
import com.yhx.notices.data.local.entity.TodoEntity
import com.yhx.notices.domain.model.RepeatRule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
) {
    fun observeActive(): Flow<List<TodoEntity>> = todoDao.observeActive()
    fun observeDone(): Flow<List<TodoEntity>> = todoDao.observeDone()
    fun observeSubtasks(): Flow<List<TodoEntity>> = todoDao.observeSubtasks()

    suspend fun add(content: String, dueAt: Long? = null, repeat: RepeatRule = RepeatRule.NONE): Long =
        todoDao.insert(TodoEntity(content = content, dueAt = dueAt, repeatRule = repeat))

    suspend fun addSubtask(parentId: Long, content: String): Long =
        todoDao.insert(TodoEntity(parentId = parentId, content = content))

    suspend fun observeActiveOnce(): List<TodoEntity> = todoDao.activeWithDue()

    suspend fun setDue(id: Long, dueAt: Long?) {
        val todo = todoDao.getById(id) ?: return
        todoDao.update(todo.copy(dueAt = dueAt))
    }

    suspend fun updateTodo(id: String, content: String, dueAt: Long?, repeat: RepeatRule) {
        val todo = todoDao.getById(id.toLongOrNull() ?: return) ?: return
        todoDao.update(todo.copy(content = content, dueAt = dueAt, repeatRule = repeat))
    }

    suspend fun setDone(id: Long, done: Boolean) =
        todoDao.setDone(id, done, if (done) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = todoDao.softDelete(id, System.currentTimeMillis())

    suspend fun get(id: Long): TodoEntity? = todoDao.getById(id)

    suspend fun update(todo: TodoEntity) = todoDao.update(todo)
}
