package com.ahu.ahutong.ui.plugin

import android.content.Context
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginCapability
import com.ahu.ahutong.core.plugin.PluginCapabilityDeniedException
import com.ahu.ahutong.core.plugin.PluginHostServices
import com.ahu.ahutong.core.plugin.PluginStorage
import com.ahu.ahutong.data.network.AhuHttp
import okhttp3.OkHttpClient

/**
 * 宿主能力实现：插件拿到的一切都经过这里。
 *
 * - HTTP：AhuHttp.plain() 的裸客户端——无 CookieJar、无自动登录、无全局拦截器，
 *   插件顺不走主 App 的任何登录态（教务/一卡通/学习通）。
 * - 存储：SharedPreferences 命名空间 plugin_<id>，插件间互不可见；
 *   未声明 PLUGIN_STORAGE 的插件调用即拒。
 */
class PluginHostServicesImpl(
    override val appContext: Context,
    private val plugin: AhuPlugin
) : PluginHostServices {

    private val httpClient: OkHttpClient by lazy {
        ensure(PluginCapability.NETWORK)
        AhuHttp.plain().build()
    }

    private val pluginStorage: PluginStorage by lazy {
        SharedPrefsPluginStorage(appContext, plugin.meta.id)
    }

    override fun http(): OkHttpClient {
        ensure(PluginCapability.NETWORK)
        return httpClient
    }

    override fun storage(): PluginStorage {
        ensure(PluginCapability.PLUGIN_STORAGE)
        return pluginStorage
    }

    private fun ensure(capability: PluginCapability) {
        if (capability !in plugin.capabilities) {
            throw PluginCapabilityDeniedException(capability, plugin.meta.id)
        }
    }
}

/** SharedPreferences 实现的插件隔离存储。 */
private class SharedPrefsPluginStorage(context: Context, pluginId: String) : PluginStorage {
    private val prefs = context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    override fun getBoolean(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
