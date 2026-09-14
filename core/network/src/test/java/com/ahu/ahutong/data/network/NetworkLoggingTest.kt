package com.ahu.ahutong.data.network

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.logging.HttpLoggingInterceptor

/**
 * 日志脱敏的守卫（§7 第 8 条：凭据不得进 logcat）。
 *
 * 两半：能行为的就行为——级别映射直接断言；不能行为的按源码形状钉住——
 * `HttpLoggingInterceptor` 的 logger 是私有的，脱敏后的输出在单测里拿不到，
 * 因此改成断言那四条 `redactHeader` 与 release 早退都还在。删掉任何一条，这个套件就会变红。
 */
class NetworkLoggingTest {

    @Test
    fun levelMappingMatchesTheRequestedLevel() {
        // 这个对象只在 debug 构建里返回拦截器；release 单测变体下没有这一档可断言。
        if (!com.ahu.ahutong.core.network.BuildConfig.DEBUG) return

        val basic = NetworkLogging.debugInterceptor(NetworkLogging.Level.Basic)
        val headers = NetworkLogging.debugInterceptor(NetworkLogging.Level.Headers)
        val default = NetworkLogging.debugInterceptor()

        assertEquals("BASIC", (basic as HttpLoggingInterceptor).level.name)
        assertEquals("HEADERS", (headers as HttpLoggingInterceptor).level.name)
        assertEquals("HEADERS", (default as HttpLoggingInterceptor).level.name)
    }

    @Test
    fun everyCredentialHeaderStaysRedactedAndReleaseKeepsNoLogger() {
        val source = sourceFile("core/network/src/main/java/com/ahu/ahutong/data/network/NetworkLogging.kt")
            .readText()

        listOf("Authorization", "Synjones-Auth", "Cookie", "Set-Cookie").forEach { header ->
            assertTrue(
                source.contains("redactHeader(\"$header\")"),
                "凭据头 $header 的脱敏被删掉了：日志会把它写进 logcat。"
            )
        }
        assertTrue(
            source.contains("if (!BuildConfig.DEBUG) return null"),
            "release 构建的早退被删掉了：日志代码路径会重新存在。"
        )
    }

    private fun sourceFile(relativePath: String): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        val repositoryRoot = generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        return File(repositoryRoot, relativePath)
    }
}
