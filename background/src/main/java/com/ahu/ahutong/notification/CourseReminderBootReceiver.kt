package com.ahu.ahutong.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.os.Build

class CourseReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != true)
                ) return
                val pendingResult = goAsync()
                CourseReminderScheduler.reschedule(context).invokeOnCompletion {
                    pendingResult.finish()
                }
            }
        }
    }
}
