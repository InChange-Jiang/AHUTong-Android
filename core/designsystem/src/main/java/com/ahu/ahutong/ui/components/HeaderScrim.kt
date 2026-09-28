package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

/**
 * 一级页固定标题栏遮罩（共享实现，替代各页复制的 4 段式 verticalGradient）。
 *
 * 三态路由（挂接性能开关 LocalGlassEffectsReduced）：
 * 1. 性能开关开启（关闭玻璃效果）→ 纯色 4 段渐变遮罩，零采样开销（原效果降级）
 * 2. 液态玻璃可用且性能开关关闭 → 均匀高斯模糊 + 同款渐变 scrim 叠加（方案 a）。
 *    「渐浓」观感由 scrim 渐变叠在均匀模糊之上形成——顶部近实色、下缘透出模糊的滚动内容
 * 3. 其余（液态关闭 / 低端设备不支持 blur）→ 纯色渐变遮罩兜底
 *
 * 模糊采样页面自建的内容层（[headerBackdropSource] 挂在滚动内容上）。
 * 不复用 Main 的 NavHost content 层——标题栏自身在那层里，直接采样会产生自反馈。
 */
@Composable
fun Modifier.headerScrim(headerBg: Color, contentLayer: LayerBackdrop? = null): Modifier {
    val gradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to headerBg,
            0.35f to headerBg,
            0.68f to headerBg.copy(alpha = 0.85f),
            1f to headerBg.copy(alpha = 0f)
        )
    )
    if (contentLayer == null || !isHeaderBlurActive()) return background(gradient)
    return drawPlainBackdrop(
        backdrop = contentLayer,
        shape = { RectangleShape },
        effects = {
            // 比卡片（18dp）略强：scrim 顶部近实色，模糊主要在下缘透出区可见
            blur(22.dp.toPx())
        },
        onDrawSurface = { drawRect(gradient) }
    )
}

/** 标题栏模糊是否生效：液态可用 + 性能开关关闭 + 设备支持 blur。 */
@Composable
fun isHeaderBlurActive(): Boolean =
    LocalIsLiquidGlassEnabled.current &&
        !LocalGlassEffectsReduced.current &&
        LocalLiquidGlassTokens.current.quality.supportsBlur

/**
 * 滚动内容挂载为标题栏模糊的采样源。
 * 模糊未生效时零开销直通（不建层）。
 */
@Composable
fun Modifier.headerBackdropSource(layer: LayerBackdrop): Modifier =
    if (isHeaderBlurActive()) layerBackdrop(layer) else this
