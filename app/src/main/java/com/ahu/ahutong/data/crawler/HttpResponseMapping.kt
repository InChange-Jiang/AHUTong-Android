package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import retrofit2.Response

/** 丢弃失败响应前关闭错误体，避免重复失败耗尽 OkHttp 连接。 */
internal fun <T> Response<T>.toClosedFailure(messageOverride: String? = null): AhuResult.Failure {
    errorBody()?.close()
    return AhuResult.Failure(
        AhuError.Server(
            code().takeIf { it != 0 } ?: -1,
            messageOverride ?: message()
        )
    )
}
