# Android 连接实现方案

## 1. 蓝牙串口连接

### 1.1 权限配置

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### 1.2 蓝牙连接流程

```
┌─────────────────────────────────────────────────────┐
│ 1. 检查蓝牙适配器                                     │
│    BluetoothAdapter.getDefaultAdapter()             │
├─────────────────────────────────────────────────────┤
│ 2. 检查蓝牙开启状态                                   │
│    if (!adapter.isEnabled()) → 请求开启              │
├─────────────────────────────────────────────────────┤
│ 3. 获取已配对设备                                    │
│    adapter.bondedDevices                            │
├─────────────────────────────────────────────────────┤
│ 4. 选择设备，建立连接                                │
│    device.createRfcommSocketToServiceRecord(SPP_UUID)│
│    socket.connect()                                 │
├─────────────────────────────────────────────────────┤
│ 5. 获取输入输出流                                    │
│    inputStream = socket.inputStream                  │
│    outputStream = socket.outputStream                │
└─────────────────────────────────────────────────────┘
```

### 1.3 核心代码

```kotlin
class BluetoothConnection(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) {
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    companion object {
        // SPP UUID for serial port profile
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 关闭已有连接
            disconnect()
            
            // 创建 RFCOMM 套接字
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            
            // 获取流
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            
            // 启动读取循环
            startReading()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun startReading() {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (socket?.isConnected == true) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val data = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                        onDataReceived(data)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }
    
    suspend fun send(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 发送命令（带 # 结尾）
            val fullCommand = "$command#"
            outputStream?.write(fullCommand.toByteArray(StandardCharsets.UTF_8))
            outputStream?.flush()
            
            // 读取响应
            Result.success(readResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun readResponse(): String {
        val buffer = StringBuilder()
        val readBuffer = ByteArray(1)
        
        // 设置超时读取
        withTimeoutOrNull(3000) {
            while (true) {
                val bytesRead = inputStream?.read(readBuffer) ?: -1
                if (bytesRead == -1) break
                
                val c = readBuffer[0].toChar()
                if (c == '#') {
                    buffer.append(c)
                    break
                }
                buffer.append(c)
            }
        }
        
        return buffer.toString()
    }
    
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
        inputStream = null
        outputStream = null
    }
}
```

---

## 2. WiFi TCP 连接

### 2.1 权限配置

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2.2 WiFi 连接流程

```
┌─────────────────────────────────────────────────────┐
│ 1. 检查网络连接                                     │
│    ConnectivityManager.activeNetworkInfo            │
├─────────────────────────────────────────────────────┤
│ 2. 建立 TCP Socket 连接                             │
│    Socket(IP, Port)                                │
│    默认: 192.168.1.1:9999 (常见 OnStepX 默认)       │
├─────────────────────────────────────────────────────┤
│ 3. 获取输入输出流                                    │
│    inputStream = socket.getInputStream()            │
│    outputStream = socket.getOutputStream()          │
├─────────────────────────────────────────────────────┤
│ 4. 设置超时                                         │
│    socket.soTimeout = 3000                          │
└─────────────────────────────────────────────────────┘
```

### 2.3 核心代码

```kotlin
class WiFiConnection(
    private val onDataReceived: (String) -> Unit
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    companion object {
        // OnStepX 默认 WiFi 设置
        const val DEFAULT_HOST = "192.168.1.1"
        const val DEFAULT_PORT = 9999
        const val CONNECT_TIMEOUT = 10000  // 10秒
        const val READ_TIMEOUT = 3000      // 3秒
    }
    
    suspend fun connect(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                
                socket = Socket()
                socket?.connect(
                    InetSocketAddress(host, port),
                    CONNECT_TIMEOUT
                )
                
                socket?.soTimeout = READ_TIMEOUT
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                // 启动读取循环
                startReading()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    // ... 其他方法与蓝牙类似
}
```

---

## 3. 统一连接接口

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val type: ConnectionType,
        val deviceName: String
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class ConnectionType {
    BLUETOOTH, WIFI
}

interface MountConnection {
    val state: StateFlow<ConnectionState>
    val isConnected: Boolean
    
    suspend fun connect(): Result<Unit>
    fun disconnect()
    suspend fun sendCommand(command: String): Result<String>
}
```

---

## 4. 心跳与自动重连

```kotlin
class ConnectionManager(
    private val connection: MountConnection
) {
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    // 心跳间隔：5秒
    private val heartbeatInterval = 5000L
    
    // 重连间隔：3秒
    private val reconnectDelay = 3000L
    
    // 最大重连次数：无限
    private val maxReconnectAttempts = -1
    
    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(heartbeatInterval)
                if (connection.isConnected) {
                    // 发送心跳命令
                    connection.sendCommand("GS")
                }
            }
        }
    }
    
    fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            while (attempts != maxReconnectAttempts) {
                delay(reconnectDelay)
                
                if (!connection.isConnected) {
                    connection.connect()
                    attempts++
                } else {
                    attempts = 0
                }
            }
        }
    }
    
    fun stop() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
    }
}
```

---

## 5. 关键参数总结

| 参数 | 值 | 说明 |
|------|-----|------|
| 蓝牙 UUID | 00001101-0000-1000-8000-00805F9B34FB | SPP 协议 |
| WiFi 默认 IP | 192.168.1.1 | OnStepX 默认 |
| WiFi 默认端口 | 9999 | OnStepX 默认 |
| 连接超时 | 10 秒 | connect() 超时 |
| 读取超时 | 3 秒 | 响应等待超时 |
| 心跳间隔 | 5 秒 | 保持连接 |
| 重连延迟 | 3 秒 | 断开后重连 |

---

## 6. 坐标系转换

```kotlin
// RA: "05:34:32" -> Double (小时)
fun parseRA(str: String): Double {
    val parts = str.trimStart('+', '-').split(":")
    return parts[0].toDouble() + parts[1].toDouble() / 60 + parts[2].toDouble() / 3600
}

// Dec: "+45*12:34" -> Double (度)
fun parseDec(str: String): Double {
    val sign = if (str.startsWith('-')) -1.0 else 1.0
    val content = str.trimStart('+', '-')
    val parts = content.replace("*", ":").split(":")
    return sign * (parts[0].toDouble() + parts[1].toDouble() / 60 + parts[2].toDouble() / 3600)
}

// 格式化为 LX200 命令格式
fun formatRA(raHours: Double): String {
    val h = (raHours).toInt()
    val m = ((raHours - h) * 60).toInt()
    val s = ((raHours - h - m / 60.0) * 3600).toInt()
    return "%02d:%02d:%02d".format(h, m, s)
}

fun formatDec(decDeg: Double): String {
    val sign = if (decDeg < 0) "-" else "+"
    val d = decDeg.absoluteValue.toInt()
    val m = ((decDeg.absoluteValue - d) * 60).toInt()
    val s = ((decDeg.absoluteValue - d - m / 60.0) * 3600).toInt()
    return "%s%02d*%02d:%02d".format(sign, d, m, s)
}
```

---

**文档版本：** 1.0  
**创建日期：** 2026-05-12