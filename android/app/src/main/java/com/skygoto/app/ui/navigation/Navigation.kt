package com.skygoto.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
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
import com.skygoto.app.ui.screens.catalog.CatalogScreen
import com.skygoto.app.ui.screens.connect.ConnectScreen
import com.skygoto.app.ui.screens.control.ControlScreen
import com.skygoto.app.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Connect : Screen("connect", "连接", Icons.Default.Menu)
    object Control : Screen("control", "控制", Icons.Default.TouchApp)
    object Catalog : Screen("catalog", "目录", Icons.Default.Book)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    Screen.Connect,
    Screen.Control,
    Screen.Catalog,
    Screen.Settings
)

@Composable
fun SkyGotoNavHost() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Connect.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Connect.route) { ConnectScreen() }
            composable(Screen.Control.route) { ControlScreen() }
            composable(Screen.Catalog.route) { CatalogScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
