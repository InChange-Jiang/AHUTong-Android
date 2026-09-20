package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.core.common.AhuError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import retrofit2.Response

class HttpResponseMappingTest {

    @Test
    fun `mapping a failed response closes its error body`() {
        val body = TrackingResponseBody()
        val failure = Response.error<String>(503, body).toClosedFailure("上游不可用")

        assertTrue(body.closed)
        assertEquals(AhuError.Server(503, "上游不可用"), failure.error)
    }

    private class TrackingResponseBody : ResponseBody() {
        private val delegate = Buffer().writeUtf8("failure")
        private val trackedSource = object : ForwardingSource(delegate) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        var closed = false
            private set

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = delegate.size

        override fun source(): BufferedSource = trackedSource
    }
}
