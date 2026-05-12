package com.skygoto.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skygoto.app.ui.theme.*

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 赤道仪设置
        SettingsSection(title = "赤道仪") {
            SettingsItem(
                icon = Icons.Default.SwapHoriz,
                title = "导星速率",
                subtitle = "1x (恒星时)"
            )
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "GOTO 速率",
                subtitle = "最高 48x"
            )
            SettingsItem(
                icon = Icons.Default.Tune,
                title = "限位设置",
                subtitle = "东 180° / 西 180°"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 界面设置
        SettingsSection(title = "界面") {
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "主题",
                subtitle = "深色模式"
            )
            SettingsItem(
                icon = Icons.Default.Language,
                title = "语言",
                subtitle = "简体中文"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 关于
        SettingsSection(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "1.0.0"
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "协议兼容",
                subtitle = "OnStepX / LX200"
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Secondary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}