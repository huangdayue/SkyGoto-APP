/*
 * 文件名：Color.kt
 * 描述：颜色主题定义 - 定义应用使用的所有颜色常量
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义应用的颜色主题常量
 * - 深色主题配色方案（适合天文观测）
 * - 为 Theme.kt 提供颜色值
 *
 * 配色说明：
 * - 深蓝黑色背景：减少眼睛疲劳，适合夜间使用
 * - 青色(Accent)强调：科技感强，视觉效果突出
 * - 绿色(Accent2)辅助：用于成功状态指示
 * - 暖黄色(Warning)：用于警告提示
 * - 红色(Error)：用于错误状态
 *
 * 使用方式：
 * - 直接引用颜色值（如 Color.Primary）
 * - 或通过 Theme.kt 中的 MaterialTheme.colorScheme 使用
 */

package com.skygoto.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主色调 - 深蓝黑色
 *
 * 用于应用的主要背景色。
 * 选择深色调的原因：
 * 1. 夜间天文观测时减少屏幕对眼睛的刺激
 * 2. 与星空背景融合，减少视觉干扰
 * 3. 节省 OLED 屏幕电量
 */
val Primary = Color(0xFF0A1628)

/**
 * 次要色 - 深灰蓝色
 *
 * 用于卡片、对话框等次要背景元素。
 * 比 Primary 稍亮，用于层次区分。
 */
val Secondary = Color(0xFF1E2A3A)

/**
 * 强调色 - 青色
 *
 * 用于主要交互元素的高亮显示。
 * 如：按钮、链接、选中状态等。
 * 特点：科技感强，在深色背景上易于识别。
 */
val Accent = Color(0xFF00D4FF)

/**
 * 辅助强调色 - 绿色
 *
 * 用于成功状态、正向指标等。
 * 如：追踪中、连接成功等状态指示。
 */
val Accent2 = Color(0xFF00FF88)

/**
 * 警告色 - 橙黄色
 *
 * 用于警告提示、不良状态等。
 * 如：低电量、即将超时、需要注意等。
 */
val Warning = Color(0xFFFFB800)

/**
 * 错误色 - 红色
 *
 * 用于错误状态、失败操作等。
 * 如：连接失败、GOTO 错误、硬件故障等。
 */
val Error = Color(0xFFFF4444)

/**
 * 主要文字颜色 - 白色
 *
 * 用于标题、重要文字等。
 * 在深色背景上提供最大可读性。
 */
val TextPrimary = Color(0xFFFFFFFF)

/**
 * 次要文字颜色 - 灰白色
 *
 * 用于次要文字、描述信息等。
 * 降低对比度减少视觉疲劳。
 */
val TextSecondary = Color(0xFFB0B8C4)