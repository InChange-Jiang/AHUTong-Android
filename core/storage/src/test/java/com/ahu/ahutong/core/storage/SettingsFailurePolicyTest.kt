package com.ahu.ahutong.core.storage

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/** ADR 0003 的读写失败策略：读失败回默认值、写失败重试一次，两半都钉在这里。 */
class SettingsFailurePolicyTest {

    @Test
    fun readFailureFallsBackToTheDefaultValue() = runBlocking {
        val values = flow<Int> { throw IOException("disk") }
            .fallbackToDefaultOnReadFailure(default = 42)
            .toList()

        assertEquals(listOf(42), values)
    }

    @Test
    fun readFailureThatIsNotAnIoProblemStillPropagates() {
        assertFailsWith<IllegalStateException> {
            runBlocking {
                flow<Int> { throw IllegalStateException("bug") }
                    .fallbackToDefaultOnReadFailure(default = 42)
                    .toList()
            }
        }
    }

    @Test
    fun writeFailureIsRetriedExactlyOnce() = runBlocking {
        var attempts = 0
        val result = retryOnceOnWriteFailure {
            attempts += 1
            if (attempts == 1) throw IOException("transient")
            "written"
        }

        assertEquals("written", result)
        assertEquals(2, attempts)
    }

    @Test
    fun secondWriteFailureIsReportedToTheCaller() {
        var attempts = 0

        assertFailsWith<IOException> {
            runBlocking {
                retryOnceOnWriteFailure {
                    attempts += 1
                    throw IOException("still failing")
                }
            }
        }

        assertEquals(2, attempts)
    }
}
