package com.ahu.ahutong.data.model

/**
 * "使用内置默认色"的设置哨兵值：主题色字段取它时表示用户没有自定义颜色。
 * 设置层（写入）与设计系统（判断）都要用它，所以放在模型层——两端都只依赖这里。
 */
const val DEFAULT_THEME_COLOR = "default"

enum class AppUiTheme(val storageValue: String, val displayName: String) {
    MATERIAL("material", "Material"),
    MIUIX("miuix", "Miuix"),
    LIQUID_GLASS("liquid_glass", "LiquidGlass"),
    RADIANT("radiant_ui", "RadiantUI");

    val usesLiquidGlass: Boolean
        get() = this == LIQUID_GLASS || this == RADIANT

    companion object {
        fun fromStorage(
            value: String?,
            legacyUseLiquidGlass: Boolean?,
            legacyUiStyle: String? = null
        ): AppUiTheme =
            entries.firstOrNull { it.storageValue == value }
                ?: when (legacyUiStyle) {
                    "original" -> MATERIAL
                    "liquid_glass" -> LIQUID_GLASS
                    "radiant_ui" -> RADIANT
                    else -> null
                }
                ?: if (legacyUseLiquidGlass == false) MATERIAL else RADIANT
    }
}
