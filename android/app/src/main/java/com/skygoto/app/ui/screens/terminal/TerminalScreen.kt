@file:OptIn(ExperimentalMaterial3Api::class)

package com.skygoto.app.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skygoto.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val hexModeEnabled by viewModel.hexModeEnabled.collectAsState()
    val showNotConnected by viewModel.showNotConnectedHint.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showCommandRef by remember { mutableStateOf(false) }

    // 生命周期：控制轮询
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showNotConnected) {
        if (showNotConnected) {
            snackbarHostState.showSnackbar("⚠ 请先连接赤道仪")
            viewModel.dismissNotConnectedHint()
        }
    }

    // 清屏确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空聊天记录") },
            text = { Text("确定清空所有聊天记录？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMessages()
                    showClearDialog = false
                }) { Text("确定", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Secondary,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // 指令参考全屏弹窗
    if (showCommandRef) {
        CommandReferenceDialog(
            onDismiss = { showCommandRef = false },
            onCommandSelected = { cmd -> viewModel.setCommandFromReference(cmd) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TerminalTopBar(
                isConnected = isConnected,
                hexModeEnabled = hexModeEnabled,
                onClearClick = { showClearDialog = true },
                onHexToggle = viewModel::toggleHexMode
            )
        },
        bottomBar = {
            TerminalInputBar(
                inputText = inputText,
                onInputChange = viewModel::updateInputText,
                onSend = viewModel::sendCommand,
                onCommandRefClick = { showCommandRef = true }
            )
        },
        containerColor = Primary
    ) { padding ->
        ChatMessageList(
            messages = uiState,
            modifier = Modifier.padding(padding)
        )
    }
}

// ============================================================
// 顶部状态栏
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalTopBar(
    isConnected: Boolean,
    hexModeEnabled: Boolean,
    onClearClick: () -> Unit,
    onHexToggle: () -> Unit
) {
    TopAppBar(
        title = { Text("终端", color = TextPrimary) },
        actions = {
            // 连接状态指示
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Accent2 else Error)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Hex 切换按钮
            IconButton(onClick = onHexToggle) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Hex 模式",
                    tint = if (hexModeEnabled) Warning else TextSecondary
                )
            }

            // 清屏按钮
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "清屏",
                    tint = TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Secondary)
    )
}

// ============================================================
// 聊天消息列表
// ============================================================

@Composable
private fun ChatMessageList(
    messages: List<TerminalMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 新消息到来时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        // 空状态
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "终端就绪",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = "输入 LX200 命令并发送",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.isFromUser) {
                    UserMessageBubble(msg)
                } else {
                    MountMessageBubble(msg)
                }
            }
        }
    }
}

// ============================================================
// 用户消息气泡（右侧、青色）
// ============================================================

@Composable
private fun UserMessageBubble(msg: TerminalMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 4.dp
            ),
            color = Accent,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = msg.content,
                    color = Primary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            text = formatTime(msg.timestamp),
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
        )
    }
}

// ============================================================
// 赤道仪消息气泡（左侧、深灰 / Hex 模式）
// ============================================================

@Composable
private fun MountMessageBubble(msg: TerminalMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 4.dp,
                bottomEnd = 12.dp
            ),
            color = Secondary,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.hexMode) {
                    // Hex 双栏显示
                    HexDumpContent(msg.content)
                } else {
                    Text(
                        text = msg.content,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Text(
            text = formatTime(msg.timestamp),
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

// ============================================================
// Hex Dump 格式化显示
// ============================================================

@Composable
private fun HexDumpContent(content: String) {
    val bytes = content.toByteArray(Charsets.US_ASCII)

    // 每 8 字节一组
    val chunkSize = 8
    val lines = bytes.toList().chunked(chunkSize)

    lines.forEach { chunk ->
        val hexPart = chunk.joinToString(" ") { byte ->
            "%02X".format(byte)
        }.padEnd(chunkSize * 3, ' ')

        val asciiPart = chunk.joinToString("") { byte ->
            if (byte in 0x20..0x7E) byte.toInt().toChar().toString() else "."
        }

        Text(
            text = "$hexPart  $asciiPart",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
}

// ============================================================
// 底部输入栏
// ============================================================

@Composable
private fun TerminalInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCommandRefClick: () -> Unit
) {
    Surface(
        color = Secondary,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("输入 LX200 指令...", color = TextSecondary.copy(alpha = 0.5f))
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent2,
                    unfocusedBorderColor = Secondary,
                    cursorColor = Accent2
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // 发送按钮
            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent2,
                    contentColor = Primary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送", modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 指令参考按钮
            FilledIconButton(
                onClick = onCommandRefClick,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent.copy(alpha = 0.3f),
                    contentColor = Accent
                )
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = "指令参考", modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ============================================================
// 工具函数
// ============================================================

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
