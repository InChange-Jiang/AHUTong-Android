package com.ahu.ahutong.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * 支付页用得到的那一条设置：是否使用内置安全键盘。
 *
 * 之所以单独列出来：付费页只需要问这一个问题，而 [SettingsStore] 有近四十条设置——
 * 让三个付费 ViewModel 的测试去实现整条接口，只为问一句"用内置键盘吗"，并不划算。
 * 这与会话、提醒等接缝同一条取舍：接口按使用者真正要问的问题来定。
 */
interface PaymentKeyboardSetting {

    val useBuiltInSecurePasswordKeyboard: Flow<Boolean>
}

