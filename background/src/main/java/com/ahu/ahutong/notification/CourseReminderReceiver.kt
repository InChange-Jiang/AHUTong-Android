package com.ahu.ahutong.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahu.ahutong.notification.model.CourseReminderPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LIVE_COUNTDOWN_DISMISSED) {
            CourseLiveUpdateHelper.cancelScheduledUpdate(context)
            return
        }

        val payload = CourseReminderPayload.fromIntent(intent) ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        if (payload.isDebug) {
                            CourseReminderScheduler.deliverDebugReminder(context, payload).join()
                        } else {
                            // Re-read settings and the timetable: queued or legacy payloads can be stale.
                            CourseReminderScheduler.reschedule(context).join()
                        }
                    }

                    ACTION_UPDATE_LIVE_COUNTDOWN -> {
                        // Cancel minute alarms left by an older version. SystemUI now owns the timer.
                        CourseLiveUpdateHelper.cancelScheduledUpdate(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_REMIND = "com.ahu.ahutong.notification.ACTION_REMIND_COURSE"
        const val ACTION_UPDATE_LIVE_COUNTDOWN =
            "com.ahu.ahutong.notification.ACTION_UPDATE_LIVE_COUNTDOWN"
        const val ACTION_LIVE_COUNTDOWN_DISMISSED =
            "com.ahu.ahutong.notification.ACTION_LIVE_COUNTDOWN_DISMISSED"
    }
}
