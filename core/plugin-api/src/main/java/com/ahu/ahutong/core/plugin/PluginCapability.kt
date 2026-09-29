package com.ahu.ahutong.core.plugin

/**
 * 插件能力声明：插件想要什么，就在这里声明什么。
 *
 * 审核纪律：能力声明会进入代码评审视野；声明了不需要的能力 = 评审打回。
 * 未声明的能力在宿主侧不会被提供（例如未声明 [PLUGIN_STORAGE] 时 host.storage() 抛异常）。
 */
enum class PluginCapability {
    /** 插件可以发起网络请求（宿主提供无全局会话的裸 HTTP 客户端）。 */
    NETWORK,

    /** 插件可以读写自己的隔离存储（命名空间 plugin_<id>，与其他插件/主 App 数据互不可见）。 */
    PLUGIN_STORAGE,

    /** 插件可以使用相机取景与拍照（宿主持有 CAMERA 权限并统一处理运行时授权）。 */
    CAMERA
}
