package com.ahu.ahutong.data

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.toUserMessage
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR 0001 的异常映射契约：同一类失败在任何模块都必须落到同一个错误分支，
 * 否则 UI 又会退化成看字符串判断。
 */
class AhuErrorMappingTest {

    @Test
    fun `socket timeout maps to Timeout`() {
        assertEquals(AhuError.Timeout, SocketTimeoutException("timeout").toAhuError())
    }

    @Test
    fun `dns and connect failures map to Network`() {
        assertEquals(AhuError.Network, UnknownHostException("host").toAhuError())
        assertEquals(AhuError.Network, ConnectException("refused").toAhuError())
        assertEquals(AhuError.Network, IOException("broken pipe").toAhuError())
    }

    @Test
    fun `illegal state maps to ProtocolChanged and keeps the original text`() {
        assertEquals(
            AhuError.ProtocolChanged("课表响应缺少数据"),
            IllegalStateException("课表响应缺少数据").toAhuError()
        )
        assertEquals(
            AhuError.ProtocolChanged("malformed json"),
            JsonSyntaxException("malformed json").toAhuError()
        )
    }

    @Test
    fun `user messages stay stable for upstream texts`() {
        assertEquals("课表响应缺少数据", AhuError.ProtocolChanged("课表响应缺少数据").toUserMessage())
        assertEquals("登录已过期", AhuError.Unauthorized("登录已过期").toUserMessage())
        assertEquals("服务异常（500）", AhuError.Server(500, "").toUserMessage())
    }
}
