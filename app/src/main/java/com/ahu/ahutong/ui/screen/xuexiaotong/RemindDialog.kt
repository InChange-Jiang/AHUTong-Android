package com.ahu.ahutong.ui.screen.xuexiaotong

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.n1
import com.kyant.monet.withNight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindDialog(
    setting: RemindSetting,
    onSave: (RemindSetting) -> Unit,
    onDismiss: () -> Unit,
    onTest: () -> Unit
) {
    var enabled by remember { mutableStateOf(setting.enabled) }
    var lead by remember { mutableIntStateOf(setting.leadMinutes) }
    var onlyTodo by remember { mutableStateOf(setting.onlyTodo) }

    val leadOptions = listOf(0 to "截止时", 60 to "提前1小时", 360 to "提前6小时", 720 to "提前12小时", 1440 to "提前1天")
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(SmoothRoundedCornerShape(32.dp))
                .background(96.n1 withNight 10.n1)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "通知设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // 开启作业提醒
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "开启作业提醒",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.scale(0.78f),
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(80.n1 withNight 30.n1)
            )

            if (enabled) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "提前提醒",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        leadOptions.forEach { (v, label) ->
                            Box(
                                modifier = Modifier
                                    .clickable { lead = v }
                                    .background(
                                        if (lead == v) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (lead == v) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "仅提醒未完成",
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = onlyTodo,
                            onCheckedChange = { onlyTodo = it },
                            modifier = Modifier.scale(0.78f),
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    // 精确闹钟权限引导
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = context.getSystemService(AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            )
                                        } catch (_: Exception) { }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "精确提醒未开启，提醒可能延迟。",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "去授权",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(80.n1 withNight 30.n1)
            )

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "取消",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            onSave(RemindSetting(enabled, lead, onlyTodo))
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "保存",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}