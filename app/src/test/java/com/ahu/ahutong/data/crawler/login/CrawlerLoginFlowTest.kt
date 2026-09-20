package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.api.adwmh.createAdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.createJwxtApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 登录失败的分类契约（P2 验证项：断网 / 密码错 / 协议变更必须给出不同的 AhuError）。
 *
 * 迁移前这三种情况在界面上分不出来：断网把异常原文抛给 UI，密码错与校方改版都只报一句"登录失败"。
 * 现在分类由 [CrawlerLoginFlow] 负责，两个协议客户端与验证码识别都是构造参数，
 * 于是可以在纯 JVM 上用 fixture 把每种情形各跑一遍。
 */
class CrawlerLoginFlowTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun flow(baseUrl: String = server.url("/").toString()) = CrawlerLoginFlow(
        jwxt = createJwxtApi(OkHttpClient(), baseUrl),
        adwmh = createAdwmhApi(OkHttpClient(), baseUrl),
        captchaSolver = FixedCaptchaSolver,
        casBaseUrl = baseUrl.trimEnd('/')
    )

    /** 按路径分发 fixture：两条链路并行发请求，FIFO 队列的到达顺序不可靠。 */
    private fun serve(routes: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return routes[path]
                    ?: MockResponse().setResponseCode(404).setBody("no fixture for " + path)
            }
        }
    }

    @Test
    fun `a dead network is a transport failure, not bad credentials`() = runBlocking {
        val baseUrl = server.url("/").toString()
        server.shutdown()

        val outcome = flow(baseUrl).login("20210001", "secret")

        assertTrue(outcome is CrawlerLoginOutcome.TransportFailure, "实际是 " + outcome)
        assertEquals(AhuError.Network, outcome.error)
    }

    @Test
    fun `a rejected password is not reported as a protocol change`() = runBlocking {
        serve(
            mapOf(
                "/remind/authcode" to image(),
                "/user/login" to json(ADWMH_WRONG_PASSWORD_JSON),
                "/student/sso/login" to html(CAS_LOGIN_PAGE_HTML),
                "/cas/device" to ok(),
                "/cas/login" to ok()
            )
        )

        val outcome = flow().login("20210001", "wrong")

        assertEquals(CrawlerLoginOutcome.CredentialsRejected, outcome)
    }

    @Test
    fun `a redesigned login page is a protocol change, even when the other link succeeded`() = runBlocking {
        serve(
            mapOf(
                "/remind/authcode" to image(),
                "/user/login" to json(ADWMH_OK_JSON),
                "/student/sso/login" to html(REDESIGNED_CAS_PAGE_HTML)
            )
        )

        val outcome = flow().login("20210001", "correct")

        assertTrue(outcome is CrawlerLoginOutcome.ProtocolChanged, "实际是 " + outcome)
    }

    @Test
    fun `reaching the jwxt home page after the cas redirect means success`() = runBlocking {
        serve(
            mapOf(
                "/remind/authcode" to image(),
                "/user/login" to json(ADWMH_OK_JSON),
                "/student/sso/login" to html(CAS_LOGIN_PAGE_HTML),
                "/cas/device" to ok(),
                "/cas/login" to redirectTo("/student/home"),
                "/student/home" to ok()
            )
        )

        val outcome = flow().login("20210001", "correct")

        assertTrue(outcome is CrawlerLoginOutcome.Succeeded, "实际是 " + outcome)
    }

    @Test
    fun `an upstream business rejection keeps the upstream message`() = runBlocking {
        serve(
            mapOf(
                "/remind/authcode" to image(),
                "/user/login" to json(ADWMH_LOCKED_JSON),
                "/student/sso/login" to html(CAS_LOGIN_PAGE_HTML),
                "/cas/device" to ok(),
                "/cas/login" to redirectTo("/student/home"),
                "/student/home" to ok()
            )
        )

        val outcome = flow().login("20210001", "correct")

        assertEquals(CrawlerLoginOutcome.Upstream(10002, "账号已被锁定，请联系管理员"), outcome)
    }

    @Test
    fun `a browser verification demand stays a domain state, not an error`() = runBlocking {
        serve(
            mapOf(
                "/remind/authcode" to image(),
                "/user/login" to json(ADWMH_OK_JSON),
                "/student/sso/login" to MockResponse().setResponseCode(412).setBody("verify")
            )
        )

        val outcome = flow().login("20210001", "correct")

        assertTrue(outcome is CrawlerLoginOutcome.WebVerificationRequired, "实际是 " + outcome)
    }

    private fun ok() = MockResponse().setResponseCode(200).setBody("")

    private fun image() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/jpeg")
        .setBody("fake-image")

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private fun html(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    private fun redirectTo(path: String) = MockResponse()
        .setResponseCode(302)
        .setHeader("Location", server.url(path).toString())

    private object FixedCaptchaSolver : CaptchaSolver {
        override suspend fun solve(imageBytes: ByteArray): String = "1234"
    }

    private companion object {
        const val ADWMH_OK_JSON =
            """{"code":10000,"msg":"","object":{"user":{"userName":"张三","idNumber":"20210001"}}}"""
        const val ADWMH_WRONG_PASSWORD_JSON =
            """{"code":10001,"msg":"用户名或密码错误","object":{"user":{"userName":"","idNumber":""}}}"""
        const val ADWMH_LOCKED_JSON =
            """{"code":10002,"msg":"账号已被锁定，请联系管理员","object":{"user":{"userName":"","idNumber":""}}}"""

        const val CAS_LOGIN_PAGE_HTML =
            """<html><body><form action="/cas/login"><input name="lt" value="LT-123"/></form></body></html>"""
        const val REDESIGNED_CAS_PAGE_HTML =
            """<html><body><div class="sso">统一身份认证</div></body></html>"""
    }
}
