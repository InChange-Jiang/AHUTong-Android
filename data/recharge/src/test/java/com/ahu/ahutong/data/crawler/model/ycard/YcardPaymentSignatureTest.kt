package com.ahu.ahutong.data.crawler.model.ycard

import kotlin.test.Test
import kotlin.test.assertEquals

class YcardPaymentSignatureTest {

    @Test
    fun `shared form builder preserves fields and signs the canonical payload`() {
        val body = buildSignedPaymentFormBody(
            params = linkedMapOf("orderid" to "O1", "paystep" to "2"),
            timestamp = "1700000000000",
            nonce = "nonce"
        )
        val values = (0 until body.size).associate { index -> body.name(index) to body.value(index) }

        assertEquals("O1", values["orderid"])
        assertEquals("2", values["paystep"])
        assertEquals("56321", values["APP_ID"])
        assertEquals("1700000000000", values["TIMESTAMP"])
        assertEquals("SHA256", values["SIGN_TYPE"])
        assertEquals("nonce", values["NONCE"])
        assertEquals(
            "8AEAA43C97840F877609E1EC66906383E084C5720413A4267C42A34A55BF4211",
            values["SIGN"]
        )
    }
}
