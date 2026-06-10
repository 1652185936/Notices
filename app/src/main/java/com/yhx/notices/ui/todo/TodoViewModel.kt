package com.yhx.notices.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.local.entity.TodoEntity
import com.yhx.notices.data.repository.TodoRepository
import com.yhx.notices.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TodoGroups(
    val overdue: List<TodoEntity> = emptyList(),
    val today: List<TodoEntity> = emptyList(),
    val upcoming: List<TodoEntity> = emptyList(),
    val noDate: List<TodoEntity> = emptyList(),
    val done: List<TodoEntity> = emptyList(),
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repo: TodoRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val groups: StateFlow<TodoGroups> = combine(
        repo.observeActive(), repo.observeDone()
    ) { active, done ->
        val now = System.currentTimeMillis()
        val endOfToday = endOfToday()
        TodoGroups(
            overdue = active.filter { it.dueAt != null && it.dueAt < now },
            today = active.filter { it.dueAt != null && it.dueAt in now..endOfToday },
            upcoming = active.filter { it.dueAt != null && it.dueAt > endOfToday },
            noDate = active.filter { it.dueAt == null },
            done = done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodoGroups())

    fun add(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { repo.add(content.trim()) }
    }

    fun toggle(todo: TodoEntity) {
        viewModelScope.launch { repo.setDone(todo.id, !todo.isDone) }
    }

    fun delete(todo: TodoEntity) {
        viewModelScope.launch { scheduler.cancel(todo.id); repo.delete(todo.id) }
    }

    fun setReminder(todo: TodoEntity, triggerAt: Long) {
        viewModelScope.launch {
            repo.setDue(todo.id, triggerAt)
            scheduler.schedule(todo.id, todo.content, triggerAt)
        }
    }

    private fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
