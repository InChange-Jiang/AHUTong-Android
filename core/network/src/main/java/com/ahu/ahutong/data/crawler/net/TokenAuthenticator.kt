package com.ahu.ahutong.data.crawler.net

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val sessionExpiryHook: SessionExpiryHook) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null
        if (!SessionRefreshPolicy.isMarkedExpired(
                response.header(SessionRefreshPolicy.EXPIRED_RESPONSE_HEADER)
            )
        ) return null

        val observedGeneration = SessionRefreshCoordinator.observedGeneration(response.request)
        return runBlocking {
            val refreshed = sessionExpiryHook.refresh(observedGeneration)
            if (!refreshed) return@runBlocking null

            response.request.newBuilder()
                .removeHeader("Cookie")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
