package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.server.AhuTong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 生产实现：把验证码图片交给 AhuTong 的 OCR 服务。请求形状与迁移前逐字一致。 */
internal object AhuTongCaptchaSolver : CaptchaSolver {

    override suspend fun solve(imageBytes: ByteArray): String {
        val part = MultipartBody.Part.createFormData(
            "captcha", "img.jpg",
            imageBytes.toRequestBody("image/jpg".toMediaType())
        )
        return AhuTong.API.getCaptchaResult(part).result
    }
}
