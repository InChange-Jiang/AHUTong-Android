package com.ahu.ahutong.ui.state

/**
 * 「清除所有数据」这一件事。
 *
 * 它是一串有顺序的本机复位：提醒排期 → 设置 → 个性化学习记录 → 登录态与 WebView Cookie
 * → 业务缓存 → 原生服务 → 第一方 Cookie → 会话标记过期。
 *
 * 这串顺序原先内联在设置页的对话框回调里，只有真机点一次才能验证；收成一个操作之后，
 * 界面只表达「用户确认了」，顺序与范围由实现负责（见 :app 的 DeviceDataReset），
 * 而实现是否被调用、被调用几次，可以在 JVM 单测里断言。
 */
interface AppDataReset {

    /** 清空本机与账号、缓存、设置相关的数据；随后的导航由调用方决定。 */
    suspend fun clearAll()
}

