package com.yhx.notices

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yhx.notices.reminder.ReminderReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NoticesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createReminderChannel()
    }

    private fun createReminderChannel() {
        val channel = NotificationChannel(
            ReminderReceiver.CHANNEL_ID,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "待办事项到点提醒" }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
