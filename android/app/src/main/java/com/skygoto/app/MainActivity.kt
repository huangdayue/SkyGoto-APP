/*
 * 文件名：MainActivity.kt
 * 描述：应用主入口 Activity，使用 Hilt 进行依赖注入
 *       负责设置全屏边缘到边缘显示，并加载 Compose 导航主题
 * 作者：SkyGoto Team
 * 日期：2024
 */
package com.skygoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.skygoto.app.ui.navigation.SkyGotoNavHost
import com.skygoto.app.ui.theme.SkyGotoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主活动类 - 应用启动的第一个 Activity
 * 
 * 使用 Hilt 的 @AndroidEntryPoint 注解启用依赖注入，
 * 设置边缘到边缘布局以支持现代 Android 系统的沉浸式体验，
 * 并通过 setContent 设置 Jetpack Compose 主题和导航结构。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Activity 创建时的回调方法
     * @param savedInstanceState 如果 Activity 是首次创建，则为 null；
     *                           如果是重新创建，则包含之前保存的状态数据
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边缘到边缘显示，让内容延伸到系统栏下方
        enableEdgeToEdge()
        // 设置 Compose 内容
        setContent {
            // 应用深色主题
            SkyGotoTheme {
                // 填充整个屏幕并显示导航主机
                Surface(modifier = Modifier.fillMaxSize()) {
                    SkyGotoNavHost()
                }
            }
        }
    }
}
