package com.ahu.ahutong.data.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 安装包的文件约定与摘要校验的契约测试。
 *
 * 这些规则原先散在 MainViewModel 的私有方法里，只能在真机上间接验证；搬进模块之后
 * 它们第一次有了直接的用例——尤其是"摘要对不上就把文件删掉"这一条，它决定下次会不会
 * 拿一个损坏的包去安装。
 */
class ApkIntegrityTest {

    private val dir: File = Files.createTempDirectory("apk-integrity").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `sha256 of a known payload matches the published digest`() {
        val file = fileOf("hello")

        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ApkIntegrity.sha256Of(file)
        )
    }

    @Test
    fun `a matching digest keeps the file`() {
        val file = fileOf("hello")

        val verified = ApkIntegrity.verifySha256(
            file,
            "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824",
            "test APK"
        )

        assertTrue(verified)
        assertTrue(file.exists())
    }

    @Test
    fun `a mismatching digest deletes the file so it cannot be installed later`() {
        val file = fileOf("hello")

        val verified = ApkIntegrity.verifySha256(file, "deadbeef", "test APK")

        assertFalse(verified)
        assertFalse(file.exists())
    }

    @Test
    fun `stale cleanup removes only update files at or below the current version`() {
        val old = fileOf("", "update-100.apk")
        val current = fileOf("", "update-200.apk")
        val newer = fileOf("", "update-300.apk")
        val partOfOld = fileOf("", "update-100.apk.part")
        val unrelated = fileOf("", "notes.txt")
        val malformed = fileOf("", "update-abc.apk")

        ApkIntegrity.cleanStaleApks(dir, currentVersionCode = 200)

        assertFalse(old.exists())
        assertFalse(partOfOld.exists())
        assertFalse(current.exists())
        assertTrue(newer.exists())
        assertTrue(unrelated.exists())
        assertTrue(malformed.exists())
    }

    @Test
    fun `the apk file name carries the version code`() {
        assertEquals("update-431.apk", ApkIntegrity.apkFile(dir, 431).name)
    }

    private fun fileOf(content: String, name: String = "payload.bin"): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}

