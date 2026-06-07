@file:OptIn(ExperimentalMaterial3Api::class)

package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skygoto.app.domain.model.AlignSession
import com.skygoto.app.domain.model.AlignStarSync
import com.skygoto.app.domain.model.AlignState
import com.skygoto.app.domain.model.BrightStar
import com.skygoto.app.domain.model.Direction
import com.skygoto.app.domain.model.MountStatus
import com.skygoto.app.domain.model.MoveRate
import com.skygoto.app.ui.components.ResultBanner
import com.skygoto.app.ui.components.ALIGN_HELP_SECTIONS
import com.skygoto.app.ui.components.HelpDialog
import com.skygoto.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 生命周期
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.isConnected) viewModel.startPollingWhenVisible()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopPollingWhenHidden()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
    ) {
        // 顶部状态栏
        ConnectionStatusBar(isConnected = uiState.isConnected)

        // Tab 切换
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Secondary,
            contentColor = TextPrimary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("控制") },
                selectedContentColor = Accent2,
                unselectedContentColor = TextSecondary,
                icon = { Icon(Icons.Default.TouchApp, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    viewModel.loadAlignStarList()
                },
                text = { Text("对齐") },
                selectedContentColor = Accent2,
                unselectedContentColor = TextSecondary,
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("PEC") },
                selectedContentColor = Accent2,
                unselectedContentColor = TextSecondary,
                icon = { Icon(Icons.Default.ShowChart, contentDescription = null) }
            )
        }

        // Tab 内容
        when (selectedTab) {
            0 -> ControlTab(
                uiState = uiState,
                viewModel = viewModel
            )
            1 -> AlignTab(
                uiState = uiState,
                viewModel = viewModel
            )
            2 -> PecScreen()
        }
    }

    // GOTO 结果提示
    uiState.gotoResult?.let { config ->
        ResultBanner(config = config, onDismiss = viewModel::clearGotoResult)
    }
    uiState.result?.let { config ->
        ResultBanner(config = config, onDismiss = viewModel::clearGotoResult)
    }
}

// =====================================================================
// 顶部状态栏
// =====================================================================

@Composable
private fun ConnectionStatusBar(isConnected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Primary,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OnStepX", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Accent2 else Error)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isConnected) "已连接" else "未连接",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// =====================================================================
// Tab 0：控制面板
// =====================================================================

@Composable
private fun ControlTab(
    uiState: ControlUiState,
    viewModel: ControlViewModel
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 坐标显示
        CoordinatesDisplay(uiState.mountStatus)
        Spacer(Modifier.height(12.dp))

        // 追踪开关
        TrackingControl(
            isTracking = uiState.mountStatus.tracking,
            onToggle = viewModel::toggleTracking
        )
        Spacer(Modifier.height(12.dp))

        // 速率
        RateSelector(
            selectedRate = uiState.selectedRate,
            onRateSelected = viewModel::selectRate
        )
        Spacer(Modifier.height(16.dp))

        // D-Pad
        DPadControl(
            onDirectionPressed = viewModel::move,
            onDirectionReleased = viewModel::stopMove,
            onStopPressed = viewModel::stopMove,
            onHome = viewModel::home,
            onSetZero = viewModel::setZeroPosition
        )
    }
}

