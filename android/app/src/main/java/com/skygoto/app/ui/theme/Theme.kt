/*
 * 文件名：Theme.kt
 * 描述：应用主题定义 - 配置 Compose Material 3 深色主题
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 配置 Jetpack Compose Material 3 深色主题
 * - 设置状态栏颜色
 * - 定义整个应用的视觉风格
 *
 * 主题特点：
 * - 深色模式：适合夜间天文观测
 * - 自定义颜色方案：青绿强调色
 * - 沉浸式状态栏：与主题融为一体
 *
 * 使用方式：
 * - 使用 SkyGotoTheme 包裹整个应用
 * - 在 Composable 中通过 MaterialTheme.colorScheme 访问颜色
 */

package com.skygoto.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 深色主题配色方案
 *
 * 基于深蓝黑色背景，青色和绿色作为强调色。
 * 使用 darkColorScheme 定义 Material 3 所需的颜色角色。
 */
private val DarkColorScheme = darkColorScheme(
    // 主色调 - 用于主要 UI 元素
    primary = Accent,          // 青色 - 强调色
    secondary = Accent2,       // 绿色 - 辅助强调

    // 背景和表面 - 深色背景减少眼睛疲劳
    background = Primary,       // 主背景色
    surface = Secondary,       // 表面/卡片背景

    // 文字颜色 - 确保可读性
    onPrimary = TextPrimary,   // 主色上的文字（白色）
    onSecondary = TextPrimary, // 次色上的文字
    onBackground = TextPrimary, // 背景上的文字
    onSurface = TextPrimary,   // 表面上的文字

    // 错误色 - 用于错误状态
    error = Error
)

/**
 * SkyGoto 应用主题
 *
 * 应用级别的 Compose Theme 组件。
 * 包裹整个应用的 UI 层次结构。
 *
 * 主题特性：
 * 1. 深色配色 - 适合夜间天文观测使用
 * 2. 沉浸式状态栏 - 状态栏颜色与主题融合
 * 3. 暗色状态栏图标 - 在深色背景上可见
 *
 * @param content 子 Composable 内容
 */
@Composable
fun SkyGotoTheme(
    content: @Composable () -> Unit
) {
    // 使用深色配色方案
    val colorScheme = DarkColorScheme

    // 获取当前视图
    val view = LocalView.current

    // 非编辑模式下设置状态栏样式
    if (!view.isInEditMode) {
        SideEffect {
            // 获取 Activity 窗口
            val window = (view.context as Activity).window

            // 将状态栏颜色设置为主色调
            window.statusBarColor = Primary.toArgb()

            // 配置状态栏图标样式
            // isAppearanceLightStatusBars = false 表示使用浅色图标
            // 在深色背景上更易见
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // 应用 Material Theme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}