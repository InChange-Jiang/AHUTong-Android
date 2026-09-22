package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.screen.main.schedule.shortScheduleLocation
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlin.math.absoluteValue

/**
 * 今日课程条：横向滚轮卡片（cover-flow）。
 * 三区语义——左 = 上一节，中 = 当前/即将（高亮扁平矩形标识，始终居中），右 = 下一节；
 * 支持左右滑动翻页，中间卡 z 序最高并叠压两侧（景深）。
 */
@Composable
fun CourseStrip(
    todayCourses: List<Course>,
    currentMinutes: Int,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (todayCourses.isEmpty()) {
        AppCard(
            modifier = modifier.fillMaxWidth().height(88.dp).padding(horizontal = 16.dp),
            onClick = onOpenSchedule
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "今日无课",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "点按查看完整课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // 焦点课：进行中的；否则下一节；全结束则最后一节；全未开始则第一节
    val focusIndex = remember(todayCourses, currentMinutes) {
        val ranges = todayCourses.map(ScheduleSectionTimes::getCourseTimeRangeInMinutes)
        ranges.indexOfFirst { currentMinutes in it }
            .takeIf { it >= 0 }
            ?: ranges.indexOfFirst { currentMinutes < it.first }.takeIf { it >= 0 }
            ?: todayCourses.lastIndex
    }
    val pagerState = rememberPagerState(initialPage = focusIndex) { todayCourses.size }
    LaunchedEffect(focusIndex) {
        if (pagerState.currentPage != focusIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(focusIndex)
        }
    }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val pageWidth = screenWidthDp * 0.52f
    val pageWidthPx = with(density) { pageWidth.toPx() }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        pageSize = PageSize.Fixed(pageWidth),
        contentPadding = PaddingValues(horizontal = (screenWidthDp - pageWidth) / 2),
        pageSpacing = (-28).dp // 负间距：两侧卡被中间卡叠压
    ) { page ->
        val offset =
            (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
        val course = todayCourses[page]
        val range = ScheduleSectionTimes.getCourseTimeRangeInMinutes(course)
        val status = when {
            currentMinutes in range -> "进行中"
            currentMinutes < range.first -> "即将开始"
            else -> "已结束"
        }
        val isFocus = page == focusIndex

        AppCard(
            onClick = onOpenSchedule,
            modifier = Modifier
                .heightIn(min = 88.dp)
                .zIndex(1f - offset)
                .graphicsLayer {
                    val t = offset.coerceIn(0f, 1.5f)
                    scaleX = 1f - 0.12f * t
                    scaleY = 1f - 0.12f * t
                    alpha = 1f - 0.45f * t
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isFocus) {
                    // 高亮扁平小矩形：标识当前/即将
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(90.a1 withNight 40.a1)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            color = 10.n1 withNight 95.n1,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isFocus) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${course.startTime} 起 · ${course.location.shortScheduleLocation()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
