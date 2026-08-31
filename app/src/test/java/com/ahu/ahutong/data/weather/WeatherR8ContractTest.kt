package com.ahu.ahutong.data.weather

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeatherR8ContractTest {
    @Test
    fun `release keeps complete Gson weather contracts`() {
        val rules = File(repositoryRoot(), "app/proguard-rules.pro").readText()

        assertTrue(rules.contains("-keep class com.ahu.ahutong.data.weather.** { *; }"))
        assertFalse(
            rules.contains(
                "-keepclassmembers,allowoptimization class com.ahu.ahutong.data.weather.**"
            )
        )
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/proguard-rules.pro").isFile }
    }
}
