package com.ahu.ahutong.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.ui.components.GlassBackdropContainer
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.screen.main.home.AtAGlance
import com.ahu.ahutong.ui.screen.main.home.CampusCard
import com.ahu.ahutong.ui.screen.main.home.CampusSpan
import com.ahu.ahutong.ui.screen.main.home.CourseStrip
import com.ahu.ahutong.ui.screen.main.home.HomeGrid
import com.ahu.ahutong.ui.screen.main.home.HomeGridConfig
import com.ahu.ahutong.ui.screen.main.home.HomeTitleRow
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.normalizeHomeGridIcons
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private const val HOME_REFRESH_INTERVAL_MS = 30_000L

/**
 * 主页（全主题唯一布局族）：
 * 标题行（日期 + 天气胶囊）→ At a Glance（小号）→ 课程条（滚轮三区）→ 3×4 组件网格。
 * 布局与主题正交——主题只换材质皮肤，不换结构。
 * 编辑：长按网格进入；图标格 × 删除、空位 ＋ 添加、校园卡角标拖拽变形（2×2 ⇄ 1×4）。
 */
@Composable
fun Home(
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel(),
    navController: NavHostController,
    behaviorRuntime: BehaviorPredictionRuntime,
    onOpenSchedule: () -> Unit = { navController.navigate("schedule") },
    homeEditEnabled: Boolean = false,
    enterEditModeRequest: Boolean = false,
    onEnterEditModeRequestConsumed: () -> Unit = {}
) {
    val schedule = scheduleViewModel.schedule.observeAsState().value?.getOrNull() ?: emptyList()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    val localScheduleConfig by produceState<ScheduleConfigBean?>(
        initialValue = null,
        key1 = scheduleConfig
    ) {
        value = scheduleConfig ?: withContext(Dispatchers.IO) {
            CurrentWeekResolver.resolveLocalConfig()?.config
        }
    }
    val effectiveScheduleConfig = scheduleConfig ?: localScheduleConfig
    val isInSemester = effectiveScheduleConfig?.isInSemester != false
    val currentWeek = effectiveScheduleConfig?.week ?: 1
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    val initialCalendar = remember { Calendar.getInstance(Locale.CHINA) }
    var currentMinutes by remember {
        mutableIntStateOf(
            initialCalendar.get(Calendar.HOUR_OF_DAY) * 60 + initialCalendar.get(Calendar.MINUTE)
        )
    }
    val todayCourses = remember(schedule, effectiveScheduleConfig, isInSemester, currentWeek) {
        if (isInSemester) {
            schedule
                .asSequence()
                .filter { effectiveScheduleConfig?.week in it.startWeek..it.endWeek }
                .filter { it.weekday == (effectiveScheduleConfig?.weekDay ?: 1) }
                .filter {
                    if (currentWeek in it.weekIndexes) {
                        true
                    } else {
                        currentWeek % 2 == it.startWeek % 2
                    }
                }
                .sortedBy { it.startTime }
                .toList()
        } else {
            emptyList()
        }
    }

    // ---- 网格配置（全主题一份，v2 存储含旧族迁移） ----
    val knownWidgetIds = remember { HomeWidgetRegistry.widgets.mapTo(mutableSetOf()) { it.id } }
    var gridConfig by remember {
        mutableStateOf(
            HomeGridConfig(
                campusSpan = CampusSpan.fromStorage(AHUCache.getCampusCardSpanV2()),
                icons = normalizeHomeGridIcons(AHUCache.getHomeGridIconsV2(), knownWidgetIds)
            )
        )
    }
    var isEditingHome by remember { mutableStateOf(false) }
    var qrExpanded by remember { mutableStateOf(false) }

    fun saveIcons(icons: List<String?>) {
        val normalized = normalizeHomeGridIcons(icons, knownWidgetIds)
        gridConfig = gridConfig.copy(icons = normalized)
        AHUCache.saveHomeGridIconsV2(normalized)
    }

    /** 拖拽换位：交换两槽内容（压实由 saveIcons 保证）。 */
    fun swapSlots(from: Int, to: Int) {
        val next = gridConfig.icons.toMutableList()
        if (from !in next.indices || to !in next.indices || from == to) return
        val tmp = next[from]
        next[from] = next[to]
        next[to] = tmp
        saveIcons(next)
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_MOVED, from + 1, to + 1,
            coarseValueBucket = "GRID_SWAP"
        )
    }

    fun removeSlot(slotIndex: Int) {
        val next = gridConfig.icons.toMutableList()
        val removed = next.getOrNull(slotIndex) ?: return
        next[slotIndex] = null
        saveIcons(next)
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_REMOVED, removed, null,
            coarseValueBucket = removed.uppercase()
        )
    }

    fun addToSlot(slotIndex: Int, widgetId: String) {
        val next = gridConfig.icons.toMutableList()
        if (slotIndex !in next.indices || widgetId in next) return
        next[slotIndex] = widgetId
        saveIcons(next)
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_ADDED, null, widgetId,
            coarseValueBucket = widgetId.uppercase()
        )
    }

    fun changeCampusSpan(span: CampusSpan) {
        if (gridConfig.campusSpan == span) return
        gridConfig = gridConfig.copy(campusSpan = span)
        AHUCache.saveCampusCardSpanV2(span.storageValue)
    }

    fun enterEdit() {
        if (homeEditEnabled) isEditingHome = true
    }

    fun exitEdit() {
        isEditingHome = false
    }

    BackHandler(enabled = isEditingHome) { exitEdit() }

    LaunchedEffect(enterEditModeRequest) {
        if (enterEditModeRequest) {
            enterEdit()
            onEnterEditModeRequestConsumed()
        }
    }
    LaunchedEffect(homeEditEnabled) {
        if (!homeEditEnabled) exitEdit()
    }
    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            discoveryViewModel.loadActivityBean()
            scheduleViewModel.loadConfig()
            scheduleViewModel.refreshSchedule(isRefresh = true)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = withContext(Dispatchers.IO) { DebugClock.nowDate() }
            val calendar = Calendar.getInstance(Locale.CHINA).apply { time = now }
            currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            delay(HOME_REFRESH_INTERVAL_MS)
            discoveryViewModel.refreshCardBalance()
        }
    }
    DisposableEffect(Unit) { onDispose { exitEdit() } }

    GlassBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        CompositionLocalProvider(LocalLiquidGlassAmbientBackdrop provides backdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
                    .pointerInput(isEditingHome) {
                        detectTapGestures(onTap = { if (isEditingHome) exitEdit() })
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .systemBarsPadding()
                        .padding(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HomeTitleRow(onOpenWeather = { navController.navigate("weather") })
                    AtAGlance(
                        todayCourses = todayCourses,
                        currentMinutes = currentMinutes,
                        onOpenSchedule = onOpenSchedule,
                        isInSemester = isInSemester,
                        enabled = !isEditingHome
                    )
                    if (todayCourses.isNotEmpty()) {
                        CourseStrip(
                            todayCourses = todayCourses,
                            currentMinutes = currentMinutes,
                            onOpenSchedule = onOpenSchedule
                        )
                    }
                    HomeGrid(
                        config = gridConfig,
                        isEditing = isEditingHome,
                        qrExpanded = qrExpanded,
                        onEnterEdit = ::enterEdit,
                        onRemoveSlot = ::removeSlot,
                        onAddToSlot = ::addToSlot,
                        onSwapSlots = ::swapSlots,
                        onCampusSpanChange = ::changeCampusSpan,
                        onOpenMore = { navController.navigate("widgets") },
                        onOpenWidget = { route -> navController.navigate(route) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        campusContent = { cellModifier ->
                            CampusCard(
                                balance = discoveryViewModel.balance,
                                transitionBalance = discoveryViewModel.transitionBalance,
                                onRefreshBalance = discoveryViewModel::refreshCardBalance,
                                navController = navController,
                                enabled = !isEditingHome,
                                modifier = cellModifier,
                                onQrVisibilityChange = { qrExpanded = it }
                            )
                        }
                    )
                }
            }
        }
    }
}
