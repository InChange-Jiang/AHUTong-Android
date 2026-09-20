package com.ahu.ahutong.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.io.File
import java.security.MessageDigest

/**
 * 安装前校验：体积、路径可信、包名、版本单调性、签名指纹。
 *
 * 从 MainActivity 搬来——那时它在 Activity 的私有方法里，任何新的安装路径都可能绕开这几条检查。
 * 返回 null 表示通过，否则返回**直接给用户看的原因**（文案与迁移前逐字一致）。
 *
 * 它必须跑在真实设备上：签名与包信息来自 PackageManager，JVM 单测只能覆盖纯逻辑部分（见 ApkIntegrity）。
 */
object ApkVerifier {

    private const val TAG = "ApkUpdate"

    fun verifyBeforeInstall(apkFile: File): String? {
        val context = AppEnvironmentHolder.context()
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return "安装包不存在或为空"
        }

        val canonicalApk = runCatching { apkFile.canonicalFile }.getOrElse {
            return "安装包路径无效"
        }
        val trustedDirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        val isInTrustedDir = trustedDirs.any { dir ->
            canonicalApk.path == dir.path || canonicalApk.path.startsWith(dir.path + File.separator)
        }
        if (!isInTrustedDir) {
            return "安装包位置不可信"
        }

        val packageManager = context.packageManager
        val packageName = context.packageName
        val flags = signatureFlags()

        val archiveInfo = packageManager.getPackageArchiveInfo(canonicalApk.absolutePath, flags)
            ?: return "安装包解析失败"
        if (archiveInfo.packageName != packageName) {
            return "安装包包名不匹配"
        }

        if (versionCodeOf(archiveInfo) <= currentVersionCode(packageManager, packageName)) {
            return "安装包版本不高于当前版本"
        }

        if (!hasMatchingSigningCertificate(packageManager, packageName, archiveInfo, flags)) {
            return "安装包签名与当前应用不一致"
        }

        return null
    }

    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun hasMatchingSigningCertificate(
        packageManager: PackageManager,
        packageName: String,
        archiveInfo: PackageInfo,
        flags: Int
    ): Boolean {
        val installedInfo = try {
            packageManager.getPackageInfo(packageName, flags)
        } catch (e: Exception) {
            Log.w(TAG, "failed to read installed package signatures", e)
            return false
        }

        val archiveSigners = signatureDigests(archiveInfo, includeHistory = false)
        val installedSigners = signatureDigests(installedInfo, includeHistory = false)
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return archiveSigners == installedSigners
        }

        val archiveSigningInfo = archiveInfo.signingInfo ?: return false
        val installedSigningInfo = installedInfo.signingInfo ?: return false
        if (archiveSigningInfo.hasMultipleSigners() || installedSigningInfo.hasMultipleSigners()) {
            return archiveSigners == installedSigners
        }

        val archiveHistory = signatureDigests(archiveInfo, includeHistory = true)
        val installedHistory = signatureDigests(installedInfo, includeHistory = true)
        return archiveSigners.any { it in installedHistory } ||
            installedSigners.any { it in archiveHistory }
    }

    private fun signatureDigests(info: PackageInfo, includeHistory: Boolean): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (includeHistory && !signingInfo.hasMultipleSigners()) {
                signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners
            } else {
                signingInfo.apkContentsSigners
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun currentVersionCode(packageManager: PackageManager, packageName: String): Long {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return versionCodeOf(packageInfo)
    }

    private fun versionCodeOf(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
}

