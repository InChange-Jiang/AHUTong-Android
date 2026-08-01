package com.ahu.ahutong.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.notification.CourseReminderCapability
import com.ahu.ahutong.notification.CourseReminderNotifier
import com.ahu.ahutong.notification.CourseReminderScheduler
import com.ahu.ahutong.ui.components.LiquidToggle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun Preferences() {

    val preferencesViewModel: PreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    var isRequestingPermission by remember { mutableStateOf(false) }
    var useCmbCardRecharge by remember { mutableStateOf(AHUCache.isCmbCardRechargePreferred()) }
    var showClearLearningConfirm by remember { mutableStateOf(false) }

    val showQRCode by preferencesViewModel.showQRCode.collectAsState()
    val personalizationEnabled by preferencesViewModel.personalizationEnabled.collectAsState()
    val predictivePrefetchEnabled by preferencesViewModel.predictivePrefetchEnabled.collectAsState()
    val wifiOnlyPrefetch by preferencesViewModel.wifiOnlyPrefetch.collectAsState()
    val behaviorRetentionDays by preferencesViewModel.behaviorRetentionDays.collectAsState()
    val useLiquidGlass by preferencesViewModel.useLiquidGlass.collectAsState()
    val courseReminderEnabled by preferencesViewModel.courseReminderEnabled.collectAsState()
    val courseReminderLiveCountdownEnabled by preferencesViewModel.courseReminderLiveCountdownEnabled.collectAsState()

    val cardColor = 100.n1 withNight 20.n1
    val backdrop = rememberCanvasBackdrop { drawRect(cardColor) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            preferencesViewModel.setCourseReminderEnabled(true)
            CourseReminderScheduler.reschedule(context)
        } else {
            preferencesViewModel.setCourseReminderEnabled(false)
            Toast.makeText(context, "未授予通知权限，无法开启课前提醒", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = R.string.preferences),
            modifier = Modifier.padding(24.dp, 32.dp),
            style = MaterialTheme.typography.headlineLarge
        )

        PreferenceToggleCard(
            sectionTitle = "猜你想用",
            settingTitle = "显示快捷建议",
            description = "根据仅在本机学习的使用习惯，在合适时机显示快捷建议。",
            selected = { personalizationEnabled },
            onSelect = preferencesViewModel::setPersonalizationEnabled,
            cardColor = cardColor,
            backdrop = backdrop
        )

        PreferenceToggleCard(
            sectionTitle = "智能预加载",
            settingTitle = "提前加载预测内容",
            description = "预测可能使用的只读内容并提前加载，命中后可以更快打开页面。",
            selected = { predictivePrefetchEnabled },
            onSelect = preferencesViewModel::setPredictivePrefetchEnabled,
            cardColor = cardColor,
            backdrop = backdrop
        )

        PreferenceToggleCard(
            sectionTitle = "预加载网络",
            settingTitle = "仅在 Wi-Fi 下智能预加载",
            selected = { wifiOnlyPrefetch },
            onSelect = preferencesViewModel::setWifiOnlyPrefetch,
            cardColor = cardColor,
            backdrop = backdrop
        )

        PreferenceToggleCard(
            sectionTitle = "付款码",
            settingTitle = "主页默认显示付款码",
            selected = { showQRCode },
            onSelect = preferencesViewModel::setShowQRCode,
            cardColor = cardColor,
            backdrop = backdrop
        )

        PreferenceRetentionCard(
            selectedDays = behaviorRetentionDays,
            cardColor = cardColor,
            onSelect = preferencesViewModel::setBehaviorRetentionDays
        )

        PreferenceActionCard(
            sectionTitle = "本地学习记录",
            actionTitle = "清除本地学习记录",
            description = "删除当前账号的行为统计、训练样本、模型和晋级状态。",
            cardColor = cardColor,
            onClick = { showClearLearningConfirm = true }
        )

        Column(
            modifier =
                Modifier
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable {
                        val enabled = !useCmbCardRecharge
                        useCmbCardRecharge = enabled
                        AHUCache.setCmbCardRechargePreferred(enabled)
                    }
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "充值", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "总是使用招商银行充值")
                    Text(
                        text = "开启后首页校园卡充值会直接进入招商银行充值",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LiquidToggle(
                    selected = { useCmbCardRecharge },
                    onSelect = { enabled ->
                        useCmbCardRecharge = enabled
                        AHUCache.setCmbCardRechargePreferred(enabled)
                    },
                    backdrop = backdrop
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable {
                        if (!courseReminderEnabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                if (!isRequestingPermission) {
                                    isRequestingPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                preferencesViewModel.setCourseReminderEnabled(true)
                                CourseReminderScheduler.reschedule(context)
                            }
                        } else {
                            preferencesViewModel.setCourseReminderEnabled(false)
                            CourseReminderScheduler.cancel(context)
                        }
                    }
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "通知", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "课前提醒")
                    Text(
                        text = "上课前 10 分钟提醒下一节课",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LiquidToggle(
                    selected = { courseReminderEnabled },
                    onSelect = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                if (!isRequestingPermission) {
                                    isRequestingPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                preferencesViewModel.setCourseReminderEnabled(true)
                                CourseReminderScheduler.reschedule(context)
                            }
                        } else {
                            preferencesViewModel.setCourseReminderEnabled(false)
                            CourseReminderScheduler.cancel(context)
                        }
                    },
                    backdrop = backdrop
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(cardColor)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "通知增强", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "课前倒计时岛卡提醒（实验性）")
                    Text(
                        text = "仅部分系统支持 需同时开启课前提醒",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LiquidToggle(
                    selected = {
                        courseReminderLiveCountdownEnabled && Build.VERSION.SDK_INT >= 36
                    },
                    onSelect = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT < 36) {
                            Toast.makeText(
                                context,
                                "当前 Android 版本暂不支持岛卡提醒",
                                Toast.LENGTH_SHORT
                            ).show()
                            preferencesViewModel.setCourseReminderLiveCountdownEnabled(false)
                        } else {
                            preferencesViewModel.setCourseReminderLiveCountdownEnabled(enabled)
                            if (!enabled) {
                                CourseReminderNotifier.cancelActiveReminder(context)
                            }
                        }
                    },
                    backdrop = backdrop
                )
            }
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT < 36) {
                        Toast.makeText(
                            context,
                            "当前 Android 版本暂不支持岛卡提醒",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val promotionIntent =
                            CourseReminderCapability.createPromotionSettingsIntent(context)
                        val fallbackIntent =
                            CourseReminderCapability.createNotificationSettingsIntent(context)
                        runCatching {
                            context.startActivity(promotionIntent)
                        }.getOrElse {
                            context.startActivity(fallbackIntent)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "管理系统岛卡权限")
            }
        }

        Column(
            modifier =
                Modifier
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable { preferencesViewModel.setUseLiquidGlass(!preferencesViewModel.useLiquidGlass.value) }
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "液态玻璃", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "启用液态玻璃效果")
                LiquidToggle(
                    selected = { useLiquidGlass },
                    onSelect = { preferencesViewModel.setUseLiquidGlass(!preferencesViewModel.useLiquidGlass.value) },
                    backdrop = backdrop
                )
            }
        }

        ThemeColorSelector(preferencesViewModel, cardColor)
    }

    if (showClearLearningConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLearningConfirm = false },
            title = { Text("清除本地学习记录？") },
            text = { Text("将删除当前账号的行为统计、训练样本、模型与晋级状态，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    preferencesViewModel.clearPersonalizationLearning()
                    showClearLearningConfirm = false
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClearLearningConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PreferenceRetentionCard(
    selectedDays: Int,
    cardColor: Color,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "本地学习记录保留期", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "只影响本机行为事件的保留时间；模型和聚合统计仍受清除记录操作控制。",
            color = 50.n1 withNight 80.n1,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(7, 14, 30).forEach { days ->
                TextButton(onClick = { onSelect(days) }) {
                    Text(
                        text = "$days 天",
                        color = if (selectedDays == days) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (selectedDays == days) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleCard(
    sectionTitle: String,
    settingTitle: String,
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    cardColor: Color,
    backdrop: Backdrop,
    description: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(cardColor)
            .clickable { onSelect(!selected()) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = sectionTitle, style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = settingTitle)
                description?.let {
                    Text(
                        text = it,
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LiquidToggle(
                selected = selected,
                onSelect = onSelect,
                backdrop = backdrop
            )
        }
    }
}

@Composable
private fun PreferenceActionCard(
    sectionTitle: String,
    actionTitle: String,
    description: String,
    cardColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = sectionTitle, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = actionTitle, color = MaterialTheme.colorScheme.error)
            Text(
                text = description,
                color = 50.n1 withNight 80.n1,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ThemeColorSelector(
    viewModel: PreferencesViewModel,
    cardColor: androidx.compose.ui.graphics.Color
) {
    val themeColor by viewModel.themeColor.collectAsState()
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColorInput by remember { mutableStateOf("") }

    val colors = listOf(
        null to "默认",
        "#FF4A90E2" to "极光蓝",
        "#FFE07A9F" to "樱花粉",
        "#FFF4A261" to "落日橙",
        "#FF5C6BC0" to "靛夜蓝",
        "#FF6A994E" to "苔藓绿",
        "#FF9B7EDE" to "薰衣草紫",
        "#FFD64550" to "绯红花",
        "#FF4CC9F0" to "天空青",
        "#FF2E8B57" to "森林翡翠",
        "#FF6A4C93" to "午夜紫",
        "#FFFF6F61" to "珊瑚粉",
        "#FF7ED9C3" to "北极薄荷"
    )

    val isCustomColor = themeColor != null && colors.none { it.first == themeColor }

    if (showCustomColorDialog) {
        AlertDialog(
            containerColor = cardColor,
            onDismissRequest = { showCustomColorDialog = false },
            title = {
                Text(
                    text = "自定义主题颜色",
                    color = 10.n1 withNight 100.n1,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "请输入ARGB Hex颜色代码 (例如 #FF007FAC)",
                        color = 30.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = customColorInput,
                        onValueChange = { customColorInput = it },
                        label = { Text("Hex Color") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmoothRoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = 40.a1 withNight 80.a1,
                            unfocusedBorderColor = 50.n1 withNight 60.n1,
                            focusedLabelColor = 40.a1 withNight 80.a1,
                            unfocusedLabelColor = 50.n1 withNight 60.n1,
                            cursorColor = 40.a1 withNight 80.a1,
                            focusedTextColor = 10.n1 withNight 100.n1,
                            unfocusedTextColor = 10.n1 withNight 100.n1
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            // Validate color parsing
                            android.graphics.Color.parseColor(customColorInput)
                            viewModel.setThemeColor(customColorInput)
                            showCustomColorDialog = false
                        } catch (e: Exception) {
                            // Invalid color, maybe show error or just ignore
                        }
                    }
                ) {
                    Text(
                        text = "确定",
                        color = 40.a1 withNight 80.a1,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomColorDialog = false }) {
                    Text(
                        text = "取消",
                        color = 40.a1 withNight 80.a1,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "主题颜色", style = MaterialTheme.typography.headlineSmall)

        // Use FlowRow or LazyRow if there are many colors. For now, a simple wrapped layout or Column is fine.
        // Let's use a FlowRow equivalent or just a simple vertical list of rows if we want to be safe without experimental APIs,
        // or just a Row with horizontal scroll if we expect few items.
        // Given the design, a horizontal scrollable Row seems appropriate for color circles.

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Color Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    customColorInput = if (isCustomColor) themeColor ?: "" else ""
                    showCustomColorDialog = true
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .background(
                            if (isCustomColor && themeColor != null) Color(
                                android.graphics.Color.parseColor(
                                    themeColor
                                )
                            ) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCustomColor) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Custom",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "自定义",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            colors.forEach { (colorHex, name) ->
                val isSelected = themeColor == colorHex
                val color = if (colorHex != null) {
                    Color(android.graphics.Color.parseColor(colorHex))
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        colorResource(id = android.R.color.system_accent1_500)
                    } else {
                        Color(0xFF007FAC)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.setThemeColor(colorHex) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            50.a1
                        } else {
                            Color.Black withNight Color.White
                        }
                    )
                }
            }
        }
    }
}
