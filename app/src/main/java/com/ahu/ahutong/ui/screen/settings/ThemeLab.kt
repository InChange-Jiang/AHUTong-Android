package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsDialogSelectRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.pack.ComponentSlotId
import com.ahu.ahutong.ui.theme.pack.SlotSource

/**
 * 主题实验室（Theme Park 控制台）：
 * 套装预设 + 13 个组件槽位逐一混搭 + 实时预览。
 * 预览区全部走 App* 组件——改槽位即整树重组，所见即全局所得。
 */
@Composable
fun ThemeLab(
    onBack: () -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val appUiTheme by viewModel.appUiTheme.collectAsState()
    val slotOverrides by viewModel.componentSlotOverrides.collectAsState()
    var dialogPreviewShown by remember { mutableStateOf(false) }
    var sheetPreviewShown by remember { mutableStateOf(false) }
    var previewToggle by remember { mutableStateOf(true) }
    var previewChip by remember { mutableStateOf(true) }
    var previewText by remember { mutableStateOf("") }
    var previewSelection by remember { mutableStateOf("选项一") }

    SettingsPageLayout(
        title = "主题实验室",
        onBack = onBack
    ) {
        SettingsSection(
            title = "界面风格套装",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SettingsSelectRow(
                title = "界面风格",
                subtitle = "整套组件与交互风格的基线；下方槽位可在此基础上逐件混搭",
                selected = appUiTheme,
                choices = AppUiTheme.entries.map { SettingsChoice(it, it.displayName) },
                onSelected = viewModel::setAppUiTheme,
                showDivider = false
            )
        }

        SettingsSection(
            title = "实时预览（当前混搭效果）",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppButton(
                        onClick = {},
                        variant = AppButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    ) { Text("主要") }
                    AppButton(
                        onClick = {},
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("次要") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppToggle(
                        checked = previewToggle,
                        onCheckedChange = { previewToggle = it },
                        contentDescription = "预览开关"
                    )
                    AppFilterChip(
                        selected = previewChip,
                        onClick = { previewChip = !previewChip },
                        label = { Text("筛选片") }
                    )
                    AppCircularProgressIndicator()
                }
                AppTextField(
                    value = previewText,
                    onValueChange = { previewText = it },
                    label = "文本框",
                    modifier = Modifier.fillMaxWidth()
                )
                AppSelectField(
                    label = "下拉选择器",
                    selected = previewSelection,
                    options = listOf("选项一", "选项二", "选项三").map { AppSelectOption(it, it) },
                    onSelected = { previewSelection = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        onClick = { dialogPreviewShown = true },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("预览弹窗") }
                    AppButton(
                        onClick = { sheetPreviewShown = true },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("预览抽屉") }
                }
            }
        }

        SettingsSection(
            title = "组件槽位（逐件指定实现来源）",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            ComponentSlotId.entries.forEachIndexed { index, slot ->
                SettingsDialogSelectRow(
                    title = slot.displayName,
                    selected = slotOverrides[slot],
                    choices = listOf(
                        SettingsChoice<SlotSource?>(null, "跟随套装（${appUiTheme.displayName}）")
                    ) + SlotSource.entries.map { SettingsChoice<SlotSource?>(it, it.displayName) },
                    onSelected = { source -> viewModel.setComponentSlotOverride(slot, source) },
                    dialogTitle = "${slot.displayName} · 实现来源",
                    showDivider = index < ComponentSlotId.entries.lastIndex
                )
            }
        }

        if (slotOverrides.isNotEmpty()) {
            AppButton(
                onClick = viewModel::clearComponentSlotOverrides,
                variant = AppButtonVariant.Destructive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) { Text("一键还原为整套（${appUiTheme.displayName}）") }
        }
    }

    if (dialogPreviewShown) {
        AppDialog(
            title = "弹窗预览",
            onDismiss = { dialogPreviewShown = false },
            actions = listOf(
                AppDialogAction("取消", onClick = { dialogPreviewShown = false }),
                AppDialogAction(
                    "确定",
                    onClick = { dialogPreviewShown = false },
                    style = AppDialogActionStyle.Primary
                )
            ),
            content = { Text("这是当前混搭下的弹窗样式。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
    }

    if (sheetPreviewShown) {
        AppModalBottomSheet(
            title = "底部抽屉预览",
            onDismissRequest = { sheetPreviewShown = false }
        ) {
            Text(
                text = "这是当前混搭下的底部抽屉样式。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
