package com.ahu.ahutong.data.update

import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.network.retrofit
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import javax.inject.Inject

/**
 * APK 下载专用的 Retrofit 接口。
 *
 * 与普通 API 分享同一个仓库工厂，但用**独立的 dispatcher** 并且**不跟随重定向**：
 * 重定向由下载器逐跳校验目标主机（计划 P4 要求校验不可绕过），跟着跳会绕过它。
 * 独立 dispatcher 是为了"切镜像时取消下载"不会连累普通接口调用。
 */
interface ApkDownloadApi {

    @Streaming
    @GET
    suspend fun downloadByUrl(@Url fileUrl: String): Response<ResponseBody>

    companion object {

        private const val BASE_URL = "https://openahu.org"

        private val client: OkHttpClient by lazy {
            val base = AhuHttp.plain(
                connectTimeoutSeconds = 20,
                readTimeoutSeconds = 120,
                writeTimeoutSeconds = 30,
                callTimeoutSeconds = 900
            )
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", userAgent())
                        .header("Accept", "*/*")
                        .build()
                    chain.proceed(request)
                }
                .build()
            createApkDownloadClient(base)
        }

        val API: ApkDownloadApi by lazy { retrofit(BASE_URL, client).create(ApkDownloadApi::class.java) }

        /** 取消所有进行中的下载（切镜像时用：关掉 socket，别等读超时）。 */
        fun cancelAll() {
            client.dispatcher.cancelAll()
        }

        private fun userAgent(): String {
            val context = AppEnvironmentHolder.context()
            val versionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
            return "AHUTong/$versionName (Android)"
        }
    }
}

/** 下载器拥有的传输接缝：生产走 Retrofit，测试可提供内存响应并观察取消。 */
interface ApkDownloadTransport {
    suspend fun download(url: String): Response<ResponseBody>
    fun cancelAll()
}

class RetrofitApkDownloadTransport @Inject constructor() : ApkDownloadTransport {
    override suspend fun download(url: String): Response<ResponseBody> =
        ApkDownloadApi.API.downloadByUrl(url)

    override fun cancelAll() = ApkDownloadApi.cancelAll()
}

internal fun createApkDownloadClient(baseClient: OkHttpClient): OkHttpClient =
    baseClient.newBuilder()
        // Cancelling an APK or switching its mirror must not cancel ordinary API calls.
        .dispatcher(Dispatcher())
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
