package com.ahu.ahutong.core.common

import android.content.Context

/**
 * 应用级环境：需要 Context 的非 UI 代码（存储、Cookie、下载目录、错误提示）
 * 只依赖这个接口，而不再 import :app 里的 Application 类。
 *
 * 为什么要单列一层：分层规则要求 :core:* 与 :data:* 不得依赖 :app，
 * 而"具体 Application 的静态字段"恰好是一条反向依赖，也是数据层脱离真机就测不了的原因
 * （没有真 Application 就跑不起来）。生产实现由 :app 在 Application.onCreate() 最前面安装，
 * 测试可以安装 fake。
 *
 * 局限（记录在案，供后续阶段参考）：这是可安装的定位器，不是构造注入。
 * 等各领域真正抽成 Gradle 模块、Hilt 能跨模块提供 ApplicationContext 时，
 * 应逐步改为构造注入，让本对象退化为 :app 内部的装配细节。
 */
interface AppEnvironment {
    val context: Context
}

/** [AppEnvironment] 的安装点。只有 :app 的 Application 允许调用 [install]。 */
object AppEnvironmentHolder {

    @Volatile
    private var installed: AppEnvironment? = null

    fun install(environment: AppEnvironment) {
        installed = environment
    }

    fun require(): AppEnvironment = installed
        ?: error(
            "AppEnvironment 尚未安装：请在 Application.onCreate() 的最前面调用 " +
                "AppEnvironmentHolder.install(...)"
        )

    /** 需要 Context 时的取值点；未安装即抛错，而不是悄悄退化成空 Context。 */
    fun context(): Context = require().context
}
