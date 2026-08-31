package com.ahu.ahutong.ui.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as MaterialNavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.LiquidBottomTab
import com.ahu.ahutong.ui.components.LiquidBottomTabs
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.data.model.AppUiTheme
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem as MiuixNavigationItem

private data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination("home", "主页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomDestination("schedule", "课表", Icons.Filled.TableChart, Icons.Outlined.TableChart),
    BottomDestination("tools", "小工具", Icons.Filled.Build, Icons.Outlined.Build),
    BottomDestination("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun BoxScope.BottomNavBar(
    backdrop: Backdrop,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
    if (selectedRoute !in bottomDestinations.map { it.route }) return

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
                    bottomDestinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                },
                onTabSelected = { index ->
                    onDestinationSelected(bottomDestinations[index].route)
                },
                backdrop = backdrop,
                tabsCount = bottomDestinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                bottomDestinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        selected = selected,
                        onClick = {
                            onDestinationSelected(destination.route)
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
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
    } else if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val selectedIndex = bottomDestinations
            .indexOfFirst { it.route == selectedRoute }
            .coerceAtLeast(0)
        MiuixNavigationBar(
            items = bottomDestinations.mapIndexed { index, destination ->
                MiuixNavigationItem(
                    label = destination.label,
                    icon = if (index == selectedIndex) {
                        destination.selectedIcon
                    } else {
                        destination.unselectedIcon
                    }
                )
            },
            selected = selectedIndex,
            onClick = { index ->
                onDestinationSelected(bottomDestinations[index].route)
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    } else {
        MaterialNavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            bottomDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationSelected(destination.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
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
