package com.ahu.ahutong.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ahu.ahutong.background.scheduleReadModel
import com.ahu.ahutong.background.courseReminderSettings
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.notification.model.CourseReminderPayload
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object CourseReminderScheduler {
    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = CourseReminderCommands(schedulerScope)
    internal const val CHANNEL_ID = "course_reminder_v2"

    private const val CHANNEL_NAME = "课前提醒"
    private const val CHANNEL_DESCRIPTION = "上课前 10 分钟提醒下一节课"
    private const val REQUEST_CODE = 2001
    private const val DEBUG_REQUEST_CODE_BASE = 2100
    private const val DEBUG_LIVE_COUNTDOWN_MINUTES = 3L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun reschedule(context: Context): Job = submit {
        rescheduleNow(context.applicationContext)
    }

    private fun submit(action: suspend () -> Unit): Job = commands.submit {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Unable to update course reminder", error)
        }
    }

    private suspend fun rescheduleNow(context: Context) {
        cancelScheduledReminder(context)
        CourseLiveUpdateHelper.cancelScheduledUpdate(context)
        val readModel = scheduleReadModel()
        if (!readModel.canUseUndergraduateAcademics() || !isReminderEnabled()) {
            CourseReminderNotifier.cancelActiveReminder(context)
            CourseReminderNotifier.cancelStandardReminders(context)
            return
        }
        val accountKey = readModel.reminderAccountKey()
        if (accountKey == null) {
            CourseReminderNotifier.cancelActiveReminder(context)
            CourseReminderNotifier.cancelStandardReminders(context)
            return
        }
        val now = LocalDateTime.now()
        val deliveryStore = CourseReminderDeliveryStore(context)
        val deliveredKeys = deliveryStore.deliveredKeys(System.currentTimeMillis())
        val reminders = findReminders(now)
        CourseLiveUpdateHelper.retainCurrentOccurrence(context, reminders.map { it.key }.toSet())
        for (reminder in reminders.filter { it.key !in deliveredKeys }) {
            if (readModel.reminderAccountKey() != accountKey || !isReminderEnabled()) return
            val payload = buildPayload(reminder)
            if (reminder.reminderAt.isAfter(now)) {
                scheduleAlarm(context, reminder.reminderAt.toEpochMillis(), buildPendingIntent(context, payload))
                return
            }
            // The original alarm may still be delayed. Deliver once while the course is upcoming.
            val result = CourseReminderNotifier.showReminder(context, payload)
            if (result != CourseReminderNotifier.DeliveryResult.BLOCKED) {
                deliveryStore.markDelivered(reminder.key, reminder.courseStart.toEpochMillis())
            }
        }
    }

    fun cancel(context: Context): Job = submit {
        cancelScheduledReminder(context)
        CourseReminderNotifier.cancelActiveReminder(context)
        CourseReminderNotifier.cancelStandardReminders(context)
    }

    suspend fun clearDeliveryHistory(context: Context) {
        commands.withLock { CourseReminderDeliveryStore(context).clear() }
    }

    fun dismissActiveReminder(context: Context): Job = schedulerScope.launch {
        commands.withLock { CourseReminderNotifier.cancelActiveReminder(context) }
    }

    fun deliverDebugReminder(context: Context, payload: CourseReminderPayload): Job = schedulerScope.launch {
        try {
            commands.withLock { CourseReminderNotifier.showReminder(context, payload) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Unable to deliver debug course reminder", error)
        }
    }

    fun scheduleDebugReminder(context: Context, delayMinutes: Int) {
        createNotificationChannel(context)
        val triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L
        val payload = CourseReminderPayload(
            courseName = "课前提醒测试",
            location = "预计 $delayMinutes 分钟后触发",
            timeText = "调试通知",
            notificationId = DEBUG_REQUEST_CODE_BASE + delayMinutes,
            allowLiveCountdown = false,
            isDebug = true
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DEBUG_REQUEST_CODE_BASE + delayMinutes,
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = CourseReminderReceiver.ACTION_REMIND
                payload.writeToIntent(this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(context, triggerAtMillis, pendingIntent)
    }

    fun scheduleDebugLiveUpdateReminder(context: Context, delayMinutes: Int) {
        createNotificationChannel(context)
        val triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L
        val payload = CourseReminderPayload(
            courseName = "课前岛卡测试",
            location = "调试入口",
            timeText = "${DEBUG_LIVE_COUNTDOWN_MINUTES} 分钟后开始",
            courseStartAtMillis = triggerAtMillis + DEBUG_LIVE_COUNTDOWN_MINUTES * 60_000L,
            notificationId = DEBUG_REQUEST_CODE_BASE + 100 + delayMinutes,
            allowLiveCountdown = true,
            isDebug = true
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DEBUG_REQUEST_CODE_BASE + 100 + delayMinutes,
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = CourseReminderReceiver.ACTION_REMIND
                payload.writeToIntent(this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(context, triggerAtMillis, pendingIntent)
    }

    fun scheduleDebugNextCourseInThreeMinutes(context: Context): String? {
        createNotificationChannel(context)

        val nextReminder = findReminders().firstOrNull() ?: return null
        val course = nextReminder.course
        val startRange = ScheduleSectionTimes.getCourseTimeRangeInMinutes(course)
        val startMinutes = startRange.first
        val startTimeText = "%02d:%02d".format(startMinutes / 60, startMinutes % 60)
        val sections = "${course.startTime}-${course.startTime + course.length - 1}节"
        val payload = CourseReminderPayload(
            courseName = course.name,
            location = course.location,
            timeText = "$sections $startTimeText",
            courseStartAtMillis = System.currentTimeMillis() + DEBUG_LIVE_COUNTDOWN_MINUTES * 60_000L,
            notificationId = DEBUG_REQUEST_CODE_BASE + 300,
            allowLiveCountdown = true,
            isDebug = true
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DEBUG_REQUEST_CODE_BASE + 300,
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = CourseReminderReceiver.ACTION_REMIND
                payload.writeToIntent(this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(context, System.currentTimeMillis() + 1_000L, pendingIntent)
        return course.name
    }

    private fun cancelScheduledReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CourseReminderReceiver::class.java).setAction(
                CourseReminderReceiver.ACTION_REMIND
            ),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private suspend fun isReminderEnabled(): Boolean =
        courseReminderSettings().courseReminderEnabled.first()

    private fun buildPayload(reminder: CourseReminderPlan.Occurrence): CourseReminderPayload {
        val startRange = ScheduleSectionTimes.getCourseTimeRangeInMinutes(reminder.course)
        val startMinutes = startRange.first
        val startTimeText = "%02d:%02d".format(startMinutes / 60, startMinutes % 60)
        val sections = "${reminder.course.startTime}-${reminder.course.startTime + reminder.course.length - 1}节"
        val timeText = "$sections $startTimeText"
        return CourseReminderPayload(
            courseName = reminder.course.name,
            location = reminder.course.location,
            timeText = timeText,
            courseStartAtMillis = reminder.courseStart.toEpochMillis(),
            notificationId = reminder.key.hashCode(),
            allowLiveCountdown = true,
            occurrenceKey = reminder.key
        )
    }

    private fun buildPendingIntent(context: Context, payload: CourseReminderPayload): PendingIntent {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = CourseReminderReceiver.ACTION_REMIND
            payload.writeToIntent(this)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findReminders(
        now: LocalDateTime = LocalDateTime.now(),
        deliveredKeys: Set<String> = emptySet()
    ): List<CourseReminderPlan.Occurrence> {
        val readModel = scheduleReadModel()
        val accountKey = readModel.reminderAccountKey() ?: return emptyList()
        val semesterKey = readModel.cachedSemesterKey() ?: return emptyList()
        val startTime = readModel.schoolTermStartTime(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        ) ?: return emptyList()
        val termStartDate = runCatching { LocalDate.parse(startTime) }.getOrNull() ?: return emptyList()
        return CourseReminderPlan.build(
            accountKey, semesterKey.raw, termStartDate,
            readModel.cachedSchedule(semesterKey.raw).orEmpty(), now,
            todayInSemester = readModel.cachedConfig().isInSemester,
            deliveredKeys = deliveredKeys
        )
    }

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private const val TAG = "CourseReminderScheduler"
}
