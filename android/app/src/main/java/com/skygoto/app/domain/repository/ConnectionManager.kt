package com.skygoto.app.domain.repository

import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.data.protocol.ProtocolConnection
import com.skygoto.app.data.protocol.TcpConnection
import com.skygoto.app.domain.model.ConnectionState
import com.skygoto.app.domain.model.ConnectionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一连接管理器
 * 同时支持 WiFi (TCP) 和蓝牙 (RFCOMM) 连接
 */
@Singleton
class ConnectionManager @Inject constructor() {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private var currentConnection: ProtocolConnection? = null
    private var bluetoothManager: BluetoothConnectionManager? = null
    
    val isConnected: Boolean
        get() = currentConnection?.isConnected == true
    
    /**
     * WiFi TCP 连接
     */
    suspend fun connectWiFi(host: String, port: Int): Result<ProtocolConnection> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            val connection = TcpConnection(host, port)
            currentConnection = connection
            
            _connectionState.value = ConnectionState.Connected(
                type = ConnectionType.WIFI,
                deviceName = "$host:$port"
            )
            
            Result.success(connection)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            currentConnection = null
            Result.failure(e)
        }
    }
    
    /**
     * 初始化蓝牙管理器
     */
    fun initBluetooth(manager: BluetoothConnectionManager) {
        bluetoothManager = manager
    }
    
    /**
     * 获取已配对的蓝牙设备
     */
    fun getPairedBluetoothDevices(): List<PairedBluetoothDevice> {
        return bluetoothManager?.getPairedDevices()?.map { device ->
            PairedBluetoothDevice(
                name = device.name ?: "Unknown",
                address = device.address
            )
        } ?: emptyList()
    }
    
    /**
     * 蓝牙连接
     */
    suspend fun connectBluetooth(
        deviceName: String,
        deviceAddress: String,
        socketCreator: (String) -> ProtocolConnection?
    ): Result<ProtocolConnection> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            val connection = socketCreator(deviceAddress)
                ?: throw Exception("Failed to create connection")
            
            currentConnection = connection
            
            _connectionState.value = ConnectionState.Connected(
                type = ConnectionType.BLUETOOTH,
                deviceName = deviceName,
                deviceAddress = deviceAddress
            )
            
            Result.success(connection)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            currentConnection = null
            Result.failure(e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        currentConnection?.close()
        currentConnection = null
        bluetoothManager?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * 获取当前连接
     */
    fun getCurrentConnection(): ProtocolConnection? = currentConnection
}

data class PairedBluetoothDevice(
    val name: String,
    val address: String
)
