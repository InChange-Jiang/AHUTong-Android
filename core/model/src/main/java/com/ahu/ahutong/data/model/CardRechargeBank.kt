package com.ahu.ahutong.data.model

enum class CardRechargeBank(val storageValue: String) {
    AGRICULTURAL_BANK("agricultural_bank"),
    CHINA_MERCHANTS_BANK("china_merchants_bank"),
    ALIPAY("alipay");

    companion object {
        fun fromStorage(value: String?): CardRechargeBank? =
            entries.firstOrNull { it.storageValue == value }
    }
}
