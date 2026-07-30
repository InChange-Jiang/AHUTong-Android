package com.ahu.ahutong.ui.screen.setup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebLoginUrlMatcherTest {

    @Test
    fun recognizesJwxtSsoUrlWithTicketAndTrailingSlash() {
        assertTrue(isJwxtSsoUrl("https://jw.ahu.edu.cn/student/sso/login"))
        assertTrue(isJwxtSsoUrl("https://jw.ahu.edu.cn/student/sso/login/"))
        assertTrue(isJwxtSsoUrl("https://jw.ahu.edu.cn/student/sso/login?ticket=ST-1"))
    }

    @Test
    fun rejectsOtherOrUntrustedUrls() {
        assertFalse(isJwxtSsoUrl("https://jw.ahu.edu.cn/student/home"))
        assertFalse(isJwxtSsoUrl("http://jw.ahu.edu.cn/student/sso/login"))
        assertFalse(isJwxtSsoUrl("https://jw.ahu.edu.cn.example.com/student/sso/login"))
        assertFalse(isJwxtSsoUrl("not a url"))
        assertFalse(isJwxtSsoUrl(null))
    }
}
