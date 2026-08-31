package com.ahu.ahutong.data.crawler.net

import com.ahu.ahutong.AHUApplication
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.Request

/** Coordinates one first-party re-login for a burst of expired requests. */
object SessionRefreshCoordinator {
    private val refreshMutex = Mutex()

    @Volatile
    private var generation = 0L

    fun currentGeneration(): Long = generation

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

    fun markExpired() {
        AHUApplication.sessionExpired = true
    }

    suspend fun refreshIfNeeded(
        observedGeneration: Long,
        refresh: suspend () -> Boolean
    ): Boolean = refreshMutex.withLock {
        if (generation != observedGeneration) return@withLock true
        if (!refresh()) return@withLock false

        generation += 1
        AHUApplication.sessionExpired = false
        true
    }
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
