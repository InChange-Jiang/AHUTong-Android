package com.ahu.ahutong.data.crawler.login

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.configs.Constants
import com.ahu.ahutong.data.crawler.model.adwnh.Info
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.toAhuError
import com.ahu.ahutong.utils.DES
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup

/**
 * 爬虫登录的结局。
 *
 * 关键点是"被拒绝"与"对方改版"必须分开：前者要用户改输入，后者要开发者改解析，
 * 而迁移前这两种情况都只给一句"登录失败"。
 */
internal sealed interface CrawlerLoginOutcome {

    data class Succeeded(val user: User) : CrawlerLoginOutcome

    /** 校园网已过、教务还要安全验证：这是域状态，不是错误。 */
    data class WebVerificationRequired(val user: User) : CrawlerLoginOutcome

    /** 登录页正常返回，但校方不接受这组凭据。 */
    data object CredentialsRejected : CrawlerLoginOutcome

    /** 页面结构与预期不符——不是网络问题，是对方改版。 */
    data class ProtocolChanged(val detail: String) : CrawlerLoginOutcome

    /** 上游明确返回的业务错误（安大智慧拒绝并给了原因）。 */
    data class Upstream(val code: Int, val message: String) : CrawlerLoginOutcome

    /** 传输层问题（断网 / 超时）：与"校方拒绝"和"校方改版"都不是一回事。 */
    data class TransportFailure(val error: AhuError) : CrawlerLoginOutcome
}

/**
 * 爬虫登录的全部协议动作与失败分类（原 AHURepository.loginWithCrawler 的爬虫分支）。
 *
 * 单独成类的理由：登录是最要紧的一条路径，而它的失败语义原先只有一句"登录失败"——
 * 断网、密码错、校方改版在界面上完全一样，用户与客服都无从处置。
 * 失败被分成 [CrawlerLoginOutcome] 的几种情况之后，就可以用 fixture 在 JVM 上验证：
 * 两个协议客户端与验证码识别都是构造参数（生产实现见 [AhuTongCaptchaSolver]）。
 *
 * 本类只负责协议与分类，不碰存储与 Cookie；成功后的副作用由调用方负责。
 */
