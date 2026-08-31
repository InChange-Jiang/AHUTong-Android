package com.ahu.ahutong.data.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkSecurityConfigTest {
    @Test
    fun `cleartext is limited to the in-process loopback bridge`() {
        val xml = File(repositoryRoot(), "app/src/main/res/xml/network_security_config.xml")
            .readText()

        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertEquals(1, Regex("<domain-config cleartextTrafficPermitted=\"true\"").findAll(xml).count())
        assertTrue(xml.contains(">127.0.0.1</domain>"))
        assertTrue(xml.contains(">localhost</domain>"))
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/res").isDirectory }
    }
}
