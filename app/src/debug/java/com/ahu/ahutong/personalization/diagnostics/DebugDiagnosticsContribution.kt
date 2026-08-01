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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.RuntimeDiagnosticsState
import com.ahu.ahutong.personalization.runtime.SanitizedDiagnosticsSnapshot
import com.ahu.ahutong.personalization.prefetch.PrefetchCoordinator
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("端侧行为模型诊断", style = MaterialTheme.typography.headlineMedium)
            Text("Debug 构建 · 内容已脱敏 · 不显示账号、输入文本、付款码或 Token")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("模型诊断悬浮球", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = preferences::setEnabled)
            }
            Text(if (paused) "实时刷新已暂停（长按悬浮球切换）" else "实时刷新最高 2 Hz")
        }
        item { DiagnosticsSummary(state) }
        item { ProbabilityPanel("统计模型", state.statProbabilities) }
        item { ProbabilityPanel("Tiny MLP active", state.tinyProbabilities) }
        item { ProbabilityPanel("真实决策", state.effectiveProbabilities) }
        item {
            Text("训练", style = MaterialTheme.typography.titleMedium)
            Text(state.lastTraining?.let { "${it.reason} · ${it.batches} batches · loss=${it.averageLoss ?: "--"} · ${(it.elapsedNanos / 1_000_000.0)} ms" } ?: "尚无训练切片")
            Text("样本=${snapshot.trainingSamples} · non-NONE=${snapshot.organicNonNoneSamples} · 动作族=${snapshot.actionFamilies}")
            Text("revision=${snapshot.trainingRevision} · candidate=${snapshot.candidateCheckpoint ?: "--"} · checksum=${snapshot.activeChecksum ?: "--"}")
            Text("模型=${snapshot.modelSizeBytes} bytes · stat day=${snapshot.statLearningStartedDay ?: "--"} · tiny day=${snapshot.tinyTrainingStartedDay ?: "--"}")
        }
        item {
            Text("晋级窗口", style = MaterialTheme.typography.titleMedium)
            snapshot.promotionWindows.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Text("预热与遥测", style = MaterialTheme.typography.titleMedium)
            prefetch.values.forEach { Text("${it.actionId}: ${it.state} ${it.failureCode ?: ""}", style = MaterialTheme.typography.bodySmall) }
            Text("待上传聚合报告=${snapshot.pendingTelemetryReports}")
        }
        item {
            Text("最近机会时间线", style = MaterialTheme.typography.titleMedium)
            snapshot.recentTimeline.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DiagnosticsSummary(state: RuntimeDiagnosticsState) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("机会与状态", style = MaterialTheme.typography.titleMedium)
            Text("${state.preparationState} · decision=${state.decisionId?.take(8) ?: "--"}")
            Text("stage=${state.stage} · tier=${state.tier} · λ=${state.lambda}")
            Text("checkpoint=${state.activeCheckpoint?.take(8) ?: "--"}")
            Text("上一动作=${state.previousAction ?: "--"} · 结算=${state.lastResolution ?: "--"}")
            state.lastFailure?.let { Text("异常=$it", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ProbabilityPanel(title: String, probabilities: Map<String, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        probabilities.entries.sortedByDescending(Map.Entry<String, Float>::value).take(12).forEach { (action, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary))
                }
                Text(" ${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnostics(implementation: DebugDiagnosticsContribution): DiagnosticsContribution
}
