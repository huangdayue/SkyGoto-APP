# Android 连接实现方案

> **文档版本：** 2.0（更新于 2026-05-21）

## 概述

SkyGoto APP 支持两种连接赤道仪的方式：蓝牙 RFCOMM 串口和 WiFi TCP Socket。两种方式共用同一套 `LX200Protocol` 协议层，通过 `ProtocolConnection` 接口统一抽象。

---

## 1. 架构概览

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│              ConnectScreen.kt                    │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              ConnectionManager.kt                │
│         (管理连接状态、轮询、心跳)                │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│            BluetoothConnectionManager.kt          │
│  BluetoothClient / TcpConnection / ProtocolConnection │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              LX200Protocol.kt                     │
│         (LX200 命令编解码、响应解析)               │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              MountRepositoryImpl.kt               │
│    (赤道仪操作：GOTO/追踪/移动/状态查询)           │
└─────────────────────────────────────────────────┘
```

---

## 2. ProtocolConnection 接口

```kotlin
interface ProtocolConnection {
    val isConnected: Boolean
    suspend fun sendAndReceive(command: String): String
    suspend fun sendCommandNoResponse(command: String)
    suspend fun sendAndReceiveSingleChar(command: String): String
    suspend fun flushInput()
    fun close()
}
```

所有连接实现（蓝牙、TCP）都实现此接口，`LX200Protocol` 不关心底层连接方式。

---

## 3. 蓝牙串口连接

### 3.1 权限配置

```xml
<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Android 11 及以下 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### 3.2 蓝牙连接流程

```
┌──────────────────────────────────────────────────┐
│ 1. BluetoothAdapter.getDefaultAdapter()           │
│    → 检查蓝牙适配器是否可用                         │
├──────────────────────────────────────────────────┤
│ 2. !adapter.isEnabled() → 请求开启蓝牙              │
├──────────────────────────────────────────────────┤
│ 3. adapter.bondedDevices → 获取已配对设备列表        │
├──────────────────────────────────────────────────┤
│ 4. device.createRfcommSocketToServiceRecord(SPP)  │
│    socket.connect() → 建立 RFCOMM 连接             │
├──────────────────────────────────────────────────┤
│ 5. 获取 inputStream / outputStream                 │
│    → 启动数据读写                                 │
└──────────────────────────────────────────────────┘
```

### 3.3 BluetoothConnection 内部类

```kotlin
private class BluetoothConnection(
    private var socket: android.bluetooth.BluetoothSocket
) : ProtocolConnection {
    
    private val reader = socket.inputStream.bufferedReader()
    private val writer = socket.outputStream.bufferedWriter()
    
    override val isConnected: Boolean
        get() = socket.isConnected
    
    override suspend fun sendAndReceive(command: String): String = withContext(Dispatchers.IO) {
        if (!socket.isConnected) throw IOException("Socket not connected")
        
        val fullCommand = "$command#"
        writer.write(fullCommand)
        writer.flush()
        
        val response = StringBuilder()
        val buffer = CharArray(1)
        
        try {
            while (true) {
                val bytesRead = reader.read(buffer)
                if (bytesRead == -1) break
                if (buffer[0] == '#') break
                response.append(buffer[0])
            }
        } catch (e: Exception) { }
        
        response.toString()
    }
    
    override fun close() {
        try { socket?.close() } catch (e: IOException) { }
    }
    
    // ... flushInput / sendCommandNoResponse / sendAndReceiveSingleChar
}
```

### 3.4 SPP UUID

```kotlin
private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
```

---

## 4. WiFi TCP 连接

### 4.1 权限配置

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 4.2 TcpConnection 内部类

```kotlin
class TcpConnection(
    private val host: String = "192.168.1.1",
    private val port: Int = 9999,
    private val connectTimeout: Int = 10000,
    private val readTimeout: Int = 3000
) : ProtocolConnection {
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    
    override val isConnected: Boolean
        get() = socket?.isConnected == true && !socket!!.isClosed
    
    override suspend fun sendAndReceive(command: String): String = withContext(Dispatchers.IO) {
        flushInput()
        ensureConnected()
        
        val fullCommand = "$command#"
        writer!!.print(fullCommand)
        writer!!.flush()
        
        val response = StringBuilder()
        val buffer = CharArray(1)
        
        withTimeoutOrNull(readTimeout.toLong()) {
            while (true) {
                val bytesRead = reader!!.read(buffer)
                if (bytesRead == -1) break
                if (buffer[0] == '#') {
                    response.append('#')
                    break
                }
                response.append(buffer[0])
            }
        }
        
        response.toString()
    }
    
    // ... 其他方法
}
```

### 4.3 默认连接参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 默认 IP | `192.168.1.1` | OnStepX WiFi 默认地址 |
| 默认端口 | `9999` | OnStepX TCP 串口默认端口 |
| 连接超时 | 10000 ms | connect() 超时 |
| 读取超时 | 3000 ms | 响应等待超时 |

---

## 5. ConnectionManager 状态管理

```kotlin
class ConnectionManager private constructor(
    private val connection: ProtocolConnection
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var pollingJob: Job? = null  // 状态轮询
    private var heartbeatJob: Job? = null // 心跳保活
    
    fun startPolling(intervalMs: Long = 1000) { }
    fun stopPolling() { }
    fun pausePolling() { }
    fun resumePolling() { }
    
    private fun notifyDisconnected() { }
    private fun notifyConnected() { }
}
```

**状态流转：**
```
Disconnected → Connecting → Connected → Error → Disconnected
```

---

## 6. 心跳与自动重连

### 6.1 心跳机制

轮询间隔 1000ms，每次轮询 `:GR#` 和 `:GD#` 获取当前位置，同时检测连接是否存活。

### 6.2 断开通知

当 `socket.isConnected` 返回 `false` 时，触发 `notifyDisconnected()`，通知 UI 更新状态并显示重连提示。

---

## 7. 坐标解析工具

```kotlin
// String.clean() - 清理不可见字符
private fun String.clean(): String {
    return this.replace("\r", "").replace("\n", "").replace(" ", "")
        .filter { it.isDigit() || it == ':' || it == '*' || it == '+' || it == '-' }
}

// parseRA - 解析赤经
private fun parseRA(ra: String): Double? {
    val clean = ra.clean()
    val match = Regex("""([+-]?\d+):(\d+):(\d+)""").find(clean) ?: return null
    val (h, m, s) = match.destructured
    return h.toDouble() + m.toDouble() / 60 + s.toDouble() / 3600
}

// parseDec - 解析赤纬（支持 : 或 * 分隔秒位）
private fun parseDec(dec: String): Double? {
    val clean = dec.clean().replace("*", ":")
    val match = Regex("""([+-]\d+):(\d+):(\d+)""").find(clean) ?: return null
    val (d, m, s) = match.destructured
    val sign = if (dec.startsWith('-')) -1.0 else 1.0
    return sign * (d.toDouble() + m.toDouble() / 60 + s.toDouble() / 3600)
}
```

---

## 8. 关键参数总结

| 参数 | 蓝牙 | WiFi |
|------|------|------|
| UUID | `00001101-...-00805F9B34FB`（SPP）| - |
| 默认 IP | - | `192.168.1.1` |
| 默认端口 | - | `9999` |
| 连接超时 | 系统默认 | 10 秒 |
| 读取超时 | 3 秒 | 3 秒 |
| 轮询间隔 | 1000 ms | 1000 ms |