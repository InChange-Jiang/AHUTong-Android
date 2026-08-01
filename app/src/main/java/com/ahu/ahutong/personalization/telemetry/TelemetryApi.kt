package com.ahu.ahutong.personalization.telemetry

import com.ahu.ahutong.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TelemetryApi {
    @POST("/v1/on-device-model-evaluations/credentials")
    suspend fun credential(
        @Body request: TelemetryCredentialRequest
    ): retrofit2.Response<TelemetryCredentialResponse>

    @POST("/v1/on-device-model-evaluations/batch")
    suspend fun upload(
        @Header("Authorization") telemetryCredential: String,
        @Header("X-Body-SHA256") bodySha256Hex: String,
        @Body exactBody: RequestBody
    ): retrofit2.Response<ResponseBody>

    @POST("/v1/on-device-model-evaluations/delete")
    suspend fun delete(
        @Body request: TelemetryDeletionRequest
    ): retrofit2.Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://openahu.org"
        private val allowedPaths = setOf(
            "/v1/on-device-model-evaluations/credentials",
            "/v1/on-device-model-evaluations/batch",
            "/v1/on-device-model-evaluations/delete"
        )

        private val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.isHttps && request.url.host == "openahu.org" && request.url.encodedPath in allowedPaths) {
                    "telemetry request escaped the fixed HTTPS allowlist"
                }
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Cookie")
                        .header("User-Agent", "AHUTong/${BuildConfig.VERSION_NAME} (Android telemetry)")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        val API: TelemetryApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApi::class.java)
    }
}
