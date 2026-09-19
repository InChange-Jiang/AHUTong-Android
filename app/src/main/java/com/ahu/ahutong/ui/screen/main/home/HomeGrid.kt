package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GridGap = 10.dp
private val CellHeight = 61.dp // 原 92dp 的 2/3
private val QrExpandedHeight = 460.dp

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
 * 主页 3×4 组件网格（全主题唯一布局族）。
 * 校园卡锚定左上占 4 格：编辑态拖右下角弧形手柄，阻尼轻微形变、过阈值弹跳翻转形态；
 * 7 功能槽压实对齐（空位只在末尾）+「更多」固定贪心排尾；编辑态图标格可拖拽换位、×删、空位 ＋ 加。
 * 二维码：点校园卡原位弹开成大卡（[qrExpanded] 联动卡片尺寸动画）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGrid(
    config: HomeGridConfig,
    isEditing: Boolean,
    qrExpanded: Boolean,
    onEnterEdit: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onAddToSlot: (Int, String) -> Unit,
    onSwapSlots: (Int, Int) -> Unit,
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
    val qrHPx = with(density) { QrExpandedHeight.toPx() }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gridWidthPx = with(density) { maxWidth.toPx() }
        val cellWPx = (gridWidthPx - gapPx * (HomeGridConfig.GRID_COLS - 1)) / HomeGridConfig.GRID_COLS
        val w2x2 = cellWPx * 2 + gapPx
        val h2x2 = cellHPx * 2 + gapPx
        val w1x4 = gridWidthPx
        val h1x4 = cellHPx
        val spanTarget = when (config.campusSpan) {
            CampusSpan.SQUARE_2X2 -> w2x2 to h2x2
            CampusSpan.ROW_1X4 -> w1x4 to h1x4
        }

        // 主卡片尺寸：QR 弹开 / 形态切换 / 阻尼拖拽 共用的动画通道
        val campusW = remember { Animatable(w2x2) }
        val campusH = remember { Animatable(h2x2) }
        LaunchedEffect(config.campusSpan, qrExpanded, cellWPx) {
            val (tw, th) = if (qrExpanded) gridWidthPx to qrHPx else spanTarget
            launch { campusW.animateTo(tw, CampusJellySpring) }
            launch { campusH.animateTo(th, CampusJellySpring) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (cellHPx * 3 + gapPx * 2).toDp() })
        ) {
            // ---- 主卡片 ----
            Box(
                modifier = Modifier
                    .width(with(density) { campusW.value.toDp() })
                    .height(with(density) { campusH.value.toDp() })
                    .zIndex(if (qrExpanded) 6f else 2f)
            ) {
                campusContent(Modifier.fillMaxSize())

                if (isEditing && !qrExpanded) {
                    // 弧形手柄：贴合右下角圆角的小粗弧
                    val arcColor = 70.a1 withNight 60.a1
                    var dragAccum by remember { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .pointerInput(cellWPx, config.campusSpan) {
                                val threshold = cellWPx * 0.6f
                                detectDragGestures(
                                    onDragStart = { dragAccum = 0f },
                                    onDragEnd = {
                                        // 未过阈：弹回原形态
                                        scope.launch {
                                            launch { campusW.animateTo(spanTarget.first, CampusJellySpring) }
                                            launch { campusH.animateTo(spanTarget.second, CampusJellySpring) }
                                        }
                                        dragAccum = 0f
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            launch { campusW.animateTo(spanTarget.first, CampusJellySpring) }
                                            launch { campusH.animateTo(spanTarget.second, CampusJellySpring) }
                                        }
                                        dragAccum = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    // 方向投影：2×2 向右下拉伸趋向 1×4；1×4 向左上收趋向 2×2
                                    val signed = when (config.campusSpan) {
                                        CampusSpan.SQUARE_2X2 -> dragAmount.x - dragAmount.y * 0.5f
                                        CampusSpan.ROW_1X4 -> -dragAmount.x + dragAmount.y * 0.5f
                                    }
                                    dragAccum += signed
                                    // 阻尼：只轻微形变（橡皮筋）
                                    val damped = (dragAccum * 0.18f)
                                        .coerceIn(-cellWPx * 0.2f, cellWPx * 0.2f)
                                    scope.launch {
                                        when (config.campusSpan) {
                                            CampusSpan.SQUARE_2X2 -> {
                                                campusW.snapTo(spanTarget.first + damped)
                                                campusH.snapTo(spanTarget.second - damped * 0.3f)
                                            }
                                            CampusSpan.ROW_1X4 -> {
                                                campusW.snapTo(spanTarget.first + damped)
                                                campusH.snapTo(spanTarget.second + damped * 0.3f)
                                            }
                                        }
                                    }
                                    // 过阈：立即弹跳翻转 + 触发重排
                                    if (dragAccum > threshold) {
                                        dragAccum = 0f
                                        onCampusSpanChange(
                                            when (config.campusSpan) {
                                                CampusSpan.SQUARE_2X2 -> CampusSpan.ROW_1X4
                                                CampusSpan.ROW_1X4 -> CampusSpan.SQUARE_2X2
                                            }
                                        )
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 4.5.dp.toPx()
                            val radius = 15.dp.toPx()
                            val inset = 7.dp.toPx()
                            drawArc(
                                color = arcColor,
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(
                                    size.width - inset - radius * 2,
                                    size.height - inset - radius * 2
                                ),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // ---- 图标格 / 空位 / 更多 ----
            var draggingSlot by remember { mutableStateOf<String?>(null) }
            var dragDelta by remember { mutableStateOf(Offset.Zero) }

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
                    val isDragging = draggingSlot == slotKey
                    val cellModifier = Modifier
                        .zIndex(if (isDragging) 5f else 1f)
                        .offset {
                            IntOffset(
                                (animator.x.value + if (isDragging) dragDelta.x else 0f).roundToInt(),
                                (animator.y.value + if (isDragging) dragDelta.y else 0f).roundToInt()
                            )
                        }
                        .width(with(density) { cellWPx.toDp() })
                        .height(CellHeight)

                    when {
                        slotKey == HomeGridConfig.MORE_SLOT_ID -> GridIconCell(
                            iconId = R.drawable.ic_more_all,
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
                                Box(
                                    modifier = cellModifier.pointerInput(isEditing, slotKey) {
                                        if (!isEditing) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingSlot = slotKey
                                                dragDelta = Offset.Zero
                                            },
                                            onDragEnd = {
                                                // 找最近格（仅限功能槽），交换顺序后压实
                                                val cx = animator.x.value + dragDelta.x + cellWPx / 2
                                                val cy = animator.y.value + dragDelta.y + cellHPx / 2
                                                val nearest = placements
                                                    .filter { it.first != CampusCardSlotId }
                                                    .mapNotNull { (k, pl) ->
                                                        val idx = when {
                                                            k.startsWith("#empty_") ->
                                                                k.removePrefix("#empty_").toInt()
                                                            k == HomeGridConfig.MORE_SLOT_ID -> null
                                                            else -> config.icons.indexOf(k)
                                                                .takeIf { it >= 0 }
                                                        }
                                                        idx?.let { it to pl }
                                                    }
                                                    .minByOrNull { (_, pl) ->
                                                        val gx = pl.col * (cellWPx + gapPx) + cellWPx / 2
                                                        val gy = pl.row * (cellHPx + gapPx) + cellHPx / 2
                                                        (gx - cx) * (gx - cx) + (gy - cy) * (gy - cy)
                                                    }
                                                if (nearest != null && slotIndex >= 0 &&
                                                    nearest.first != slotIndex
                                                ) {
                                                    onSwapSlots(slotIndex, nearest.first)
                                                }
                                                draggingSlot = null
                                                dragDelta = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingSlot = null
                                                dragDelta = Offset.Zero
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragDelta += dragAmount
                                        }
                                    }
                                ) {
                                    GridIconCell(
                                        iconId = spec.iconId,
                                        title = spec.title,
                                        tint = spec.tint,
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { if (!isEditing) onOpenWidget(spec.route) },
                                        onLongClick = onEnterEdit
                                    )
                                    if (isEditing) {
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

    // 添加组件选择器（可滚动完整列表）
    pickerSlot?.let { slot ->
        val placed = config.icons.filterNotNull().toSet()
        val candidates = HomeWidgetRegistry.widgets.filter { it.id !in placed }
        AppModalBottomSheet(
            title = "添加到主页",
            onDismissRequest = { pickerSlot = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        repeat(4 - row.size) { Box(modifier = Modifier.weight(1f)) }
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
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    AppCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = title,
                modifier = Modifier.size(26.dp),
                tint = tint
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
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
