package com.skygoto.app.ui.screens.connect

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.skygoto.app.data.datasource.ScannedBluetoothDevice
import com.skygoto.app.ui.theme.*

@Composable
fun BluetoothPairingScreen(
    isScanning: Boolean,
    scannedDevices: List<ScannedBluetoothDevice>,
    isConnecting: Boolean,
    connectingDeviceName: String?,
    error: String?,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (ScannedBluetoothDevice) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // 权限状态
    var hasConnectPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasScanPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasConnectPermission = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        hasScanPermission = permissions[Manifest.permission.BLUETOOTH_SCAN] == true
    }
    
    LaunchedEffect(Unit) {
        if (!hasConnectPermission || !hasScanPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
    ) {
        TopAppBar(
            title = { Text("选择蓝牙设备", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
        )
        
        if (!hasConnectPermission || !hasScanPermission) {
            // 无权限提示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "需要蓝牙权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                        )
                    }) {
                        Text("授予权限")
                    }
                }
            }
        } else {
            // 扫描按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (scannedDevices.isEmpty()) "点击扫描附近的蓝牙设备" else "发现 ${scannedDevices.size} 个设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                if (isScanning) {
                    OutlinedButton(
                        onClick = onStopScanClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止扫描")
                    }
                } else {
                    Button(
                        onClick = onScanClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("扫描")
                    }
                }
            }
            
            // 扫描动画
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val infiniteTransition = rememberInfiniteTransition(label = "scan")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Icon(
                            Icons.Default.Radar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation),
                            tint = Accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "正在扫描...",
                            color = Accent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // 连接中状态
            if (isConnecting && connectingDeviceName != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "正在连接: $connectingDeviceName",
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
            
            // 错误提示
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(it, color = Error)
                    }
                }
            }
            
            // 设备列表
            if (scannedDevices.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "未发现设备",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            "请点击\"扫描\"按钮搜索附近蓝牙设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 已配对设备分组
                    val pairedDevices = scannedDevices.filter { it.isPaired }
                    val unpairedDevices = scannedDevices.filter { !it.isPaired }
                    
                    if (pairedDevices.isNotEmpty()) {
                        item {
                            Text(
                                "已配对设备",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(pairedDevices) { device ->
                            BluetoothDeviceItem(
                                device = device,
                                isConnecting = isConnecting && connectingDeviceName == device.name,
                                onClick = { onDeviceClick(device) }
                            )
                        }
                    }
                    
                    if (unpairedDevices.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "附近设备",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(unpairedDevices) { device ->
                            BluetoothDeviceItem(
                                device = device,
                                isConnecting = isConnecting && connectingDeviceName == device.name,
                                onClick = { onDeviceClick(device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceItem(
    device: ScannedBluetoothDevice,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnecting) Accent.copy(alpha = 0.1f) else Secondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.isPaired) Accent.copy(alpha = 0.2f)
                        else TextSecondary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isPaired) Accent else TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (device.isPaired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已配对",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent
                        )
                    }
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                if (device.rssi != 0) {
                    Text(
                        text = "信号: ${device.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (!isConnecting) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}