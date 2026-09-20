package com.ahu.ahutong.data.model

/**
 * 登录调用的域结果。
 *
 * 取代旧的 `AHUResponse<User>` + `code == WEB_VERIFICATION_REQUIRED_CODE` 约定：
 * "账号已过校园网、但教务还需要一次安全验证"是一个正常的业务状态，而不是错误码。
 */
sealed interface LoginOutcome {

    /** 登录完成，可以进入主界面。 */
    data class Success(val user: User) : LoginOutcome

    /** 校园网已通过，但教务系统要求先完成一次安全验证；[user] 用于验证完成后的收尾。 */
    data class JwxtWebVerificationRequired(val user: User) : LoginOutcome
}
