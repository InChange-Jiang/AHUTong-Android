package com.ahu.ahutong.core.plugin

/**
 * 插件图标：编译期插件用模块资源，运行期插件（.ahup 包）用 PNG 字节流——
 * 运行期插件不在宿主的资源体系里，图标只能随包携带。
 */
sealed interface PluginIcon {
    /** 编译期插件：模块内的 drawable 资源。 */
    data class Resource(val resId: Int) : PluginIcon

    /** 运行期插件：.ahup 包内 PNG 的字节流（装载器从包里读出）。 */
    data class Bytes(val png: ByteArray) : PluginIcon
}

/**
 * 插件元数据：小工具页入口卡片的一切信息。
 *
 * @property id 插件唯一 id（路由用，`plugin/<id>`）；只允许小写字母、数字、下划线
 * @property title 入口与页头标题
 * @property summary 一句话简介（小工具页副标题）
 * @property icon 入口图标（[PluginIcon.Resource] 或 [PluginIcon.Bytes]）
 * @property tint 入口图标配色（ARGB Long，与其他小工具同款硬编码）
 * @property version 插件版本号（插件内部自理，宿主不解析）
 * @property author 插件作者署名
 */
data class PluginMeta(
    val id: String,
    val title: String,
    val summary: String,
    val icon: PluginIcon,
    val tint: Long,
    val version: String,
    val author: String
) {
    init {
        require(id.matches(Regex("[a-z0-9_]+"))) {
            "插件 id 只允许小写字母/数字/下划线：$id"
        }
    }
}
