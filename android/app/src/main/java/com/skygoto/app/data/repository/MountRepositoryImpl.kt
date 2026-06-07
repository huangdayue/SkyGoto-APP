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
import kotlinx.coroutines.withTimeoutOrNull
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
    
    // 连续获取状态失败的次数（WiFi断开检测）
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3
    
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
        consecutiveFailures = 0  // 新连接，重置失败计数
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
                consecutiveFailures = 0  // 重置连续失败计数
                
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
                consecutiveFailures++
                AppLogger.e(TAG, "getStatus 异常: ${e.message}")
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    _isConnected.value = false
                    pollingJob?.cancel()
                    connectionManager.notifyDisconnected()
                }
                Result.failure(e)
            }
        }
    }
    
    override suspend fun startTracking(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :Te 返回单个字符 '1'（成功）或 '0'（失败），不带 #
            val response = p.sendAndReadSingleDigit(Cmd.TRACKING_ON)
            AppLogger.i(TAG, "startTracking response: '$response'")
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
            AppLogger.i(TAG, "stopTracking response: '$response'")
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
            AppLogger.i(TAG, "setMoveRate: $rateCmd")
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
        AppLogger.i(TAG, "setTargetAndGoto: RA=$ra, Dec=$dec")
        
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            
            try {
                // 设置目标赤经 :Sr 返回单个字符 '1'（成功）或 '0'（失败），不带 #
                AppLogger.i(TAG, "Sending SET_TARGET_RA: :Sr$ra#")
                val raResponse = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_RA}$ra")
                AppLogger.i(TAG, "SET_TARGET_RA response: '$raResponse'")
                
                // 设置目标赤纬 :Sd 返回单个字符 '1'（成功）或 '0'（失败），不带 #
                AppLogger.i(TAG, "Sending SET_TARGET_DEC: :Sd$dec#")
                val decResponse = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_DEC}$dec")
                AppLogger.i(TAG, "SET_TARGET_DEC response: '$decResponse'")
                
                // 执行 GOTO（:MS 返回单个字符响应码，suppressFrame=true 不发送 #）
                AppLogger.i(TAG, "Sending GOTO_TARGET: ${Cmd.GOTO_TARGET}#")
                val response = p.sendAndReadSingleDigit(Cmd.GOTO_TARGET)
                AppLogger.i(TAG, "GOTO_TARGET response: '$response'")
                
                if (response.isEmpty()) {
                    return@withLock Result.failure(Exception("GOTO 命令超时"))
                }
                
                val firstDigit = response.firstNotNullOfOrNull { it.digitToIntOrNull() } ?: -1
                AppLogger.i(TAG, "GOTO response code: $firstDigit (full response: '$response')")
                
                if (firstDigit == 0) {
                    _mountStatus.value = _mountStatus.value.copy(
                        targetRa = ra,
                        targetDec = dec,
                        slewing = true
                    )
                    return@withLock Result.success(GotoResult.Success)
                } else {
                    return@withLock Result.success(GotoResult.GotoError(firstDigit, GotoErrorCodes.getMessage(firstDigit)))
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
            AppLogger.i(TAG, "cancelGoto STOP_ALL sent")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun syncToCurrentPosition(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :CM# 返回 "N/A#" 成功 或 "E0#" 等失败，带 # 结束符
            val response = p.sendCommand(Cmd.SYNC_TO_CATALOG).getOrNull()?.trim() ?: ""
            AppLogger.i(TAG, "syncToCurrentPosition response: '$response'")
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
            AppLogger.i(TAG, "home HOME_GOTO sent")
            return@withLock Result.success(Unit)
        }
    }

    override suspend fun homeAndWait(): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))

        // 1. 发送回零命令
        p.sendCommandNoResponse(Cmd.HOME_GOTO)
        AppLogger.i(TAG, "homeAndWait: HOME_GOTO sent")

        // 2. 轮询 :D# 直到赤道仪停止（超时 120 秒）
        val timeoutMs = 120_000L
        val pollIntervalMs = 500L
        val startMs = System.currentTimeMillis()

        while (System.currentTimeMillis() - startMs < timeoutMs) {
            val motionResp = p.sendAndReadSingleDigit(Cmd.GET_MOTION_STATUS)
            // :D# 返回：移动中 = 0x7f (char 127)，停止 = '#' (char 35)
            val stopped = motionResp == "#" || motionResp == ""
            if (stopped) {
                AppLogger.i(TAG, "homeAndWait: 回零完成")
                return Result.success(Unit)
            }
            kotlinx.coroutines.delay(pollIntervalMs)
        }

        AppLogger.w(TAG, "homeAndWait: 回零超时")  // 超时是警告，保留 WARN
        // 超时后发 :Q# 停止运动
        p.sendCommandNoResponse(Cmd.STOP_ALL)
        return Result.failure(Exception("回零超时（120秒）"))
    }
    
    override suspend fun setZeroPosition(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            // :hF# 无响应，立即返回
            p.sendCommandNoResponse(Cmd.HOME_RESET)
            AppLogger.i(TAG, "setZeroPosition HOME_RESET sent")
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
            AppLogger.i(TAG, "SET_LATITUDE response: '$latResponse', SET_LONGITUDE response: '$lonResponse'")
            
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
                AppLogger.i(TAG, "getLocation: 开始请求 :Gt#")
                val latResponse = p.sendCommand(Cmd.GET_LATITUDE).getOrNull()?.trim() ?: ""
                AppLogger.i(TAG, "getLocation: lat响应='$latResponse' (长度=${latResponse.length})")
                AppLogger.i(TAG, "getLocation: 开始请求 :Gg#")
                val lonResponse = p.sendCommand(Cmd.GET_LONGITUDE).getOrNull()?.trim() ?: ""
                AppLogger.i(TAG, "getLocation: lon响应='$lonResponse' (长度=${lonResponse.length})")
                AppLogger.i(TAG, "getLocation: lat='$latResponse', lon='$lonResponse'")
                Result.success(Pair(latResponse, lonResponse))
            } catch (e: Exception) {
                AppLogger.e(TAG, "getLocation异常: ${e.message}", e)
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
                AppLogger.i(TAG, "getLocalTime: '$response'")
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
                AppLogger.i(TAG, "getDate: '$response'")
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
                AppLogger.i(TAG, "getTimezone: '$response'")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setTime(time: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "setTime: sending :SL$time#")
            // :SLHH:MM:SS# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_TIME}$time")
            AppLogger.i(TAG, "setTime response: '$response'")
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
            AppLogger.i(TAG, "setDate: sending :SC$date#")
            // :SCMM/DD/YY# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_DATE}$date")
            AppLogger.i(TAG, "setDate response: '$response'")
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
            AppLogger.i(TAG, "setTimezone: sending :SG$timezone#")
            // :SGsHH:MM# 返回 '1' 成功，'0' 失败
            val response = p.sendAndReadSingleDigit("${Cmd.SET_UTC_OFFSET}$timezone")
            AppLogger.i(TAG, "setTimezone response: '$response'")
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
        
        // 秒：带四舍五入，避免截断丢失精度
        val sec = if (parts.size >= 3) {
            // 三部分格式：度:分:秒
            val rawSec = parts[2].filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
            ((rawSec + 0.5).toInt()).toString().padStart(2, '0')
        } else {
            // 两部分格式：度分:秒 或 度:秒
            val rawSec = parts.getOrNull(1)?.filter { it.isDigit() }?.toDoubleOrNull() ?: 0.0
            ((rawSec + 0.5).toInt()).toString().padStart(2, '0')
        }
        
        val result = "$sign$deg*$min:$sec"
        AppLogger.i(TAG, "dmsToOnStepFormat('$dms') = '$result'")
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
        AppLogger.i(TAG, "Polling started")
    }
    
    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        AppLogger.i(TAG, "Polling stopped")
    }
    
    override fun pausePolling() {
        pollingJob?.cancel()
        pollingJob = null
        AppLogger.i(TAG, "Polling paused")
    }
    
    override fun resumePolling() {
        startPolling()
    }
    
    // ========== 对齐模式 ==========

    override suspend fun startAlign(starCount: Int): Result<Unit> {
        val cmd = "${Cmd.ALIGN_START}$starCount"
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "startAlign: $cmd")
            val response = p.sendAndReadSingleDigit(cmd)
            AppLogger.i(TAG, "startAlign response: '$response'")
            return@withLock if (response == "1") Result.success(Unit)
            else Result.failure(Exception("启动${starCount}星对齐失败"))
        }
    }
    
    override suspend fun acceptAlignStar(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "acceptAlignStar: :A+#")
            val response = p.sendAndReadSingleDigit(Cmd.ALIGN_ACCEPT)
            AppLogger.i(TAG, "acceptAlignStar response: '$response'")
            return@withLock if (response == "1") Result.success(Unit)
            else Result.failure(Exception("接受校准星失败"))
        }
    }
    override suspend fun finishAlign(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "finishAlign: :AW# (no :A-, not supported by OnStepX)")
            val writeResp = p.sendAndReadSingleDigit(Cmd.ALIGN_WRITE)
            AppLogger.i(TAG, "finishAlign ALIGN_WRITE response: '$writeResp'")
            return@withLock if (writeResp == "1") Result.success(Unit)
            else Result.failure(Exception("保存对齐模型失败"))
        }
    }

    override suspend fun cancelAlign(): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "cancelAlign: :A6# (start new align to reset state)")
            // OnStepX 没有 :A-# 取消命令。发 :A6# 会触发 home.reset()→alignReset() 清空对齐状态。
            // 同时也让赤道仪回零位，符合用户取消对齐的预期。
            p.sendCommandNoResponse("${Cmd.ALIGN_START}6")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun clearAlignModel(starCount: Int): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "clearAlignModel: :A${starCount}# + :AW#")
            // 开始对齐（自动清除 RAM 中的模型）
            val startResp = p.sendAndReadSingleDigit("${Cmd.ALIGN_START}$starCount")
            if (startResp != "1") {
                return@withLock Result.failure(Exception("清除对齐模型失败"))
            }
            // 立即写入 NV（覆盖旧数据）
            delay(100)
            val writeResp = p.sendAndReadSingleDigit(Cmd.ALIGN_WRITE)
            AppLogger.i(TAG, "clearAlignModel write response: '$writeResp'")
            return@withLock Result.success(Unit)
        }
    }

    override suspend fun getAlignStatus(): Result<Triple<Int, Int, Int>> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            val raw = p.sendCommand(Cmd.ALIGN_GET_STATUS).getOrNull()?.trim() ?: ""
            AppLogger.i(TAG, "getAlignStatus raw: '$raw'")
            val parts = raw.split(",")
            if (parts.size >= 3) {
                val max = parts[0].toIntOrNull() ?: 0
                val cur = parts[1].toIntOrNull() ?: 0
                val last = parts[2].toIntOrNull() ?: 0
                return@withLock Result.success(Triple(max, cur, last))
            }
            Result.failure(Exception("解析对齐状态失败: '$raw'"))
        }
    }

    override suspend fun setAlignTarget(ra: String, dec: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            AppLogger.i(TAG, "setAlignTarget: RA=$ra, Dec=$dec")
            val raResp = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_RA}$ra")
            if (raResp != "1") {
                return@withLock Result.failure(Exception("设置对齐目标 RA 失败: '$raResp'"))
            }
            val decResp = p.sendAndReadSingleDigit("${Cmd.SET_TARGET_DEC}$dec")
            if (decResp != "1") {
                return@withLock Result.failure(Exception("设置对齐目标 Dec 失败: '$decResp'"))
            }
            return@withLock Result.success(Unit)
        }
    }

    // ========== PEC - 周期性误差补偿 ==========
    
    override suspend fun getPecState(): Result<PecInfo> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                val raw = p.sendCommand(Cmd.PEC_STATUS).getOrNull()?.trim() ?: "?"
                val stateCode = if (raw.isNotEmpty()) raw[0] else '?'
                val hasIndex = raw.length > 1 && raw[1] == '.'
                val state = PecState.fromCode(stateCode)
                
                // 尝试读取配置
                val wormStepsStr = p.sendCommand(Cmd.PEC_GET_WORM_STEPS).getOrNull()?.trim() ?: "0"
                val bufSizeStr = p.sendCommand(Cmd.PEC_GET_BUFFER_SIZE).getOrNull()?.trim() ?: "0"
                val wormSteps = wormStepsStr.toLongOrNull() ?: 0L
                val bufSize = bufSizeStr.toIntOrNull() ?: 0
                
                // 当前索位置
                val indexStr = p.sendCommand(Cmd.PEC_GET_INDEX_POS).getOrNull()?.trim() ?: "0"
                val indexPos = indexStr.toIntOrNull() ?: 0
                
                // 当前修正值 (:VR# 读当前播放位置的修正)
                val curCorrStr = p.sendCommand("${Cmd.PEC_READ_ENTRY}#").getOrNull()?.trim() ?: "0"
                val curCorrParts = curCorrStr.split(",")
                val curCorr = curCorrParts.firstOrNull()?.toIntOrNull() ?: 0
                
                val progress = if (bufSize > 0 && state == PecState.RECORDING && indexPos > 0)
                    (indexPos * 100 / bufSize).coerceIn(0, 100) else 0
                
                val info = PecInfo(
                    state = state,
                    hasIndexDetect = hasIndex,
                    isRecorded = state >= PecState.READY_PLAY && state <= PecState.PLAYING,
                    bufferSizeSeconds = bufSize,
                    wormRotationSteps = wormSteps,
                    currentIndexSecond = indexPos,
                    currentCorrection = curCorr,
                    progressPercent = progress
                )
                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun pecPlay(): Result<Unit> {
        return sendPecSimple(Cmd.PEC_PLAY)
    }
    
    override suspend fun pecStop(): Result<Unit> {
        return sendPecSimple(Cmd.PEC_STOP)
    }
    
    override suspend fun pecRecord(): Result<Unit> {
        return sendPecSimple(Cmd.PEC_RECORD)
    }
    
    override suspend fun pecClear(): Result<Unit> {
        return sendPecSimple(Cmd.PEC_CLEAR)
    }
    
    override suspend fun pecSave(): Result<Unit> {
        return sendPecSimple(Cmd.PEC_SAVE)
    }
    
    override suspend fun getPecConfig(): Result<Pair<Long, Int>> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                val w = p.sendCommand(Cmd.PEC_GET_WORM_STEPS).getOrNull()?.trim()?.toLongOrNull() ?: 0L
                val b = p.sendCommand(Cmd.PEC_GET_BUFFER_SIZE).getOrNull()?.trim()?.toIntOrNull() ?: 0
                Result.success(Pair(w, b))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setPecWormSteps(steps: Long): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            val resp = p.sendAndReadSingleDigit("${Cmd.PEC_SET_WORM_STEPS}$steps")
            return@withLock if (resp == "1") Result.success(Unit)
            else Result.failure(Exception("设置蜗杆步数失败"))
        }
    }
    
    override suspend fun readPecEntry(index: Int): Result<Int> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                val raw = p.sendCommand("${Cmd.PEC_READ_ENTRY}$index").getOrNull()?.trim() ?: "0"
                val value = raw.toIntOrNull() ?: 0
                Result.success(value)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun writePecEntry(index: Int, value: Int): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            p.sendCommandNoResponse("${Cmd.PEC_WRITE_ENTRY}$index,$value")
            return@withLock Result.success(Unit)
        }
    }
    
    override suspend fun loadPecCurve(bufferSize: Int): Result<List<Int>> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        if (bufferSize <= 0 || bufferSize > 3600) {
            return Result.failure(Exception("无效的缓存大小"))
        }
        pausePolling()
        try {
            val values = mutableListOf<Int>()
            for (i in 0 until bufferSize) {
                // 每条命令单独获取锁，避免长时间阻塞轮询导致 UI 冻结
                val raw = commandMutex.withLock {
                    p.sendCommand("${Cmd.PEC_READ_ENTRY}$i").getOrNull()?.trim() ?: "0"
                }
                values.add(raw.toIntOrNull() ?: 0)
                if (i % 100 == 99) delay(100)  // 每百条让串口排空
                else delay(10)                  // 微延迟防止溢出
            }
            return Result.success(values)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            resumePolling()
        }
    }
    
    override suspend fun savePecCurve(values: List<Int>): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        pausePolling()
        try {
            for ((i, v) in values.withIndex()) {
                val clamped = v.coerceIn(-127, 127)
                // 每条命令单独获取锁，避免长时间阻塞轮询
                commandMutex.withLock {
                    p.sendCommandNoResponse("${Cmd.PEC_WRITE_ENTRY}$i,$clamped")
                }
                if (i % 100 == 99) delay(100)
                else delay(10)
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            resumePolling()
        }
    }
    
    private suspend fun sendPecSimple(command: String): Result<Unit> {
        return commandMutex.withLock {
            val p = protocol ?: return@withLock Result.failure(Exception("Not connected"))
            p.sendCommandNoResponse(command)
            return@withLock Result.success(Unit)
        }
    }

    // ========== 终端命令支持 ==========
    
    /**
     * 发送原始 LX200 命令（fire-and-forget，不等待响应）
     */
    override suspend fun sendRawCommand(command: String): Result<Unit> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return try {
            // 不锁 mutex，终端命令与状态轮询互不干扰
            // （轮询仅在控制页面可见时运行，终端与控制页面不同时可见）
            p.sendCommandNoResponse(command)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 非阻塞读取赤道仪缓冲区
     */
    override suspend fun readAvailableBytes(): Result<String> {
        val p = protocol ?: return Result.failure(Exception("Not connected"))
        return try {
            val data = p.readAvailableBytes()
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
