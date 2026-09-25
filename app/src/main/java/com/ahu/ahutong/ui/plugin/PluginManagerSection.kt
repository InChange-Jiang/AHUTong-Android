package com.ahu.ahutong.ui.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppSectionCard

/**
 * 小工具页尾部的插件管理区：安装 .ahup + 已装插件列表（可卸载）。
 */
@Composable
fun PluginManagerSection() {
    val context = LocalContext.current
    val plugins by PluginRegistry.plugins.collectAsState()
    var candidate by remember { mutableStateOf<AhupCandidate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { AhupInstaller.inspect(context, uri) }
                .onSuccess { candidate = it }
                .onFailure { error = it.message ?: "插件包解析失败" }
        }
    }

    AppSectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "插件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "安装 .ahup 插件包以扩展小工具。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 已装运行期插件（内置的不在管理范围内）
            val runtimePlugins = AhupInstaller.installedIds(context)
            runtimePlugins.forEach { id ->
                val manifest = AhupInstaller.manifestOf(context, id)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(manifest?.title ?: id, fontWeight = FontWeight.Medium)
                        Text(
                            "v${manifest?.version ?: "?"} · ${manifest?.author ?: "未知"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppButton(
                        onClick = {
                            AhupInstaller.uninstall(context, id)
                            PluginRegistry.reload(context)
                        },
                        variant = AppButtonVariant.Secondary
                    ) { Text("卸载") }
                }
            }
            if (runtimePlugins.isEmpty()) {
                Text(
                    "尚未安装运行期插件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AppButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                variant = AppButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            ) { Text("安装插件包（.ahup）") }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 安装确认弹窗：元数据 + 能力 + 签名状态
    candidate?.let { c ->
        val signatureText = when (c.signatureStatus) {
            AhupInstaller.SignatureStatus.TRUSTED -> "✅ 团队签名（可信）"
            AhupInstaller.SignatureStatus.UNSIGNED -> "⚠️ 未签名——仅安装你信任来源的插件包"
            AhupInstaller.SignatureStatus.INVALID -> "🚫 签名无效——包可能被篡改"
        }
        AppDialog(
            title = "安装插件",
            onDismiss = { candidate = null },
            subtitle = "${c.manifest.title} v${c.manifest.version}",
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { candidate = null },
                    style = AppDialogActionStyle.Neutral
                ),
                AppDialogAction(
                    label = "安装",
                    onClick = {
                        AhupInstaller.install(context, c)
                        PluginRegistry.reload(context)
                        candidate = null
                    },
                    style = if (c.signatureStatus == AhupInstaller.SignatureStatus.INVALID) {
                        AppDialogActionStyle.Danger
                    } else {
                        AppDialogActionStyle.Primary
                    }
                )
            )
        ) {
            Text("作者：${c.manifest.author}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("声明能力：${c.manifest.capabilities.ifEmpty { setOf("无") }.joinToString("、")}",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                signatureText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (c.signatureStatus != AhupInstaller.SignatureStatus.TRUSTED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "插件与主程序同进程运行，可信插件才可能触碰 App 数据。请勿安装来历不明的插件包。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
