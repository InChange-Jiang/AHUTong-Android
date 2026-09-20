package com.ahu.ahutong.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * 课程提醒用得到的两条设置：提醒总开关、实时倒计时开关。
 *
 * 与 [PaymentKeyboardSetting] 同一条取舍：接口按使用者真正要问的问题来定。提醒排期是后台职责，
 * 而 [SettingsStore] 有近四十个成员、其中一半是写入口——把整个门面交出去，等于连"顺手改用户设置"
 * 的能力一起交出去；两条只读流就够了。
 */
interface CourseReminderSettings {

    val courseReminderEnabled: Flow<Boolean>

    val courseReminderLiveCountdownEnabled: Flow<Boolean>
}
