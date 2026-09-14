package com.ahu.ahutong.data.debug

/**
 * 调试用"当前时间"的来源：[DebugClock] 只依赖这个接口，时间相关的领域逻辑
 * （教学周解析、提醒调度）因此不再依赖业务缓存这类存储实现。
 *
 * 与 AppEnvironment 的差别是刻意的：缺少 Context 没有合理默认值，那里"未安装即抛错"；
 * 而缺少时间来源时"用真实时间"本身就是正确行为，所以这里允许不安装。
 */
interface DebugTimeSource {

    /** 返回被 mock 的当前时间；未启用 mock 时返回 null，表示使用真实时间。 */
    fun mockedNowMillis(): Long?
}

/** [DebugTimeSource] 的安装点。只有 `:app` 的 Application 允许调用 [install]。 */
object DebugTimeSourceHolder {

    @Volatile
    private var installed: DebugTimeSource? = null

    fun install(source: DebugTimeSource) {
        installed = source
    }

    /** 未安装即视为"没有 mock"。 */
    fun mockedNowMillis(): Long? = installed?.mockedNowMillis()
}
