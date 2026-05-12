package com.skygoto.app.domain.repository

import com.skygoto.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 赤道仪仓储接口
 */
interface MountRepository {
    val mountStatus: StateFlow<MountStatus>
    val isConnected: StateFlow<Boolean>
    
    suspend fun connect(host: String, port: Int): Result<Unit>
    suspend fun disconnect()
    suspend fun getStatus(): Result<MountStatus>
    
    // 追踪控制
    suspend fun startTracking(): Result<Unit>
    suspend fun stopTracking(): Result<Unit>
    
    // 移动控制
    suspend fun move(direction: Direction, rate: MoveRate = MoveRate.GUIDE): Result<Unit>
    suspend fun stopMove(): Result<Unit>
    
    // GOTO
    suspend fun setTargetAndGoto(ra: String, dec: String): Result<GotoResult>
    suspend fun cancelGoto(): Result<Unit>
    
    // 同步
    suspend fun syncToCurrentPosition(): Result<Unit>
}
