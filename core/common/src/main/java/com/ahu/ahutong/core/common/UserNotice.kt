package com.ahu.ahutong.core.common

/**
 * 面向用户的提示出口（异常提示的接缝）。
 *
 * 核心模块只报告"出了什么事"，文案与呈现方式由展示侧决定（ADR 0001 规则 3）：
 * 生产实现是 :app 里的 Toast 适配器，测试可以安装一个记录型 fake。
 *
 * 与 AppEnvironment 的差别是刻意的：缺少 Context 必然出错，所以那里未安装即抛错；
 * 而缺少提示出口的后果只是"少弹一个提示"，因此这里未安装时静默丢弃。
 */
interface UserNotice {

    /** 报告一次失败；实现负责翻译文案并切到正确的线程。 */
    fun showFailure(error: Throwable)
}

/** [UserNotice] 的安装点。只有 `:app` 的 Application 允许调用 [install]。 */
object UserNoticeHolder {

    @Volatile
    private var installed: UserNotice? = null

    fun install(notice: UserNotice) {
        installed = notice
    }

    /** 未安装即不提示。 */
    fun showFailure(error: Throwable) {
        installed?.showFailure(error)
    }
}
