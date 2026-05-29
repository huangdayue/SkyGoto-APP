package com.skygoto.app.data.datasource

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
        
        // 仅在首次扫描时设置已配对设备列表，后续扫描不覆盖已有列表
        if (_scannedDevices.value.isEmpty()) {
            _scannedDevices.value = getPairedDevicesAsScanned()
        }
        
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
                        // API 33+ 使用新 API，API < 33 使用旧 API
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        }
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
        
        // 修复: Android 14+ 要求 registerReceiver 必须指定导出行为标志
        // 使用 RECEIVER_EXPORTED 因为我们需要接收系统广播
        if (Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, filter)
        }
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
 *
 * 修复说明（2026-05-28）：
 * - 不再使用 BufferedReader/withTimeoutOrNull 的组合
 *   （withTimeoutOrNull 无法中断 Java 阻塞 I/O 的 reader.read()）
 * - 改用 InputStream.available() + delay() 轮询模式，
 *   确保协程可以被正常取消，避免 commandMutex 被永久锁死
 * - 使用 wall-clock 时间作为超时后备，防止无限等待
 */
@SuppressLint("MissingPermission")
private class BluetoothConnection(
    private val socket: android.bluetooth.BluetoothSocket
) : ProtocolConnection {

    companion object {
        private const val READ_TIMEOUT_MS = 3000L
        private const val SINGLE_CHAR_TIMEOUT_MS = 1000L
        private const val POLL_DELAY_MS = 30L  // 轮询间隔，平衡响应速度与 CPU 占用
    }

    // 不使用 BufferedReader，直接用 InputStream 避免内部缓冲
    private val inputStream = socket.inputStream
    private val writer = socket.outputStream.bufferedWriter()

    override val isConnected: Boolean
        get() = socket.isConnected

    override suspend fun flushInput() {
        // 清除输入流中可能残留的旧数据
        try {
            val available = inputStream.available()
            if (available > 0) {
                val discard = ByteArray(available)
                inputStream.read(discard)
            }
        } catch (_: Exception) {
            // 忽略清理错误
        }
    }

    override suspend fun sendAndReceive(command: String): String = withContext(Dispatchers.IO) {
        if (!socket.isConnected) throw IOException("Socket not connected")

        // 清空可能残留的旧数据
        flushInput()

        // 发送命令（带 # 结尾）
        val fullCommand = "$command#"
        writer.write(fullCommand)
        writer.flush()

        // 读取响应直到 #
        // 使用 available() + delay() 轮询替代阻塞 read()，
        // 确保 withTimeoutOrNull 可以真正取消协程（delay 是挂起点）
        val response = StringBuilder()
        val byteBuf = ByteArray(1)
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS

        val timedOut = withTimeoutOrNull(READ_TIMEOUT_MS) {
            while (isActive && System.currentTimeMillis() < deadline) {
                val available = try {
                    inputStream.available()
                } catch (e: IOException) {
                    break  // 连接已断开
                }

                if (available > 0) {
                    try {
                        val bytesRead = inputStream.read(byteBuf)
                        if (bytesRead == -1) break
                        val c = (byteBuf[0].toInt() and 0xFF).toChar()
                        if (c == '#') break
                        response.append(c)
                    } catch (e: IOException) {
                        break
                    }
                } else {
                    // 没有可用数据时短暂休眠，让出 CPU 并允许协程取消
                    delay(POLL_DELAY_MS)
                }
            }
        }

        // 超时返回已读取的部分（通常为空）
        response.toString()
    }

    override suspend fun sendCommandNoResponse(command: String) {
        if (!socket.isConnected) return
        val fullCommand = if (command.endsWith("#")) command else "$command#"
        writer.write(fullCommand)
        writer.flush()
    }

    override suspend fun sendAndReceiveSingleChar(command: String): String = withContext(Dispatchers.IO) {
        if (!socket.isConnected) return@withContext ""
        
        // 清空可能残留的旧数据
        flushInput()
        
        val fullCommand = "$command#"
        writer.write(fullCommand)
        writer.flush()

        val byteBuf = ByteArray(1)
        val deadline = System.currentTimeMillis() + SINGLE_CHAR_TIMEOUT_MS

        withTimeoutOrNull(SINGLE_CHAR_TIMEOUT_MS) {
            while (isActive && System.currentTimeMillis() < deadline) {
                val available = try {
                    inputStream.available()
                } catch (e: IOException) {
                    break
                }

                if (available > 0) {
                    try {
                        val bytesRead = inputStream.read(byteBuf)
                        if (bytesRead == -1) break
                        // 成功读取到数据，返回
                        val result = (byteBuf[0].toInt() and 0xFF).toChar().toString()
                        return@withTimeoutOrNull result
                    } catch (e: IOException) {
                        break
                    }
                } else {
                    delay(POLL_DELAY_MS)
                }
            }
            // 超时或连接断开
            ""
        } ?: ""  // withTimeoutOrNull 返回 null 表示超时
    }

    /**
     * 非阻塞读取当前缓冲区所有可用字节
     * 0 等待时间 — 有数据就返回，没数据立即返回空串
     * ViewModel 层负责解析 # 终止符和累积逻辑
     */
    override suspend fun readAvailableBytes(): String = withContext(Dispatchers.IO) {
        if (!socket.isConnected) return@withContext ""

        try {
            val available = inputStream.available()
            if (available <= 0) return@withContext ""

            val buffer = ByteArray(available)
            val bytesRead = inputStream.read(buffer)
            if (bytesRead <= 0) return@withContext ""

            // 返回原始字节串（包含 #、换行等所有字符）
            String(buffer, 0, bytesRead, Charsets.US_ASCII)
        } catch (e: IOException) {
            ""
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            // 忽略关闭错误
        }
    }
}