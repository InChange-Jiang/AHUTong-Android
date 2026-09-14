package com.ahu.ahutong.data.crawler.net

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 识别"被重定向回校内登录页"，把它翻译成一个明确的 401，并通知会话层。
 *
 * 网络层不自己改登录态：续期与状态写入都经 [SessionExpiryHook] 交给会话层。
 */
class AutoLoginInterceptor(private val sessionExpiryHook: SessionExpiryHook) : Interceptor {

    val TAG = "AutoLoginInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = SessionRefreshCoordinator.tagRequest(chain.request())
        val response = chain.proceed(originalRequest)
        Log.d(TAG, "first-party request completed with status=${response.code}")

        val location = response.header("Location")
        if (
            response.code in 300..399 &&
            SessionRefreshPolicy.isFirstPartyLoginRedirect(originalRequest.url, location)
        ) {
            Log.i(TAG, "First-party session redirect detected")
            sessionExpiryHook.onExpired()
            return response.newBuilder()
                .code(401)
                .header(SessionRefreshPolicy.EXPIRED_RESPONSE_HEADER, "1")
                .build()
        }

        return response
    }
}
