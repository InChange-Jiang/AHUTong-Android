package com.ahu.ahutong.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle

/**
 * 热更新弹窗（强制）：下载中不可关闭；完成后仅提供「立即重启」。
 * 骨架统一为 AppDialog（P0 弹窗族）。
 */
@Composable
fun HotUpdateDialog(
    isDownloading: Boolean = false,
    onConfirm: () -> Unit
) {
    AppDialog(
        title = if (isDownloading) "正在更新" else "更新完成",
        onDismiss = {},
        titleStyle = MaterialTheme.typography.headlineSmall,
        titleFontWeight = FontWeight.Bold,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        headerContent = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        actions = if (isDownloading) {
            emptyList()
        } else {
            listOf(
                AppDialogAction(
                    "立即重启",
                    onClick = onConfirm,
                    style = AppDialogActionStyle.Primary
                )
            )
        },
        content = {
            Text(
                text = if (isDownloading) {
                    "正在下载热更新，请稍候..."
                } else {
                    "已完成热更新，更新内容可前往设置-更新日志中查看。为防止数据异常，请立即重启应用以应用新版本。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}
