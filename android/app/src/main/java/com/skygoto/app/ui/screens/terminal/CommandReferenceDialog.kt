package com.skygoto.app.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skygoto.app.ui.theme.*

/**
 * 全屏指令参考对话框
 *
 * 按分类组织所有 LX200 命令，点击后自动填入终端输入框。
 */
@Composable
fun CommandReferenceDialog(
    onDismiss: () -> Unit,
    onCommandSelected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Primary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                CommandReferenceTopBar(onBack = onDismiss)

                // 内容区
                CommandReferenceContent(onCommandSelected = { cmd ->
                    onCommandSelected(cmd)
                    onDismiss()
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandReferenceTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("LX200 指令参考", color = TextPrimary) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Secondary)
    )
}

@Composable
private fun CommandReferenceContent(
    onCommandSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        commandReferenceByCategory.forEach { (category, commands) ->
            item(key = "header_$category") {
                CategoryHeader(category = category)
            }
            items(commands, key = { "cmd_${it.command}" }) { cmd ->
                CommandItem(cmd = cmd, onClick = { onCommandSelected(cmd.command) })
            }
            item(key = "spacer_$category") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String) {
    Text(
        text = category,
        color = Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(Secondary, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun CommandItem(
    cmd: CommandReference,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 命令本体（等宽字体高亮）
        Text(
            text = cmd.command,
            color = Accent2,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(140.dp)
        )

        // 参数占位符标记
        if (cmd.hasParams) {
            Text(
                text = "📝",
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // 描述
        Text(
            text = cmd.description,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }

    Divider(color = Secondary, thickness = 0.5.dp)
}
