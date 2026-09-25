package com.ahu.ahutong.core.plugin

/**
 * 插件隔离存储：键值读写，命名空间由宿主按插件 id 隔离。
 * 只支持字符串/数字/布尔——插件要存结构就自己序列化 JSON。
 */
interface PluginStorage {
    fun getString(key: String): String?
    fun getLong(key: String): Long?
    fun getBoolean(key: String): Boolean?

    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)

    fun remove(key: String)

    /** 清空本插件的全部存储（不影响其他插件与主 App）。 */
    fun clear()
}
