package com.ahu.ahutong.data.crawler.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

class SessionRefreshPolicyTest {
    private val requestUrl = "https://jw.ahu.edu.cn/student/for-std/lesson-search".toHttpUrl()

    @Test
    fun `recognizes first party login redirect`() {
        assertTrue(
            SessionRefreshPolicy.isFirstPartyLoginRedirect(
                requestUrl,
                "https://one.ahu.edu.cn/cas/login?service=https%3A%2F%2Fjw.ahu.edu.cn"
            )
        )
        assertTrue(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "/tologin?refer=student"))
    }

    @Test
    fun `does not refresh for unrelated or external redirects`() {
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "/notice?refer=home"))
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "https://example.com/login"))
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, null))
    }

    @Test
    fun `request keeps the generation observed when it was dispatched`() {
        val generation = SessionRefreshCoordinator.currentGeneration()
        val request = Request.Builder().url(requestUrl).build()
        val tagged = SessionRefreshCoordinator.tagRequest(request)

        assertEquals(generation, SessionRefreshCoordinator.observedGeneration(tagged))
        assertSame(tagged, SessionRefreshCoordinator.tagRequest(tagged))
    }
}
