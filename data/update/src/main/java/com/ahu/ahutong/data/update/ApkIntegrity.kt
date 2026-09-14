package com.ahu.ahutong.data.update

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * 安装包的本地文件约定与完整性校验：文件名、残留清理、SHA-256 计算与核对。
 *
 * 从 MainViewModel 搬来，行为与日志逐字保持一致（含对不上摘要时删掉文件的处理）——
 * 那时它是 ViewModel 的私有方法，任何新的下载/安装路径都写不到同一份规则。
 */
object ApkIntegrity {

    private const val TAG = "ApkUpdate"
    private const val BUFFER_SIZE = 64 * 1024

    /** 更新包的命名：update-<versionCode>.apk（下载中的中间文件是 .part / .meta）。 */
    private val apkFileRegex = Regex("""^update-(\d+)\.apk(?:\.(?:part|meta))?$""")

    fun apkFile(dir: File, versionCode: Int): File = File(dir, "update-$versionCode.apk")

    /** 计算文件 SHA-256，返回小写 hex。必须在 IO 线程调用。 */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 核对摘要；对不上就删掉文件，避免下次又拿它去安装。 */
    fun verifySha256(file: File, expectedSha256: String, label: String): Boolean {
        val hash = runCatching { sha256Of(file) }.getOrNull()
        val match = hash.equals(expectedSha256, ignoreCase = true)
        if (!match) {
            Log.w(TAG, "$label sha256 mismatch, expected=$expectedSha256, got=$hash, deleting")
            file.delete()
        }
        return match
    }

    /** 清理版本号 <= 当前版本的残留 APK，跳过不符合命名规范的文件。 */
    fun cleanStaleApks(dir: File, currentVersionCode: Int) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val match = apkFileRegex.matchEntire(file.name) ?: continue
            val versionCode = match.groupValues[1].toIntOrNull() ?: continue
            if (versionCode <= currentVersionCode) {
                Log.i(TAG, "deleting stale APK: ${file.name}")
                file.delete()
            }
        }
    }
}

