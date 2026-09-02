package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp
import com.ahu.ahutong.data.crawler.utils.sha256
import com.ahu.ahutong.data.model.CardRechargeBank

class CardPayRequest(orderId: String, bank: CardRechargeBank) : RequestBody() {

    init {
        val time = getTimestamp()
        val nonce = generateNonce()
        val appId = "56321"
        val payStep = "2"
        val (payType, payTypeId) = when (bank) {
            CardRechargeBank.AGRICULTURAL_BANK -> "BANKCARD" to "63"
            CardRechargeBank.CHINA_MERCHANTS_BANK -> "PAYMENTCASHIER" to "81"
            CardRechargeBank.ALIPAY -> error("Alipay recharge is handled outside the campus-card API")
        }
        val redirectUrl = "https://ycard.ahu.edu.cn/payment/?name=result"
        val userAgent = "h5"
        val synAccessSource = "h5"

        addParams(
            mapOf(
                "opAppId" to "",
                "paytypeid" to payTypeId,
                "paytype" to payType,
                "paystep" to payStep,
                "orderid" to orderId,
                "redirect_url" to redirectUrl,
                "userAgent" to userAgent,
                "APP_ID" to appId,
                "TIMESTAMP" to time,
                "SIGN_TYPE" to "SHA256",
                "NONCE" to nonce,
                "SIGN" to sha256("APP_ID=$appId&NONCE=$nonce&SIGN_TYPE=SHA256&TIMESTAMP=$time&orderid=$orderId&paystep=$payStep&paytype=$payType&paytypeid=$payTypeId&redirect_url=$redirectUrl&userAgent=$userAgent&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4").uppercase(),
                "synAccessSource" to synAccessSource
            )
        )
    }

}
