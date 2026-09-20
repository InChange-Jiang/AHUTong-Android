package com.ahu.ahutong.ext

import com.ahu.ahutong.core.common.UserNotice
import com.ahu.ahutong.core.common.UserNoticeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.UnknownHostException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * launchSafe 的提示接缝契约：核心模块只上报异常，文案与 Toast 由 :app 的实现负责。
 * 这条断言只有在 :core:common 不再自己 import Toast 与中文文案之后才写得出来——
 * 接缝建立之前，这类逻辑根本没法在 JVM 上验证。
 */
class LaunchSafeNoticeTest {

    private class RecordingNotice : UserNotice {
        val failures = mutableListOf<Throwable>()

        override fun showFailure(error: Throwable) {
            failures.add(error)
        }
    }

    @AfterTest
    fun tearDown() {
        // 还原成"不提示"，避免污染其它用例。
        UserNoticeHolder.install(NoopNotice)
    }

    @Test
    fun `a failing launchSafe reports the failure to the installed notice`() = runBlocking {
        val notice = RecordingNotice()
        UserNoticeHolder.install(notice)
        val failure = UnknownHostException("no dns")

        CoroutineScope(Dispatchers.Unconfined).launchSafe { throw failure }.join()

        assertEquals(1, notice.failures.size)
        assertSame(failure, notice.failures.single())
    }

    private companion object {
        val NoopNotice = object : UserNotice {
            override fun showFailure(error: Throwable) = Unit
        }
    }
}
