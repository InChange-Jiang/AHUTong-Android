package com.ahu.ahutong.core.plugin

/**
 * 插件元数据：小工具页入口卡片的一切信息。
 *
 * @property id 插件唯一 id（路由用，`plugin/<id>`）；只允许小写字母、数字、下划线
 * @property title 入口与页头标题
 * @property summary 一句话简介（小工具页副标题）
 * @property iconRes 入口图标资源（插件模块自己的 drawable）
 * @property tint 入口图标配色（ARGB Long，与其他小工具同款硬编码）
 * @property version 插件版本号（插件内部自理，宿主不解析）
 * @property author 插件作者署名
 */
data class PluginMeta(
    val id: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
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
