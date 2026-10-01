package com.ahu.ahutong.notification

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize mutations and discard queued commands superseded by a newer user action. */
internal class CourseReminderCommands(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val version = AtomicLong()

    fun submit(action: suspend () -> Unit): Job {
        val submittedVersion = version.incrementAndGet()
        return scope.launch {
            mutex.withLock {
                if (submittedVersion == version.get()) action()
            }
        }
    }

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }
}
