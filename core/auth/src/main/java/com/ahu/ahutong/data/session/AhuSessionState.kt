package com.ahu.ahutong.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录态的单一真相（ADR 0002 的第一步）。
 *
 * 取代 AHUApplication 上的两个公开静态字段：
 * - `sessionExpired`：原先有 4 处写入、**没有任何读取方**；
 * - `reLoginMutex`：声明后从未被使用。
 *
 * 因此这次收口是行为保持的：写入语义一一对应到 [Status]，同时把状态变成可订阅的单一来源，
 * 后续 UI 与网络层都从这里读取，而不是各自持有全局布尔值。
 */
object AhuSessionState {

    enum class Status {
        /** 尚未登录（初始态）。 */
        Anonymous,

        /** 会话有效（登录成功或刷新成功）。 */
        Authenticated,

        /** 会话已失效，需要重新登录。 */
        Expired
    }

    private val _status = MutableStateFlow(Status.Anonymous)
    val status: StateFlow<Status> = _status

    fun markExpired() {
        _status.value = Status.Expired
    }

    fun markAuthenticated() {
        _status.value = Status.Authenticated
    }

    /** 主动登出：回到未登录状态（与"会话过期"区分，便于 UI 决定是否提示重新登录）。 */
    fun markAnonymous() {
        _status.value = Status.Anonymous
    }
}
