package com.ahu.ahutong.core.common

/**
 * 统一结果类型：跨模块边界只允许出现"成功值"或 [AhuError]。
 * 与 Kotlin `Result` 的区别是错误类型是封闭的、可穷举的（ADR 0001）。
 */
sealed interface AhuResult<out T> {

    data class Success<T>(val value: T) : AhuResult<T>

    data class Failure(val error: AhuError) : AhuResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    val isFailure: Boolean get() = this is Failure

    fun valueOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AhuError? = (this as? Failure)?.error
}

inline fun <T, R> AhuResult<T>.map(transform: (T) -> R): AhuResult<R> = when (this) {
    is AhuResult.Success -> AhuResult.Success(transform(value))
    is AhuResult.Failure -> this
}

/** 与 Kotlin `Result` 同形的遍历接口，便于既有调用点平滑迁移。 */
inline fun <T> AhuResult<T>.onSuccess(action: (T) -> Unit): AhuResult<T> {
    if (this is AhuResult.Success) action(value)
    return this
}

inline fun <T> AhuResult<T>.onFailure(action: (AhuError) -> Unit): AhuResult<T> {
    if (this is AhuResult.Failure) action(error)
    return this
}
