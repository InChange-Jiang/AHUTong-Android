package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User

/**
 * 会话身份的读取（当前登录用户）。
 *
 * 续期要"拿存储的凭据重新登录"，其中"是谁"由它提供：实现是 [SessionStore]，
 * 接口让会话协调器不必依赖只能在设备上运行的 Keystore / MMKV。
 */
fun interface SessionAccount {

    fun currentUser(): User?
}

