package com.skygoto.app.domain.repository

import android.util.Log
import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.data.protocol.LX200Protocol
import com.skygoto.app.data.protocol.ProtocolConnection
import com.skygoto.app.data.protocol.TcpConnection
import com.skygoto.app.domain.model.ConnectionState
import com.skygoto.app.domain.model.ConnectionType
import com.skygoto.app.util.AppLogger
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
class ConnectionManager @Inject constructor(
    private val bluetoothManager: BluetoothConnectionManager
) {

    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var currentConnection: ProtocolConnection? = null
    
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
            
            // 验证连接：尝试获取版本信息
            val protocol = LX200Protocol(connection)
            val versionResult = protocol.sendCommand(":GVP#")
            
            versionResult.fold(
                onSuccess = { version ->
                    _connectionState.value = ConnectionState.Connected(
                        type = ConnectionType.WIFI,
                        deviceName = "$host:$port",
                        extraInfo = version.take(32)
                    )
                    Result.success(connection)
                },
                onFailure = { e ->
                    connection.close()
                    currentConnection = null
                    _connectionState.value = ConnectionState.Error("连接验证失败: ${e.message}")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            currentConnection = null
            Result.failure(e)
        }
    }
    
    /**
     * 获取已配对的蓝牙设备
     */
    fun getPairedBluetoothDevices(): List<PairedBluetoothDevice> {
        return bluetoothManager.getPairedDevices().map { device ->
            PairedBluetoothDevice(
                name = device.name ?: "Unknown",
                address = device.address
            )
        }
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
            
            // 验证连接：尝试获取版本信息
            val protocol = LX200Protocol(connection)
            val versionResult = protocol.sendCommand(":GVP#")
            
            versionResult.fold(
                onSuccess = { version ->
                    _connectionState.value = ConnectionState.Connected(
                        type = ConnectionType.BLUETOOTH,
                        deviceName = deviceName,
                        deviceAddress = deviceAddress,
                        extraInfo = version.take(32)
                    )
                    Result.success(connection)
                },
                onFailure = { e ->
                    connection.close()
                    currentConnection = null
                    _connectionState.value = ConnectionState.Error("连接验证失败: ${e.message}")
                    Result.failure(e)
                }
            )
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
        bluetoothManager.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 通知连接已断开（由 MountRepository 在检测到连接失效时调用）
     *
     * 当心跳检测到连接已断开时，需要同步更新 ConnectionManager 的状态，
     * 以确保 ConnectScreen 和 ControlScreen 的连接状态保持一致。
     */
    fun notifyDisconnected() {
        AppLogger.e(TAG, "[CONNECTION_MANAGER] notifyDisconnected: 开始处理断开通知")
        currentConnection?.close()
        currentConnection = null
        bluetoothManager.disconnect()
        AppLogger.e(TAG, "[CONNECTION_MANAGER] notifyDisconnected: 即将设置 _connectionState = Disconnected")
        _connectionState.value = ConnectionState.Disconnected
        AppLogger.e(TAG, "[CONNECTION_MANAGER] notifyDisconnected: _connectionState 已设为 Disconnected")
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
