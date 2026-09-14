package com.ahu.ahutong.core.common

import kotlinx.coroutines.flow.Flow

/**
 * 调试用的假数据信号：mock 场景开关与刷新计数。
 *
 * 界面用它做两件事：判断「没登录也能看」（假数据演示），与「mock 场景被刷新过，重新拉一次」。
 * 产生这两个信号的实现是按构建变体分源集的（debug / release 各一份同名实现），
 * 因此它只能待在 :app——界面通过这个端口读它，于是不必知道 mock 实现存在。
 *
 * 2026-09-14：从 :data:grade 搬进 :core:common。成绩、考试与充值三个 feature 都要用它，
 * 而 feature 之间不能互相依赖；能力接缝的接口住在核心层，是 CourseReminderControl 用过的位置。
 */
interface MockDataSignals {

    /** 调试用的假数据开关：为真时不必登录也能看到数据。 */
    fun usesMockData(): Boolean

    /** mock 场景被刷新过几次（0 = 从未）。 */
    fun mockRefreshRevisions(): Flow<Long>
}
