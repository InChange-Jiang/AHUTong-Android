package com.ahu.ahutong.notification

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseReminderCommandsTest {
    @Test
    fun `a queued enable cannot resurrect alarms after the user disables reminders`() = runBlocking {
        val commands = CourseReminderCommands(this)
        val alarms = mutableListOf<String>()
        val enable = commands.submit { alarms += "enabled" }
        val disable = commands.submit { alarms.clear() }
        enable.join()
        disable.join()
        assertEquals(emptyList(), alarms)
    }

    @Test
    fun `a queued cancellation cannot erase the latest enable`() = runBlocking {
        val commands = CourseReminderCommands(this)
        val alarms = mutableListOf<String>()
        val disable = commands.submit { alarms.clear() }
        val enable = commands.submit { alarms += "enabled" }
        disable.join()
        enable.join()
        assertEquals(listOf("enabled"), alarms)
    }

    @Test
    fun `commands waiting behind a running reschedule respect the final user choice`() = runBlocking {
        val commands = CourseReminderCommands(this)
        val alarms = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val initial = commands.submit {
            started.complete(Unit)
            gate.await()
            alarms += "initial"
        }
        started.await()
        val staleCancellation = commands.submit { alarms.clear() }
        val latest = commands.submit { alarms += "latest" }
        gate.complete(Unit)
        initial.join()
        staleCancellation.join()
        latest.join()
        assertEquals(listOf("initial", "latest"), alarms)
    }
}
