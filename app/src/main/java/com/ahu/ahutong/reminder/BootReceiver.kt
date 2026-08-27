package com.ahu.ahutong.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahu.ahutong.data.xuexiaotong.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Store.saveRemindedMap(emptyMap())
                ReminderScheduler.scheduleAll(context)
            }
        }
    }
}