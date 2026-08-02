package com.ahu.ahutong.personalization.diagnostics

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.RuntimeDiagnosticsState
import com.ahu.ahutong.personalization.runtime.SanitizedDiagnosticsSnapshot
import com.ahu.ahutong.personalization.prefetch.PrefetchCoordinator
import com.ahu.ahutong.personalization.prefetch.PrefetchDiagnostic
import com.ahu.ahutong.personalization.prefetch.PrefetchState
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class DebugDiagnosticsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("model_diagnostics_debug", Context.MODE_PRIVATE)
    val enabled = MutableStateFlow(preferences.getBoolean("model_diagnostics_enabled", true))
    val paused = MutableStateFlow(false)
    fun setEnabled(value: Boolean) {
        enabled.value = value
        preferences.edit().putBoolean("model_diagnostics_enabled", value).apply()
    }
    fun togglePaused() { paused.value = !paused.value }
    fun x(): Float = preferences.getFloat("floating_ball_x", 0f)
    fun y(): Float = preferences.getFloat("floating_ball_y", 0f)
    fun save(x: Float, y: Float) { preferences.edit().putFloat("floating_ball_x", x).putFloat("floating_ball_y", y).apply() }
}

@Singleton
class DebugDiagnosticsContribution @Inject constructor(
    private val preferences: DebugDiagnosticsPreferences,
    private val prefetchCoordinator: PrefetchCoordinator
) : DiagnosticsContribution {
    override fun isDiagnosticsRoute(route: String?): Boolean = route == DIAGNOSTICS_ROUTE

    override fun installRoutes(
        builder: NavGraphBuilder,
        navController: NavHostController,
        runtime: BehaviorPredictionRuntime
    ) {
        builder.composable(DIAGNOSTICS_ROUTE) {
            DiagnosticsScreen(runtime, prefetchCoordinator, preferences)
        }
    }

    @Composable
    override fun BoxScope.Overlay(
        navController: NavHostController,
        runtime: BehaviorPredictionRuntime,
        blocked: Boolean
    ) {
        val enabled by preferences.enabled.collectAsState()
        if (!enabled || blocked) return
        var offsetX by remember { mutableFloatStateOf(preferences.x()) }
        var offsetY by remember { mutableFloatStateOf(preferences.y()) }
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val ballPx = with(density) { 52.dp.toPx() }
        val horizontalInsetPx = with(density) { 12.dp.toPx() }
        val minX = -(screenWidthPx - ballPx - horizontalInsetPx * 2).coerceAtLeast(0f)
        val maxY = (screenHeightPx / 2f - ballPx).coerceAtLeast(0f)
        LaunchedEffect(minX, maxY) {
            offsetX = offsetX.coerceIn(minX, 0f)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(end = 12.dp)
                .size(52.dp)
                .clip(CircleShape)
                .pointerInput(minX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < minX / 2f) minX else 0f
                            preferences.save(offsetX, offsetY)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(minX, 0f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(-maxY, maxY)
                    }
                }
                .combinedClickable(
                    onClick = { navController.navigate(DIAGNOSTICS_ROUTE) { launchSingleTop = true } },
                    onLongClick = preferences::togglePaused
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Analytics, contentDescription = "模型诊断")
            }
        }
    }

    private companion object {
        const val DIAGNOSTICS_ROUTE = "debug_model_diagnostics"
    }
}

