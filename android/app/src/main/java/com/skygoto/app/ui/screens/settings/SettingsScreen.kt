package com.skygoto.app.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.skygoto.app.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 监听页面可见性，进入时自动加载赤道仪信息
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadAllFromMount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 位置权限请求
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.updateHasLocationPermission(fineLocation || coarseLocation)
        if (fineLocation || coarseLocation) {
            viewModel.syncPhoneLocationToMount()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ==================== 赤道仪位置 ====================
        SettingsSection(title = "赤道仪位置") {
            // 经度（只读，显示从赤道仪获取的位置）
            SettingsTextFieldReadOnly(
                icon = Icons.Default.Public,
                title = "经度",
                value = uiState.longitude,
                placeholder = "120:00:00"
            )
            
            Divider(color = Secondary)
            
            // 纬度（只读，显示从赤道仪获取的位置）
            SettingsTextFieldReadOnly(
                icon = Icons.Default.Place,
                title = "纬度",
                value = uiState.latitude,
                placeholder = "30:00:00"
            )
            
            Divider(color = Secondary)
            
            // 同步手机 GPS 到赤道仪按钮
            SettingsClickable(
                icon = Icons.Default.Smartphone,
                title = "同步手机位置至赤道仪",
                subtitle = "将手机 GPS 位置发送给赤道仪",
                onClick = {
                    if (uiState.hasLocationPermission) {
                        viewModel.syncPhoneLocationToMount()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                enabled = !uiState.isSyncingLocation && uiState.isMountConnected,
                isLoading = uiState.isSyncingLocation
            )
            
            // 位置错误提示
            uiState.locationError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== 赤道仪时间 ====================
        SettingsSection(title = "赤道仪时间") {
            // 当前时间显示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "赤道仪当前时间",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Text(
                        uiState.mountTime.ifEmpty { "未连接" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent
                    )
                }
                
                IconButton(
                    onClick = { viewModel.syncMountTime() },
                    enabled = !uiState.isSyncingTime
                ) {
                    if (uiState.isSyncingTime) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "同步时间",
                            tint = Accent
                        )
                    }
                }
            }
            
            Divider(color = Secondary)
            
            // 同步到安卓设备时间
            SettingsClickable(
                icon = Icons.Default.PhoneAndroid,
                title = "同步时间到赤道仪",
                subtitle = "将安卓设备日期、时间和时区同步到赤道仪",
                onClick = { viewModel.syncToAndroidTime() },
                enabled = !uiState.isSyncingTime && uiState.isMountConnected
            )
            
            Divider(color = Secondary)
            
            // 同步到恒星时（已移除，用户要求删除）
            // SettingsClickable(
            //     icon = Icons.Default.AutoMode,
            //     title = "同步到恒星时",
            //     subtitle = "计算并同步当前恒星时",
            //     onClick = { viewModel.calculateSiderealTime() },
            //     enabled = !uiState.isSyncingTime && uiState.isMountConnected
            // )
            
            // 错误提示
            uiState.mountTimeError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== 界面设置 ====================
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
        
        // ==================== 日志 ====================
        SettingsSection(title = "日志") {
            SettingsClickable(
                icon = Icons.Default.Description,
                title = "导出日志",
                subtitle = "将日志文件分享到其他应用",
                onClick = {
                    viewModel.exportLogs()
                }
            )
            
            Divider(color = Secondary)
            
            SettingsClickable(
                icon = Icons.Default.Delete,
                title = "清空日志",
                subtitle = "删除当前已记录的日志数据",
                onClick = {
                    viewModel.clearLogs()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== 关于 ====================
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
        
        Spacer(modifier = Modifier.height(32.dp))
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

@Composable
private fun SettingsTextFieldReadOnly(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    placeholder: String = ""
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
            Text(
                text = value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isEmpty()) TextSecondary else TextPrimary
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    trailing: @Composable (() -> Unit)? = null
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
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(placeholder, color = TextSecondary)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextSecondary,
                    cursorColor = Accent,
                    focusedContainerColor = Primary,
                    unfocusedContainerColor = Primary
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsClickable(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) TextPrimary else TextPrimary.copy(alpha = 0.5f)
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f)
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (enabled) Accent else Accent.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingsDropdown(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
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
            Text(
                options.find { it.first == selectedValue }?.second ?: selectedValue,
                style = MaterialTheme.typography.bodySmall,
                color = Accent
            )
        }
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = TextSecondary
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Secondary)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (value == selectedValue) Accent else TextPrimary
                        )
                    },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}