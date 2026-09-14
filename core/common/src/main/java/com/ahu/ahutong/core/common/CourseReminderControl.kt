package com.ahu.ahutong.core.common

/**
 * 界面要触发的后台动作：课前提醒的排期，以及系统权限入口。
 *
 * 提醒属于后台职责（计划 §3.2 的 :background，P4 收口），所以这里是一组命令，而不是对
 * 调度器或通知器的引用；实现留在 :app（见 AndroidCourseReminderControl）。
 *
 * 放在 :core:common 而不是某个 feature 里，和 UserNotice 同一个理由：它是一份**能力接缝**，
 * 现在已经有两个使用者——设置页（开关提醒）与课表页（课表或学期变化后重算提醒）。
 * 它因此不能住在任何一个 feature 里：feature 之间不允许互相依赖。
 */
interface CourseReminderControl {

    /** 设置变化后重新排期。 */
    fun reschedule()

    /** 提醒被关掉：撤销已排期的任务。 */
    fun cancel()

    /** 岛卡提醒被关掉：撤掉正在显示的那一条。 */
    fun cancelActiveReminder()

    /**
     * 打开系统里管理课前提醒权限的入口。
     *
     * 具体是哪一个页面（岛卡专用页，还是本应用的通知设置页）由实现决定：
     * 界面只说"我要打开提醒的系统入口"，于是它既不需要构造 Intent，也不需要认识回落顺序。
     */
    fun openSystemSettings()
}
