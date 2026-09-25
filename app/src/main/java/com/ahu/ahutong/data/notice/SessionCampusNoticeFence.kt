package com.ahu.ahutong.data.notice

import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.session.SessionStore
import javax.inject.Inject

internal class SessionCampusNoticeFence @Inject constructor() : CampusNoticeAccountFence {
    override fun currentAccountId(): String? = SessionStore.currentUser()?.xh
    override fun generation(): Long = SessionRefreshCoordinator.currentGeneration()

    override suspend fun commitIfCurrent(generation: Long, accountId: String, action: () -> Unit): Boolean {
        var committed = false
        SessionRefreshCoordinator.commitIfCurrent(generation) {
            if (currentAccountId() == accountId) {
                action()
                committed = true
            }
        }
        return committed
    }
}
