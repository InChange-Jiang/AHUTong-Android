package com.ahu.ahutong.data.crawler.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Coordinates one first-party re-login for a burst of expired requests.
 *
 * 只负责"请求代号"：标记请求发出时的会话 generation，并保证并发失效时只真正续期一次。
 * 登录态的写入（过期 / 已认证）不在这里 —— 那是会话层的职责，网络层经 [SessionExpiryHook] 通知，
 * 会话层在自己的实现里写 AhuSessionState。
 */
object SessionRefreshCoordinator {
    private val refreshMutex = Mutex()

    @Volatile
    private var generation = 0L

    /**
     * 已经失败过的那一代。
     *
     * ADR 0002：一次会话失效只允许自动续期一次。成功会推进代号，后到的请求直接复用；
     * 失败若不记下来，排队等锁的每个请求都会拿着同一个代号再登一次（N 个 401 就是 N 次密码登录，
     * 每次还各有一份 30 秒预算）。记下失败之后，同一代的后续请求立刻拿到「续期失败」。
     */
    @Volatile
    private var failedGeneration: Long? = null

    fun currentGeneration(): Long = generation

    /**
     * 一次真正的登录刚刚成功（用户手动登录或完成 Web 验证）：推进代号并清掉失败记忆。
     * 推进代号会让登录前发出的慢响应变成旧响应，避免它在新会话上再次触发自动续期。
     * 与自动续期共用同一把锁，防止较早启动的失败续期在新登录之后重新写回失败记忆。
     */
    suspend fun onAuthenticated(action: () -> Unit = {}) = refreshMutex.withLock {
        generation += 1
        failedGeneration = null
        action()
    }

    /** 主动登出同样推进代号，使所有已发出的请求与在途续期立即失去提交资格。 */
    suspend fun onSignedOut(action: () -> Unit = {}) = refreshMutex.withLock {
        generation += 1
        // 退出后的匿名代号禁止自动续期；否则残留清理完成前的新请求还能拿旧凭据重登。
        // 下一次真实登录会由 onAuthenticated 推进代号并解除这道闸。
        failedGeneration = generation
        action()
    }

    /** 只让仍属于当前会话代号的结果提交副作用；与手动登录的代号推进原子互斥。 */
    suspend fun commitIfCurrent(observedGeneration: Long, action: () -> Unit): Boolean =
        refreshMutex.withLock {
            if (generation != observedGeneration) return@withLock false
            action()
            true
        }

    /**
     * Pins the session generation that was current when a request actually left the client.
     * A slow response from the old session can otherwise arrive just after a successful refresh
     * and incorrectly start another full login.
     */
    fun tagRequest(request: Request): Request {
        if (request.tag(SessionRequestGeneration::class.java) != null) return request
        return request.newBuilder()
            .tag(SessionRequestGeneration::class.java, SessionRequestGeneration(generation))
            .build()
    }

    fun observedGeneration(request: Request): Long =
        request.tag(SessionRequestGeneration::class.java)?.value ?: generation

    suspend fun refreshIfNeeded(
        observedGeneration: Long,
        timeoutMillis: Long = REFRESH_TIMEOUT_MS,
        refresh: suspend () -> Boolean
    ): Boolean = refreshMutex.withLock {
        // 当前代号若由失败续期或主动退出封禁，任何旧/新请求都不得被告知“可以重试”。
        if (failedGeneration == generation) return@withLock false
        if (generation != observedGeneration) return@withLock true
        // ADR 0002 的有界刷新：单次续期带总超时，超时即当失败——不重试，也不推进代号。
        val refreshed = try {
            withTimeout(timeoutMillis) { refresh() }
        } catch (e: TimeoutCancellationException) {
            false
        }
        if (!refreshed) {
            failedGeneration = observedGeneration
            return@withLock false
        }

        generation += 1
        failedGeneration = null
        true
    }

    /** 单次续期的总预算：一次完整登录的合理上限，超了就不再等（ADR 0002）。 */
    const val REFRESH_TIMEOUT_MS = 30_000L
}

internal data class SessionRequestGeneration(val value: Long)

internal object SessionRefreshPolicy {
    const val EXPIRED_RESPONSE_HEADER = "X-AHUTong-Session-Expired"

    fun isMarkedExpired(responseHeader: String?): Boolean = responseHeader == "1"

    fun isFirstPartyLoginRedirect(requestUrl: HttpUrl, location: String?): Boolean {
        val target = location?.let(requestUrl::resolve) ?: return false
        val host = target.host.lowercase()
        if (host != "ahu.edu.cn" && !host.endsWith(".ahu.edu.cn")) return false

        val path = target.encodedPath.lowercase()
        val hasLoginPath = path.contains("tologin") ||
            path.contains("/login") ||
            path.contains("/cas/")
        return hasLoginPath
    }
}
