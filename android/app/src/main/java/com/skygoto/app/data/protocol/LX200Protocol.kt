package com.skygoto.app.data.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * LX200 协议通信类
 */
class LX200Protocol(
    private val connection: ProtocolConnection
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val isConnected: Boolean
        get() = connection.isConnected
    
    suspend fun sendCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = connection.sendAndReceive(command)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // 解析 RA: "05:34:32" -> Double (小时)
    fun parseRA(str: String): Double {
        val clean = str.trim().removePrefix("+")
        val parts = clean.split(":")
        if (parts.size < 3) return 0.0
        val h = parts[0].toDoubleOrNull() ?: return 0.0
        val m = parts[1].toDoubleOrNull() ?: return 0.0
        val s = parts[2].toDoubleOrNull() ?: return 0.0
        return h + m / 60.0 + s / 3600.0
    }
    
    // 解析 Dec: "+45*12:34" -> Double (度)
    fun parseDec(str: String): Double {
        val sign = if (str.startsWith('-')) -1.0 else 1.0
        val content = str.trimStart('+', '-').replace("*", ":")
        val parts = content.split(":")
        if (parts.size < 2) return 0.0
        return sign * (
            (parts[0].toDoubleOrNull() ?: 0.0) + 
            (parts[1].toDoubleOrNull() ?: 0.0) / 60.0 +
            (if (parts.size > 2) (parts[2].toDoubleOrNull() ?: 0.0) / 3600.0 else 0.0)
        )
    }
    
    // 格式化 RA 为命令格式
    fun formatRA(raHours: Double): String {
        val h = raHours.toInt()
        val m = ((raHours - h) * 60).toInt()
        val s = ((raHours - h - m / 60.0) * 3600.0).toInt()
        return "%02d:%02d:%02d".format(h, m, s)
    }
    
    // 格式化 Dec 为命令格式
    fun formatDec(decDeg: Double): String {
        val sign = if (decDeg < 0) "-" else "+"
        val abs = kotlin.math.abs(decDeg)
        val d = abs.toInt()
        val m = ((abs - d) * 60).toInt()
        val s = ((abs - d - m / 60.0) * 3600.0).toInt()
        return "%s%02d*%02d:%02d".format(sign, d, m, s)
    }
    
    suspend fun sendCommandNoResponse(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connection.sendCommandNoResponse(command)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendAndReadSingleDigit(command: String): String = withContext(Dispatchers.IO) {
        connection.sendAndReceiveSingleChar(command)
    }
    
    fun close() {
        scope.cancel()
        connection.close()
    }
}

/**
 * 协议连接接口
 */
interface ProtocolConnection {
    suspend fun sendAndReceive(command: String): String
    suspend fun sendCommandNoResponse(command: String)
    suspend fun sendAndReceiveSingleChar(command: String): String
    suspend fun flushInput()
    fun close()
    val isConnected: Boolean
}

/**
 * WiFi TCP 连接实现
 */
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
        flushInput()  // 发送前清空缓冲区
        ensureConnected()
        
        // 发送命令（带 # 结尾）
        val fullCommand = "$command#"
        writer!!.print(fullCommand)
        writer!!.flush()
        
        // 读取响应直到 #
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
    
    override suspend fun sendCommandNoResponse(command: String) {
        flushInput()
        ensureConnected()
        
        val fullCommand = if (command.endsWith("#")) command else "$command#"
        writer!!.print(fullCommand)
        writer!!.flush()
        // 不等待响应，立即返回
    }
    
    override suspend fun sendAndReceiveSingleChar(command: String): String = withContext(Dispatchers.IO) {
        flushInput()
        ensureConnected()
        
        val fullCommand = if (command.endsWith("#")) command else "$command#"
        writer!!.print(fullCommand)
        writer!!.flush()
        
        // 只读第一个字符，1秒超时
        val buffer = CharArray(1)
        val deadline = System.currentTimeMillis() + 1000
        
        while (System.currentTimeMillis() < deadline) {
            val bytesRead = reader!!.read(buffer)
            if (bytesRead == -1) break
            return@withContext buffer[0].toString()
        }
        ""  // 超时返回空
    }
    
    override suspend fun flushInput() {
        // TCP 连接是面向流的，通常不需要清空
    }
    
    private fun ensureConnected() {
        if (!isConnected) {
            socket = Socket()
            socket!!.connect(InetSocketAddress(host, port), connectTimeout)
            socket!!.soTimeout = readTimeout
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), StandardCharsets.UTF_8))
            writer = PrintWriter(socket!!.getOutputStream(), true, StandardCharsets.UTF_8)
        }
    }
    
    override fun close() {
        try { socket?.close() } catch (e: Exception) { }
        reader?.close()
        writer?.close()
    }
}
