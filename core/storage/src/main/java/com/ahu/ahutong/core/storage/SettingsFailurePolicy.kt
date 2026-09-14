package com.ahu.ahutong.core.storage

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * ADR 0003 给「不能丢」这一档定的失败策略：**读失败回默认值，写失败重试一次**。
 *
 * 两半都在这里，实现只负责接线（[SettingsStore] 的生产实现是 :app 的 PreferencesManager）。
 * 读失败不抛给界面：一条设置读不出来，不该让整个页面崩掉；写失败重试一次是给磁盘抖动留的余地，
 * 第二次仍失败就照实抛给调用方——设置是「不能丢」的一档，静默吞掉比报错更糟。
 *
 * DataStore 的 CorruptionException 也是 IOException，因此损坏文件与读写故障走同一条路。
 */
fun <T> Flow<T>.fallbackToDefaultOnReadFailure(
    default: T,
    onFailure: (Throwable) -> Unit = {}
): Flow<T> = catch { error ->
    if (error is IOException) {
        onFailure(error)
        emit(default)
    } else {
        throw error
    }
}

/** 见 [fallbackToDefaultOnReadFailure]：写失败重试一次，第二次仍失败则把异常抛给调用方。 */
suspend fun <T> retryOnceOnWriteFailure(
    onRetry: (Throwable) -> Unit = {},
    block: suspend () -> T
): T = try {
    block()
} catch (error: IOException) {
    onRetry(error)
    block()
}
