package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.util.Log
import com.ahu.ahutong.core.plugin.AhuPlugin
import java.util.ServiceLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件注册表 = 编译期内置（ServiceLoader）+ 运行期安装（.ahup）的合并视图。
 *
 * 宿主对插件零静态引用：内置插件拔模块即消失；运行期插件卸载即消失。
 * [plugins] 是 StateFlow——安装/卸载后 UI 与路由自动重组。
 */
object PluginRegistry {

    private val _plugins = MutableStateFlow<List<AhuPlugin>>(emptyList())
    val plugins: StateFlow<List<AhuPlugin>> = _plugins.asStateFlow()

    private var initialized = false

    /** 进程内初始化一次（Main 启动时调用）。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        reload(context)
    }

    /** 安装/卸载后调用：重新发现全部插件。 */
    fun reload(context: Context) {
        val builtIn = runCatching {
            ServiceLoader.load(AhuPlugin::class.java, AhuPlugin::class.java.classLoader).toList()
        }.onFailure { Log.e(TAG, "内置插件发现失败", it) }
            .getOrDefault(emptyList())
        val runtime = RuntimePluginLoader.loadAll(context.applicationContext)
        _plugins.value = builtIn + runtime
        Log.i(TAG, "插件注册表：内置 ${builtIn.size} 个，运行期 ${runtime.size} 个")
    }

    fun byId(id: String): AhuPlugin? = _plugins.value.firstOrNull { it.meta.id == id }

    private const val TAG = "PluginRegistry"
}
