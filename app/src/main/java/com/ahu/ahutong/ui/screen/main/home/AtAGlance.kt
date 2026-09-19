package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * At a Glance（缩小版）：轻量信息带——一行主标题（当前课/下节课/今日状态）+
 * 一行小字副标题（剩余时间/地点）。全主题统一，无形态分叉。
 */
@Composable
fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    onOpenSchedule: () -> Unit,
    isInSemester: Boolean = true,
    enabled: Boolean = true
) {
    val currentCourse = todayCourses.find {
        currentMinutes in ScheduleViewModel.getCourseTimeRangeInMinutes(it)
    }
    val nextCourse = todayCourses.firstOrNull {
        currentMinutes < ScheduleViewModel.getCourseTimeRangeInMinutes(it).first
    }
    val headline = when {
        currentCourse != null -> "正在上课 · ${currentCourse.name}"
        nextCourse != null -> "下节课是 ${nextCourse.name}"
        !isInSemester -> "假期中"
        else -> "今日空闲"
    }
    val subtitle = when {
        currentCourse != null -> {
            val duration = ScheduleViewModel.getCourseTimeRangeInMinutes(currentCourse).last -
                currentMinutes
            "距下课还有 ${formatCourseDuration(duration)}"
        }
        nextCourse != null -> {
            val duration = ScheduleViewModel.getCourseTimeRangeInMinutes(nextCourse).first -
                currentMinutes
            "还有 ${formatCourseDuration(duration)}，在 ${nextCourse.location}"
        }
        !isInSemester -> "安排属于自己的一天吧"
        else -> "今天暂无课程安排"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onOpenSchedule) else Modifier)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = 10.n1 withNight 95.n1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = 45.n1 withNight 70.n1
        )
    }
}

private fun formatCourseDuration(durationMinutes: Int): String = when {
    durationMinutes % 60 == 0 -> "${durationMinutes / 60}小时整"
    durationMinutes > 60 -> "${durationMinutes / 60}小时${durationMinutes % 60}分钟"
    else -> "${durationMinutes}分钟"
}
