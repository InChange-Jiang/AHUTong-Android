package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.liquidGlassSurface
import com.ahu.ahutong.ui.components.liquidGlassTint
import com.ahu.ahutong.ui.screen.main.schedule.shortScheduleLocation
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * 主页课程划卡（HomeClass）：课程条轮盘里的单张课程卡。
 *
 * 全主题共用同一实现（主页排版已统一）；表面材质随液态能力分发——
 * 液态启用时用校园卡同款的 ambient 真玻璃，否则实色回落。
 * 所有卡一律带状态徽标（缩小字号），保证轮盘里每张卡等高。
 */
@Composable
fun HomeClassCard(
    course: Course,
    status: String,
    isFocus: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = SmoothRoundedCornerShape(20.dp)
    val surface = if (LocalIsLiquidGlassEnabled.current) {
        Modifier.liquidGlassSurface(
            backdrop = LocalLiquidGlassAmbientBackdrop.current,
            shape = cardShape,
            surfaceColor = liquidGlassTint()
        )
    } else {
        Modifier.appLiquidGlassSurface(
            shape = cardShape,
            fallbackColor = 100.n1 withNight 20.n1
        )
    }
    Box(
        modifier = modifier
            .clip(cardShape)
            .then(surface)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 状态徽标：所有卡统一显示（等高前提），字号比正文小一档（10sp）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(90.a1 withNight 40.a1)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status,
                    color = 10.n1 withNight 95.n1,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isFocus) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "第 ${course.startTime} 节起 · ${course.location.shortScheduleLocation()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
