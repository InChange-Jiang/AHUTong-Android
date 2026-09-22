# 主页课程轮盘卡片阴影被矩形裁剪 —— 调研结论与修复方案

> 调研范围：HomeClassCard / CourseStrip 玻璃卡在滑动切换时阴影被裁的问题。
> 结论基于工程实际依赖的 Compose Foundation/UI 源码（Gradle 缓存 1.10.2 / 1.8.3）与
> backdrop 库源码（io.github.kyant0:backdrop）逐行核对，非猜测。

## 症状

- 卡片停稳（settle）后：阴影正常显示。
- 只要有一点点位移：**瞬间出现一个矩形边界，把阴影全部裁掉**。
- 矩形边界 = 卡片自身的 layout 边界。

## 涉及文件

| 文件 | 作用 |
| --- | --- |
| `app/src/main/java/com/ahu/ahutong/ui/screen/main/home/CourseStrip.kt` | 课程条轮盘（HorizontalPager），问题现场 |
| `app/src/main/java/com/ahu/ahutong/ui/screen/main/home/HomeClass.kt` | HomeClassCard，玻璃表面 + backdrop 库阴影 |
| `core/designsystem/src/main/java/com/ahu/ahutong/ui/components/LiquidGlass.kt` | `liquidGlassSurface()`（blur 18dp + Shadow 14dp 黑 12%） |

问题代码位置：`CourseStrip.kt` HorizontalPager 的 page 内容，约 119–126 行：

```kotlin
HomeClassCard(
    course = course,
    status = status,
    isFocus = isFocus,
    onClick = onOpenSchedule,
    modifier = Modifier
        .zIndex(1f - offset)
        .graphicsLayer {
            val t = offset.coerceIn(0f, 1.5f)
            scaleX = 1f - 0.12f * t
            scaleY = 1f - 0.12f * t
            alpha = 1f - 0.45f * t   // ← 元凶：alpha < 1 触发离屏裁剪
        }
)
```

## 根因（源码级实锤）

**不是邻卡遮挡，不是 Pager 容器裁剪，是 `graphicsLayer` 的 `alpha < 1` 触发的隐式离屏裁剪。**

机制链条：

1. backdrop 库的 `ShadowNode` 把阴影画在**节点边界之外**（约 2×radius 处），
   平时这个越界绘制是合法的（Compose 默认不裁剪越界绘制）。
2. Compose 官方 KDoc（`GraphicsLayerScope.size`，ui 1.10.2 源码原文）：

   > Drawing commands can extend beyond the size specified, however,
   > **if the graphicsLayer is promoted to an offscreen rasterization layer,
   > any content rendered outside of the specified size will be clipped.**

3. 什么时候被提升为离屏层？`AndroidGraphicsLayer.android.kt`（ui-graphics 源码）：

   ```kotlin
   val useSaveLayer =
       layerAlpha < 1.0f ||            // alpha 严格小于 1 即触发
           layerBlendMode != BlendMode.SrcOver ||
           layerColorFilter != null ||
           compositingStrategy == CompositingStrategy.Offscreen
   if (useSaveLayer) {
       androidCanvas.saveLayer(left, top, right, bottom, paint) // 用 layer 矩形做裁剪边界
   }
   ```

4. 因此：
   - **静止的焦点卡**：offset = 0 → t = 0 → **alpha 恰好 = 1.0f** → 不触发离屏 → 阴影正常。
   - **任何位移**：t > 0 → alpha < 1（哪怕 0.999）→ 离屏提升 → saveLayer 矩形裁掉越界的阴影。
   - 纯 scale / translation **不会**触发（不在 useSaveLayer 条件里），只有 alpha 会。

### 可自验证的预测

两侧邻卡静止时 alpha = 0.55 < 1，它们的阴影**现在也一直被裁着**，只是被缩小、
叠压和半透明挡住不显眼。修复前仔细看侧卡应该能发现；修复后应该恢复。

## 修复方案（按推荐度排序）

### 方案 1（推荐先试，一行改动）：`CompositingStrategy.ModulateAlpha`

```kotlin
.graphicsLayer {
    val t = offset.coerceIn(0f, 1.5f)
    scaleX = 1f - 0.12f * t
    scaleY = 1f - 0.12f * t
    alpha = 1f - 0.45f * t
    compositingStrategy = CompositingStrategy.ModulateAlpha   // ← 新增这一行
}
```

原理：ModulateAlpha 跳过离屏缓冲，把 alpha 直接调制到绘制命令上（等效 paint alpha），
不做 saveLayer → 无隐式裁剪。JetBrains compose-multiplatform 官方 changelog 明确说明
这就是为避开"离屏提升的隐式 clipToBounds"而设计的用法。

代价：若 layer 内部有重叠的半透明内容，alpha 会分别作用于每条绘制命令而非整体合成。
本卡片内容为平面绘制、玻璃表面自己已是独立 offscreen 层，预期视觉差异为零。
若发现玻璃混合出现异常（半透明内容交叉处变淡），退到方案 2 / 3。

需要 import：`androidx.compose.ui.graphics.CompositingStrategy`。

### 方案 2：扩大 layer 边界（bleed 方案，视觉零妥协）

把卡片包进一个比卡片大 2×阴影半径的 Box，graphicsLayer 挂外层 Box，卡片内容 inset。
离屏裁剪矩形变大，阴影落在边界内。结构改动大，动画库常用这招预留 bleed 区。

### 方案 3：去掉 alpha 动画

景深渐隐改由卡片内部实现（如 `liquidGlassTint()` 的 alpha 随 offset 变淡、文字颜色变淡）。
彻底不触发离屏，但视觉稍打折扣。

### 不推荐的路线（调研中已排除）

- **邻卡遮挡 / zIndex 翻转**：不是本次症状的原因（症状是"一动就裁、停下恢复"，
  与邻卡位置无关；zIndex 只影响绘制顺序）。
- **Pager / LazyRow 容器裁剪**：Foundation 的 `clipScrollableContainer` 只裁主轴（左右），
  副轴（上下）各留 30dp 豁免区（`MaxSupportedElevation = 30.dp`），14dp 阴影上下方向不受影响。
- **升级 backdrop 2.x**：问题在 Compose 合成策略，不在库版本。
- **`graphicsLayer.shadowElevation` 硬件阴影**：同样受离屏裁剪影响，且先 clip 会裁掉玻璃 blur 边缘。

## 验收要点

1. 滑动过程中卡片阴影连续存在，无矩形硬边。
2. 停稳后焦点卡阴影与修复前一致（半径 14dp、黑色 12% 透明度）。
3. 侧卡（alpha 0.55）阴影在静止时也恢复显示（修复前是被裁的）。
4. 暗色 / 亮色主题、RadiantUI / LiquidGlass 两主题下各验证一次。
5. 玻璃卡混合效果无异常（重点看半透明 tint 与背景渐变的交界）。

## 参考来源

- 本机 Gradle 缓存 Compose 源码：`ui-graphics` `GraphicsLayerScope.kt`（size KDoc）、
  `AndroidGraphicsLayer.android.kt`（useSaveLayer 逻辑）、foundation `ClipScrollableContainer.kt`
- backdrop 库源码：`ShadowNode`（阴影画在节点边界外）
- JetBrains compose-multiplatform CHANGELOG（ModulateAlpha 避免隐式 clipToBounds 的说明）
