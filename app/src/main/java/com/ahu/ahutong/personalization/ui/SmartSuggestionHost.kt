package com.ahu.ahutong.personalization.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.PredictionUiState
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.collect

@Composable
fun SmartSuggestionHost(
    runtime: BehaviorPredictionRuntime,
    backdrop: Backdrop,
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
    val suggestionShape = ContinuousCapsule
    val animationScope = rememberCoroutineScope()
    val pressInteractions = remember(suggestion.executionId) { MutableInteractionSource() }
    val interactiveHighlight = remember(animationScope, suggestion.executionId) {
        InteractiveHighlight(
            animationScope = animationScope,
            highlightRadiusMultiplier = 0.9f
        )
    }
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val isLightTheme = !isSystemInDarkTheme()
    val glassContainerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.4f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val lifetimeOpacity = remember(suggestion.executionId) { Animatable(1f) }
    val accentContentColor = MaterialTheme.colorScheme.primary.copy(alpha = lifetimeOpacity.value)
    val primaryContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = lifetimeOpacity.value)
    val secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lifetimeOpacity.value)

    LaunchedEffect(
        suggestion.executionId,
        suggestion.shownAtElapsedMs,
        suggestion.expiresAtElapsedMs,
        suggestion.visibilityPaused
    ) {
        if (suggestion.visibilityPaused) {
            lifetimeOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        val remainingDurationMs = (suggestion.expiresAtElapsedMs - now).coerceAtLeast(0L)
        lifetimeOpacity.snapTo(
            SuggestionPolicy.remainingVisibilityFraction(
                shownAtElapsedMs = suggestion.shownAtElapsedMs,
                expiresAtElapsedMs = suggestion.expiresAtElapsedMs,
                nowElapsedMs = now
            )
        )
        if (remainingDurationMs > 0L) {
            lifetimeOpacity.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = remainingDurationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    LaunchedEffect(pressInteractions, suggestion.executionId) {
        var activePress: PressInteraction.Press? = null
        pressInteractions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    activePress = interaction
                    runtime.pauseSuggestionVisibility(suggestion.executionId)
                }
                is PressInteraction.Release -> {
                    if (interaction.press == activePress) {
                        activePress = null
                        runtime.restartSuggestionVisibility(suggestion.executionId)
                    }
                }
                is PressInteraction.Cancel -> {
                    if (interaction.press == activePress) {
                        activePress = null
                        runtime.restartSuggestionVisibility(suggestion.executionId)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (isLiquidGlass) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { suggestionShape },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                            opacity(lifetimeOpacity.value)
                        },
                        highlight = { Highlight.Default.copy(alpha = lifetimeOpacity.value) },
                        shadow = { Shadow(alpha = lifetimeOpacity.value) },
                        layerBlock = {
                            val width = size.width
                            val height = size.height
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
                            val maxOffset = size.minDimension
                            val offset = interactiveHighlight.offset
                            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                            val maxDragScale = 4f.dp.toPx() / height
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX = scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                            scaleY = scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                        },
                        onDrawSurface = {
                            drawRect(
                                glassContainerColor.copy(
                                    alpha = glassContainerColor.alpha * lifetimeOpacity.value
                                )
                            )
                        }
                    )
                } else {
                    Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = suggestionShape,
                            ambientColor = Color.Black.copy(alpha = 0.12f * lifetimeOpacity.value),
                            spotColor = Color.Black.copy(alpha = 0.12f * lifetimeOpacity.value)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = lifetimeOpacity.value),
                            shape = suggestionShape
                        )
                }
            )
            .clickable(
                interactionSource = pressInteractions,
                indication = null,
                role = Role.Button,
                onClick = { onSuggestionClick(suggestion) }
            )
            .semantics { contentDescription = "猜你想用：${suggestion.title}" }
            .clip(suggestionShape)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
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
                tint = accentContentColor
            )
            Column {
                Text(
                    "猜你想用 · ${suggestion.title}",
                    color = primaryContentColor,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    suggestion.reason,
                    color = secondaryContentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = runtime::dismissSuggestionByUser) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭建议",
                    tint = primaryContentColor
                )
            }
        }
    }
}
