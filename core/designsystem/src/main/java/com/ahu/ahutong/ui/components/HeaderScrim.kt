package com.ahu.ahutong.ui.components

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy

/**
 * 一级页固定标题栏遮罩（共享实现，替代各页复制的 4 段式 verticalGradient）。
 *
 * 三态路由（挂接性能开关 LocalGlassEffectsReduced）：
 * 1. 性能开关开启（关闭玻璃效果）→ 纯色 4 段渐变遮罩，零采样开销（原效果降级）
 * 2. 液态玻璃可用且性能开关关闭 → 渐变式高斯模糊（纯毛玻璃，无任何颜色叠加）：
 *    顶部强度与底部导航栏一致（vibrancy + floating 级 blur，完整毛玻璃观感），
 *    向下线性衰减到底部 0%（内容完全清晰）。
 *    API 33+ 用 AGSL 逐行调制不透明度；API 31-32 无 AGSL 退化为均匀毛玻璃
 * 3. 其余（液态关闭 / 低端设备不支持 blur）→ 纯色渐变遮罩兜底
 *
 * 模糊采样源 = 全局背景层（壁纸/场景渐变，ambient）+ 页面内容层（[headerBackdropSource]
 * 挂在滚动内容上）的组合。壁纸画在 ambient 层，只采内容层会漏掉壁纸——头部静止区域是
 * 透明像素，blur 后仍透明，视觉上"完全没有模糊"（Main 导航栏 P2 回归同款坑）。
 * 不复用 Main 的 NavHost content 层——标题栏自身在那层里，直接采样会产生自反馈。
 */
@Composable
fun Modifier.headerScrim(headerBg: Color, contentLayer: LayerBackdrop? = null): Modifier {
    if (contentLayer == null || !isHeaderBlurActive()) {
        return background(headerScrimGradient(headerBg))
    }
    // 采样源组合：页面内容层 + 全局背景层（壁纸在 ambient 层，仅采内容层会漏掉壁纸 →
    // 头部静止区域采到透明像素，blur 后仍透明，表现为"看不到任何模糊"，即 Main 导航栏
    // P2 回归的同款坑，解法同 rememberCombinedBackdrop(ambient, content)）
    val source = rememberCombinedBackdrop(LocalLiquidGlassAmbientBackdrop.current, contentLayer)
    // 与底部导航栏（LiquidBottomTabs）同款强度参数：vibrancy + floating 级 blur 半径
    val blurRadiusPx = with(LocalDensity.current) {
        LocalLiquidGlassTokens.current.floating.blurRadius.toPx()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 渐变式毛玻璃：均匀 blur 后接 AGSL 按行调制 alpha（顶部全显模糊 → 底部 0）。
        // 强度对齐底部导航栏（LiquidBottomTabs 用 tokens.floating 的 vibrancy+blur）：
        // blur 半径取 floating.blurRadius，顶部 alpha=1（与导航栏一致的"完整毛玻璃"观感），
        // 向下线性衰减到 0。
        // backdrop 1.0.0 无 runtimeShaderEffect 扩展，走 scope.obtainRuntimeShader
        // + RenderEffect.createRuntimeShaderEffect 手工链（内部就是官方链式合成）
        return drawPlainBackdrop(
            backdrop = source,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(blurRadiusPx)
                val shader = obtainRuntimeShader(
                    "header_progressive_blur",
                    PROGRESSIVE_BLUR_SHADER
                )
                shader.setFloatUniform("height", size.height.coerceAtLeast(1f))
                effect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                )
            },
            onDrawSurface = { } // 纯毛玻璃：不叠加任何颜色渐变
        )
    }
    // API 31-32：RenderEffect 可用但无 AGSL，退化为均匀毛玻璃（仍无颜色叠加）
    return drawPlainBackdrop(
        backdrop = source,
        shape = { RectangleShape },
        effects = {
            vibrancy()
            blur(blurRadiusPx)
        },
        onDrawSurface = { }
    )
}

/**
 * 渐变模糊 AGSL：输入为已模糊的背景层（预乘 alpha），
 * 按像素 y 调制不透明度——顶部与底部导航栏同强度（全显模糊采样），底部线性衰减到 0。
 */
private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float height;
half4 main(float2 coord) {
    half4 c = content.eval(coord);
    float t = clamp(1.0 - coord.y / height, 0.0, 1.0);
    return c * t;
}
"""

/** 性能降级态 / 兜底态的纯色 4 段渐变遮罩（原一级页标题栏配方）。 */
private fun headerScrimGradient(headerBg: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to headerBg,
        0.35f to headerBg,
        0.68f to headerBg.copy(alpha = 0.85f),
        1f to headerBg.copy(alpha = 0f)
    )
)

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
