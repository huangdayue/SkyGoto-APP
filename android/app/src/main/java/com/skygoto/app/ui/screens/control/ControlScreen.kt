@file:OptIn(ExperimentalMaterial3Api::class)

package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skygoto.app.domain.model.Direction
import com.skygoto.app.domain.model.MoveRate
import com.skygoto.app.ui.components.ResultBanner
import com.skygoto.app.ui.theme.*

@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    // 生命周期感知：只在控制页面可见时轮询
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 用户进入控制页面且已连接 → 开始轮询
                    if (uiState.isConnected) {
                        viewModel.startPollingWhenVisible()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // 用户离开控制页面 → 停止轮询
                    viewModel.stopPollingWhenHidden()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 连接状态栏
        ConnectionStatusBar(isConnected = uiState.isConnected)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 坐标显示
        CoordinatesDisplay(
            ra = uiState.mountStatus.ra,
            dec = uiState.mountStatus.dec,
            alt = uiState.mountStatus.alt,
            az = uiState.mountStatus.az
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 追踪控制
        TrackingControl(
            isTracking = uiState.mountStatus.tracking,
            onToggle = viewModel::toggleTracking
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 速率选择
        RateSelector(
            selectedRate = uiState.selectedRate,
            onRateSelected = viewModel::selectRate
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // D-Pad 方向控制
        DPadControl(
            onDirectionPressed = viewModel::move,
            onDirectionReleased = viewModel::stopMove,
            onStopPressed = viewModel::stopMove,
            onHome = viewModel::home,
            onSetZero = viewModel::setZeroPosition
        )
    }

    // GOTO 结果
    uiState.gotoResult?.let { config ->
        ResultBanner(
            config = config,
            onDismiss = viewModel::clearGotoResult
        )
    }

    // 错误/失败/断开连接等统一提示
    uiState.result?.let { config ->
        ResultBanner(
            config = config,
            onDismiss = viewModel::clearGotoResult
        )
    }
}

@Composable
private fun ConnectionStatusBar(isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "控制",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Accent2 else Error)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "已连接" else "未连接",
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CoordinatesDisplay(
    ra: String, dec: String, alt: String, az: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Secondary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前位置",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 第一行：RA 和 Dec
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RA", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(ra, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dec", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(dec, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 第二行：Alt 和 Az
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Alt", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(alt, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Az", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(az, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun TrackingControl(
    isTracking: Boolean,
    onToggle: () -> Unit
) {
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
private fun RateSelector(
    selectedRate: MoveRate,
    onRateSelected: (MoveRate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text("移动速率", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedRate.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MoveRate.entries.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.displayName) },
                        onClick = {
                            onRateSelected(rate)
                            expanded = false
                        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上排：零 | N | 置
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 零位按钮（椭圆胶囊形，稍大）
            Button(
                onClick = onHome,
                modifier = Modifier.height(44.dp).width(90.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Secondary,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("零位", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            
            // N按钮
            DirectionButton(
                direction = Direction.NORTH,
                onPressed = { onDirectionPressed(Direction.NORTH) },
                onReleased = onDirectionReleased
            )
            
            // 置为零位按钮（椭圆胶囊形）
            Button(
                onClick = onSetZero,
                modifier = Modifier.height(44.dp).width(90.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Secondary,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("置为零位", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 中间行：W   STOP   E
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // W按钮
            DirectionButton(
                direction = Direction.WEST,
                onPressed = { onDirectionPressed(Direction.WEST) },
                onReleased = onDirectionReleased
            )
            
            // STOP按钮
            StopButton(
                onPressed = onStopPressed
            )
            
            // E按钮
            DirectionButton(
                direction = Direction.EAST,
                onPressed = { onDirectionPressed(Direction.EAST) },
                onReleased = onDirectionReleased
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // S按钮（居中）
        DirectionButton(
            direction = Direction.SOUTH,
            onPressed = { onDirectionPressed(Direction.SOUTH) },
            onReleased = onDirectionReleased
        )
    }
}

@Composable
private fun StopButton(
    modifier: Modifier = Modifier,
    onPressed: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) Error.copy(alpha = 0.7f)
                else Error
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressed()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "STOP",
                tint = Primary,
                modifier = Modifier.size(if (isPressed) 32.dp else 28.dp)
            )
            Text(
                "STOP",
                fontSize = if (isPressed) 12.sp else 10.sp,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DirectionButton(
    direction: Direction,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    val icon = when (direction) {
        Direction.NORTH -> Icons.Default.KeyboardArrowUp
        Direction.SOUTH -> Icons.Default.KeyboardArrowDown
        Direction.EAST -> Icons.Default.KeyboardArrowRight
        Direction.WEST -> Icons.Default.KeyboardArrowLeft
    }
    val label = when (direction) {
        Direction.NORTH -> "N"
        Direction.SOUTH -> "S"
        Direction.EAST -> "E"
        Direction.WEST -> "W"
    }
    
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) Accent 
                else Accent.copy(alpha = 0.8f)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressed()
                        tryAwaitRelease()
                        isPressed = false
                        onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, 
                contentDescription = direction.name, 
                tint = Primary,
                modifier = Modifier.size(if (isPressed) 28.dp else 24.dp)
            )
            Text(
                label, 
                fontSize = if (isPressed) 11.sp else 10.sp, 
                color = Primary, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}
