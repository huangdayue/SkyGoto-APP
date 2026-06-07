package com.skygoto.app.ui.screens.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.data.datasource.ScannedBluetoothDevice
import com.skygoto.app.data.protocol.LX200Protocol
import com.skygoto.app.data.protocol.ProtocolConnection
import com.skygoto.app.data.repository.MountRepositoryImpl
import com.skygoto.app.domain.model.ConnectionState
import com.skygoto.app.domain.repository.ConnectionManager
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ConnectViewModel"

data class ConnectUiState(
    val host: String = "192.168.0.1",
    val port: String = "9998",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val pairedDevices: List<Pair<String, String>> = emptyList(), // name, address
    val isWifiConnection: Boolean = true,
    val deviceName: String = "",
    val firmwareVersion: String = "",
    val isScanning: Boolean = false,
    val scannedDevices: List<ScannedBluetoothDevice> = emptyList(),
    val showConnectedToast: Boolean = false
)

/**
 * 连接页面 ViewModel
 *
 * 修复说明：
 * - 蓝牙连接管理器(BluetoothConnectionManager)现在通过 Hilt 注入单例，
 *   不再自己 new 出实例，避免资源泄漏（广播接收器、扫描超时 Runnable）
 * - ConnectionManager 也通过构造函数注入同一个 BluetoothConnectionManager 单例
 * - 断开连接时，统一通过 connectionManager.disconnect() 关闭资源
 * - ViewModel.onCleared() 不再 close() bluetoothManager，因为它是应用级单例
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val bluetoothManager: BluetoothConnectionManager,
    private val connectionManager: ConnectionManager,
    private val mountRepository: MountRepositoryImpl
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionType = MutableStateFlow(ConnectionTabType.WIFI)
    val connectionType: StateFlow<ConnectionTabType> = _connectionType.asStateFlow()
    
    init {
        // 观察连接状态
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is com.skygoto.app.domain.model.ConnectionState.Connected -> {
                        _isConnected.value = true
                        _uiState.update { it.copy(isConnecting = false, error = null) }
                    }
                    is com.skygoto.app.domain.model.ConnectionState.Error -> {
                        _isConnected.value = false
                        _uiState.update { it.copy(isConnecting = false, error = state.message) }
                    }
                    is com.skygoto.app.domain.model.ConnectionState.Connecting -> {
                        _uiState.update { it.copy(isConnecting = true, error = null) }
                    }
                    else -> {
                        _isConnected.value = false
                    }
                }
            }
        }
        
        // 初始化蓝牙管理器（使用 Hilt 注入的单例，统一管理资源）
        initBluetooth()
    }
    
    @SuppressLint("MissingPermission")
    private fun initBluetooth() {
        refreshBluetoothDevices()
        
        // 观察扫描状态和设备列表
        viewModelScope.launch {
            bluetoothManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        
        viewModelScope.launch {
            bluetoothManager.scannedDevices.collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
    }
    
    fun selectConnectionType(type: ConnectionTabType) {
        AppLogger.i("UserAction", "切换连接方式: ${type.name}")
        _connectionType.value = type
        _uiState.update { it.copy(isWifiConnection = type == ConnectionTabType.WIFI) }
    }
    
    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }
    
    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }
    
    fun refreshBluetoothDevices() {
        val devices = bluetoothManager.getPairedDevices().map { device ->
            (device.name ?: "Unknown") to device.address
        }
        _uiState.update { it.copy(pairedDevices = devices) }
    }
    
    /**
     * 开始蓝牙扫描
     */
    fun startBluetoothScan() {
        bluetoothManager.startScan()
    }
    
    /**
     * 停止蓝牙扫描
     */
    fun stopBluetoothScan() {
        bluetoothManager.stopScan()
    }
    
    /**
     * 连接到扫描到的蓝牙设备
     */
    fun connectToScannedDevice(device: ScannedBluetoothDevice) {
        viewModelScope.launch {
            AppLogger.d(TAG, "Connecting to device: ${device.name} (${device.address})")
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            val result = bluetoothManager.connectByAddress(device.address)
            
            AppLogger.d(TAG, "connectByAddress result: $result")
            
            result.fold(
                onSuccess = { connection ->
                    // 验证连接：发送 :GVM# 获取版本信息
                    val protocol = LX200Protocol(connection)
                    val versionResult = protocol.sendCommand(":GVM#")
                    
                    versionResult.fold(
                        onSuccess = { version ->
                            mountRepository.setConnection(connection)
                            _isConnected.value = true
                            _uiState.update { 
                                it.copy(
                                    isConnecting = false,
                                    deviceName = device.name,
                                    firmwareVersion = version.trim(),
                                    isWifiConnection = false,
                                    showConnectedToast = true
                                ) 
                            }
                            AppLogger.d(TAG, "Bluetooth connected successfully. Firmware: ${version.trim()}")
                        },
                        onFailure = { e ->
                            connection.close()
                            _uiState.update { 
                                it.copy(isConnecting = false, error = "连接验证失败: ${e.message}") 
                            }
                            AppLogger.e(TAG, "GVM command failed", e)
                        }
                    )
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(isConnecting = false, error = e.message ?: "蓝牙连接失败") 
                    }
                    AppLogger.e(TAG, "Bluetooth connect failed", e)
                }
            ) ?: run {
                _uiState.update { it.copy(isConnecting = false, error = "蓝牙连接不可用") }
            }
        }
    }
    
    fun connectWifi() {
        val host = _uiState.value.host
        val port = _uiState.value.port
        AppLogger.i("UserAction", "WiFi连接尝试: $host:$port")
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            val result = connectionManager.connectWiFi(host, port.toIntOrNull() ?: 9998)
            
            result.fold(
                onSuccess = { connection ->
                    AppLogger.i("UserAction", "WiFi连接成功: $host:$port")
                    try {
                        // connectionManager.connectWiFi 已验证过连接，版本号已存入 state
                        val versionStr = (connectionManager.connectionState.value as? ConnectionState.Connected)?.extraInfo ?: ""
                        mountRepository.setConnection(connection)
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                deviceName = "$host:$port",
                                firmwareVersion = versionStr,
                                isWifiConnection = true,
                                showConnectedToast = true
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "WiFi连接成功但后续处理异常: ${e.message}", e)
                        _uiState.update {
                            it.copy(isConnecting = false, error = "连接后处理异常: ${e.message}")
                        }
                    }
                },
                onFailure = { e ->
                    AppLogger.e("UserAction", "WiFi连接失败: ${e.message}", e)
                    _uiState.update { 
                        it.copy(isConnecting = false, error = e.message ?: "连接失败") 
                    }
                }
            )
        }
    }
    
    /**
     * 连接到配对的蓝牙设备（通过设备列表选择）
     */
    @SuppressLint("MissingPermission")
    fun connectBluetooth(deviceName: String, deviceAddress: String) {
        viewModelScope.launch {
            AppLogger.d(TAG, "connectBluetooth called: $deviceName ($deviceAddress)")
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            val device = getBluetoothDevice(deviceAddress)
            if (device == null) {
                AppLogger.e(TAG, "Device not found: $deviceAddress")
                _uiState.update { it.copy(isConnecting = false, error = "未找到蓝牙设备") }
                return@launch
            }
            
            val result = bluetoothManager.connect(device)
            
            result.fold(
                onSuccess = { connection ->
                    AppLogger.d(TAG, "Bluetooth connection established, verifying with :GVM#")
                    // 验证连接：发送 :GVM# 获取版本信息
                    val protocol = LX200Protocol(connection)
                    val versionResult = protocol.sendCommand(":GVM#")
                    
                    versionResult.fold(
                        onSuccess = { version ->
                            AppLogger.d(TAG, "GVM response: $version")
                            mountRepository.setConnection(connection)
                            _isConnected.value = true
                            _uiState.update { 
                                it.copy(
                                    isConnecting = false,
                                    deviceName = deviceName,
                                    firmwareVersion = version.trim(),
                                    isWifiConnection = false,
                                    showConnectedToast = true
                                ) 
                            }
                        },
                        onFailure = { e ->
                            AppLogger.e(TAG, "GVM command failed", e)
                            connection.close()
                            _uiState.update { 
                                it.copy(isConnecting = false, error = "连接验证失败: ${e.message}") 
                            }
                        }
                    )
                },
                onFailure = { e ->
                    AppLogger.e(TAG, "Bluetooth connect failed", e)
                    _uiState.update { 
                        it.copy(isConnecting = false, error = e.message ?: "蓝牙连接失败") 
                    }
                }
            ) ?: run {
                _uiState.update { it.copy(isConnecting = false, error = "蓝牙连接不可用") }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getBluetoothDevice(address: String): BluetoothDevice? {
        return bluetoothManager.getPairedDevices().find { it.address == address }
    }
    
    fun clearConnectedToast() {
        _uiState.update { it.copy(showConnectedToast = false) }
    }
    
    fun disconnect() {
        AppLogger.i("UserAction", "断开连接")
        viewModelScope.launch {
            connectionManager.disconnect()
            mountRepository.clearConnection()
            _isConnected.value = false
            _uiState.update { it.copy(deviceName = "", firmwareVersion = "", showConnectedToast = false) }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 注意：不再在这里 close() bluetoothManager
        // 因为它是 Hilt 单例，由 Hilt 统一管理生命周期
        // close() 会注销广播接收器和停止扫描，影响其他页面使用
    }
}