package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User

/**
 * 会话身份查询：本机存没存着登录用户、存的是谁。
 *
 * 它回答的是 [AhuSession] 没回答的那半个问题：[AhuSession.state] 说的是「会话此刻有效吗」，
 * 而界面常常只需要「这台设备上有没有登录过、是谁」。实现是 :app 的 SessionStore。
 */
interface SessionIdentity {

    /** 本机是否存有已登录用户。 */
    fun isLoggedIn(): Boolean

    /** 当前登录用户；没有则为 null。 */
    fun currentUser(): User?
}

