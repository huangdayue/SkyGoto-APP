package com.skygoto.app.ui.screens.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.data.protocol.LX200Protocol
import com.skygoto.app.data.protocol.ProtocolConnection
import com.skygoto.app.data.repository.MountRepositoryImpl
import com.skygoto.app.domain.repository.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class ConnectUiState(
    val host: String = "192.168.1.1",
    val port: String = "9999",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val pairedDevices: List<Pair<String, String>> = emptyList(), // name, address
    val isWifiConnection: Boolean = true,
    val deviceName: String = ""
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val mountRepository: MountRepositoryImpl,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionType = MutableStateFlow(ConnectionTabType.WIFI)
    val connectionType: StateFlow<ConnectionTabType> = _connectionType.asStateFlow()
    
    private var bluetoothManager: BluetoothConnectionManager? = null
    
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
        
        // 初始化蓝牙管理器
        initBluetooth()
    }
    
    @SuppressLint("MissingPermission")
    private fun initBluetooth() {
        bluetoothManager = BluetoothConnectionManager(context)
        refreshBluetoothDevices()
    }
    
    fun selectConnectionType(type: ConnectionTabType) {
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
        val devices = bluetoothManager?.getPairedDevices()?.map { device ->
            (device.name ?: "Unknown") to device.address
        } ?: emptyList()
        _uiState.update { it.copy(pairedDevices = devices) }
    }
    
    fun connectWifi() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            val host = _uiState.value.host
            val port = _uiState.value.port.toIntOrNull() ?: 9999
            
            val result = connectionManager.connectWiFi(host, port)
            
            result.fold(
                onSuccess = { connection ->
                    mountRepository.setConnection(connection)
                    _uiState.update { 
                        it.copy(
                            isConnecting = false, 
                            deviceName = "$host:$port",
                            isWifiConnection = true
                        ) 
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(isConnecting = false, error = e.message ?: "连接失败") 
                    }
                }
            )
        }
    }
    
    fun connectBluetooth(deviceName: String, deviceAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            val device = getBluetoothDevice(deviceAddress)
            if (device == null) {
                _uiState.update { it.copy(isConnecting = false, error = "未找到蓝牙设备") }
                return@launch
            }
            
            val result = bluetoothManager?.connect(device)
            
            result?.fold(
            
            result?.fold(
                onSuccess = { connection ->
                    mountRepository.setConnection(connection)
                    _uiState.update { 
                        it.copy(
                            isConnecting = false,
                            deviceName = deviceName,
                            isWifiConnection = false
                        ) 
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(isConnecting = false, error = e.message ?: "蓝牙连接失败") 
                    }
                }
            ) ?: _uiState.update { 
                it.copy(isConnecting = false, error = "蓝牙连接不可用") 
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getBluetoothDevice(address: String): BluetoothDevice? {
        return bluetoothManager?.getPairedDevices()?.find { it.address == address }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect()
            mountRepository.clearConnection()
            _isConnected.value = false
            _uiState.update { it.copy(deviceName = "") }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        bluetoothManager?.close()
    }
}
