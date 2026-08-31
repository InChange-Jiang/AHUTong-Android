package com.ahu.ahutong.ui.screen.main

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CmbRechargePageStyleTest {
    private val darkPalette = CmbRechargePagePalette(
        colorScheme = "dark",
        background = "#111111",
        surface = "#222222",
        surfaceVariant = "#333333",
        text = "#EEEEEE",
        secondaryText = "#BBBBBB",
        outline = "#444444",
        accent = "#80BFFF",
        onAccent = "#102030",
        success = "#81C784",
        scrim = "rgba(0, 0, 0, 0.62)"
    )

    @Test
    fun styleScriptCoversTheSavedRechargePageStates() {
        val script = buildCmbRechargeStyleScript(darkPalette)

        assertContains(script, "color-scheme: dark")
        assertContains(script, "#app .van-nav-bar")
        assertContains(script, "display: none !important")
        assertContains(script, "#app .charge")
        assertContains(script, "#app .van-action-sheet")
        assertContains(script, "#app .keyboard")
        assertContains(script, "#app .resultBox")
        assertContains(script, darkPalette.background)
        assertContains(script, darkPalette.text)
        assertContains(script, darkPalette.accent)
    }

    @Test
    fun styleScriptDoesNotHookOrReadThePaymentPage() {
        val script = buildCmbRechargeStyleScript(darkPalette)
        val disallowedOperations = listOf(
            "addEventListener",
            "MutationObserver",
            "XMLHttpRequest",
            "fetch(",
            "document.cookie",
            "localStorage",
            "sessionStorage",
            ".click()",
            ".submit()"
        )

        disallowedOperations.forEach { operation ->
            assertFalse(script.contains(operation), "Unexpected page operation: $operation")
        }
    }

    @Test
    fun styleScriptKeepsCarouselAndPaymentControlsVisuallyIntact() {
        val script = buildCmbRechargeStyleScript(darkPalette)

        assertContains(script, "padding: 20px 0 28px !important")
        assertContains(script, "background-size: 100% 100% !important")
        assertContains(script, "#app .closeAmount .van-hairline--surround::after")
        assertContains(script, "content: none !important")
        assertContains(script, "border-radius: 14px !important")
        assertContains(script, "#app .van-password-input__security li")
        assertContains(script, "background: var(--ahutong-surface-variant) !important")
        assertContains(script, "background: var(--ahutong-text) !important")
    }

    @Test
    fun successBoundsScriptLocatesOnlyTheResultPageReturnButton() {
        val script = buildCmbRechargeSuccessReturnBoundsScript()

        assertContains(
            script,
            "#app button.van-button.van-button--default.van-button--normal.van-button--block.van-button--round"
        )
        assertContains(script, "window.location.hostname.toLowerCase() === 'epay92.ahu.edu.cn'")
        assertContains(script, "window.location.port === '443'")
        assertContains(script, "path === '/cashier-mobile/chargeresult'")
        assertContains(script, "document.querySelector('#app .resultBox')")
        assertContains(script, "document.querySelectorAll(")
        assertContains(script, "button.getBoundingClientRect()")
        assertContains(script, "window.visualViewport")
        assertContains(script, "button.style.pointerEvents = 'none'")

        listOf(
            "addEventListener",
            "document.cookie",
            "localStorage",
            "sessionStorage",
            "XMLHttpRequest",
            "fetch(",
            "MutationObserver",
            "input.value",
            "innerText",
            "textContent"
        ).forEach { operation ->
            assertFalse(script.contains(operation), "Unexpected result hook operation: $operation")
        }
    }

    @Test
    fun styleTargetAllowsOnlyKnownHostsAndPaths() {
        assertTrue(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge?disable=1"
            )
        )
        assertFalse(isCmbRechargeStyleTarget("http://epay92.ahu.edu.cn/cashier-mobile/"))
        assertTrue(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult"
            )
        )
        assertTrue(isCmbRechargeStyleTarget("https://ycard.ahu.edu.cn/charge-app/"))

        assertFalse(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn.evil.example/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn/other?next=/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn/cashier-mobile-redirect/charge"
            )
        )
        assertFalse(isCmbRechargeStyleTarget("https://other.ahu.edu.cn/charge-app/"))
        assertFalse(
            isCmbRechargeStyleTarget(
                "https://epay92.ahu.edu.cn:444/cashier-mobile/charge"
            )
        )
    }

    @Test
    fun successUrlIsStrictlyScoped() {
        assertTrue(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult"
            )
        )
        assertTrue(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult/?order=1"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "http://epay92.ahu.edu.cn/cashier-mobile/chargeResult"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn.evil.example/cashier-mobile/chargeResult"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "http://epay92.ahu.edu.cn:8080/cashier-mobile/chargeResult"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn:444/cashier-mobile/chargeResult"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult-fake"
            )
        )
        assertFalse(
            isCmbRechargeSuccessUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge?next=/cashier-mobile/chargeResult"
            )
        )

    }

    @Test
    fun nativeEntryUrlIsStrictlyScoped() {
        assertFalse(
            isCmbRechargeNativeEntryUrl(
                "http://epay92.ahu.edu.cn/cashier-mobile/charge?disable=1"
            )
        )
        assertTrue(
            isCmbRechargeInsecureEntryUrl(
                "http://epay92.ahu.edu.cn/cashier-mobile/charge?disable=1"
            )
        )
        assertFalse(
            isCmbRechargeInsecureEntryUrl(
                "http://epay92.ahu.edu.cn.evil.example/cashier-mobile/charge"
            )
        )
        assertTrue(
            isCmbRechargeNativeEntryUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge/"
            )
        )
        assertFalse(
            isCmbRechargeNativeEntryUrl(
                "https://epay92.ahu.edu.cn.evil.example/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbRechargeNativeEntryUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult"
            )
        )
        assertFalse(
            isCmbRechargeNativeEntryUrl(
                "https://epay92.ahu.edu.cn:444/cashier-mobile/charge"
            )
        )
    }

    @Test
    fun hiddenFlowUrlIncludesOnlyTheTrustedBootstrapAndNativeEntry() {
        assertTrue(
            isCmbRechargeHiddenFlowUrl(
                "https://ycard.ahu.edu.cn/berserker-base/redirect?appId=253"
            )
        )
        assertTrue(
            isCmbRechargeHiddenFlowUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge?disable=1"
            )
        )
        assertFalse(
            isCmbRechargeHiddenFlowUrl(
                "https://ycard.ahu.edu.cn/berserker-base/redirect/other"
            )
        )
        assertFalse(
            isCmbRechargeHiddenFlowUrl(
                "https://ycard.ahu.edu.cn.evil.example/berserker-base/redirect"
            )
        )
    }

    @Test
    fun webContentIsRevealedOnlyAfterAnExplicitNativeAction() {
        val bootstrapUrl =
            "https://ycard.ahu.edu.cn/berserker-base/redirect?appId=253"
        val nativeEntryUrl =
            "https://epay92.ahu.edu.cn/cashier-mobile/charge?disable=1"
        val officialPaymentUrl =
            "https://epay92.ahu.edu.cn/cashier-mobile/pay"
        val successUrl =
            "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult"

        assertFalse(shouldRevealCmbRechargeWebContent(null, revealAllowed = true))
        assertFalse(shouldRevealCmbRechargeWebContent(bootstrapUrl, revealAllowed = true))
        assertFalse(shouldRevealCmbRechargeWebContent(nativeEntryUrl, revealAllowed = true))
        assertFalse(shouldRevealCmbRechargeWebContent(officialPaymentUrl, revealAllowed = false))
        assertTrue(shouldRevealCmbRechargeWebContent(officialPaymentUrl, revealAllowed = true))
        assertFalse(shouldRevealCmbRechargeWebContent(successUrl, revealAllowed = true))
    }

    @Test
    fun nativeBalanceConvertsServerCentsToYuan() {
        assertEquals(7.79, normalizeCmbRechargeBalance(779.0), 0.0001)
        assertEquals(0.0, normalizeCmbRechargeBalance(Double.NaN), 0.0001)
    }

    @Test
    fun preloadedSessionExpiresAtThreeMinutes() {
        val readyAt = 10_000L

        assertTrue(isCmbRechargeSessionFresh(readyAt, readyAt))
        assertTrue(
            isCmbRechargeSessionFresh(
                readyAt,
                readyAt + CMB_RECHARGE_PRELOAD_VALIDITY_MS - 1L
            )
        )
        assertFalse(
            isCmbRechargeSessionFresh(
                readyAt,
                readyAt + CMB_RECHARGE_PRELOAD_VALIDITY_MS
            )
        )
        assertFalse(isCmbRechargeSessionFresh(0L, readyAt))
        assertFalse(isCmbRechargeSessionFresh(readyAt, readyAt - 1L))
    }

    @Test
    fun rechargeCannotDispatchBeforePasswordIsProvided() {
        assertFalse(
            canDispatchCmbRecharge(
                amount = "100",
                password = null,
                hasFreshSession = true
            )
        )
        assertFalse(
            canDispatchCmbRecharge(
                amount = "100",
                password = "",
                hasFreshSession = true
            )
        )
        assertFalse(
            canDispatchCmbRecharge(
                amount = "100",
                password = "123456",
                hasFreshSession = false
            )
        )
        assertTrue(
            canDispatchCmbRecharge(
                amount = "100",
                password = "123456",
                hasFreshSession = true
            )
        )

        assertTrue(
            canDispatchCmbPassword(
                password = "123456",
                dispatchInProgress = false,
                hasWebView = true
            )
        )
        assertFalse(
            canDispatchCmbPassword(
                password = null,
                dispatchInProgress = false,
                hasWebView = true
            )
        )
        assertFalse(
            canDispatchCmbPassword(
                password = "123456",
                dispatchInProgress = true,
                hasWebView = true
            )
        )
    }

    @Test
    fun expiredSessionRecoveryRequiresCompleteSubmissionAndRunsOnlyOnce() {
        assertTrue(isCmbSessionExpiredMessage("登录已失效，请重新登录"))
        assertTrue(isCmbSessionExpiredMessage("当前会话已过期"))
        assertFalse(isCmbSessionExpiredMessage("查询密码错误"))

        assertTrue(
            shouldRecoverCmbSession(
                message = "登录失效",
                recoveryAttempted = false,
                amount = "100",
                password = "123456"
            )
        )
        assertFalse(
            shouldRecoverCmbSession(
                message = "登录失效",
                recoveryAttempted = true,
                amount = "100",
                password = "123456"
            )
        )
        assertFalse(
            shouldRecoverCmbSession(
                message = "登录失效",
                recoveryAttempted = false,
                amount = "100",
                password = null
            )
        )
        assertFalse(
            shouldRecoverCmbSession(
                message = "查询密码错误",
                recoveryAttempted = false,
                amount = "100",
                password = "123456"
            )
        )
    }

    @Test
    fun loginRedirectAndHttpsUpgradeAreStrictlyScoped() {
        assertTrue(
            isCmbLoginRedirectUrl(
                "https://epay92.ahu.edu.cn/member/login/redirect?ticket=hidden"
            )
        )
        assertFalse(
            isCmbLoginRedirectUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbLoginRedirectUrl(
                "https://epay92.ahu.edu.cn.evil.example/member/login/redirect"
            )
        )
        assertFalse(
            isCmbLoginRedirectUrl(
                "http://epay92.ahu.edu.cn/member/login/redirect"
            )
        )

        assertEquals(
            "https://epay92.ahu.edu.cn/cashier-mobile/cashier",
            buildCmbHttpsCashierUrl(
                "http://epay92.ahu.edu.cn/cashier-mobile/cashier"
            )
        )
        assertEquals(
            "https://epay92.ahu.edu.cn/cashier-mobile/cashier?ticket=hidden&embed=true",
            buildCmbHttpsCashierUrl(
                "http://epay92.ahu.edu.cn/cashier-mobile/cashier?ticket=hidden&embed=true"
            )
        )
        assertNull(
            buildCmbHttpsCashierUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/cashier"
            )
        )
        assertNull(
            buildCmbHttpsCashierUrl(
                "http://epay92.ahu.edu.cn.evil.example/cashier-mobile/cashier"
            )
        )
    }

    @Test
    fun mainFrameAllowlistIncludesTheCmbLoginRedirectOnlyOnTheTrustedOrigin() {
        assertTrue(
            isCmbRechargeAllowedMainFrameUrl(
                "https://epay92.ahu.edu.cn/member/login/redirect?ticket=hidden"
            )
        )
        assertTrue(
            isCmbRechargeAllowedMainFrameUrl(
                "https://epay92.ahu.edu.cn/cashier-mobile/charge"
            )
        )
        assertFalse(
            isCmbRechargeAllowedMainFrameUrl(
                "http://epay92.ahu.edu.cn/member/login/redirect"
            )
        )
        assertFalse(
            isCmbRechargeAllowedMainFrameUrl(
                "https://epay92.ahu.edu.cn.evil.example/member/login/redirect"
            )
        )
        assertFalse(
            isCmbRechargeAllowedMainFrameUrl(
                "https://epay92.ahu.edu.cn/member/login/redirect/other"
            )
        )
    }

    @Test
    fun normalizedOverlayBoundsAreParsedAndValidated() {
        val bounds = assertNotNull(
            parseCmbRechargeNormalizedBounds("[0.05,0.72,0.90,0.08]")
        )
        assertTrue(
            shouldConfirmCmbRechargeSuccess(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult",
                bounds
            )
        )
        assertFalse(
            shouldConfirmCmbRechargeSuccess(
                "https://epay92.ahu.edu.cn/cashier-mobile/chargeResult",
                null
            )
        )
        assertFalse(
            shouldConfirmCmbRechargeSuccess(
                "http://epay92.ahu.edu.cn/cashier-mobile/chargeResult",
                bounds
            )
        )
        assertTrue(bounds.left in 0.049f..0.051f)
        assertTrue(bounds.top in 0.719f..0.721f)
        assertTrue(bounds.width in 0.899f..0.901f)
        assertTrue(bounds.height in 0.079f..0.081f)

        assertNull(parseCmbRechargeNormalizedBounds(null))
        assertNull(parseCmbRechargeNormalizedBounds("null"))
        assertNull(parseCmbRechargeNormalizedBounds("[0,0,1]"))
        assertNull(parseCmbRechargeNormalizedBounds("[NaN,0.7,0.9,0.08]"))
        assertNull(parseCmbRechargeNormalizedBounds("[-0.1,0.7,0.9,0.08]"))
        assertNull(parseCmbRechargeNormalizedBounds("[0.2,0.7,0.9,0.08]"))
        assertNull(parseCmbRechargeNormalizedBounds("[0.05,0.99,0.9,0.08]"))
        assertNull(parseCmbRechargeNormalizedBounds("[0.05,0.7,0.01,0.08]"))
        assertNull(parseCmbRechargeNormalizedBounds("[0.05,0.7,0.9,0.005]"))
    }
}
