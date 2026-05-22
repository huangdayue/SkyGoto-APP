/*
 * 文件名：ResultBanner.kt
 * 描述：结果提示弹窗组件 - 统一的结果展示组件
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 通用的结果提示弹窗组件
 * - 根据 ResultBannerConfig 的级别自动匹配颜色
 * - 支持自动消失和手动关闭
 *
 * 使用方式：
 * - 传入 ResultBannerConfig 配置
 * - 可为 null（不显示）
 * - 自动 3 秒后消失（可配置）
 */

package com.skygoto.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skygoto.app.ui.theme.TextPrimary
import com.skygoto.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * 结果提示弹窗组件
 *
 * @param config 弹窗配置（为 null 时不显示）
 * @param onDismiss 关闭回调
 */
@Composable
fun ResultBanner(
    config: ResultBannerConfig?,
    onDismiss: () -> Unit
) {
    // 如果 config 为空或消息为空，不显示
    if (config == null || config.message.isEmpty()) {
        return
    }

    // 自动消失
    if (config.duration > 0) {
        LaunchedEffect(config.message) {
            delay(config.duration)
            onDismiss()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = config.level.toBackgroundColor()
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = config.message,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = TextSecondary
                )
            }
        }
    }
}

/**
 * 结果提示弹窗组件（简化版，直接传入消息和级别）
 *
 * @param message 显示的消息
 * @param level 结果级别
 * @param onDismiss 关闭回调
 */
@Composable
fun ResultBanner(
    message: String,
    level: ResultLevel,
    onDismiss: () -> Unit
) {
    ResultBanner(
        config = ResultBannerConfig(message, level),
        onDismiss = onDismiss
    )
}