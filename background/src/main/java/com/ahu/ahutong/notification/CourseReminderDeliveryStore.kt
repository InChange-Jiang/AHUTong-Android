package com.ahu.ahutong.notification

import android.content.Context
import android.util.Log

/** Accessed under the scheduler mutex. Records survive process death and reboot. */
internal class CourseReminderDeliveryStore(context: Context) {
    private val preferences = context.getSharedPreferences("course_reminder_deliveries", Context.MODE_PRIVATE)

    fun deliveredKeys(nowMillis: Long): Set<String> {
        val records = preferences.all
        val expired = records.filterValues { it !is Long || it <= nowMillis }.keys
        if (expired.isNotEmpty()) {
            val editor = preferences.edit()
            expired.forEach(editor::remove)
            editor.apply()
        }
        return records.keys - expired
    }

    fun markDelivered(key: String, courseStartMillis: Long) {
        if (!preferences.edit().putLong(key, courseStartMillis).commit()) {
            Log.w("CourseReminderDelivery", "Unable to persist delivered reminder")
        }
    }

    fun clear() {
        if (!preferences.edit().clear().commit()) {
            Log.w("CourseReminderDelivery", "Unable to clear delivered reminders")
        }
    }
}
