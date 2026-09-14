package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager

/**
 * [AhuSession] 的生产装配：真实的登录动作、凭据保险箱、身份存储与残留清理。
 *
 * 装配收在一处，是为了让"换实现"只发生一次；将来 `:core:auth` 真的抽成模块时，
 * 这里就是它的边界——届时四个适配器由 `:app` 注入。
 */
object DefaultAhuSession : AhuSession by RepositoryAhuSession(
    login = SessionSignIn { username, password, preferNative ->
        AHURepository.loginWithCrawler(username, password, preferNative)
    },
    credentials = SecureCredentialVault,
    account = SessionAccount { SessionStore.currentUser() },
    residue = object : SessionResidue {

        override fun clearDerivedToken() {
            // 续期成功后只丢派生的校园卡令牌：第一方 Cookie 是刚建立的会话本身。
            TokenManager.clear()
        }

        override suspend fun clear() {
            EvaluationRepository.clearSession()
            TokenManager.clear()
            CookieManager.cookieJar.clear()
        }
    }
)
