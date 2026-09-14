package com.ahu.ahutong.core.common

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AppEnvironment 接缝的契约（P2 完成判据之一：AHUApplication.getApp() 归零）。
 *
 * 这里只钉住「未安装即失败」这一条：生产上它由 Application.onCreate() 安装，
 * 一旦有人漏装，需要 Context 的数据层必须立刻炸在明确的位置，
 * 而不是拿到一个空 Context 静默出错——那正是原先静态字段最容易出的问题。
 *
 * 安装之后的取值行为依赖真 Context，属于 instrumentation 范围。
 */
class AppEnvironmentHolderTest {

    @Test
    fun `an uninstalled environment fails loudly instead of returning nothing`() {
        val missing = assertFailsWith<IllegalStateException> { AppEnvironmentHolder.require() }
        assertTrue(
            missing.message.orEmpty().contains("AppEnvironmentHolder.install"),
            "错误信息必须指出安装点，否则现场只能靠猜：" + missing.message,
        )

        // context() 走同一条断言，避免将来有人给它加一条「返回空实现」的兜底。
        assertFailsWith<IllegalStateException> { AppEnvironmentHolder.context() }
    }
}
