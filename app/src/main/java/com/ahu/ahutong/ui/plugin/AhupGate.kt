package com.ahu.ahutong.ui.plugin

import android.content.Context

/**
 * 插件总开关（开发者选项）：关 = 全部门禁——卸载全部运行期插件并清空其存储。
 * 开关状态存独立 prefs，卸载连带 plugin_<id> 存储一起清，关闭即彻底退场。
 */
object AhupGate {

    private const val PREFS = "ahup_gate"
    private const val KEY_DEV_MODE = "dev_mode"

    fun isDevMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEV_MODE, false)

    fun setDevMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEV_MODE, enabled).apply()
        if (!enabled) uninstallAll(context)
    }

    /** 卸载全部运行期插件 + 清空每个插件的隔离存储。 */
    fun uninstallAll(context: Context) {
        AhupInstaller.installedIds(context).forEach { id ->
            AhupInstaller.uninstall(context, id)
            context.getSharedPreferences("plugin_$id", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        PluginRegistry.reload(context)
    }
}
