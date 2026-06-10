package com.yhx.notices.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yhx.notices.data.repository.TodoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待办时间提醒调度。见 docs/06 §3 可靠性方案。
 * 当前版本：精确闹钟 + 应用前台重建；重复规则与开机重建为后续增强。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoRepo: TodoRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(todoId: Long, content: String, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        val pi = pendingIntent(todoId, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(todoId: Long) {
        alarmManager.cancel(pendingIntent(todoId, ""))
    }

    /** 应用启动时重建所有未来提醒（弥补进程被杀/重启）。 */
    suspend fun rescheduleActive() {
        val now = System.currentTimeMillis()
        todoRepo.observeActiveOnce().forEach { todo ->
            val due = todo.dueAt ?: return@forEach
            if (due > now) schedule(todo.id, todo.content, due)
        }
    }

    private fun pendingIntent(todoId: Long, content: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.yhx.notices.REMINDER"
            putExtra(ReminderReceiver.EXTRA_TODO_ID, todoId)
            putExtra(ReminderReceiver.EXTRA_CONTENT, content)
        }
        return PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
