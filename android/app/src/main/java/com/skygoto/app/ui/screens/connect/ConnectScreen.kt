@file:OptIn(ExperimentalMaterial3Api::class)

package com.skygoto.app.ui.screens.connect

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skygoto.app.data.datasource.ScannedBluetoothDevice
import com.skygoto.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Logo / Title
        Text(
            text = "SkyGoto",
            style = MaterialTheme.typography.displayMedium,
            color = Accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "赤道仪控制器",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 连接状态指示
        ConnectionIndicator(isConnected = isConnected)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 连接方式选择标签页
        if (!isConnected) {
            ConnectionTypeTabs(
                selectedType = connectionType,
                onTypeSelected = viewModel::selectConnectionType
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (connectionType) {
                ConnectionTabType.WIFI -> {
                    WifiConnectionForm(
                        host = uiState.host,
                        port = uiState.port,
                        isConnecting = uiState.isConnecting,
                        error = uiState.error,
                        onHostChange = viewModel::updateHost,
                        onPortChange = viewModel::updatePort,
                        onConnect = viewModel::connectWifi
                    )
                }
                ConnectionTabType.BLUETOOTH -> {
                    BluetoothTabContent(
                        uiState = uiState,
                        onScanClick = viewModel::startBluetoothScan,
                        onStopScanClick = viewModel::stopBluetoothScan,
                        onDeviceClick = viewModel::connectToScannedDevice
                    )
                }
            }
        } else {
            ConnectedInfo(
                connectionType = if (uiState.isWifiConnection) "WiFi" else "蓝牙",
                address = uiState.deviceName,
                onDisconnect = viewModel::disconnect
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 版本信息
        Text(
            text = "v1.0.0 | OnStepX 兼容",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ConnectionTypeTabs(
    selectedType: ConnectionTabType,
    onTypeSelected: (ConnectionTabType) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedType.ordinal,
        containerColor = Secondary,
        contentColor = Accent
    ) {
        Tab(
            selected = selectedType == ConnectionTabType.WIFI,
            onClick = { onTypeSelected(ConnectionTabType.WIFI) },
            text = { Text("WiFi") },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
        )
        Tab(
            selected = selectedType == ConnectionTabType.BLUETOOTH,
            onClick = { onTypeSelected(ConnectionTabType.BLUETOOTH) },
            text = { Text("蓝牙") },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
        )
    }
}

@Composable
private fun ConnectionIndicator(isConnected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isConnected) Accent2.copy(alpha = 0.2f) else Secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (isConnected) Accent2 else TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isConnected) "已连接到赤道仪" else "未连接",
            style = MaterialTheme.typography.titleMedium,
            color = if (isConnected) Accent2 else TextSecondary
        )
    }
}

@Composable
private fun WifiConnectionForm(
    host: String,
    port: String,
    isConnecting: Boolean,
    error: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP地址", color = TextSecondary) },
                placeholder = { Text("192.168.1.1", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Accent) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Secondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("端口", color = TextSecondary) },
                placeholder = { Text("9999", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = Accent) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Secondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
            
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isConnecting && host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接", color = Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BluetoothTabContent(
    uiState: ConnectUiState,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (ScannedBluetoothDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 扫描按钮和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.scannedDevices.isEmpty()) "点击扫描附近蓝牙设备" 
                           else "发现 ${uiState.scannedDevices.size} 个设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                if (uiState.isScanning) {
                    OutlinedButton(
                        onClick = onStopScanClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止")
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
            if (uiState.isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Text("正在扫描...", color = Accent)
                }
            }
            
            // 错误提示
            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
            
            // 连接中状态
            if (uiState.isConnecting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent
                )
            }
            
            // 设备列表
            if (uiState.scannedDevices.isEmpty() && !uiState.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "未发现设备",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // 已配对设备
                val pairedDevices = uiState.scannedDevices.filter { it.isPaired }
                val unpairedDevices = uiState.scannedDevices.filter { !it.isPaired }
                
                if (pairedDevices.isNotEmpty()) {
                    Text(
                        "已配对设备",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    pairedDevices.forEach { device ->
                        BluetoothDeviceButton(
                            device = device,
                            isConnecting = uiState.isConnecting,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
                
                if (unpairedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "附近设备",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    unpairedDevices.forEach { device ->
                        BluetoothDeviceButton(
                            device = device,
                            isConnecting = uiState.isConnecting,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceButton(
    device: ScannedBluetoothDevice,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isConnecting,
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            Icons.Default.Bluetooth, 
            contentDescription = null, 
            tint = if (device.isPaired) Accent else TextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (device.isPaired) device.name else "${device.name} (未配对)",
            color = if (device.isPaired) TextPrimary else TextSecondary
        )
        if (device.rssi != 0) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ConnectedInfo(
    connectionType: String,
    address: String,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (connectionType == "WiFi") Icons.Default.Wifi else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Accent2,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$connectionType: $address",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("断开连接")
            }
        }
    }
}

enum class ConnectionTabType {
    WIFI,
    BLUETOOTH
}