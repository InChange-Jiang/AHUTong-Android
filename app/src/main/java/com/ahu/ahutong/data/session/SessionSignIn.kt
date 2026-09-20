package com.ahu.ahutong.data.session

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.LoginOutcome

/**
 * 会话层执行"一次登录协议动作"的能力。
 *
 * [AhuSession] 因此不必认识具体网关：生产装配（[DefaultAhuSession]）走仓库层
 * （爬虫 / 原生 / 回退策略保持不变），测试注入固定结果。
 * 这是让 ADR 0002 的登录流程能在 JVM 上被驱动的关键一刀。
 */
fun interface SessionSignIn {

    /** [preferNative]：首登原生优先，会话续期明确走爬虫。 */
    suspend fun signIn(
        username: String,
        password: String,
        preferNative: Boolean
    ): AhuResult<LoginOutcome>
}

