package com.ahu.ahutong.ui.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.ui.components.LiquidBottomTab
import com.ahu.ahutong.ui.components.LiquidBottomTabs
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.kyant.backdrop.Backdrop

private data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination("home", "主页", Icons.Outlined.Home, Icons.Outlined.Home),
    BottomDestination("schedule", "课表", Icons.Outlined.TableChart, Icons.Outlined.TableChart),
    BottomDestination("tools", "小工具", Icons.Outlined.Build, Icons.Outlined.Build),
    BottomDestination("settings", "设置", Icons.Outlined.Settings, Icons.Outlined.Settings)
)

@Composable
fun BoxScope.BottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val selectedRoute = currentRoute?.destination?.route
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
                    navController.navigatePreservingHome(bottomDestinations[index].route)
                },
                backdrop = backdrop,
                tabsCount = bottomDestinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                bottomDestinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        onClick = {
                            navController.navigatePreservingHome(destination.route)
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
    } else {
        NavigationBar(
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
                    onClick = { navController.navigatePreservingHome(destination.route) },
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

private fun NavController.navigatePreservingHome(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo("home") { inclusive = false }
        launchSingleTop = true
    }
}
