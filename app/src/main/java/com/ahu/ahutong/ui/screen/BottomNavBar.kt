package com.ahu.ahutong.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.LiquidBottomTab
import com.ahu.ahutong.ui.components.LiquidBottomTabs
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDockState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongSubTab
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun BoxScope.BottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    if (isRadiantUi) {
        RadiantBottomNavBar(navController, backdrop)
    } else {
        ClassicBottomNavBar(navController, backdrop)
    }
}

private fun NavController.navigatePreservingHome(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo("home") { inclusive = false }
        launchSingleTop = true
    }
}

// ==================== 曜光版：学习通日历提级为第三 tab（日程/课程轮换） ====================

private data class RadiantDestination(
    val route: String,
    val label: String,
    val icon: Painter
)

@Composable
private fun BoxScope.RadiantBottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    // 首次引导：学习通 tab 兼具「日程/课程」轮换切换，新用户不易发现，
    // 首次进入学习通页时在其上方弹一次气泡提示，点击气泡或完成一次切换后永久消失。
    val context = LocalContext.current
    val guidePrefs = remember {
        context.getSharedPreferences("app_guide", Context.MODE_PRIVATE)
    }
    var tabGuideShown by remember {
        mutableStateOf(guidePrefs.getBoolean("xxt_tab_guide_shown", false))
    }
    var tabsBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissTabGuide() {
        if (!tabGuideShown) {
            tabGuideShown = true
            guidePrefs.edit().putBoolean("xxt_tab_guide_shown", true).apply()
        }
    }

    val onXuexiaotongSub = XuexiaotongDockState.tab == XuexiaotongSubTab.SCHEDULE
    val destinations = listOf(
        RadiantDestination("home", "主页", painterResource(R.drawable.ic_nav_home)),
        RadiantDestination("schedule", "课表", painterResource(R.drawable.ic_nav_schedule)),
        RadiantDestination(
            "xuexiaotong",
            if (onXuexiaotongSub) "日程" else "课程",
            painterResource(if (onXuexiaotongSub) R.drawable.ic_nav_plan else R.drawable.ic_nav_degree_hat)
        ),
        RadiantDestination("settings", "设置", painterResource(R.drawable.ic_nav_settings))
    )

    val currentRoute by navController.currentBackStackEntryAsState()
    val selectedRoute = currentRoute?.destination?.route
    if (selectedRoute !in destinations.map { it.route }) return

    fun onTabTapped(route: String) {
        if (route == navController.currentBackStackEntry?.destination?.route) {
            if (route == "xuexiaotong") {
                XuexiaotongDockState.toggle()
                dismissTabGuide()
            }
            return
        }
        navController.navigatePreservingHome(route)
    }

    fun navigateToTab(route: String) {
        navController.navigatePreservingHome(route)
    }

    if (LocalIsLiquidGlassEnabled.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            LiquidBottomTabs(
                selectedTabIndex = {
                    destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                },
                onTabSelected = { index ->
                    navigateToTab(destinations[index].route)
                },
                onCurrentTabTapped = {
                    selectedRoute?.let { onTabTapped(it) }
                },
                backdrop = backdrop,
                tabsCount = destinations.size,
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .onGloballyPositioned { tabsBounds = it.boundsInWindow() }
            ) {
                destinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        onClick = { onTabTapped(destination.route) }
                    ) {
                        Icon(
                            painter = destination.icon,
                            contentDescription = destination.label
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    } else {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { tabsBounds = it.boundsInWindow() },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabTapped(destination.route) },
                    icon = {
                        Icon(
                            painter = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }

    if (!tabGuideShown && selectedRoute == "xuexiaotong") {
        tabsBounds?.let { bounds ->
            var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
            var guideVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(350)
                guideVisible = true
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { overlayOrigin = it.boundsInWindow().topLeft }
            ) {
                AnimatedVisibility(
                    visible = guideVisible,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 3 }
                ) {
                    AnchoredGuideBubble(
                        anchorCenterX = { bounds.left + bounds.width * 0.625f - overlayOrigin.x },
                        anchorTopY = { bounds.top - overlayOrigin.y },
                        backdrop = backdrop,
                        text = "再次点击可切换日程 / 课程页",
                        onDismiss = { dismissTabGuide() }
                    )
                }
            }
        }
    }
}

// 将气泡内容锚定到指定坐标：水平居中对齐锚点（贴屏幕边缘时收边）、底部位于锚点上方
@Composable
private fun AnchoredGuideBubble(
    anchorCenterX: () -> Float,
    anchorTopY: () -> Float,
    backdrop: Backdrop,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            GuideBubbleCard(text = text, backdrop = backdrop, onDismiss = onDismiss)
        },
        modifier = modifier
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val margin = 10.dp.roundToPx()
        val parentWidth = constraints.maxWidth
        val anchorX = anchorCenterX().roundToInt()
        val x = (anchorX - placeable.width / 2)
            .coerceIn(margin, (parentWidth - placeable.width - margin).coerceAtLeast(margin))
        val y = anchorTopY().roundToInt() - placeable.height - 10.dp.roundToPx()
        layout(parentWidth, constraints.maxHeight) {
            placeable.placeRelative(x, y)
        }
    }
}

// 引导气泡卡：曜光分支无条件走玻璃材质（vibrancy+blur+lens 实时采样背后页面内容），
// 不随液态玻璃开关切换；造型与"猜你想用"气泡完全同款（ContinuousCapsule 胶囊），
// 带 primary 色灯泡图标，整体上下呼吸浮动引导视线指向下方按钮。
@Composable
private fun GuideBubbleCard(
    text: String,
    backdrop: Backdrop,
    onDismiss: () -> Unit
) {
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
    val infiniteTransition = rememberInfiniteTransition(label = "guideBubble")
    val bubbleBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubbleBob"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .graphicsLayer { translationY = bubbleBob * 3.dp.toPx() }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow() },
                onDrawSurface = { drawRect(glassContainerColor) }
            )
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            Icons.Rounded.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== 经典版：小工具第三 tab（原样式） ====================

private data class ClassicDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val classicDestinations = listOf(
    ClassicDestination("home", "主页", Icons.Outlined.Home),
    ClassicDestination("schedule", "课表", Icons.Outlined.TableChart),
    ClassicDestination("tools", "小工具", Icons.Outlined.Build),
    ClassicDestination("settings", "设置", Icons.Outlined.Settings)
)

@Composable
private fun BoxScope.ClassicBottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val selectedRoute = currentRoute?.destination?.route
    if (selectedRoute !in classicDestinations.map { it.route }) return

    if (LocalIsLiquidGlassEnabled.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            LiquidBottomTabs(
                selectedTabIndex = {
                    classicDestinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                },
                onTabSelected = { index ->
                    navController.navigatePreservingHome(classicDestinations[index].route)
                },
                backdrop = backdrop,
                tabsCount = classicDestinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                classicDestinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        onClick = {
                            navController.navigatePreservingHome(destination.route)
                        }
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    } else {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            classicDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigatePreservingHome(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}