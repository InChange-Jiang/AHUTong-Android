package com.ahu.ahutong.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CasLoginActionResolverTest {
    @Test
    fun resolvesRelativeActionAgainstCasDirectory() {
        assertEquals(
            "https://one.ahu.edu.cn/cas/login?service=campus-card",
            resolveCasLoginAction(
                "https://one.ahu.edu.cn/cas/login?service=campus-card",
                "login?service=campus-card"
            )
        )
    }

    @Test
    fun preservesAbsoluteCasAction() {
        assertEquals(
            "https://one.ahu.edu.cn/cas/login;jsessionid=abc?service=card",
            resolveCasLoginAction(
                "https://one.ahu.edu.cn/cas/login?service=card",
                "/cas/login;jsessionid=abc?service=card"
            )
        )
    }

    @Test
    fun rejectsInvalidPageUrl() {
        assertNull(resolveCasLoginAction("not-a-url", "login"))
    }
}