@Composable
private fun CoordinatesDisplay(status: MountStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Secondary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("当前位置", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RA", fontSize = 11.sp, color = Accent)
                    Text(status.ra, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dec", fontSize = 11.sp, color = Accent)
                    Text(status.dec, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Alt", fontSize = 11.sp, color = Accent)
                    Text(status.alt, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Az", fontSize = 11.sp, color = Accent)
                    Text(status.az, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun TrackingControl(isTracking: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("追踪", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Switch(
            checked = isTracking,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent2,
                checkedTrackColor = Accent2.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun RateSelector(selectedRate: MoveRate, onRateSelected: (MoveRate) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("移动速率", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedRate.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary,
                    focusedBorderColor = Accent2
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MoveRate.entries.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.displayName) },
                        onClick = { onRateSelected(rate); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DPadControl(
    onDirectionPressed: (Direction) -> Unit,
    onDirectionReleased: () -> Unit,
    onStopPressed: () -> Unit,
    onHome: () -> Unit,
    onSetZero: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 上：零位 | N | 置为零位
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SmallCapsuleButton("零位", Icons.Default.Home, onHome)
            DirectionCircle(Direction.NORTH, onDirectionPressed, onDirectionReleased)
            SmallCapsuleButton("置为零位", Icons.Default.AddLocation, onSetZero)
        }
        Spacer(Modifier.height(20.dp))
        // 中：W | STOP | E
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DirectionCircle(Direction.WEST, onDirectionPressed, onDirectionReleased)
            StopCircle(onStopPressed)
            DirectionCircle(Direction.EAST, onDirectionPressed, onDirectionReleased)
        }
        Spacer(Modifier.height(20.dp))
        // 下：S
        DirectionCircle(Direction.SOUTH, onDirectionPressed, onDirectionReleased)
    }
}

@Composable
private fun SmallCapsuleButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(44.dp).width(90.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Secondary, contentColor = TextPrimary),
        shape = RoundedCornerShape(22.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DirectionCircle(direction: Direction, onPressed: (Direction) -> Unit, onReleased: () -> Unit) {
    val icon = when (direction) {
        Direction.NORTH -> Icons.Default.KeyboardArrowUp
        Direction.SOUTH -> Icons.Default.KeyboardArrowDown
        Direction.EAST -> Icons.Default.KeyboardArrowRight
        Direction.WEST -> Icons.Default.KeyboardArrowLeft
    }
    val label = when (direction) {
        Direction.NORTH -> "N"; Direction.SOUTH -> "S"
        Direction.EAST -> "E"; Direction.WEST -> "W"
    }
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(if (isPressed) Accent else Accent.copy(alpha = 0.8f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true; onPressed(direction)
                        tryAwaitRelease(); isPressed = false; onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = Primary, modifier = Modifier.size(if (isPressed) 28.dp else 24.dp))
            Text(label, fontSize = if (isPressed) 11.sp else 10.sp, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StopCircle(onPressed: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (isPressed) Error.copy(alpha = 0.7f) else Error)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { isPressed = true; onPressed(); tryAwaitRelease(); isPressed = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Stop, contentDescription = "STOP", tint = Primary, modifier = Modifier.size(if (isPressed) 32.dp else 28.dp))
            Text("STOP", fontSize = if (isPressed) 12.sp else 10.sp, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

// =====================================================================
// Tab 1：对齐向导
// =====================================================================

@Composable
private fun AlignTab(uiState: ControlUiState, viewModel: ControlViewModel) {
    val session = uiState.alignSession

    when (session.state) {
        AlignState.IDLE -> AlignIdleView(uiState, viewModel)
        AlignState.HOMING -> CenteredMessage("正在回零...")
        AlignState.STARTING -> CenteredMessage("正在启动对齐...")
        AlignState.SELECTING_STAR -> AlignSelectStarView(uiState, viewModel)
        AlignState.GOTOING -> CenteredMessage("GOTO 到目标星中...")
        AlignState.CENTERING -> AlignCenteringView(uiState, viewModel)
        AlignState.SYNCING -> CenteredMessage("正在同步...")
        AlignState.MODEL_BUILDING -> CenteredMessage("正在计算对齐模型...")
        AlignState.COMPLETE -> AlignCompleteView(uiState, viewModel)
        AlignState.CANCELLING -> CenteredMessage("正在取消...")
    }
}

/** 空闲状态：开始对齐 */
@Composable
private fun AlignIdleView(uiState: ControlUiState, viewModel: ControlViewModel) {
    var showHelp by remember { mutableStateOf(false) }

    if (showHelp) {
        HelpDialog(
            title = "星点对齐 - 帮助",
            sections = ALIGN_HELP_SECTIONS,
            onDismiss = { showHelp = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 帮助按钮
        IconButton(
            onClick = { showHelp = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = "帮助", tint = TextSecondary)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(64.dp), tint = Accent2)
        Spacer(Modifier.height(16.dp))
        Text("星点对齐", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "将对齐 6 颗亮星以建立精确指向模型\nGOTO 精度可达 1~3 角分",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // 星数选择
        Text("对齐星数", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(3, 6, 9).forEach { n ->
                FilterChip(
                    selected = uiState.alignSession.totalStars == n,
                    onClick = { viewModel.setAlignStarCount(n) },
                    label = { Text("${n}星") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent2,
                        selectedLabelColor = Primary
                    )
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = viewModel::startAlignment,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent2, contentColor = Primary),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("开始对齐", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        // 清除对齐数据
        OutlinedButton(
            onClick = { viewModel.clearAlignModel() },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("清除对齐数据")
        }
    }
    }
}

/** 选择目标星 */
@Composable
private fun AlignSelectStarView(uiState: ControlUiState, viewModel: ControlViewModel) {
    val session = uiState.alignSession
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 计算星星当前 Alt/Az 用于列表显示
        viewModel.computeStarAltAz()
        if (uiState.alignStarList.isEmpty()) viewModel.loadAlignStarList()
    }

    if (showHelp) {
        HelpDialog(
            title = "星点对齐 - 帮助",
            sections = ALIGN_HELP_SECTIONS,
            onDismiss = { showHelp = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // 进度标头
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${session.completedCount + 1}/${session.totalStars} 星", style = MaterialTheme.typography.titleMedium, color = Accent2)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "帮助", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                Text("${session.completedCount}/${session.totalStars}", fontSize = 12.sp, color = TextSecondary)
            }
            // 进度条
            val progressVal = (session.completedCount.toFloat() / session.totalStars.toFloat()).coerceIn(0f, 1f)
            // 避免 weight(0) 导致 Compose 布局崩溃
            Row(Modifier.fillMaxWidth().height(8.dp)) {
                if (progressVal > 0f) {
                    Box(Modifier.weight(progressVal).fillMaxHeight().background(Accent2, RoundedCornerShape(if (progressVal >= 1f) 4.dp else 0.dp)))
                }
                if (progressVal < 1f) {
                    Box(Modifier.weight(1f - progressVal).fillMaxHeight().background(Accent.copy(alpha = 0.15f), RoundedCornerShape(if (progressVal > 0f) 0.dp else 4.dp)))
                }
            }
        }

        // 选中星信息
        item {
            val selectedAltAz = uiState.starAltAzList.find { it.first.name == uiState.selectedStarName }
            Card(
                colors = CardDefaults.cardColors(containerColor = Secondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectPrevAlignStar() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一星", tint = TextSecondary)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.selectedStarName.isNotBlank()) {
                            Text(uiState.selectedStarName, style = MaterialTheme.typography.titleSmall, color = Accent2, fontWeight = FontWeight.Bold)
                            if (selectedAltAz != null) {
                                Text("Alt ${String.format("%.0f", selectedAltAz.second.first)}° Az ${String.format("%.0f", selectedAltAz.second.second)}°  可见 ${uiState.starAltAzList.size}星",
                                    fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Text("请选择一颗校准星", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    IconButton(onClick = { viewModel.selectNextAlignStar() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一星", tint = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 操作按钮
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::cancelAlignment,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                    shape = RoundedCornerShape(22.dp)
                ) { Text("取消") }
                Button(
                    onClick = viewModel::alignGoto,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.selectedStarName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent2, contentColor = Primary),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("GOTO")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 星数统计
        item {
            Text("可见校准星 (${uiState.starAltAzList.size}颗)", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }

        // 可见星列表
        items(uiState.starAltAzList, key = { it.first.ra }) { item ->
            val star = item.first
            val starAlt = item.second.first
            val starAz = item.second.second
            val isSelected = star.name == uiState.selectedStarName
            val completed = session.completedStars.any { it.starData.ra == star.ra }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(enabled = !completed) {
                    viewModel.selectAlignStar(star)
                    viewModel.selectStarFromChart(star)
                },
                colors = CardDefaults.cardColors(containerColor = when {
                    completed -> Secondary.copy(alpha = 0.4f)
                    isSelected -> Accent2.copy(alpha = 0.2f)
                    else -> Secondary
                }),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(star.name, fontSize = 13.sp,
                            color = if (completed) TextSecondary else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                    Text("Alt ${String.format("%.0f", starAlt)}° Az ${String.format("%.0f", starAz)}°",
                        fontSize = 10.sp, color = Accent.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text("${String.format("%.1f", star.magnitude)}等", fontSize = 11.sp, color = Accent)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AlignCenteringView(uiState: ControlUiState, viewModel: ControlViewModel) {
    val session = uiState.alignSession

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(64.dp), tint = Accent)
        Spacer(Modifier.height(16.dp))
        Text("用方向键将星精确居中", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "星 ${session.completedCount + 1}/${session.totalStars}\n" +
                    "建议使用「居中 8x」速率微调",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        // 速率建议
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.selectedRate == MoveRate.CENTERING,
                onClick = { viewModel.selectRate(MoveRate.CENTERING) },
                label = { Text("居中 8x") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent2,
                    selectedLabelColor = Primary
                )
            )
            FilterChip(
                selected = uiState.selectedRate == MoveRate.GUIDE,
                onClick = { viewModel.selectRate(MoveRate.GUIDE) },
                label = { Text("导星 1x") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent2,
                    selectedLabelColor = Primary
                )
            )
        }
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = viewModel::alignSync,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent2, contentColor = Primary),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("已居中，同步", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = viewModel::cancelAlignment,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
            shape = RoundedCornerShape(22.dp)
        ) {
            Text("取消对齐")
        }
    }
}

/** 对齐完成 */
@Composable
private fun AlignCompleteView(uiState: ControlUiState, viewModel: ControlViewModel) {
    val session = uiState.alignSession

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(72.dp), tint = Accent2)
        Spacer(Modifier.height(16.dp))
        Text("对齐完成！", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text("已完成 ${session.completedCount}/${session.totalStars} 星", style = MaterialTheme.typography.titleMedium, color = TextSecondary)

        if (session.rmsError > 0) {
            Spacer(Modifier.height(4.dp))
            Text("模型精度: ${String.format(java.util.Locale.US, "%.1f", session.rmsError)}′", style = MaterialTheme.typography.bodyLarge, color = Accent)
        }

        Spacer(Modifier.height(8.dp))
        Text("GOTO 精度已优化，模型已保存到 NV", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(16.dp))

        // 已对齐星列表
        session.completedStars.forEach { sync ->
            Text("★ ${sync.starData.name}", fontSize = 14.sp, color = TextPrimary)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.cancelAlignment() // 重设为 IDLE 状态
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent2, contentColor = Primary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("完成")
        }
    }
}

/** 居中消息 */
@Composable
private fun CenteredMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent2)
            Spacer(Modifier.height(16.dp))
            Text(msg, color = TextSecondary)
        }
    }
}
