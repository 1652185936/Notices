package com.yhx.notices.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.local.entity.TodoEntity
import com.yhx.notices.data.repository.TodoRepository
import com.yhx.notices.domain.model.RepeatRule
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

data class TodoUiState(
    val groups: TodoGroups = TodoGroups(),
    val childrenByParent: Map<Long, List<TodoEntity>> = emptyMap(),
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repo: TodoRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<TodoUiState> = combine(
        repo.observeActive(), repo.observeDone(), repo.observeSubtasks()
    ) { active, done, subs ->
        val now = System.currentTimeMillis()
        val endOfToday = endOfToday()
        val groups = TodoGroups(
            overdue = active.filter { it.dueAt != null && it.dueAt < now },
            today = active.filter { it.dueAt != null && it.dueAt in now..endOfToday },
            upcoming = active.filter { it.dueAt != null && it.dueAt > endOfToday },
            noDate = active.filter { it.dueAt == null },
            done = done,
        )
        TodoUiState(groups, subs.groupBy { it.parentId!! })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodoUiState())

    fun add(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { repo.add(content.trim()) }
    }

    fun addSubtask(parentId: Long, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { repo.addSubtask(parentId, content.trim()) }
    }

    fun toggle(todo: TodoEntity) {
        viewModelScope.launch {
            val newDone = !todo.isDone
            repo.setDone(todo.id, newDone)
            // 重复待办完成 → 生成下一次
            if (newDone && todo.parentId == null && todo.repeatRule != RepeatRule.NONE && todo.dueAt != null) {
                val next = advance(todo.dueAt, todo.repeatRule)
                val newId = repo.add(todo.content, next, todo.repeatRule)
                scheduler.schedule(newId, todo.content, next)
            }
        }
    }

    fun updateTodo(todo: TodoEntity, content: String, dueAt: Long?, repeat: RepeatRule) {
        viewModelScope.launch {
            repo.updateTodo(todo.id.toString(), content, dueAt, repeat)
            scheduler.cancel(todo.id)
            if (dueAt != null && dueAt > System.currentTimeMillis()) scheduler.schedule(todo.id, content, dueAt)
        }
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

    private fun advance(from: Long, rule: RepeatRule): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        when (rule) {
            RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatRule.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatRule.WEEKDAYS -> {
                do { cal.add(Calendar.DAY_OF_YEAR, 1) }
                while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            RepeatRule.NONE -> {}
        }
        return cal.timeInMillis
    }

    private fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
