package com.skygoto.app.ui.screens.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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

/** 统计总数用于顶部栏显示 */
internal val totalCommands: Int get() = commandReferenceData.size
internal val totalCategories: Int get() = commandReferenceByCategory.size

/**
 * 全屏指令参考对话框
 *
 * 按分类组织所有 LX200 命令，点击分类标题可展开/收起。
 * 默认全部展开，点击后自动填入终端输入框。
 */
@Composable
fun CommandReferenceDialog(
    onDismiss: () -> Unit,
    onCommandSelected: (String) -> Unit
) {
    // 每个分类的展开状态，默认全部收起
    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            commandReferenceByCategory.keys.forEach { key -> put(key, false) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Primary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CommandReferenceTopBar(onBack = onDismiss)
                CommandReferenceContent(
                    expandedStates = expandedStates,
                    onCommandSelected = { cmd ->
                        onCommandSelected(cmd)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandReferenceTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "LX200 指令参考  ($totalCategories 类 ${totalCommands}条)",
                color = TextPrimary,
                fontSize = 16.sp
            )
        },
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
    expandedStates: MutableMap<String, Boolean>,
    onCommandSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        commandReferenceByCategory.forEach { (category, commands) ->
            val expanded = expandedStates[category] ?: true

            // 分类标题行（可点击展开/收起）
            item(key = "header_$category") {
                CategoryHeader(
                    category = category,
                    count = commands.size,
                    expanded = expanded,
                    onToggle = { expandedStates[category] = !expanded }
                )
            }

            // 命令条目（带动画）
            item(key = "content_$category") {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        commands.forEach { cmd ->
                            CommandItem(
                                cmd = cmd,
                                onClick = { onCommandSelected(cmd.command) }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(Secondary, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/收起图标
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        // 分类名
        Text(
            text = "$category  ($count)",
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun CommandItem(
    cmd: CommandReference,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 命令本体（等宽字体高亮）
            Text(
                text = cmd.command,
                color = Accent2,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(150.dp)
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

        // 附加提示（返回值说明等）
        if (cmd.note.isNotEmpty()) {
            Text(
                text = "↳ ${cmd.note}",
                color = TextTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
    }

    Divider(color = Secondary, thickness = 0.5.dp)
}
