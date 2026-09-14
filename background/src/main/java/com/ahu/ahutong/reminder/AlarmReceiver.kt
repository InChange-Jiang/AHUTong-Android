package com.ahu.ahutong.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return

        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "学习通日历"
        val content = intent.getStringExtra(ReminderScheduler.EXTRA_CONTENT) ?: "提醒"
        val reminderKey = ReminderScheduler.reminderKey(intent)

        ReminderScheduler.ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            ReminderScheduler.notificationIdFor(reminderKey),
            ReminderScheduler.buildReminderNotification(context, title, content)
        )
    }
}