internal class CrawlerLoginFlow(
    private val jwxt: JwxtApi,
    private val adwmh: AdwmhApi,
    private val captchaSolver: CaptchaSolver,
    /** CAS 根地址；生产固定，契约测试指向本地 fixture 服务。 */
    private val casBaseUrl: String = CAS_BASE_URL,
    /** 教务服务的 service 参数，原样拼进 CAS 登录 URL（与迁移前逐字一致）。 */
    private val jwxtServiceParam: String = JWXT_SERVICE_PARAM
) {

    suspend fun login(username: String, password: String): CrawlerLoginOutcome = try {
        loginOrThrow(username, password)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val error = e.toAhuError()
        // 归类不了的一律照旧抛出：不能把程序错误伪装成"网络不可用"。
        if (error is AhuError.Unknown) throw e
        CrawlerLoginOutcome.TransportFailure(error)
    }

    private suspend fun loginOrThrow(
        username: String,
        password: String
    ): CrawlerLoginOutcome = coroutineScope {
        val adwmhLogin = async(Dispatchers.IO) { loginAdwmh(username, password) }
        val jwxtLogin = async { loginJwxt(username, password) }

        val info = adwmhLogin.await()
        val jwxtResult = jwxtLogin.await()

        val user = info
            ?.takeIf { it.code == ADWMH_SUCCESS_CODE }
            ?.let { User(it.`object`.user.userName, it.`object`.user.idNumber) }

        if (user != null && jwxtResult == JwxtLoginResult.Succeeded) {
            return@coroutineScope CrawlerLoginOutcome.Succeeded(user)
        }
        if (user != null && jwxtResult == JwxtLoginResult.WebVerificationRequired) {
            return@coroutineScope CrawlerLoginOutcome.WebVerificationRequired(user)
        }

        // 走到这里就是失败。按最可信的信号分类，而不是一律报"登录失败"。
        return@coroutineScope when (val reason = (jwxtResult as? JwxtLoginResult.Failed)?.reason) {
            JwxtFailure.NoLoginForm ->
                CrawlerLoginOutcome.ProtocolChanged("登录页没有找到登录表单（校方可能已改版）")
            JwxtFailure.EmptyBody ->
                CrawlerLoginOutcome.ProtocolChanged("登录页响应为空（校方可能已改版）")
            is JwxtFailure.HttpError ->
                CrawlerLoginOutcome.Upstream(reason.code, "登录页返回 HTTP " + reason.code)
            JwxtFailure.CredentialsRejected ->
                CrawlerLoginOutcome.CredentialsRejected
            null -> {
                // 教务侧没报错，那就是安大智慧拒绝了这次登录：带上它的错误码与原文。
                CrawlerLoginOutcome.Upstream(
                    info?.code ?: -1,
                    info?.msg?.takeIf { it.isNotBlank() } ?: "登录失败"
                )
            }
        }
    }

    /** 教务侧（CAS）登录：把失败原因带出来，供上层分类。 */
    private suspend fun loginJwxt(username: String, password: String): JwxtLoginResult {
        val loginPage = jwxt.fetchLoginInfo()
        val finalUrl = loginPage.raw().request.url.toString()

        if (loginPage.code() == WEB_VERIFICATION_REQUIRED_CODE) {
            loginPage.errorBody()?.close()
            Log.w(TAG, "JWXT browser verification required")
            return JwxtLoginResult.WebVerificationRequired
        }

        if (!loginPage.isSuccessful) {
            loginPage.errorBody()?.close()
            Log.w(TAG, "JWXT login page failed with HTTP " + loginPage.code())
            return JwxtLoginResult.Failed(JwxtFailure.HttpError(loginPage.code()))
        }

        val loginBody = loginPage.body()
        if (loginBody == null) {
            Log.w(TAG, "JWXT login page returned an empty body")
            return JwxtLoginResult.Failed(JwxtFailure.EmptyBody)
        }

        val document = Jsoup.parse(loginBody.use { it.string() })
        val lt = document.selectFirst("input[name=lt]")?.attr("value")

        if (lt == null) {
            // 登录页上没有 CAS 表单字段：不是网络问题，是校方把页面改了。
            Log.w(TAG, "JWXT login page has no CAS form field")
            return if (finalUrl.endsWith(Constants.JWXT_HOME)) {
                JwxtLoginResult.Succeeded
            } else {
                JwxtLoginResult.Failed(JwxtFailure.NoLoginForm)
            }
        }

        val cipher = DES().strEnc(username + password + lt, "1", "2", "3")
        // 显式给出协议里的默认字段：既让 wire 字段在流程里可见，也避免调用 Kotlin 生成的
        // 默认参数桥接方法——那个桥接是 JwxtApi 的静态成员，会连带初始化整套 Android 客户端。
        val device = jwxt.device(
            casBaseUrl + "/cas/device",
            username.length,
            password.length,
            cipher,
            "login"
        )
        Log.d(TAG, "JWXT device handshake completed with HTTP " + device.code())

        val jwxtLoginUrl = casBaseUrl + "/cas/login?service=" + jwxtServiceParam
        val response = jwxt.login(
            jwxtLoginUrl,
            cipher,
            username.length,
            password.length,
            lt,
            "e1s1",
            "submit"
        )

        return if (response.raw().request.url.toString().endsWith(Constants.JWXT_HOME)) {
            JwxtLoginResult.Succeeded
        } else {
            // 表单在、请求也发出去了，却没能进主页：校方不接受这组凭据。
            JwxtLoginResult.Failed(JwxtFailure.CredentialsRejected)
        }
    }

    /** 安大智慧侧登录：走 OCR 验证码，最多重试 [CAPTCHA_RETRY_LIMIT] 次。 */
    private suspend fun loginAdwmh(username: String, password: String): Info? {
        var failedTimes = 0
        var info: Info? = null
        // 验证码识别本身会失败，所以重试；但一次失败的识别不能连坐并行的教务会话刷新。
        while (failedTimes < CAPTCHA_RETRY_LIMIT) {
            Log.d(TAG, "ADWMH login attempt " + (failedTimes + 1))
            val captchaBytes = adwmh.getAuthCode().bytes()
            val captcha = captchaSolver.solve(captchaBytes)
            info = adwmh.loginWithCaptcha(username, password, 0, captcha).use { body ->
                Gson().fromJson(body.string(), Info::class.java)
            }
            if (info?.code == ADWMH_SUCCESS_CODE) {
                Log.i(TAG, "ADWMH login succeeded")
                return info
            }
            failedTimes++
        }
        return info
    }

    /** 教务侧的登录结果。 */
    private sealed interface JwxtLoginResult {
        data object Succeeded : JwxtLoginResult
        data object WebVerificationRequired : JwxtLoginResult
        data class Failed(val reason: JwxtFailure) : JwxtLoginResult
    }

    /** 教务侧失败的原因：分开记下来，上层才能给出不同的处置。 */
    private sealed interface JwxtFailure {
        /** 登录页上没有 CAS 表单字段——页面结构与预期不符。 */
        data object NoLoginForm : JwxtFailure
        /** 表单在、请求也发了，但认证没通过。 */
        data object CredentialsRejected : JwxtFailure
        /** 登录页响应为空。 */
        data object EmptyBody : JwxtFailure
        /** 登录页返回非 2xx。 */
        data class HttpError(val code: Int) : JwxtFailure
    }

    private companion object {
        const val TAG = "CrawlerLoginFlow"
        const val CAS_BASE_URL = "https://one.ahu.edu.cn"
        const val JWXT_SERVICE_PARAM = "https%3A%2F%2Fjw.ahu.edu.cn%2Fstudent%2Fsso%2Flogin"
        const val ADWMH_SUCCESS_CODE = 10000
        const val CAPTCHA_RETRY_LIMIT = 5

        /** 教务要求的浏览器安全验证（原 AHURepository.WEB_VERIFICATION_REQUIRED_CODE）。 */
        const val WEB_VERIFICATION_REQUIRED_CODE = 412
    }
}
