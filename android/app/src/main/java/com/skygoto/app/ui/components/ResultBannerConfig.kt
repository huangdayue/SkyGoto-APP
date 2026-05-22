/*
 * 文件名：ResultBannerConfig.kt
 * 描述：结果提示弹窗配置 - 定义统一的结果级别和弹窗配置
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义结果级别枚举（成功/错误/警告/信息）
 * - 定义弹窗配置数据类
 * - 提供 Color.kt 中颜色的语义化映射
 *
 * 使用方式：
 * - 在 ViewModel 中创建 ResultBannerConfig
 * - 传入 ResultBanner Composable 组件
 * - 按结果级别自动匹配颜色
 */

package com.skygoto.app.ui.components

import androidx.compose.ui.graphics.Color
import com.skygoto.app.ui.theme.*

/**
 * 结果级别枚举
 *
 * 用于指示弹窗的类型，决定弹窗的颜色：
 * - SUCCESS: 成功操作（如 GOTO 执行成功、回零位完成）
 * - ERROR: 错误/失败操作（如连接失败、GOTO 失败）
 * - WARNING: 警告提示（如连接断开、限位警告）
 * - INFO: 一般信息提示（默认）
 */
enum class ResultLevel {
    /** 成功 - 绿色 */
    SUCCESS,

    /** 错误/失败 - 红色 */
    ERROR,

    /** 警告 - 橙色 */
    WARNING,

    /** 一般信息 - 青色（默认） */
    INFO;

    /**
     * 获取对应级别的背景颜色
     * 使用 0.3 透明度，适配深色主题
     */
    fun toBackgroundColor(): Color = when (this) {
        SUCCESS -> Accent2.copy(alpha = 0.3f)
        ERROR -> Error.copy(alpha = 0.3f)
        WARNING -> Warning.copy(alpha = 0.3f)
        INFO -> Accent.copy(alpha = 0.3f)
    }
}

/**
 * 结果弹窗配置
 *
 * @param message 显示的消息内容
 * @param level 结果级别（默认 INFO）
 * @param duration 显示时长（毫秒，默认 3000，0 表示不自动消失）
 */
data class ResultBannerConfig(
    val message: String,
    val level: ResultLevel = ResultLevel.INFO,
    val duration: Long = 3000L
) {
    companion object {
        /** 快速创建成功提示 */
        fun success(message: String, duration: Long = 3000L) =
            ResultBannerConfig(message, ResultLevel.SUCCESS, duration)

        /** 快速创建错误提示 */
        fun error(message: String, duration: Long = 3000L) =
            ResultBannerConfig(message, ResultLevel.ERROR, duration)

        /** 快速创建警告提示 */
        fun warning(message: String, duration: Long = 3000L) =
            ResultBannerConfig(message, ResultLevel.WARNING, duration)

        /** 快速创建信息提示 */
        fun info(message: String, duration: Long = 3000L) =
            ResultBannerConfig(message, ResultLevel.INFO, duration)

        /** 空配置（不显示弹窗） */
        val NONE = ResultBannerConfig("", ResultLevel.INFO, 0L)
    }
}