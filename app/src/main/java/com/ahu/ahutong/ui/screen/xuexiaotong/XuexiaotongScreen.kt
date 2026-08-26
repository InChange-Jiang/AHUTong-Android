package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlin.math.roundToInt
import java.util.Calendar

private enum class Tab { SCHEDULE, COURSE }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun XuexiaotongScreen(
    api: ChaoxingApi,
    onBack: () -> Unit
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<XuexiaotongViewModel>(
        factory = XuexiaotongViewModel.Factory(api, androidx.compose.ui.platform.LocalContext.current)
    )

    val loggedIn by viewModel.loggedIn.collectAsState()
    val works by viewModel.works.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val syncMsg by viewModel.syncProgress.collectAsState()
    val courseSyncing by viewModel.courseSyncing.collectAsState()
    val courseSyncMsg by viewModel.courseSyncProgress.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val customEvents by viewModel.customEvents.collectAsState()
    val showDone by viewModel.showDone.collectAsState()
    val doneGray by viewModel.doneGray.collectAsState()
    val showEmptyCourses by viewModel.showEmptyCourses.collectAsState()
    val remindSetting by viewModel.remindSetting.collectAsState()

    if (!loggedIn) {
        XuexiaotongLoginScreen(
            api = api,
            onLoginSuccess = { viewModel.onLoginSuccess() }
        )
        return
    }

    var tab by remember { mutableStateOf(Tab.SCHEDULE) }
    var sideMenuOpen by remember { mutableStateOf(false) }
    var selectedWork by remember { mutableStateOf<Work?>(null) }
    var showAddEvent by remember { mutableStateOf(false) }
    var pendingDeleteEvent by remember { mutableStateOf<Work?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showRemindDialog by remember { mutableStateOf(false) }
    val today = Calendar.getInstance()
    var year by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏：标题+副标题紧凑排列，右侧按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        val mainTitle = if (tab == Tab.COURSE) "课程任务" else "${year}年${month}月"
                        Text(
                            mainTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // 副标题：同步时显示进度，否则显示上次同步时间
                        val isSyncing = if (tab == Tab.COURSE) courseSyncing else syncing
                        val msg = if (tab == Tab.COURSE) courseSyncMsg else syncMsg
                        val subtitleText = if (isSyncing && msg.message.isNotEmpty()) {
                            msg.message
                        } else if (lastSync > 0) {
                            val c = Calendar.getInstance().apply { timeInMillis = lastSync }
                            val mm = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                            val dd = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                            val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                            val mi = c.get(Calendar.MINUTE).toString().padStart(2, '0')
                            "上次同步 $mm-$dd $hh:$mi"
                        } else ""
                        if (subtitleText.isNotEmpty()) {
                            Text(
                                subtitleText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 操作按钮
                Row(
                    modifier = Modifier
                        .clip(ContinuousCapsule)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        )
                ) {
                    if (tab == Tab.SCHEDULE) {
                        IconButton(onClick = { showAddEvent = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "新建日程",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { sideMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val isSyncingBtn = if (tab == Tab.COURSE) courseSyncing else syncing
                    if (isSyncingBtn) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            if (tab == Tab.COURSE) viewModel.syncCourseProgress()
                            else viewModel.syncWorks()
                        }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "同步",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 标签页内容
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 2 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally { -it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { it / 2 } + fadeOut(tween(180)))
                    }
                },
                label = "tabContent"
            ) { t ->
                Box(Modifier.weight(1f)) {
                    when (t) {
                        Tab.SCHEDULE -> {
                            ScheduleTab(
                                works = works,
                                customEvents = customEvents,
                                syncing = syncing,
                                syncMsg = syncMsg,
                                showDone = showDone,
                                doneGray = doneGray,
                                year = year,
                                month = month,
                                onChangeMonth = { delta ->
                                    var m = month + delta
                                    var y = year
                                    if (m < 1) { m = 12; y-- }
                                    if (m > 12) { m = 1; y++ }
                                    year = y; month = m
                                },
                                onWorkClick = { selectedWork = it }
                            )
                        }
                        Tab.COURSE -> {
                            CourseTab(
                                progress = progress,
                                syncing = courseSyncing,
                                syncMsg = courseSyncMsg,
                                showEmptyCourses = showEmptyCourses
                            )
                        }
                    }
                }
            }
        }

        // 底部悬浮 Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BottomDock(current = tab, onSelect = { tab = it })
        }
    }

    // 任务详情弹窗
    selectedWork?.let { work ->
        WorkDetailDialog(
            work = work,
            onDismiss = { selectedWork = null },
            onToggleDone = if (work.workId.startsWith("event_")) {
                {
                    viewModel.toggleCustomEventDone(work.workId.removePrefix("event_"))
                    selectedWork = null
                }
            } else null,
            onDelete = if (work.workId.startsWith("event_")) {
                {
                    pendingDeleteEvent = work
                    selectedWork = null
                }
            } else null
        )
    }

    // 新建日程弹窗
    if (showAddEvent) {
        AddEventDialog(
            onDismiss = { showAddEvent = false },
            onSave = { ev ->
                viewModel.addCustomEvent(ev)
                showAddEvent = false
            }
        )
    }

    // 删除自建日程确认弹窗
    pendingDeleteEvent?.let { w ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { pendingDeleteEvent = null }) {
            Column(
                modifier = Modifier
                    .clip(SmoothRoundedCornerShape(32.dp))
                    .background(96.n1 withNight 10.n1)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "删除日程",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "确定删除「${w.title}」吗？",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(80.n1 withNight 30.n1)
                )
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
                            .clickable { pendingDeleteEvent = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            .clickable {
                                viewModel.deleteCustomEvent(w.workId.removePrefix("event_"))
                                pendingDeleteEvent = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // 清空自建日程确认弹窗
    if (showClearConfirm) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showClearConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(SmoothRoundedCornerShape(32.dp))
                    .background(96.n1 withNight 10.n1)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "清空自建日程",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "确定删除全部 ${customEvents.size} 条自建日程吗？此操作不可恢复",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(80.n1 withNight 30.n1)
                )
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
                            .clickable { showClearConfirm = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            .clickable {
                                viewModel.clearCustomEvents()
                                showClearConfirm = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("全部删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // 通知设置弹窗
    if (showRemindDialog) {
        RemindDialog(
            setting = remindSetting,
            onSave = { viewModel.saveRemind(it) },
            onDismiss = { showRemindDialog = false },
            onTest = { viewModel.sendTestNotification() }
        )
    }

    // 底部抽屉菜单（参照天气页 ModalBottomSheet）
    if (sideMenuOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sideMenuOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Text(
                    "学习通日历",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "作业日历 · 课程进度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))
                BottomSheetSwitchItem("查看已完成作业", showDone) { viewModel.toggleShowDone() }
                BottomSheetSwitchItem("查看无任务点课程", showEmptyCourses) { viewModel.toggleShowEmptyCourses() }
                BottomSheetSwitchItem("已完成任务置灰", doneGray) { viewModel.toggleDoneGray() }
                Spacer(Modifier.height(8.dp))
                // 通知设置
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { sideMenuOpen = false; showRemindDialog = true }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("通知设置", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable { sideMenuOpen = false; showClearConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("清空自建日程", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { viewModel.logout(); sideMenuOpen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text("退出学习通登录", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BottomSheetSwitchItem(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.scale(0.78f),
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

/* ==================== 底部悬浮 Dock ==================== */

@Composable
private fun BottomDock(
    current: Tab,
    modifier: Modifier = Modifier,
    onSelect: (Tab) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val dockWidthDp = 168.dp
    var dockWidth by remember { mutableStateOf(0) }
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var didDrag by remember { mutableStateOf(false) }
    var startX by remember { mutableStateOf(0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = if (current == Tab.COURSE) 1f else 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.25f,
            onDragStarted = { position ->
                didDrag = false
                startX = position.x
            },
            onDragStopped = {
                val half = if (dockWidth > 0) dockWidth / 2f else with(density) { 84f.dp.toPx() }
                val target = if (didDrag) {
                    if (targetValue >= 0.5f) 1f else 0f
                } else {
                    if (startX >= half) 1f else 0f
                }
                animateToValue(target)
                onSelect(if (target >= 0.5f) Tab.COURSE else Tab.SCHEDULE)
            },
            onDrag = { _, dragAmount ->
                if (dragAmount.x != 0f) didDrag = true
                val tabWidth = if (dockWidth > 0) dockWidth / 2f else with(density) { 84f.dp.toPx() }
                updateValue(targetValue + dragAmount.x / tabWidth)
            }
        )
    }
    LaunchedEffect(current) {
        dampedDragAnimation.animateToValue(if (current == Tab.COURSE) 1f else 0f)
    }

    Box(
        modifier = modifier
            .width(dockWidthDp)
            .height(56.dp)
            .onSizeChanged { dockWidth = it.width }
    ) {
        // 底座
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surface, RoundedCornerShape(50))
        )
        // 选中滑块
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(56.dp)
                .offset {
                    IntOffset(
                        x = (dampedDragAnimation.progress * dockWidth / 2f).roundToInt(),
                        y = 0
                    )
                }
                .padding(4.dp)
                .background(Color.White, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {}
        // 文本层
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "日程",
                    fontSize = 15.sp,
                    fontWeight = if (current == Tab.SCHEDULE) FontWeight.Bold else FontWeight.Normal,
                    color = if (current == Tab.SCHEDULE) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "课程",
                    fontSize = 15.sp,
                    fontWeight = if (current == Tab.COURSE) FontWeight.Bold else FontWeight.Normal,
                    color = if (current == Tab.COURSE) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 手势层
        Box(
            Modifier
                .fillMaxSize()
                .then(dampedDragAnimation.modifier)
        ) {}
    }
}

/* ==================== 日程标签页 ==================== */

@Composable
private fun ScheduleTab(
    works: List<Work>,
    customEvents: List<CustomEvent>,
    syncing: Boolean,
    syncMsg: com.ahu.ahutong.ui.screen.xuexiaotong.SyncProgress,
    showDone: Boolean,
    doneGray: Boolean,
    year: Int,
    month: Int,
    onChangeMonth: (Int) -> Unit,
    onWorkClick: (Work) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 日历卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var accumulated = 0f
                        var started = false
                        detectHorizontalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onDragEnd = {
                                if (started) {
                                    if (accumulated < -40f) onChangeMonth(1)
                                    else if (accumulated > 40f) onChangeMonth(-1)
                                }
                            },
                            onDragCancel = {}
                        ) { change, dragAmount ->
                            change.consume()
                            started = true
                            accumulated += dragAmount
                        }
                    }
            ) {
                // 星期表头
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 月份滑动动画
                val monthKey = year * 12 + (month - 1)
                AnimatedContent(
                    targetState = monthKey,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally { -it / 2 } + fadeOut(tween(220)))
                        } else {
                            (slideInHorizontally { -it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally { it / 2 } + fadeOut(tween(220)))
                        }
                    },
                    label = "monthContent"
                ) { key ->
                    val y = key / 12
                    val m = key % 12 + 1
                    val model = remember(key, works, customEvents, showDone) {
                        CalendarModel.buildMonth(y, m, works, customEvents, showDone)
                    }
                    Column {
                        model.rows.forEachIndexed { ri, row ->
                            MonthRowView(row = row, isShade = ri % 2 == 1, doneGray = doneGray, onWorkClick = onWorkClick)
                        }
                        if (model.noWorks) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "本月暂无作业安排",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun MonthRowView(
    row: com.ahu.ahutong.ui.screen.xuexiaotong.CalendarRow,
    isShade: Boolean,
    doneGray: Boolean,
    onWorkClick: (Work) -> Unit
) {
    val cellBg = if (isShade) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f) else Color.Transparent

    Column(Modifier.fillMaxWidth()) {
        // 日期层
        Row(Modifier.fillMaxWidth().background(cellBg)) {
            row.cells.forEach { cell ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (cell.isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (cell.day == 0) "" else "${cell.day}",
                            fontSize = 12.sp,
                            color = when {
                                cell.isToday -> MaterialTheme.colorScheme.primary
                                cell.outside -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        // 色块层
        if (row.blocks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((row.layerHeight).dp)
                    .padding(top = 2.dp)
            ) {
                row.blocks.forEach { b ->
                    WorkBlockView(b, doneGray, onWorkClick)
                }
            }
        } else {
            Spacer(Modifier.height(CalendarModel.LAYER_PAD.dp))
        }
    }
}

@Composable
private fun WorkBlockView(
    b: com.ahu.ahutong.ui.screen.xuexiaotong.CalendarBlock,
    doneGray: Boolean,
    onWorkClick: (Work) -> Unit
) {
    val cellPct = 100f / 7f
    val leftPct = b.colStart * cellPct
    val widthPct = (b.colEnd - b.colStart + 1) * cellPct
    val topPx = b.lane * CalendarModel.BLOCK_STEP

    var parentWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val bg = try { Color(android.graphics.Color.parseColor(b.work.colorBg)) } catch (e: Exception) { Color(0xFF9E9E9E) }
    val text = try { Color(android.graphics.Color.parseColor(b.work.colorText)) } catch (e: Exception) { Color(0xFF9E9E9E) }
    val isDone = b.work.isDone

    val finalBg = if (isDone && doneGray) bg.copy(alpha = 0.25f) else bg.copy(alpha = 0.7f)
    val finalText = if (isDone && doneGray) text.copy(alpha = 0.4f) else text

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { parentWidth = it.width }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthPct / 100f)
                .offset {
                    IntOffset(
                        x = (parentWidth * leftPct / 100f).roundToInt(),
                        y = with(density) { topPx.dp.roundToPx() }
                    )
                }
                .padding(horizontal = 1.dp)
                .height(CalendarModel.BLOCK_H.dp)
                .background(finalBg, RoundedCornerShape(6.dp))
                                .clickable { onWorkClick(b.work) },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    b.work.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = finalText
                )
                if (b.continueNext) {
                    Text("▸", fontSize = 8.sp, lineHeight = 11.sp, color = finalText)
                }
            }
        }
    }
}

/* ==================== 课程进度标签页 ==================== */

@Composable
private fun CourseTab(
    progress: List<CourseProgress>,
    syncing: Boolean,
    syncMsg: com.ahu.ahutong.ui.screen.xuexiaotong.SyncProgress,
    showEmptyCourses: Boolean
) {
    val filtered = if (showEmptyCourses) progress
    else progress.filter { it.totalCount > 0 }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无课程进度，点击右上角同步获取",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 72.dp)
        ) {
            item(key = "__overview__") {
                CourseOverviewCard(list = filtered)
            }
            items(filtered, key = { it.courseId }) { p ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (p.totalCount > 0) "${p.percent}%" else "暂无任务点",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (p.totalCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p.percent / 100f)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (p.totalCount > 0) "已完成任务点 ${p.doneCount}/${p.totalCount}"
                            else "暂无任务点",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseOverviewCard(list: List<CourseProgress>) {
    val totalDone = list.sumOf { it.doneCount }
    val totalAll = list.sumOf { it.totalCount }
    val percent = if (totalAll > 0) (totalDone * 100 / totalAll).coerceAtMost(100) else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(Modifier.padding(vertical = 16.dp)) {
            CourseOverviewStat(
                value = "${list.size}",
                label = "门课程",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            CourseOverviewStat(
                value = "$percent%",
                label = "总进度",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            CourseOverviewStat(
                value = if (totalAll > 0) "$totalDone/$totalAll" else "0/0",
                label = "已完成任务点",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CourseOverviewStat(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}