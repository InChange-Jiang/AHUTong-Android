package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GridGap = 10.dp
private val CellHeight = 92.dp

/** 主卡片（校园卡）果冻回弹：低刚度 + 中欠阻尼。 */
private val CampusJellySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/** 配角让位：非线性轻弹。 */
private val ReflowSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private class CellAnimator {
    val x = Animatable(0f)
    val y = Animatable(0f)
}

/**
 * 主页 3×4 组件网格（全主题唯一布局族）：
 * 校园卡锚定左上角、占 4 格（2×2 ⇄ 1×4，编辑态拖右下角角标连续变形、松手果冻吸附）；
 * 7 功能槽 +「更多」由贪心算法（[computeHomeGridPlacements]）自动排布，允许留空位。
 * 编辑态：长按进入；图标格右上角 × 删除；空位 ＋ 添加；主卡片角标变形。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGrid(
    config: HomeGridConfig,
    isEditing: Boolean,
    onEnterEdit: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onAddToSlot: (Int, String) -> Unit,
    onCampusSpanChange: (CampusSpan) -> Unit,
    onOpenMore: () -> Unit,
    onOpenWidget: (String) -> Unit,
    modifier: Modifier = Modifier,
    campusContent: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val placements = remember(config) { computeHomeGridPlacements(config) }
    val gapPx = with(density) { GridGap.toPx() }
    val cellHPx = with(density) { CellHeight.toPx() }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gridWidthPx = with(density) { maxWidth.toPx() }
        val cellWPx = (gridWidthPx - gapPx * (HomeGridConfig.GRID_COLS - 1)) / HomeGridConfig.GRID_COLS
        val w2x2 = cellWPx * 2 + gapPx
        val h2x2 = cellHPx * 2 + gapPx
        val w1x4 = gridWidthPx
        val h1x4 = cellHPx

        // 主卡片尺寸动画（拖拽期间 snapTo 跟随手指，松手 animateTo 果冻吸附）
        val campusW = remember { Animatable(w2x2) }
        val campusH = remember { Animatable(h2x2) }
        LaunchedEffect(config.campusSpan, cellWPx) {
            val (tw, th) = when (config.campusSpan) {
                CampusSpan.SQUARE_2X2 -> w2x2 to h2x2
                CampusSpan.ROW_1X4 -> w1x4 to h1x4
            }
            launch { campusW.animateTo(tw, CampusJellySpring) }
            launch { campusH.animateTo(th, CampusJellySpring) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (cellHPx * 3 + gapPx * 2).toDp() })
        ) {
            // 主卡片
            Box(
                modifier = Modifier
                    .width(with(density) { campusW.value.toDp() })
                    .height(with(density) { campusH.value.toDp() })
                    .zIndex(2f)
            ) {
                campusContent(Modifier.fillMaxSize())

                if (isEditing) {
                    // 变形角标：拖右下角
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(70.a1 withNight 60.a1)
                            .pointerInput(cellWPx) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val f =
                                            (campusW.value - w2x2) / (w1x4 - w2x2).coerceAtLeast(1f)
                                        val target =
                                            if (f >= 0.5f) CampusSpan.ROW_1X4 else CampusSpan.SQUARE_2X2
                                        scope.launch {
                                            val (tw, th) = when (target) {
                                                CampusSpan.SQUARE_2X2 -> w2x2 to h2x2
                                                CampusSpan.ROW_1X4 -> w1x4 to h1x4
                                            }
                                            launch { campusW.animateTo(tw, CampusJellySpring) }
                                            launch { campusH.animateTo(th, CampusJellySpring) }
                                        }
                                        onCampusSpanChange(target)
                                    }
                                ) { change, _ ->
                                    change.consume()
                                    val fingerX = change.position.x +
                                        campusW.value - 26.dp.toPx() // 角标局部坐标 → 网格坐标
                                    val f = ((fingerX - w2x2) / (w1x4 - w2x2))
                                        .coerceIn(0f, 1f)
                                    scope.launch {
                                        campusW.snapTo(w2x2 + (w1x4 - w2x2) * f)
                                        campusH.snapTo(h2x2 + (h1x4 - h2x2) * f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⤡", color = 100.n1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 图标格 / 空位 / 更多
            for ((slotKey, p) in placements) {
                if (slotKey == CampusCardSlotId) continue
                key(slotKey) {
                    val animator = remember { CellAnimator() }
                    val targetX = p.col * (cellWPx + gapPx)
                    val targetY = p.row * (cellHPx + gapPx)
                    LaunchedEffect(targetX, targetY) {
                        launch { animator.x.animateTo(targetX, ReflowSpring) }
                        launch { animator.y.animateTo(targetY, ReflowSpring) }
                    }
                    val cellModifier = Modifier
                        .offset { IntOffset(animator.x.value.roundToInt(), animator.y.value.roundToInt()) }
                        .width(with(density) { cellWPx.toDp() })
                        .height(CellHeight)

                    when {
                        slotKey == HomeGridConfig.MORE_SLOT_ID -> GridIconCell(
                            iconId = com.ahu.ahutong.R.drawable.ic_more_all,
                            title = "更多",
                            tint = 50.n1 withNight 70.n1,
                            modifier = cellModifier,
                            onClick = onOpenMore,
                            onLongClick = onEnterEdit
                        )

                        slotKey.startsWith("#empty_") -> {
                            val slotIndex = slotKey.removePrefix("#empty_").toInt()
                            GridEmptyCell(
                                isEditing = isEditing,
                                modifier = cellModifier,
                                onClick = { pickerSlot = slotIndex },
                                onLongClick = onEnterEdit
                            )
                        }

                        else -> {
                            val spec = HomeWidgetRegistry.widgetById[slotKey]
                            val slotIndex = config.icons.indexOf(slotKey)
                            if (spec != null) {
                                Box(modifier = cellModifier) {
                                    GridIconCell(
                                        iconId = spec.iconId,
                                        title = spec.title,
                                        tint = spec.tint,
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { if (!isEditing) onOpenWidget(spec.route) },
                                        onLongClick = onEnterEdit
                                    )
                                    if (isEditing && slotIndex >= 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                                .clickable { onRemoveSlot(slotIndex) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "×",
                                                color = 100.n1,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加组件选择器
    pickerSlot?.let { slot ->
        val placed = config.icons.filterNotNull().toSet()
        val candidates = HomeWidgetRegistry.widgets.filter { it.id !in placed }
        AppModalBottomSheet(
            title = "添加到主页",
            onDismissRequest = { pickerSlot = null }
        ) {
            candidates.chunked(4).forEach { row ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { spec ->
                        GridIconCell(
                            iconId = spec.iconId,
                            title = spec.title,
                            tint = spec.tint,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onAddToSlot(slot, spec.id)
                                pickerSlot = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridIconCell(
    iconId: Int,
    title: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    AppCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = tint
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridEmptyCell(
    isEditing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { if (isEditing) onClick() }, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (isEditing) {
            Text(
                "＋",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
