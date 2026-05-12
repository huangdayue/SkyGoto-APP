package com.skygoto.app.data.repository

import com.skygoto.app.data.protocol.*
import com.skygoto.app.data.protocol.LX200Commands as Cmd
import com.skygoto.app.domain.model.*
import com.skygoto.app.domain.repository.MountRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MountRepositoryImpl @Inject constructor() : MountRepository {
    
    private var protocol: LX200Protocol? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _mountStatus = MutableStateFlow(MountStatus())
    override val mountStatus: StateFlow<MountStatus> = _mountStatus
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected
    
    private var pollingJob: Job? = null
    
    /**
     * 设置连接（由 ConnectViewModel 调用）
     */
    fun setConnection(connection: ProtocolConnection) {
        // 关闭旧连接
        pollingJob?.cancel()
        protocol?.close()
        
        // 创建新协议实例
        protocol = LX200Protocol(connection)
        _isConnected.value = true
        startPolling()
    }
    
    /**
     * 清除连接
     */
    fun clearConnection() {
        pollingJob?.cancel()
        protocol?.close()
        protocol = null
        _isConnected.value = false
        _mountStatus.value = MountStatus()
    }
    
    override suspend fun connect(host: String, port: Int): Result<Unit> {
        return try {
            val connection = TcpConnection(host, port)
            protocol = LX200Protocol(connection)
            _isConnected.value = true
            startPolling()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect() {
        pollingJob?.cancel()
        protocol?.close()
        protocol = null
        _isConnected.value = false
        _mountStatus.value = MountStatus()
    }
    
    override suspend fun getStatus(): Result<MountStatus> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        
        return try {
            val ra = p.sendCommand(Cmd.GET_RA).getOrNull()?.trim() ?: "--:--:--"
            val dec = p.sendCommand(Cmd.GET_DEC).getOrNull()?.trim() ?: "--:--:--"
            val alt = p.sendCommand(Cmd.GET_ALT).getOrNull()?.trim() ?: "--:--"
            val az = p.sendCommand(Cmd.GET_AZ).getOrNull()?.trim() ?: "---:--"
            
            // 解析跟踪速率
            val trackingRateStr = p.sendCommand(Cmd.GET_TRACKING).getOrNull()?.trim()
            val trackingRate = trackingRateStr?.toDoubleOrNull() ?: 0.0
            
            val status = MountStatus(
                ra = ra,
                dec = dec,
                alt = alt,
                az = az,
                tracking = trackingRate > 0,
                trackingRate = trackingRate
            )
            _mountStatus.value = status
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun startTracking(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return p.sendCommand(Cmd.TRACKING_ON).map { }
    }
    
    override suspend fun stopTracking(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return p.sendCommand(Cmd.TRACKING_OFF).map { }
    }
    
    override suspend fun move(direction: Direction, rate: MoveRate): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        
        // 设置速率
        val rateCmd = when (rate) {
            MoveRate.GUIDE -> Cmd.RATE_GUIDE
            MoveRate.CENTERING -> Cmd.RATE_CENTER
            MoveRate.FIND -> Cmd.RATE_FIND
            MoveRate.FAST -> Cmd.RATE_FAST
            MoveRate.SLEW -> Cmd.RATE_SLEW
        }
        p.sendCommand(rateCmd)
        
        // 发送移动命令
        val moveCmd = when (direction) {
            Direction.NORTH -> Cmd.MOVE_NORTH
            Direction.SOUTH -> Cmd.MOVE_SOUTH
            Direction.EAST -> Cmd.MOVE_EAST
            Direction.WEST -> Cmd.MOVE_WEST
        }
        return p.sendCommand(moveCmd).map { }
    }
    
    override suspend fun stopMove(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return p.sendCommand(Cmd.STOP_ALL).map { }
    }
    
    override suspend fun setTargetAndGoto(ra: String, dec: String): Result<GotoResult> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        
        // 设置目标
        p.sendCommand("${Cmd.SET_TARGET_RA}$ra")
        p.sendCommand("${Cmd.SET_TARGET_DEC}$dec")
        
        // 执行 GOTO
        val response = p.sendCommand(Cmd.GOTO).getOrNull() ?: return Result.failure(Exception("No response"))
        
        // 解析响应：'0' 表示成功，非 '0' 表示错误码
        val code = response.firstOrNull()?.digitToIntOrNull() ?: -1
        return if (code == 0) {
            _mountStatus.value = _mountStatus.value.copy(
                targetRa = ra,
                targetDec = dec,
                slewing = true
            )
            Result.success(GotoResult.Success)
        } else {
            Result.success(GotoResult.Error(code, GotoErrorCodes.getMessage(code)))
        }
    }
    
    override suspend fun cancelGoto(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        // 发送两次 Q# 以中止 GOTO
        p.sendCommand(Cmd.STOP_ALL)
        delay(100)
        return p.sendCommand(Cmd.STOP_ALL).map { }
    }
    
    override suspend fun syncToCurrentPosition(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return p.sendCommand(Cmd.SYNC).map { }
    }
    
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                getStatus()
                delay(1000) // 每秒更新
            }
        }
    }
    
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
