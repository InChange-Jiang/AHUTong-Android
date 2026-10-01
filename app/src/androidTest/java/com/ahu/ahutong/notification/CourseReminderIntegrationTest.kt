package com.ahu.ahutong.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.notification.model.CourseReminderPayload
import com.ahu.ahutong.ui.state.AndroidCourseReminderControl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseReminderIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun exactAlarmSettingsOpensTheDebugAppsPermissionPage() {
        assumeTrue(context.packageName.endsWith(".debug") && Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        AndroidCourseReminderControl().openExactAlarmSettings()
        val expected = "dat=package:${context.packageName}"
        var opened = false
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (!opened && SystemClock.elapsedRealtime() < deadline) {
            val activityState = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
            ).bufferedReader().use { it.readText() }
            opened = activityState.lineSequence().any {
                it.contains("android.settings.REQUEST_SCHEDULE_EXACT_ALARM") && it.contains(expected)
            }
            if (!opened) SystemClock.sleep(100L)
        }
        assertTrue("Exact alarm settings must open for the calling package", opened)
    }

    @Test
    fun lateDeliveryIsPersistedAndRepeatedReschedulingDoesNotPostAgain() = runBlocking {
        // The fixture is confined to the debug package's guest cache; never replace a signed-in account.
        assumeTrue(context.packageName.endsWith(".debug"))
        assumeTrue(AHUCache.getCurrentUser() == null)
        val settings = PreferencesManager(context)
        val wasEnabled = settings.courseReminderEnabled.first()
        val wasLive = settings.courseReminderLiveCountdownEnabled.first()
        val wasMock = AHUCache.getMockData()
        val previousYear = AHUCache.getSchoolYear().orEmpty()
        val previousTerm = AHUCache.getSchoolTerm().orEmpty()
        val ledger = context.getSharedPreferences("course_reminder_deliveries", Context.MODE_PRIVATE)
        val previousKeys = ledger.all.keys
        val now = LocalDateTime.now()
        assumeTrue(now.hour < 23)
        val start = now.plusMinutes(5).withSecond(0).withNano(0)
        val end = start.plusMinutes(45)
        assumeTrue(start.toLocalDate() == end.toLocalDate())
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val course = Course().apply {
            setCourseId("reminder-integration-${System.nanoTime()}")
            setName("课前提醒回归测试")
            setLocation("测试教室")
            setWeekday(start.dayOfWeek.value.toString())
            setWeekIndexes(listOf(1))
            setStartTime("1")
            setLength("1")
            setClockRange("${start.format(clock)}-${end.format(clock)}")
        }
        val semester = "9998-9999-1"
        try {
            CourseReminderScheduler.cancel(context).join()
            AHUCache.setMockData(true)
            AHUCache.saveSchoolYear("9998-9999")
            AHUCache.saveSchoolTerm(semester)
            AHUCache.saveSchoolTermStartTime("9998-9999", "1",
                now.toLocalDate().minusDays(now.dayOfWeek.value - 1L).toString())
            AHUCache.saveSchoolTermInSemester("9998-9999", "1", true, now.toLocalDate().toString())
            AHUCache.saveSchedule(semester, listOf(course))
            settings.setCourseReminderLiveCountdownEnabled(false)
            settings.setCourseReminderEnabled(true)
            CourseReminderScheduler.createNotificationChannel(context)

            CourseReminderScheduler.reschedule(context).join()
            val posted = manager.activeNotifications.single { it.tag?.startsWith("course_reminder:") == true }
            assertEquals(course.name, posted.notification.extras.getString(Notification.EXTRA_TITLE))
            assertFalse(posted.notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("10 分钟"))
            assertEquals(1, (ledger.all.keys - previousKeys).size)
            CourseReminderScheduler.reschedule(context).join()
            val repeated = manager.activeNotifications.single { it.tag == posted.tag }
            assertEquals(posted.postTime, repeated.postTime)

            // A stale broadcast/refresh after disabling must not bring the notification back.
            settings.setCourseReminderEnabled(false)
            CourseReminderScheduler.reschedule(context).join()
            assertTrue(manager.activeNotifications.none { it.tag == posted.tag })
        } finally {
            CourseReminderScheduler.cancel(context).join()
            settings.setCourseReminderEnabled(wasEnabled)
            settings.setCourseReminderLiveCountdownEnabled(wasLive)
            AHUCache.saveSchoolYear(previousYear)
            AHUCache.saveSchoolTerm(previousTerm)
            AHUCache.saveSchedule(semester, emptyList())
            AHUCache.setMockData(wasMock)
            val editor = ledger.edit()
            (ledger.all.keys - previousKeys).forEach(editor::remove)
            editor.commit()
            CourseReminderScheduler.reschedule(context).join()
        }
    }

    @Test
    fun android16LiveCountdownUsesSystemChronometerAndTimeout() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val startAt = System.currentTimeMillis() + 5 * 60_000L
        try {
            assertTrue(CourseLiveUpdateHelper.showLiveUpdate(context, CourseReminderPayload(
                "系统倒计时回归测试", "测试教室", "测试", startAt, allowLiveCountdown = true, isDebug = true)))
            val notification = manager.activeNotifications.single { it.id == 4096 }.notification
            assertEquals(startAt, notification.`when`)
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            assertTrue(notification.timeoutAfter in 1..5 * 60_000L)
        } finally {
            CourseLiveUpdateHelper.cancel(context)
        }
    }
}
