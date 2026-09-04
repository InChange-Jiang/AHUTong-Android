package com.ahu.ahutong.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ahu.ahutong.data.xuexiaotong.Store
import java.util.Calendar

object ReminderScheduler {

    const val CHANNEL_ID = "ahutong_cx_reminder"
    const val ACTION_REMIND = "com.ahu.ahutong.reminder.ACTION_REMIND_XUEXIAOTONG"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_CONTENT = "extra_content"
    private const val EXTRA_REMINDER_KEY = "extra_reminder_key"
    private const val REQUEST_CODE_NAMESPACE = 0x40000000
    private const val REQUEST_CODE_MASK = 0x0fffffff

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "学习通作业提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "作业截止与自定义日程提醒"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun allReminderKeys(): List<String> {
        val now = System.currentTimeMillis()
        val keys = mutableListOf<String>()
        Store.getWorks().forEach { w ->
            val endTs = w.endTs ?: return@forEach
            if (endTs > now) keys.add("${w.workId}|$endTs")
        }
        Store.getCustomEvents().forEach { ev ->
            val startTs = ev.startTs
            if (startTs > 0 && startTs > now) keys.add("event_${ev.id}|$startTs")
        }
        return keys
    }

    fun scheduleAll(context: Context) {
        ensureChannel(context)
        val setting = Store.getRemindSetting()
        if (!setting.enabled || !Store.hasCookie()) {
            cancelAll(context)
            return
        }

        val now = System.currentTimeMillis()
        val works = Store.getWorks()
        val events = Store.getCustomEvents()
        val reminded = Store.getRemindedMap().toMutableMap()

        works.forEach { w ->
            val endTs = w.endTs ?: return@forEach
            if (endTs <= now) return@forEach
            if (w.isDone && setting.onlyTodo) return@forEach

            val remindAt = endTs - setting.leadMinutes * 60000L
            if (remindAt <= now) return@forEach

            val key = "${w.workId}|$endTs"
            if (reminded.containsKey(key)) return@forEach

            val timeStr = formatTime(endTs)
            val ok = scheduleNotification(
                context, key, remindAt, w.courseName.ifEmpty { "作业提醒" },
                "${w.title} 将于 $timeStr 截止"
            )
            if (ok) reminded[key] = 1
        }

        events.forEach { ev ->
            if (ev.done) return@forEach
            val startTs = ev.startTs
            if (startTs <= 0 || startTs <= now) return@forEach

            val remindAt = startTs - setting.leadMinutes * 60000L
            if (remindAt <= now) return@forEach

            val key = "event_${ev.id}|$startTs"
            if (reminded.containsKey(key)) return@forEach

            val ok = scheduleNotification(
                context, key, remindAt, "日程提醒",
                "${ev.title} 将于 ${ev.startDate.substring(5)} ${ev.startTime} 开始"
            )
            if (ok) reminded[key] = 1
        }

        Store.saveRemindedMap(reminded)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val keys = (Store.getRemindedMap().keys + allReminderKeys()).distinct()
        keys.forEach { key ->
            listOf(
                requestCodeFor(key) to ACTION_REMIND,
                key.hashCode() to null
            ).forEach { (requestCode, action) ->
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    if (action != null) this.action = action
                }
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )?.let {
                    am.cancel(it)
                    it.cancel()
                }
            }
        }
        Store.saveRemindedMap(emptyMap())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications
                .filter { it.notification.channelId == CHANNEL_ID }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    fun rescheduleAll(context: Context) {
        cancelAll(context)
        scheduleAll(context)
    }

    private fun formatTime(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val month = c.get(Calendar.MONTH) + 1
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$month-$day $hh:$mm"
    }

    private fun scheduleNotification(context: Context, key: String, fireTs: Long, title: String, content: String): Boolean {
        return try {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_REMINDER_KEY, key)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCodeFor(key),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, fireTs, pending)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTs, pending)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, fireTs, pending)
            }
            true
        } catch (e: Exception) { false }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
        return true
    }

    fun sendTest(context: Context): Boolean {
        return try {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(
                notificationIdFor("test_${System.currentTimeMillis()}"),
                buildReminderNotification(context, "学习通日历", "这是一条测试通知")
            )
            true
        } catch (e: Exception) { false }
    }

    fun buildReminderNotification(context: Context, title: String, content: String): android.app.Notification {
        ensureChannel(context)
        val launch = PendingIntent.getActivity(
            context, REQUEST_CODE_NAMESPACE,
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launch)
            .build()
    }

    fun reminderKey(intent: Intent): String = intent.getStringExtra(EXTRA_REMINDER_KEY).orEmpty()

    fun notificationIdFor(key: String): Int =
        REQUEST_CODE_NAMESPACE or (key.hashCode() and REQUEST_CODE_MASK)

    private fun requestCodeFor(key: String): Int = notificationIdFor(key)
}
