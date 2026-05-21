/*
 * 文件名：Navigation.kt
 * 描述：导航组件 - 定义应用的整体页面结构和底部导航栏
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义应用的整体页面架构
 * - 实现底部导航栏（Bottom Navigation）
 * - 配置 Jetpack Navigation 路由
 * - 整合各个功能模块的 Screen
 *
 * 页面结构：
 * - ConnectScreen: 连接页面（配对/连接赤道仪）
 * - ControlScreen: 控制页面（手控器/追踪/GOTO）
 * - CatalogScreen: 目录页面（梅西耶/NGC天体浏览）
 * - SettingsScreen: 设置页面（应用偏好设置）
 *
 * 使用方式：
 * - 在 MainActivity 中调用 SkyGotoNavHost() 渲染整个界面
 * - 底部导航栏自动处理页面切换
 * - Navigation Compose 管理各页面的导航栈
 */

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

/**
 * 导航页面数据类
 *
 * 定义底部导航栏中的每个页面项。
 *
 * @param route 路由路径，用于 Navigation Compose 识别页面
 * @param title 显示标题
 * @param icon 显示图标
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    /** 连接页面 - 配对和连接赤道仪 */
    object Connect : Screen("connect", "连接", Icons.Default.Menu)

    /** 控制页面 - 赤道仪控制（追踪、GOTO、手动移动） */
    object Control : Screen("control", "控制", Icons.Default.TouchApp)

    /** 目录页面 - 天体目录浏览 */
    object Catalog : Screen("catalog", "目录", Icons.Default.Book)

    /** 设置页面 - 应用偏好设置 */
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

/**
 * 底部导航项列表
 *
 * 定义底部导航栏中显示的所有页面。
 * 顺序决定了在导航栏中从左到右的显示顺序。
 */
private val bottomNavItems = listOf(
    Screen.Connect,
    Screen.Control,
    Screen.Catalog,
    Screen.Settings
)

/**
 * SkyGoto 应用导航主机
 *
 * 组合底部导航栏和 Navigation Compose，
 * 提供完整的应用页面结构和导航功能。
 *
 * 页面切换特性：
 * - 点击底部导航项切换页面
 * - 使用 popUpTo 防止导航栈过于深入
 * - 使用 launchSingleTop 避免重复创建页面
 * - 使用 restoreState 恢复之前的页面状态
 *
 * 布局结构：
 * - Scaffold 提供整体布局框架
 * - bottomBar 底部导航栏
 * - NavHost 内容区域
 *
 * 使用方式：
 * ```kotlin
 * // 在 MainActivity 或 App composable 中调用
 * SkyGotoNavHost()
 * ```
 */
@Composable
fun SkyGotoNavHost() {
    // 创建导航控制器
    val navController = rememberNavController()

    // Scaffold 提供整体布局，包含底部导航栏
    Scaffold(
        bottomBar = {
            // 底部导航栏
            NavigationBar {
                // 获取当前导航目的地
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // 遍历底部导航项，创建导航项
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        // 图标
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        // 标签文字
                        label = { Text(screen.title) },
                        // 是否选中（当前页面）
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        // 点击事件：导航到目标页面
                        onClick = {
                            navController.navigate(screen.route) {
                                // 弹出到起始页面，避免返回时回到中间页面
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true  // 保存页面状态
                                }
                                // 如果目标页面已存在则不重复创建
                                launchSingleTop = true
                                // 恢复之前保存的页面状态
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Navigation Host，内容区域
        // 使用 innerPadding 确保内容不被底部导航栏遮挡
        NavHost(
            navController = navController,
            startDestination = Screen.Connect.route,  // 起始页面
            modifier = Modifier.padding(innerPadding)
        ) {
            // 连接页面
            composable(Screen.Connect.route) { ConnectScreen() }

            // 控制页面
            composable(Screen.Control.route) { ControlScreen() }

            // 目录页面
            composable(Screen.Catalog.route) { CatalogScreen() }

            // 设置页面
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}