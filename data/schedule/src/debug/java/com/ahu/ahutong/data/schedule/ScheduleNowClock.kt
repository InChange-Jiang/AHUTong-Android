package com.ahu.ahutong.data.schedule

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.time.LocalTime

/**
 * Debug 专用：课表「当前时间」指示线的可 mock 时钟。
 *
 * 线的位置不再定死走系统时间——debug 构建读这里：偏移分钟数持久化在 prefs，
 * 一次设置持续生效，方便反复测试课前悬停/课中/课间/全天结束各种位置。
 * Release 源集里是一个同签名的空实现（恒 0 偏移，走系统时间）。
 *
 * 放在 data:schedule（feature:schedule 可见；app 模块的 mock 调试页也能引到）。
 */
object ScheduleNowClock {
    private const val PREFS_NAME = "ahutong_mock_scenarios"
    private const val KEY_NOW_OFFSET_MINUTES = "schedule_now_offset_minutes"

    /** 相对系统时间的偏移分钟数（可为负，wrap 一天内）。 */
    fun offsetMinutes(): Int =
        prefs().getInt(KEY_NOW_OFFSET_MINUTES, 0)

    fun setOffsetMinutes(minutes: Int) {
        prefs()
            .edit()
            .putInt(KEY_NOW_OFFSET_MINUTES, minutes)
            .apply()
    }

    fun clear() {
        prefs()
            .edit()
            .remove(KEY_NOW_OFFSET_MINUTES)
            .apply()
    }

    /** mock 后的「现在」：系统时间 + 偏移。 */
    fun now(): LocalTime {
        val total = (LocalTime.now().toSecondOfDay() / 60 + offsetMinutes()) % (24 * 60)
        val wrapped = if (total < 0) total + 24 * 60 else total
        return LocalTime.ofSecondOfDay(wrapped * 60L)
    }

    private fun prefs() =
        AppEnvironmentHolder.context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
