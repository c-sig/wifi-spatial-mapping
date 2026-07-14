package com.statproj.wifispatial.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statproj.wifispatial.ui.screens.CollectionScreen
import com.statproj.wifispatial.ui.screens.ConfigScreen
import com.statproj.wifispatial.ui.screens.DataScreen
import com.statproj.wifispatial.ui.screens.MapScreen
import com.statproj.wifispatial.ui.screens.DebugScreen
import com.statproj.wifispatial.ui.screens.ReportScreen
import com.statproj.wifispatial.ui.theme.OnSurface
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.Surface
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.viewmodel.ReportViewModel

/**
 * Sealed class defining all navigation destinations.
 */
sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Config : Screen("config", "Config", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object Collect : Screen("collect", "Collect", Icons.Filled.Sensors, Icons.Outlined.Sensors)
    data object Report : Screen("report", "Report", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    data object Map : Screen("map", "Map", Icons.Filled.Map, Icons.Outlined.Map)
    data object Data : Screen("data", "Data", Icons.Filled.Storage, Icons.Outlined.Storage)
    data object Debug : Screen("debug", "Debug", Icons.Filled.Terminal, Icons.Outlined.Terminal)
}

private val screens = listOf(Screen.Config, Screen.Collect, Screen.Report, Screen.Map, Screen.Data, Screen.Debug)

/**
 * Main navigation host with bottom navigation bar.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    // Single shared view models
    val reportViewModel: ReportViewModel = viewModel()
    val configViewModel: com.statproj.wifispatial.viewmodel.ConfigViewModel = viewModel()

    Scaffold(
        containerColor = SurfaceBlack,
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                contentColor = OnSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = OnSurfaceVariant,
                            unselectedTextColor = OnSurfaceVariant,
                            indicatorColor = Primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Config.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(300)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(300)
                )
            }
        ) {
            composable(Screen.Config.route) {
                ConfigScreen(
                    onNavigateToCollect = {
                        navController.navigate(Screen.Collect.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    viewModel = configViewModel
                )
            }
            composable(Screen.Collect.route) {
                CollectionScreen(
                    configViewModel = configViewModel
                )
            }
            composable(Screen.Report.route) {
                ReportScreen(viewModel = reportViewModel)
            }
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Data.route) {
                DataScreen(viewModel = reportViewModel)
            }
            composable(Screen.Debug.route) {
                DebugScreen()
            }
        }
    }
}
