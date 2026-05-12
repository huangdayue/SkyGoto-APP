package com.skygoto.app.ui.screens.control

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skygoto.app.domain.model.Direction
import com.skygoto.app.domain.model.MoveRate
import com.skygoto.app.ui.theme.*

@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .padding(16.dp)
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
            onDirectionReleased = viewModel::stopMove
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // GOTO 结果
        uiState.gotoResult?.let { result ->
            GotoResultBanner(
                result = result,
                onDismiss = viewModel::clearGotoResult
            )
        }
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RA", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(ra, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dec", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(dec, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Alt", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(alt, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Az", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(az, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = TextPrimary)
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
    Column {
        Text("移动速率", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MoveRate.entries.take(4).forEach { rate ->
                FilterChip(
                    selected = rate == selectedRate,
                    onClick = { onRateSelected(rate) },
                    label = { Text(rate.displayName, fontSize = 10.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DPadControl(
    onDirectionPressed: (Direction) -> Unit,
    onDirectionReleased: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // 中心点
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Secondary)
        )
        
        // 北
        DirectionButton(
            direction = Direction.NORTH,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 20.dp),
            onPressed = { onDirectionPressed(Direction.NORTH) },
            onReleased = onDirectionReleased
        )
        
        // 南
        DirectionButton(
            direction = Direction.SOUTH,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-20).dp),
            onPressed = { onDirectionPressed(Direction.SOUTH) },
            onReleased = onDirectionReleased
        )
        
        // 西
        DirectionButton(
            direction = Direction.WEST,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 20.dp),
            onPressed = { onDirectionPressed(Direction.WEST) },
            onReleased = onDirectionReleased
        )
        
        // 东
        DirectionButton(
            direction = Direction.EAST,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-20).dp),
            onPressed = { onDirectionPressed(Direction.EAST) },
            onReleased = onDirectionReleased
        )
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
    
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Accent.copy(alpha = 0.8f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed()
                        tryAwaitRelease()
                        onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = direction.name, tint = Primary)
            Text(label, fontSize = 10.sp, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GotoResultBanner(
    result: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.startsWith("错误")) Error.copy(alpha = 0.3f) else Accent.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(result, color = TextPrimary)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
            }
        }
    }
}
