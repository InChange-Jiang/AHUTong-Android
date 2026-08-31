package com.ahu.ahutong.data.crawler.api.ycard

import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.Token
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface YcardApi {

    @GET("/berserker-auth/cas/redirect/neusoftCas")
    fun login(
        @Query("targetUrl") targetUrl: String = "https://ycard.ahu.edu.cn/plat/?name=loginTransit"
    ): Call<ResponseBody>


    @GET("/berserker-app/ykt/tsm/queryCard")
    suspend fun loadCardRecharge(
        @Query("scene") scene: String = "cardRecharge",
        @Query("synAccessSource") synAccessSource: String = "h5",
    ): Response<CardInfo>

    @GET("/charge/feeitem/toAppitem")
    suspend fun enterFeeItem(
        @Query("feeitemid") feeitemid: String,
        @Query("appId") appId: String,
        @Query("loginFrom") loginFrom: String = "h5",
        @Query("synAccessSource") synAccessSource: String = "h5",
        @Query("synjones-auth") synjonesAuth: String
    ): Response<ResponseBody>

    @Headers("Referer: https://ycard.ahu.edu.cn/charge-app/")
    @GET("/charge/feeitem/singleFeeitem")
    suspend fun getSingleFeeItem(
        @Query("feeitemid") feeitemid: String
    ): Response<ResponseBody>

    @POST("/charge/order/thirdOrder")
    suspend fun getOrderThirdData(@Body body: RequestBody): Response<ResponseBody>

    @Headers(
        "Referer: https://ycard.ahu.edu.cn/charge-app/",
        "Origin: https://ycard.ahu.edu.cn"
    )
    @POST("/charge/feeitem/getThirdData")
    suspend fun getFeeItemThirdData(@Body body: RequestBody): Response<ResponseBody>

    @Headers(
        "Referer: https://ycard.ahu.edu.cn/charge-app/",
        "Origin: https://ycard.ahu.edu.cn"
    )
    @POST("/blade-pay/pay")
    suspend fun pay(
        @Body body: RequestBody
    ): Response<ResponseBody>

//    @GET("/charge/order/personal_data")
//    suspend fun getPersonalData


    @FormUrlEncoded
    @POST("/berserker-auth/oauth/token")
    fun getToken(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password",
        @Field("scope") scope: String = "all",
        @Field("loginFrom") loginFrom: String = "h5",
        @Field("logintype") loginType: String = "sso",
        @Field("device_token") deviceToken: String = "h5",
        @Field("synAccessSource") synAccessSource: String = "h5",
        @Header("Authorization") authorization: String = "Basic bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm06bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm1fc2VjcmV0",
    ): Call<Token>
    /*
    username=&password=&grant_type=password&scope=all&loginFrom=h5&logintype=sso&device_token=h5&synAccessSource=h5
    * */

    companion object {

        internal const val BASE_URL = "https://ycard.ahu.edu.cn/"
        internal const val LOGIN_TARGET_URL = "https://ycard.ahu.edu.cn/plat/?name=loginTransit"


        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Synjones-Auth")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        private val cookieJar = CookieManager.cookieJar


        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val isTokenRequest = request.url.encodedPath.contains("/oauth/token") || request.url.encodedPath.contains("/neusoftCas")
            if (isTokenRequest) {
                return@Interceptor chain.proceed(request)
            }

            var token = TokenManager.getToken()

            val newRequest = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) {
                    header("Synjones-Auth", "bearer $token")
                }
            }.build()

            chain.proceed(newRequest)
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(interceptor = authInterceptor)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor)
            }
            .build()

        /**
         * The SSO bootstrap must stop as soon as CAS emits a service ticket. Following that
         * redirect all the way into loginTransit can cycle back through neusoftCas before the
         * caller has extracted the one-shot ticket.
         */
        internal val loginRedirectClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val API = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YcardApi::class.java)

        /**
         * Runs an authenticated campus-card request and retries once when its token has
         * expired. Keeping the refresh at the suspending call site avoids blocking OkHttp's
         * interceptor threads and coalesces concurrent refreshes in [TokenManager].
         */
        suspend fun <T> authorizedCall(
            request: suspend YcardApi.() -> Response<T>
        ): Response<T> {
            val attemptedToken = TokenManager.awaitToken()
            if (attemptedToken.isNullOrBlank()) {
                return Response.error(
                    401,
                    "校园卡登录凭证不可用".toResponseBody("text/plain".toMediaType())
                )
            }
            val firstResponse = API.request()
            if (firstResponse.code() != 401) return firstResponse

            firstResponse.errorBody()?.close()
            if (TokenManager.refreshAfterUnauthorized(attemptedToken).isNullOrBlank()) {
                return firstResponse
            }
            return API.request()
        }

    }
}
