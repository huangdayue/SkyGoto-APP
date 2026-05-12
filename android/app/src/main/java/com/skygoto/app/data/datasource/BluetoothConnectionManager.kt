package com.skygoto.app.data.datasource

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.skygoto.app.data.protocol.ProtocolConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.UUID

/**
 * 蓝牙串口服务 UUID (SPP - Serial Port Profile)
 */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * 蓝牙连接管理器
 */
@SuppressLint("MissingPermission")
class BluetoothConnectionManager(
    private val context: Context
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private var socket: android.bluetooth.BluetoothSocket? = null
    private var connection: BluetoothConnection? = null
    
    private val _connectionState = MutableStateFlow<BluetoothState>(BluetoothState.Disconnected)
    val connectionState: StateFlow<BluetoothState> = _connectionState
    
    sealed class BluetoothState {
        object Disconnected : BluetoothState()
        object Scanning : BluetoothState()
        data class Connected(val deviceName: String) : BluetoothState()
        data class Error(val message: String) : BluetoothState()
    }
    
    fun isBluetoothAvailable(): Boolean = adapter != null
    
    fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取已配对的设备列表
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
    
    /**
     * 连接到蓝牙设备
     */
    suspend fun connect(device: BluetoothDevice): Result<ProtocolConnection> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = BluetoothState.Scanning
            
            // 关闭已有连接
            disconnect()
            
            // 创建 RFCOMM 套接字
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            // 取消设备发现以提高连接速度
            adapter?.cancelDiscovery()
            
            // 连接到设备
            socket?.connect()
            
            connection = BluetoothConnection(socket!!)
            _connectionState.value = BluetoothState.Connected(device.name ?: "Unknown")
            
            Result.success(connection!!)
        } catch (e: Exception) {
            _connectionState.value = BluetoothState.Error(e.message ?: "Connection failed")
            disconnect()
            Result.failure(e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // 忽略关闭错误
        }
        socket = null
        connection = null
        _connectionState.value = BluetoothState.Disconnected
    }
    
    fun close() {
        disconnect()
    }
}

/**
 * 蓝牙协议连接实现
 */
@SuppressLint("MissingPermission")
private class BluetoothConnection(
    private val socket: android.bluetooth.BluetoothSocket
) : ProtocolConnection {
    
    private val reader = socket.inputStream.bufferedReader()
    private val writer = socket.outputStream.bufferedWriter()
    
    override val isConnected: Boolean
        get() = socket.isConnected
    
    override suspend fun sendAndReceive(command: String): String = withContext(Dispatchers.IO) {
        if (!socket.isConnected) throw IOException("Socket not connected")
        
        // 发送命令（带 # 结尾）
        val fullCommand = "$command#"
        writer.write(fullCommand)
        writer.flush()
        
        // 读取响应直到 #
        val response = StringBuilder()
        val buffer = CharArray(1)
        
        try {
            while (true) {
                val bytesRead = reader.read(buffer)
                if (bytesRead == -1) break
                if (buffer[0] == '#') {
                    break
                }
                response.append(buffer[0])
            }
        } catch (e: Exception) {
            // 读取超时或错误
        }
        
        response.toString()
    }
    
    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            // 忽略关闭错误
        }
    }
}
