package com.ahu.ahutong.ui.plugin

import android.util.Log
import com.ahu.ahutong.core.plugin.AhuPlugin
import java.util.ServiceLoader

/**
 * 插件注册表：ServiceLoader 从 META-INF/services 发现插件。
 *
 * 宿主对插件**零静态引用**——拔掉某个 feature 插件模块（settings.gradle.kts 去掉一行）
 * 插件即消失，主工程代码一行不用改；反之加回即出现。
 *
 * 发现失败的插件（缺索引文件、构造失败）只记日志，绝不让一个坏插件拖垮宿主。
 */
object PluginRegistry {

    val all: List<AhuPlugin> by lazy {
        runCatching {
            ServiceLoader.load(AhuPlugin::class.java, AhuPlugin::class.java.classLoader)
                .toList()
        }.onFailure { Log.e(TAG, "插件发现失败", it) }
            .getOrDefault(emptyList())
    }

    fun byId(id: String): AhuPlugin? = all.firstOrNull { it.meta.id == id }

    private const val TAG = "PluginRegistry"
}
