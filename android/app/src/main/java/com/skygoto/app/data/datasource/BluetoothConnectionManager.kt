package com.skygoto.app.data.datasource

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
 * 扫描到的蓝牙设备
 */
data class ScannedBluetoothDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean = false,
    val rssi: Int = 0
)

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
    
    // 扫描状态
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    // 扫描到的设备列表
    private val _scannedDevices = MutableStateFlow<List<ScannedBluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBluetoothDevice>> = _scannedDevices
    
    private var broadcastReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null
    
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
    
    fun hasScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_SCAN
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
     * 获取所有已配对设备（作为ScannedBluetoothDevice）
     */
    fun getPairedDevicesAsScanned(): List<ScannedBluetoothDevice> {
        return getPairedDevices().map { device ->
            ScannedBluetoothDevice(
                name = device.name ?: "Unknown",
                address = device.address,
                isPaired = true,
                rssi = 0
            )
        }
    }
    
    /**
     * 开始扫描附近蓝牙设备
     */
    fun startScan() {
        if (!hasScanPermission()) {
            return
        }
        
        if (_isScanning.value) {
            return
        }
        
        // 清除之前的扫描结果
        _scannedDevices.value = getPairedDevicesAsScanned()
        
        // 注册广播接收器
        registerBroadcastReceiver()
        
        // 开始扫描
        try {
            adapter?.startDiscovery()
            _isScanning.value = true
            
            // 设置12秒超时
            scanTimeoutRunnable = Runnable {
                stopScan()
            }
            handler.postDelayed(scanTimeoutRunnable!!, 12000)
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        if (!_isScanning.value && broadcastReceiver == null) {
            return
        }
        
        try {
            adapter?.cancelDiscovery()
        } catch (e: Exception) {
            // 忽略
        }
        
        // 移除超时回调
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
        
        // 注销广播接收器
        unregisterBroadcastReceiver()
        
        _isScanning.value = false
    }
    
    @SuppressLint("MissingPermission")
    private fun registerBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = 
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi: Int = 
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                        
                        device?.let {
                            val scannedDevice = ScannedBluetoothDevice(
                                name = it.name ?: "Unknown",
                                address = it.address,
                                isPaired = it.bondState == BluetoothDevice.BOND_BONDED,
                                rssi = rssi
                            )
                            
                            // 添加到列表（避免重复）
                            val currentList = _scannedDevices.value.toMutableList()
                            val existingIndex = currentList.indexOfFirst { d -> d.address == it.address }
                            if (existingIndex >= 0) {
                                currentList[existingIndex] = scannedDevice
                            } else {
                                currentList.add(scannedDevice)
                            }
                            _scannedDevices.value = currentList
                        }
                    }
                    
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        stopScan()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        context.registerReceiver(broadcastReceiver, filter)
    }
    
    private fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // 忽略未注册的接收器
            }
        }
        broadcastReceiver = null
    }
    
    /**
     * 连接到蓝牙设备
     */
    suspend fun connect(device: BluetoothDevice): Result<ProtocolConnection> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = BluetoothState.Scanning
            
            // 停止扫描
            stopScan()
            
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
     * 通过地址连接到蓝牙设备
     */
    suspend fun connectByAddress(address: String): Result<ProtocolConnection> = withContext(Dispatchers.IO) {
        val device = adapter?.getRemoteDevice(address)
            ?: return@withContext Result.failure(Exception("Device not found"))
        connect(device)
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
        stopScan()
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