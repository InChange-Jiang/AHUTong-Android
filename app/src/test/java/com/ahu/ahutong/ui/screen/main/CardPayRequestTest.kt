package com.ahu.ahutong.ui.screen.main

import com.ahu.ahutong.data.crawler.model.ycard.CardPayRequest
import com.ahu.ahutong.data.crawler.utils.sha256
import com.ahu.ahutong.data.model.CardRechargeBank
import org.junit.Assert.assertEquals
import org.junit.Test

class CardPayRequestTest {
    @Test
    fun chinaMerchantsBankUsesCapturedNativePaymentChannel() {
        val request = CardPayRequest(ORDER_ID, CardRechargeBank.CHINA_MERCHANTS_BANK)
        val params = request.toMap()

        assertEquals("PAYMENTCASHIER", params["paytype"])
        assertEquals("81", params["paytypeid"])
        assertEquals(expectedSignature(params), params["SIGN"])
    }

    @Test
    fun agriculturalBankKeepsExistingPaymentChannel() {
        val request = CardPayRequest(ORDER_ID, CardRechargeBank.AGRICULTURAL_BANK)
        val params = request.toMap()

        assertEquals("BANKCARD", params["paytype"])
        assertEquals("63", params["paytypeid"])
        assertEquals(expectedSignature(params), params["SIGN"])
    }

    private fun expectedSignature(params: Map<String, Any>): String = sha256(
        "APP_ID=${params["APP_ID"]}" +
            "&NONCE=${params["NONCE"]}" +
            "&SIGN_TYPE=${params["SIGN_TYPE"]}" +
            "&TIMESTAMP=${params["TIMESTAMP"]}" +
            "&orderid=$ORDER_ID" +
            "&paystep=${params["paystep"]}" +
            "&paytype=${params["paytype"]}" +
            "&paytypeid=${params["paytypeid"]}" +
            "&redirect_url=${params["redirect_url"]}" +
            "&userAgent=${params["userAgent"]}" +
            "&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4"
    ).uppercase()

    private companion object {
        const val ORDER_ID = "test-order-id"
    }
}
