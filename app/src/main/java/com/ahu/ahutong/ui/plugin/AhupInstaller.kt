package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipInputStream

/**
 * .ahup 包的安装/校验/装载。
 *
 * 包格式（ZIP）：
 *   manifest.json  展示用元数据（id/标题/作者/入口类/签名）
 *   plugin.dex     插件代码（dex）
 *   icon.png       入口图标
 *
 * 安全模型：**签名是唯一闸门，签的是可执行体（dex + icon）**。
 * manifest.json 只是展示元数据（不参与签名，篡改了也无害——能力门控读的是已签名的
 * 插件代码里的 capabilities 声明，弹窗展示的以代码声明为准）。
 * 未签名/签名不符的包给强警告，由用户自担风险确认。
 */
object AhupInstaller {

    private const val DEX_FILE = "plugin.dex"
    private const val MANIFEST_FILE = "manifest.json"

    private fun pluginDir(context: Context, id: String): File =
        File(context.filesDir, "ahup/$id")

    fun installedIds(context: Context): List<String> =
        File(context.filesDir, "ahup").listFiles()?.filter {
            File(it, DEX_FILE).exists() && File(it, MANIFEST_FILE).exists()
        }?.map { it.name }.orEmpty()

    fun manifestOf(context: Context, id: String): AhupManifest? = runCatching {
        AhupManifest.parse(File(pluginDir(context, id), MANIFEST_FILE).readText())
    }.getOrNull()

    fun dexFile(context: Context, id: String): File = File(pluginDir(context, id), DEX_FILE)
    fun iconFile(context: Context, id: String, name: String): File = File(pluginDir(context, id), name)

    /** 解析候选包（不落盘）：供安装确认弹窗展示元数据与签名状态。 */
    fun inspect(context: Context, uri: Uri): AhupCandidate {
        val entries = readZip(context, uri)
        val manifestRaw = entries[MANIFEST_FILE]
            ?: throw AhupException("包内缺少 manifest.json")
        val manifest = AhupManifest.parse(manifestRaw.decodeToString())
        val dex = entries[DEX_FILE] ?: throw AhupException("包内缺少 plugin.dex")
        val icon = entries[manifest.iconFile] ?: throw AhupException("包内缺少 ${manifest.iconFile}")
        val signatureStatus = verifySignature(manifest, dex, icon)
        return AhupCandidate(manifest, manifestRaw, dex, icon, signatureStatus)
    }

    /** 落盘安装（先 inspect 再装）。覆盖同 id 旧版本。 */
    fun install(context: Context, candidate: AhupCandidate) {
        val dir = pluginDir(context, candidate.manifest.id)
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, MANIFEST_FILE).writeBytes(candidate.manifestRaw)
        File(dir, DEX_FILE).writeBytes(candidate.dex)
        File(dir, candidate.manifest.iconFile).writeBytes(candidate.icon)
        // Android 14+：动态加载的 dex 必须只读，否则 ClassLoader 拒载
        File(dir, DEX_FILE).setReadOnly()
        Log.i(TAG, "插件已安装：${candidate.manifest.id} v${candidate.manifest.version}")
    }

    fun uninstall(context: Context, id: String) {
        pluginDir(context, id).deleteRecursively()
    }

    /* ---------------- 签名 ---------------- */

    enum class SignatureStatus { TRUSTED, UNSIGNED, INVALID }

    private fun verifySignature(
        manifest: AhupManifest,
        dex: ByteArray,
        icon: ByteArray
    ): SignatureStatus {
        if (manifest.signature.isBlank()) return SignatureStatus.UNSIGNED
        val pubkey = trustedPublicKey() ?: return SignatureStatus.UNSIGNED
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(dex + icon)
            val sig = Signature.getInstance("SHA256withRSA")
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.decode(pubkey, Base64.DEFAULT)))
            sig.initVerify(key)
            sig.update(digest)
            if (sig.verify(Base64.decode(manifest.signature, Base64.DEFAULT))) {
                SignatureStatus.TRUSTED
            } else {
                SignatureStatus.INVALID
            }
        }.getOrDefault(SignatureStatus.INVALID)
    }

    /** 钉死的团队公钥（assets/ahup_trusted_pubkey.txt，Base64 X509）。缺失 = 全部按未签名处理。 */
    private fun trustedPublicKey(): String? = runCatching {
        com.ahu.ahutong.core.common.AppEnvironmentHolder.context().assets
            .open("ahup_trusted_pubkey.txt").bufferedReader().readText().trim()
    }.getOrNull()

    /* ---------------- 工具 ---------------- */

    private fun readZip(context: Context, uri: Uri): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) out[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } ?: throw AhupException("无法读取所选文件")
        return out
    }

    private const val TAG = "AhupInstaller"
}

/** inspect 的产出：落盘前的一切。manifestRaw 是包内原文（签名状态展示的凭据）。 */
class AhupCandidate(
    val manifest: AhupManifest,
    val manifestRaw: ByteArray,
    val dex: ByteArray,
    val icon: ByteArray,
    val signatureStatus: AhupInstaller.SignatureStatus
)

class AhupException(message: String) : Exception(message)
