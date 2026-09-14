package com.ahu.ahutong.core.common

/**
 * 错误 → 用户可见文案（ADR 0001 规则 3：文案由展示侧决定，错误类型只携带上游原文）。
 *
 * 它原先在 :app 的 `AhuErrorMapping.kt` 里，ADR 当时就写明「等 P3 抽出 feature 模块时，
 * 它应随展示侧一起搬走」。feature 不能依赖 :app，所以它搬到了这里——与 [AhuError]、
 * [UserNotice] 同层，仍然是**唯一**的翻译点：翻译散开比它待在哪里危险得多。
 */
fun AhuError.toUserMessage(): String = when (this) {
    AhuError.Network -> "网络连接失败，请检查网络后重试"
    AhuError.Timeout -> "请求超时，请稍后重试"
    is AhuError.Unauthorized -> message
    is AhuError.ProtocolChanged -> detail
    is AhuError.Server -> message.ifBlank { "服务异常（" + code + "）" }
    is AhuError.Unknown -> message
}

