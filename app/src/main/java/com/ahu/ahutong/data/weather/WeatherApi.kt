package com.ahu.ahutong.data.weather

import com.ahu.ahutong.data.network.NetworkLogging
import okhttp3.OkHttpClient
import retrofit2.http.GET
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.ahu.ahutong.data.network.AhuHttp

interface WeatherApi {

    @GET("api/v1/misc/weather")
    suspend fun getWeather(
        @Query("city") city: String? = null,
        @Query("adcode") adcode: String? = null,
        @Query("extended") extended: Boolean = true,
        @Query("indices") indices: Boolean = true,
        @Query("forecast") forecast: Boolean = true,
        @Query("hourly") hourly: Boolean = true,
        @Query("lang") lang: String = "zh"
    ): WeatherResponse

    companion object {
        private val loggingInterceptor = NetworkLogging.debugInterceptor(NetworkLogging.Level.Basic)

        private val okHttpClient = AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 15
        )
            .apply {
                loggingInterceptor?.let { addInterceptor(it) }
            }
            .build()

        val API: WeatherApi = retrofit("https://uapis.cn/", okHttpClient).create(WeatherApi::class.java)
    }
}
