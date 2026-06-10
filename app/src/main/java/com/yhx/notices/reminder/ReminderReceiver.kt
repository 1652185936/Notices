package com.yhx.notices.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yhx.notices.R

/** 提醒到点：发系统通知（仅依赖 Intent 附加数据，不触 DB，保证可靠）。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, 0L)
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: "待办提醒"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("待办提醒")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(todoId.toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "todo_reminder"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_CONTENT = "content"
    }
}
