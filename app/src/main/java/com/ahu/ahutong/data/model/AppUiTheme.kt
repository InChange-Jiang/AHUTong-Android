package com.ahu.ahutong.data.model

enum class AppUiTheme(val storageValue: String, val displayName: String) {
    MATERIAL("material", "Material"),
    MIUIX("miuix", "Miuix"),
    LIQUID_GLASS("liquid_glass", "LiquidGlass");

    companion object {
        fun fromStorage(value: String?, legacyUseLiquidGlass: Boolean?): AppUiTheme =
            entries.firstOrNull { it.storageValue == value }
                ?: if (legacyUseLiquidGlass == false) MATERIAL else LIQUID_GLASS
    }
}
