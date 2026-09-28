package com.ahu.ahutong.data.schedule

import java.time.LocalTime

/**
 * Release 空实现：课表时间线恒走系统时间（零偏移），无 prefs 读写。
 * 与 debug 源集的 [ScheduleNowClock] 同签名，主代码无分支引用。
 */
object ScheduleNowClock {
    fun offsetMinutes(): Int = 0

    fun setOffsetMinutes(minutes: Int) = Unit

    fun clear() = Unit

    fun now(): LocalTime = LocalTime.now()
}
