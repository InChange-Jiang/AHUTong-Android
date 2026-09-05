package com.ahu.ahutong.data.crawler.net

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response


class AutoLoginInterceptor : Interceptor {

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
            SessionRefreshCoordinator.markExpired()
            return response.newBuilder()
                .code(401)
                .header(SessionRefreshPolicy.EXPIRED_RESPONSE_HEADER, "1")
                .build()
        }

        return response
    }
}
