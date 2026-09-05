package com.ahu.ahutong.data.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApkDownloadArchitectureTest {
    @Test
    fun `mirror switch closes active calls before joining the old download`() {
        val viewModel = source("com/ahu/ahutong/ui/state/MainViewModel.kt")
        val cancelIndex = viewModel.indexOf("AhuTong.cancelApkDownloads()")
        val joinIndex = viewModel.indexOf("previousDownload?.join()")

        assertTrue(cancelIndex >= 0)
        assertTrue(joinIndex > cancelIndex)
        assertTrue(source("com/ahu/ahutong/data/server/AhuTong.kt").contains("dispatcher.cancelAll()"))
    }

    @Test
    fun `normal APK finalization hashes the completed part only once`() {
        val viewModel = source("com/ahu/ahutong/ui/state/MainViewModel.kt")

        assertEquals(1, Regex("sha256Of\\(partFile\\)").findAll(viewModel).count())
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot(),
        "app/src/main/java/$relativePath"
    ).readText()

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
