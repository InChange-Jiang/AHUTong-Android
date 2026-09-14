package com.ahu.ahutong.data.crawler.login

/**
 * 验证码识别接缝：登录流程只依赖这个抽象。
 *
 * 生产实现是 AhuTong 的 OCR 服务（[AhuTongCaptchaSolver]）；契约测试用固定答案的 fake，
 * 于是登录流程可以在纯 JVM 单测里跑完"断网 / 密码错 / 校方改版"三种失败。
 *
 * 之所以等到现在才建立：按接缝纪律，只有出现第二个真实实现时才值得抽接口——
 * 契约测试就是那个第二实现。
 */
internal interface CaptchaSolver {

    /** 识别给定验证码图片。实现可以抛异常，调用方的重试循环会按登录失败处理。 */
    suspend fun solve(imageBytes: ByteArray): String
}
