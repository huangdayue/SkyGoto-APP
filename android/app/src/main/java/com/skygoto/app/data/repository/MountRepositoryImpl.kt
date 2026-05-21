package com.skygoto.app.data.repository

import com.skygoto.app.data.protocol.*
import com.skygoto.app.data.protocol.LX200Commands as Cmd
import com.skygoto.app.domain.model.*
import com.skygoto.app.domain.repository.ConnectionManager
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MountRepositoryImpl @Inject constructor(
    private val connectionManager: ConnectionManager
) : MountRepository {
    
    private var protocol: LX200Protocol? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _mountStatus = MutableStateFlow(MountStatus())
    override val mountStatus: StateFlow<MountStatus> = _mountStatus
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected
    
    private var pollingJob: Job? = null
    
    // 命令互斥锁：防止 polling 和 GOTO 命令同时发送导致响应交织
    private val commandMutex = Mutex()
    
    private val TAG = "MountRepository"
    
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
        // ❌ 不在这里启动 Polling，由 ControlScreen 的 lifecycle 控制
    }
    
    /**
     * 清除连接
     *
     * 修复: 添加 connectionManager.notifyDisconnected() 确保 ConnectionManager
     * 的 connectionState 也更新为 Disconnected，这样 ConnectScreen 的 UI
     * 状态指示器才能正确显示已断开。
     *
     * 之前 getStatus() 中已正确调用 notifyDisconnected()，但 clearConnection() 遗漏了。
     */
    fun clearConnection() {
        pollingJob?.cancel()
        protocol?.close()
        protocol = null
        _isConnected.value = false
        _mountStatus.value = MountStatus()
        connectionManager.notifyDisconnected()
    }
    
    override suspend fun connect(host: String, port: Int): Result<Unit> {
        return try {
            val connection = TcpConnection(host, port)
            protocol = LX200Protocol(connection)
            _isConnected.value = true
            // ❌ 不在这里启动 Polling，由 ControlScreen 的 lifecycle 控制
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
        connectionManager.notifyDisconnected()
    }
    
    override suspend fun getStatus(): Result<MountStatus> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            // 检查连接状态，如果物理连接已断开，立即处理
            if (!p.isConnected) {
                _isConnected.value = false
                pollingJob?.cancel()
                connectionManager.notifyDisconnected()
                return@withLock Result.failure(Exception("Connection lost"))
            }
            
            return@withLock try {
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
    }
    
    override suspend fun startTracking(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :Te 返回单个字符 '1'（成功）或 '0'（失败），不带 #
            val response = p.sendAndReadSingleDigit(Cmd.TRACKING_ON)
            AppLogger.w(TAG, "startTracking response: '$response'")
            return@withLock if (response == "1") {
                Result.success(Unit)
            } else {
                AppLogger.w(TAG, "startTracking failed, response: '$response'")
                Result.failure(Exception("开启追踪失败"))
            }
        }
    }
    
    override suspend fun stopTracking(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :Td 返回单个字符 '1'（成功）或 '0'（失败），不带 #
            val response = p.sendAndReadSingleDigit(Cmd.TRACKING_OFF)
            AppLogger.w(TAG, "stopTracking response: '$response'")
            return@withLock if (response == "1") {
                Result.success(Unit)
            } else {
                AppLogger.w(TAG, "stopTracking failed, response: '$response'")
                Result.failure(Exception("关闭追踪失败"))
            }
        }
    }
    
    override suspend fun move(direction: Direction): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            // 发送移动命令（速率已通过 setMoveRate 设置）
            val moveCmd = when (direction) {
                Direction.NORTH -> Cmd.MOVE_NORTH
                Direction.SOUTH -> Cmd.MOVE_SOUTH
                Direction.EAST -> Cmd.MOVE_EAST
                Direction.WEST -> Cmd.MOVE_WEST
            }
            // 移动命令无响应，立即返回
            p.sendCommandNoResponse(moveCmd)
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun setMoveRate(rate: MoveRate): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            // 设置速率
            val rateCmd = when (rate) {
                MoveRate.GUIDE -> Cmd.RATE_GUIDE
                MoveRate.CENTERING -> Cmd.RATE_CENTER
                MoveRate.FIND -> Cmd.RATE_FIND
                MoveRate.FAST -> Cmd.RATE_FAST
                MoveRate.SLEW -> Cmd.RATE_SLEW
            }
            // 速率命令无响应，立即返回
            p.sendCommandNoResponse(rateCmd)
            AppLogger.w(TAG, "setMoveRate: $rateCmd")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun stopMove(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :Q# 无响应，立即返回
            p.sendCommandNoResponse(Cmd.STOP_ALL)
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun setTargetAndGoto(ra: String, dec: String): Result<GotoResult> {
        AppLogger.w(TAG, "setTargetAndGoto: RA=$ra, Dec=$dec")
        
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            try {
                // 设置目标赤经 :Sr 返回单个字符 '1'（成功）或 '0'（失败），不带 #
                AppLogger.w(TAG, "Sending SET_TARGET_RA: :Sr$ra#")
                val raResponse = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_RA}$ra")
                AppLogger.w(TAG, "SET_TARGET_RA response: '$raResponse'")
                
                // 设置目标赤纬 :Sd 返回单个字符 '1'（成功）或 '0'（失败），不带 #
                AppLogger.w(TAG, "Sending SET_TARGET_DEC: :Sd$dec#")
                val decResponse = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_DEC}$dec")
                AppLogger.w(TAG, "SET_TARGET_DEC response: '$decResponse'")
                
                // 执行 GOTO（:MS 返回单个字符响应码，suppressFrame=true 不发送 #）
                AppLogger.w(TAG, "Sending GOTO_TARGET: ${Cmd.GOTO_TARGET}#")
                val response = p.sendAndReadSingleDigit(Cmd.GOTO_TARGET)
                AppLogger.w(TAG, "GOTO_TARGET response: '$response'")
                
                if (response.isEmpty()) {
                    return@withLock Result.failure(Exception("GOTO 命令超时"))
                }
                
                val firstDigit = response.firstNotNullOfOrNull { it.digitToIntOrNull() } ?: -1
                AppLogger.w(TAG, "GOTO response code: $firstDigit (full response: '$response')")
                
                if (firstDigit == 0) {
                    _mountStatus.value = _mountStatus.value.copy(
                        targetRa = ra,
                        targetDec = dec,
                        slewing = true
                    )
                    return@withLock Result.success(GotoResult.Success)
                } else {
                    return@withLock Result.success(GotoError(firstDigit, GotoErrorCodes.getMessage(firstDigit)))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "setTargetAndGoto exception: ${e.message}")
                return@withLock Result.failure(e)
            }
        }
    }
    
    override suspend fun cancelGoto(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :Q# 无响应，立即返回
            p.sendCommandNoResponse(Cmd.STOP_ALL)
            AppLogger.w(TAG, "cancelGoto STOP_ALL sent")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun syncToCurrentPosition(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :CM# 返回 "N/A#" 成功 或 "E0#" 等失败，带 # 结束符
            val response = p.sendCommand(Cmd.SYNC_TO_CATALOG).getOrNull()?.trim() ?: ""
            AppLogger.w(TAG, "syncToCurrentPosition response: '$response'")
            return@withLock if (response.startsWith("N/A")) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("同步失败: $response"))
            }
        }
    }
    
    override suspend fun home(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :hC# 无响应，立即返回
            p.sendCommandNoResponse(Cmd.HOME_GOTO)
            AppLogger.w(TAG, "home HOME_GOTO sent")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun setZeroPosition(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :hF# 无响应，立即返回
            p.sendCommandNoResponse(Cmd.HOME_RESET)
            AppLogger.w(TAG, "setZeroPosition HOME_RESET sent")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun setLocation(longitude: String, latitude: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            // 将 DMS 格式转换为 OnStepX 格式
            val latOnStep = dmsToOnStepFormat(latitude, isLatitude = true)
            val lonOnStep = dmsToOnStepFormat(longitude, isLatitude = false)
            
            // dmsToOnStepFormat 已经正确处理了 E/W → +/- 转换
            // (E→+, W→-), OnStepX 接收后按 West-positive 解释
            
            // 发送位置到赤道仪（:St 和 :Sg 返回 '1' 成功，'0' 失败）
            val latResponse = p.sendAndReadSingleDigit("${Cmd.SET_LATITUDE}$latOnStep")
            val lonResponse = p.sendAndReadSingleDigit("${Cmd.SET_LONGITUDE}$lonOnStep")
            AppLogger.w(TAG, "SET_LATITUDE response: '$latResponse', SET_LONGITUDE response: '$lonResponse'")
            
            return@withLock if (latResponse == "1" && lonResponse == "1") {
                Result.success(Unit)
            } else {
                Result.failure(Exception("设置位置失败 (lat='$latResponse', lon='$lonResponse')"))
            }
        }
    }
    
    override suspend fun getLocation(): Result<Pair<String, String>> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                val latResponse = p.sendCommand(Cmd.GET_LATITUDE).getOrNull()?.trim() ?: ""
                val lonResponse = p.sendCommand(Cmd.GET_LONGITUDE).getOrNull()?.trim() ?: ""
                AppLogger.w(TAG, "getLocation: lat='$latResponse', lon='$lonResponse'")
                Result.success(Pair(latResponse, lonResponse))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLocalTime(): Result<String> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                // :GL# 返回 24 小时制本地时间 HH:MM:SS
                val response = p.sendCommand(Cmd.GET_LOCAL_TIME_24H).getOrNull()?.trim() ?: ""
                AppLogger.w(TAG, "getLocalTime: '$response'")
                if (response.isNotEmpty() && response.length >= 5) {
                    Result.success(response)
                } else {
                    Result.failure(Exception("获取时间失败: '$response'"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getDate(): Result<String> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                // :GC# 返回 MM/DD/YY 格式
                val response = p.sendCommand(Cmd.GET_DATE).getOrNull()?.trim() ?: ""
                AppLogger.w(TAG, "getDate: '$response'")
                // 转换为 YYYY-MM-DD 格式
                val parts = response.split("/")
                if (parts.size == 3) {
                    val year = "20${parts[2]}"
                    val month = parts[0].padStart(2, '0')
                    val day = parts[1].padStart(2, '0')
                    Result.success("$year-$month-$day")
                } else {
                    Result.success(response)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getTimezone(): Result<String> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                // :GG# 返回 sHH:MM 格式的 UTC 偏移
                val response = p.sendCommand(Cmd.GET_TIMEZONE_OFFSET).getOrNull()?.trim() ?: ""
                AppLogger.w(TAG, "getTimezone: '$response'")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setTime(time: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.w(TAG, "setTime: sending :SL$time#")
            // :SLHH:MM:SS# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_TIME}$time")
            AppLogger.w(TAG, "setTime response: '$response'")
            return@withLock if (response == "1") {
                Result.success(Unit)
            } else {
                Result.failure(Exception("设置时间失败 (响应: $response)"))
            }
        }
    }
    
    override suspend fun setDate(date: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.w(TAG, "setDate: sending :SC$date#")
            // :SCMM/DD/YY# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_DATE}$date")
            AppLogger.w(TAG, "setDate response: '$response'")
            return@withLock if (response == "1") {
                Result.success(Unit)
            } else {
                Result.failure(Exception("设置日期失败 (响应: $response)"))
            }
        }
    }
    
    override suspend fun setTimezone(timezone: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.w(TAG, "setTimezone: sending :SG$timezone#")
            // :SGsHH:MM# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_UTC_OFFSET}$timezone")
            AppLogger.w(TAG, "setTimezone response: '$response'")
            return@withLock if (response == "1") {
                Result.success(Unit)
            } else {
                Result.failure(Exception("设置时区失败 (响应: $response)"))
            }
        }
    }
    
    /**
     * 将 DMS 格式转换为 OnStepX 格式
     * OnStepX 格式: :St+34*05:34# (纬度) 或 :Sg117*30:00# (经度)
     * 
     * 注意：OnStepX 使用 * 作为度分分隔符，不是 :
     * 输入示例: "+22°23'49\"N" 或 "22:23:49" 或 "22°23'49"
     * 
     * 支持的输入格式：
     * - "22°23'49" 或 "+22°23'49" (带度分秒符号)
     * - "22:23:49" (纯数字，冒号分隔)
     * - "22*23:49" (OnStepX 格式)
     * - "N22°23'49" 或 "22°N" (带方向字母)
     */
    private fun dmsToOnStepFormat(dms: String, isLatitude: Boolean): String {
        // 处理符号和方向标识
        // OnStepX 经度使用 West-positive 约定: 东经=负, 西经=正
        // 纬度使用 North-positive 约定: 北=正, 南=负
        val sign = when {
            dms.startsWith("-") -> "-"
            dms.contains("S") -> "-"          // 纬度南半球 → 负
            dms.contains("W") -> "+"          // 经度西经 → OnStep 正
            dms.contains("E") -> if (isLatitude) "+" else "-"  // 纬度东=正, 经度东=OnStep负
            dms.contains("N") -> "+"          // 纬度北半球 → 正
            else -> "+"
        }
        
        // 清理输入：去除符号、方向字母和度分秒标记
        val cleaned = dms.trim()
            .removePrefix("+")
            .removePrefix("-")
            .replace("N", "").replace("S", "")
            .replace("E", "").replace("W", "")
            .replace("°", "*").replace("'", "*")
            .replace("\"", ":")   // 秒的双引号 → :
        
        // 分割字符串，分隔符可能是 : 或 *
        // 对于 "22*23:49"，先按 : 分割得到 ["22*23", "49"]
        // 对于 "22:23:49"，按 : 分割得到 ["22", "23", "49"]
        val parts = cleaned.split(":").filter { it.isNotBlank() }
        if (parts.isEmpty()) return "+00*00:00"
        
        // 解析度分秒
        // parts[0] = 度分部分 (如 "22*23" 或 "22")
        // parts[1] = 秒部分 (如 "49" 或 "23"，如果度分秒都用 : 分隔则 parts[0] = "22")
        val degMinPart = parts.getOrNull(0) ?: "00"
        
        // 按 * 分割度分部分（兼容 "22*23" 和 "22" 两种情况）
        val degMinParts = degMinPart.split("*").filter { it.isNotBlank() }
        
        val deg = degMinParts.getOrNull(0)?.filter { it.isDigit() }?.padStart(2, '0') ?: "00"
        val min = degMinParts.getOrNull(1)?.filter { it.isDigit() }?.padStart(2, '0') ?: "00"
        
        // 秒：优先取 parts[1]，如果 parts 只有一项则秒为 "00"
        // 如果 parts 有3项(如 "22:23:49")，parts[1]=23 是分，parts[2]=49 是秒
        val sec = if (parts.size >= 3) {
            // 三部分格式：度:分:秒
            parts[2].filter { it.isDigit() }.padStart(2, '0')
        } else {
            // 两部分格式：度分:秒 或 度:秒
            parts.getOrNull(1)?.filter { it.isDigit() }?.padStart(2, '0') ?: "00"
        }
        
        val result = "$sign$deg*$min:$sec"
        AppLogger.w(TAG, "dmsToOnStepFormat('$dms') = '$result'")
        return result
    }
    
    override fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                getStatus()  // getStatus() itself manages commandMutex lock
                delay(1000) // 每秒更新
            }
        }
        AppLogger.w(TAG, "Polling started")
    }
    
    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        AppLogger.w(TAG, "Polling stopped")
    }
    
    override fun pausePolling() {
        pollingJob?.cancel()
        pollingJob = null
        AppLogger.w(TAG, "Polling paused")
    }
    
    override fun resumePolling() {
        startPolling()
    }
}