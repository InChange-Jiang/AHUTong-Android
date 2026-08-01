package com.ahu.ahutong.personalization.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.PredictionUiState
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun SmartSuggestionHost(
    runtime: BehaviorPredictionRuntime,
    blocked: Boolean,
    onSuggestionClick: (PredictionUiState.Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by runtime.uiState.collectAsState()
    LaunchedEffect(blocked) {
        if (blocked) runtime.hideSuggestion()
    }
    if (blocked) return
    val suggestion = state as? PredictionUiState.Suggestion ?: return
    val suggestionShape = MaterialTheme.shapes.extraLarge
    val pressInteractions = remember { MutableInteractionSource() }
    val expansion = remember { Animatable(0f) }
    val opacity = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset.Zero) }
    var waveVisible by remember { mutableStateOf(false) }
    val waveColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(pressInteractions) {
        var waveJob: Job? = null
        pressInteractions.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                waveJob?.cancel()
                pressCenter = interaction.pressPosition
                waveJob = launch {
                    expansion.snapTo(0f)
                    opacity.snapTo(PRESS_WAVE_START_ALPHA)
                    waveVisible = true
                    coroutineScope {
                        launch {
                            expansion.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = PRESS_WAVE_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                        launch {
                            opacity.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = PRESS_WAVE_DURATION_MS,
                                    easing = LinearEasing
                                )
                            )
                        }
                    }
                    waveVisible = false
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .clickable(
                interactionSource = pressInteractions,
                indication = null,
                role = Role.Button,
                onClick = { onSuggestionClick(suggestion) }
            )
            .semantics { contentDescription = "猜你想用：${suggestion.title}" },
        shape = suggestionShape,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .clip(suggestionShape)
                .drawWithContent {
                    drawContent()
                    if (waveVisible && opacity.value > 0f) {
                        val center = if (pressCenter.x.isFinite() && pressCenter.y.isFinite()) {
                            Offset(
                                x = pressCenter.x.coerceIn(0f, size.width),
                                y = pressCenter.y.coerceIn(0f, size.height)
                            )
                        } else {
                            Offset(size.width / 2f, size.height / 2f)
                        }
                        val farthestX = max(center.x, size.width - center.x)
                        val farthestY = max(center.y, size.height - center.y)
                        drawCircle(
                            color = waveColor,
                            radius = hypot(farthestX, farthestY) * expansion.value,
                            center = center,
                            alpha = opacity.value
                        )
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("猜你想用 · ${suggestion.title}", style = MaterialTheme.typography.titleSmall)
                    Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = runtime::dismissSuggestionByUser) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭建议")
                }
            }
        }
    }
}

private const val PRESS_WAVE_DURATION_MS = 700
private const val PRESS_WAVE_START_ALPHA = 0.22f
