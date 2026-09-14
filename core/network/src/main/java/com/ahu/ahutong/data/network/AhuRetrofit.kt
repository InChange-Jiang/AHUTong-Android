package com.ahu.ahutong.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit 的唯一构造点（OkHttp 侧的唯一构造点是 [AhuHttp]）。
 *
 * 迁移前有 9 处各自写 `Retrofit.Builder()...addConverterFactory(GsonConverterFactory.create())`。
 * 这类重复不会因为漏改而编译失败，只会让某条链路悄悄用上不同的转换器或配置——
 * 门禁 R9 因此禁止 `data/network` 之外出现 `Retrofit.Builder(`。
 * 各 API 依旧各自持有 baseUrl 与 client，因为它们本来就不一样。
 */
fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