@Composable
private fun DiagnosticsScreen(
    runtime: BehaviorPredictionRuntime,
    prefetchCoordinator: PrefetchCoordinator,
    preferences: DebugDiagnosticsPreferences
) {
    val state by runtime.diagnostics.collectAsState()
    val prefetch by prefetchCoordinator.diagnostics.collectAsState()
    val enabled by preferences.enabled.collectAsState()
    val paused by preferences.paused.collectAsState()
    var snapshot by remember { mutableStateOf(SanitizedDiagnosticsSnapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!paused) snapshot = runtime.sanitizedDiagnosticsSnapshot()
            delay(500)
        }
    }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DiagnosticsSection {
                Text("端侧行为模型诊断", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Debug 专用 · 所有内容均已脱敏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        text = if (paused) "已暂停刷新" else "实时刷新",
                        positive = !paused
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = preferences::togglePaused) {
                        Icon(
                            if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (paused) "继续" else "暂停")
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示诊断悬浮球", fontWeight = FontWeight.Medium)
                        Text(
                            "长按悬浮球也可以暂停或继续刷新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = preferences::setEnabled)
                }
            }
        }
        item { DiagnosticsSummary(state) }
        item {
            DiagnosticsSection(title = "下一步动作预测") {
                val best = state.effectiveProbabilities.maxByOrNull(Map.Entry<String, Float>::value)
                if (best != null) {
                    Text(
                        "当前最可能：${readableAction(best.key)}  ${(best.value * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    EmptyDiagnosticsText("等待第一个有效预测机会")
                }
                ProbabilityPanel("实际决策", "策略引擎最终使用的概率", state.effectiveProbabilities)
                HorizontalDivider()
                ProbabilityPanel("统计模型", "冷启动与故障兜底", state.statProbabilities)
                HorizontalDivider()
                ProbabilityPanel("Tiny MLP", "端侧神经模型当前输出", state.tinyProbabilities)
            }
        }
        item {
            LearningSection(state, snapshot)
        }
        item {
            DiagnosticsSection(title = "针对性语义事件") {
                LabeledValue("候选域", state.candidateScope, monospace = true)
                LabeledValue(
                    "定向动作",
                    state.targetedActions.sorted().joinToString().ifBlank { "--" },
                    monospace = true
                )
                state.candidateRejectionReason?.let {
                    LabeledValue("拒绝原因", it, monospace = true)
                }
                HorizontalDivider()
                if (snapshot.recentSemanticEvents.isEmpty()) {
                    EmptyDiagnosticsText("尚未记录已提交的语义变化")
                } else {
                    snapshot.recentSemanticEvents.take(12).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                HorizontalDivider()
                snapshot.recentSemanticChangeSets.take(8).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
        item {
            DiagnosticsSection(title = "多跳旅程与参数模型") {
                LabeledValue("Pending journey", snapshot.pendingJourney ?: "无", monospace = true)
                LabeledValue("Journey 样本", snapshot.journeyTrainingSamples.toString())
                LabeledValue("Preset 样本", snapshot.presetTrainingSamples.toString())
                snapshot.journeyProbabilities.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider()
                if (snapshot.presetCandidates.isEmpty()) {
                    EmptyDiagnosticsText("当前没有已排序的本地预设候选")
                } else {
                    snapshot.presetCandidates.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                snapshot.presetFeedbackDiagnostics.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider()
                if (snapshot.taskStates.isEmpty()) {
                    EmptyDiagnosticsText("任务模型尚未初始化")
                } else {
                    snapshot.taskStates.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
            }
        }
        item {
            PromotionSection(snapshot.promotionWindows)
        }
        item {
            PrefetchAndTelemetrySection(prefetch.values.toList(), snapshot.pendingTelemetryReports)
        }
        item {
            TimelineSection(snapshot.recentTimeline)
        }
        item {
            TechnicalDetailsSection(state, snapshot)
        }
    }
}

@Composable
private fun DiagnosticsSummary(state: RuntimeDiagnosticsState) {
    DiagnosticsSection(title = "运行概览") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(
                text = when {
                    !state.profileActive -> "等待登录"
                    !state.foreground -> "后台暂停"
                    else -> "前台运行"
                },
                positive = state.profileActive && state.foreground
            )
            StatusBadge(readablePreparation(state.preparationState), state.preparationState == "PENDING")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("模型阶段", readableStage(state.stage), Modifier.weight(1f))
            MetricTile("决策模式", readableTier(state.tier), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Tiny 权重", "${(state.lambda * 100).roundToInt()}%", Modifier.weight(1f))
            MetricTile("上一动作", readableAction(state.previousAction), Modifier.weight(1f))
        }
        state.lastResolution?.let {
            Text("最近结算：${readableAction(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        state.lastFailure?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "最近异常：${readableFailure(it)}",
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ProbabilityPanel(
    title: String,
    description: String,
    probabilities: Map<String, Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (probabilities.isEmpty()) {
            EmptyDiagnosticsText("暂无数据")
        }
        probabilities.entries.sortedByDescending(Map.Entry<String, Float>::value).take(5).forEach { (action, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.15f)) {
                    Text(readableAction(action), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        action,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary))
                }
                Text(
                    " ${(value * 100).roundToInt()}%",
                    modifier = Modifier.width(44.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun LearningSection(
    state: RuntimeDiagnosticsState,
    snapshot: SanitizedDiagnosticsSnapshot
) {
    DiagnosticsSection(title = "本地学习状态") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("训练样本", snapshot.trainingSamples.toString(), Modifier.weight(1f))
            MetricTile("有效动作样本", snapshot.organicNonNoneSamples.toString(), Modifier.weight(1f))
            MetricTile("覆盖动作族", snapshot.actionFamilies.toString(), Modifier.weight(1f))
        }
        Text(
            state.lastTraining?.let {
                "最近训练：${readableTrainingReason(it.reason)} · ${it.samples} 个样本 · ${formatDuration(it.elapsedNanos)}"
            } ?: "最近训练：尚未执行训练切片",
            style = MaterialTheme.typography.bodyMedium
        )
        state.lastTraining?.averageLoss?.let { loss ->
            Text(
                "Loss ${"%.4f".format(loss)} · 梯度范数 ${state.lastTraining?.gradientNorm?.let { "%.3f".format(it) } ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
        LabeledValue("统计模型开始学习", formatEpochDay(snapshot.statLearningStartedDay))
        LabeledValue("Tiny MLP 首次训练", formatEpochDay(snapshot.tinyTrainingStartedDay))
        LabeledValue("模型文件大小", formatBytes(snapshot.modelSizeBytes))
    }
}

@Composable
private fun PromotionSection(windows: List<String>) {
    DiagnosticsSection(title = "自动晋级评估") {
        if (windows.isEmpty()) {
            EmptyDiagnosticsText("尚未形成有效评估窗口")
        } else {
            windows.forEachIndexed { index, raw ->
                val parts = raw.split(' ')
                val passed = parts.lastOrNull() == "PASS"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(if (passed) "通过" else "未通过", passed)
                    Column(Modifier.weight(1f)) {
                        Text(readableStage(parts.getOrNull(0)), fontWeight = FontWeight.Medium)
                        Text(
                            buildString {
                                append(formatEpochDayRange(parts.getOrNull(1)))
                                parts.firstOrNull { it.startsWith("n=") }?.let { append(" · 样本 ${it.removePrefix("n=")}") }
                                parts.firstOrNull { it.startsWith("ECE=") }?.let { append(" · 校准误差 ${it.removePrefix("ECE=")}") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != windows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PrefetchAndTelemetrySection(
    entries: List<PrefetchDiagnostic>,
    pendingReports: Int
) {
    DiagnosticsSection(title = "预热与质量报告") {
        if (entries.isEmpty()) {
            EmptyDiagnosticsText("当前没有预热任务")
        } else {
            entries.sortedByDescending(PrefetchDiagnostic::startedAtElapsedMs).take(8).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(readablePrefetchState(entry.state), entry.state in SUCCESS_PREFETCH_STATES)
                    Column(Modifier.weight(1f)) {
                        Text(readableAction(entry.actionId), fontWeight = FontWeight.Medium)
                        entry.failureCode?.let {
                            Text(
                                readableFailure(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        LabeledValue("待上传聚合报告", "$pendingReports 份")
    }
}

@Composable
private fun TimelineSection(lines: List<String>) {
    DiagnosticsSection(title = "最近事件") {
        if (lines.isEmpty()) {
            EmptyDiagnosticsText("当前账号还没有行为事件")
        } else {
            lines.take(12).forEachIndexed { index, line ->
                val parts = line.split(' ', limit = 4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        parts.getOrNull(0) ?: "--",
                        modifier = Modifier.width(44.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${readableEvent(parts.getOrNull(1))} · ${readableAction(parts.getOrNull(2))}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            readableSource(parts.getOrNull(3)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != minOf(lines.lastIndex, 11)) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TechnicalDetailsSection(
    state: RuntimeDiagnosticsState,
    snapshot: SanitizedDiagnosticsSnapshot
) {
    var expanded by remember { mutableStateOf(false) }
    DiagnosticsSection {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("技术详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "排查问题时再展开",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起技术详情" else "展开技术详情"
            )
        }
        if (expanded) {
            HorizontalDivider()
            LabeledValue("Decision ID", state.decisionId?.take(8) ?: "--", monospace = true)
            LabeledValue("Active checkpoint", state.activeCheckpoint?.take(8) ?: "--", monospace = true)
            LabeledValue("Active checksum", snapshot.activeChecksum ?: "--", monospace = true)
            LabeledValue("Candidate checkpoint", snapshot.candidateCheckpoint ?: "--", monospace = true)
            LabeledValue("Training revision", snapshot.trainingRevision.toString(), monospace = true)
            LabeledValue("Session ID", state.sessionId?.take(8) ?: "--", monospace = true)
        }
    }
}

@Composable
private fun DiagnosticsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun StatusBadge(text: String, positive: Boolean) {
    val background = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = CircleShape, color = background) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun EmptyDiagnosticsText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun readableAction(id: String?): String {
    if (id.isNullOrBlank() || id == "--") return "无"
    if (id == AppActionCatalog.NONE_OUTPUT_ID) return "没有后续动作"
    if (id == AppActionCatalog.OTHER_OUTPUT_ID) return "其他动作"
    return AppActionId.fromStableId(id)?.let(AppActionCatalog::spec)?.title ?: id
}

private fun readableStage(value: String?): String = when (value) {
    "SHADOW" -> "影子学习"
    "ELIGIBLE" -> "已具备混合资格"
    "MIXED" -> "混合决策"
    "PRIMARY" -> "Tiny 主模型"
    else -> value ?: "未知"
}

private fun readableTier(value: String?): String = when (value) {
    "STAT_ONLY" -> "仅统计模型"
    "MIXED_10" -> "Tiny 10%"
    "MIXED_25" -> "Tiny 25%"
    "MIXED_50" -> "Tiny 50%"
    "PRIMARY" -> "Tiny 主模型"
    else -> value ?: "未知"
}

private fun readablePreparation(value: String): String = when (value) {
    "IDLE" -> "等待机会"
    "PREPARING" -> "正在预测"
    "PENDING" -> "等待真实行为"
    else -> value
}

private fun readableTrainingReason(value: String): String = when (value) {
    "NO_PENDING_PROFILE" -> "没有待训练账号"
    "MINIMUM_SAMPLE_GATE" -> "样本尚未达到训练门槛"
    "NOT_FOREGROUND_IDLE" -> "等待前台空闲"
    "LOW_BATTERY" -> "电量过低"
    "POWER_SAVER" -> "省电模式限制"
    "THERMAL_LIMIT" -> "设备温度限制"
    "TRAINED" -> "训练完成"
    else -> value
}

private fun readableEvent(value: String?): String = when (value) {
    "ACTION_INTENT_ACCEPTED" -> "用户动作"
    "SESSION_STARTED" -> "会话开始"
    "SESSION_ENDED" -> "会话结束"
    "PREDICTION_OPPORTUNITY" -> "创建预测机会"
    "PREFETCH_CONSUMED" -> "使用了预热结果"
    else -> value ?: "未知事件"
}

private fun readableSource(value: String?): String = when (value) {
    "ORGANIC" -> "自然行为，可用于学习"
    "SUGGESTION" -> "来自猜你想用，不参与学习"
    "DEEPLINK" -> "来自深链，不参与学习"
    "RESTORE" -> "页面恢复，不参与学习"
    "USER_PREFERENCE" -> "来自用户显式偏好，不参与学习"
    "SYSTEM" -> "系统事件，不参与学习"
    "DEBUG" -> "调试事件，不参与学习"
    else -> value ?: "来源未知"
}

private fun readablePrefetchState(value: PrefetchState): String = when (value) {
    PrefetchState.IDLE -> "等待"
    PrefetchState.RUNNING -> "预热中"
    PrefetchState.SUCCEEDED -> "已就绪"
    PrefetchState.FAILED -> "失败"
    PrefetchState.CANCELLED -> "已取消"
    PrefetchState.CONSUMED -> "已使用"
}

private fun readableFailure(value: String): String = when (value) {
    "AVAILABILITY_MISMATCH" -> "功能可用性已变化"
    "TINY_FORWARD_FAILED" -> "Tiny MLP 推理失败，已回退统计模型"
    "LOW_BATTERY" -> "电量过低"
    "POWER_SAVER" -> "省电模式限制"
    "THERMAL_LIMIT" -> "设备温度限制"
    else -> value
}

private fun formatDuration(nanos: Long): String = when {
    nanos <= 0L -> "--"
    nanos < 1_000_000L -> "${nanos / 1_000} μs"
    else -> "${nanos / 1_000_000} ms"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    else -> "${"%.1f".format(bytes / 1_024.0)} KiB"
}

private fun formatEpochDay(day: Long?): String = day?.let {
    runCatching { LocalDate.ofEpochDay(it).toString() }.getOrDefault("未知")
} ?: "尚未开始"

private fun formatEpochDayRange(value: String?): String {
    val range = value?.split("..", limit = 2).orEmpty()
    if (range.size != 2) return "窗口日期未知"
    val start = range[0].toLongOrNull()?.let(::formatEpochDay) ?: "未知"
    val end = range[1].toLongOrNull()?.let(::formatEpochDay) ?: "未知"
    return "$start 至 $end"
}

private val SUCCESS_PREFETCH_STATES = setOf(PrefetchState.SUCCEEDED, PrefetchState.CONSUMED)

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnostics(implementation: DebugDiagnosticsContribution): DiagnosticsContribution
}
