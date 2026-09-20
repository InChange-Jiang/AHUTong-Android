package com.ahu.ahutong.personalization.action

/**
 * 一次动作的来源：区分用户自己做的，与建议、深链、恢复等带进来的。
 *
 * 跟着语义词汇待在 :data:personalization：界面与端侧实现都要用它描述"这次是谁触发的"，
 * 但都不该看见对方的实现。
 */
enum class ActionSource {
    ORGANIC,
    SUGGESTION,
    DEEPLINK,
    RESTORE,
    USER_PREFERENCE,
    SYSTEM,
    DEBUG
}

