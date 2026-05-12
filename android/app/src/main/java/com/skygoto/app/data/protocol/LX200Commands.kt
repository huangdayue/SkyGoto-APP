package com.skygoto.app.data.protocol

/**
 * LX200 协议命令常量
 */
object LX200Commands {
    // 位置查询
    const val GET_RA = "GR"           // 获取当前赤经
    const val GET_DEC = "GD"          // 获取当前赤纬
    const val GET_ALT = "GA"          // 获取当前高度
    const val GET_AZ = "GZ"           // 获取当前方位
    const val GET_TRACKING = "GT"     // 获取跟踪速率
    
    // 目标设置
    const val SET_TARGET_RA = "Sr"     // 设置目标赤经
    const val SET_TARGET_DEC = "Sd"   // 设置目标赤纬
    
    // GOTO
    const val GOTO = "MS"             // 执行 GOTO
    const val GOTO_ALT_AZ = "MA"      // Alt/Az GOTO
    
    // 追踪控制
    const val TRACKING_ON = "Te"      // 开启追踪
    const val TRACKING_OFF = "Td"     // 关闭追踪
    const val TRACK_SIDEREAL = "TK"  // 恒星时追踪
    
    // 移动控制
    const val MOVE_NORTH = "Mn"       // 向北
    const val MOVE_SOUTH = "Ms"       // 向南
    const val MOVE_EAST = "Me"        // 向东
    const val MOVE_WEST = "Mw"        // 向西
    
    // 停止
    const val STOP_ALL = "Q"          // 停止所有
    const val STOP_RA = "Qe"          // 停止 RA
    const val STOP_DEC = "Qn"         // 停止 Dec
    
    // 速率设置
    const val RATE_GUIDE = "RG"       // 导星速率 1x
    const val RATE_CENTER = "RC"      // 居中速率 8x
    const val RATE_FIND = "RM"       // 寻找速率 20x
    const val RATE_FAST = "RF"       // 快速 48x
    const val RATE_SLEW = "RS"       // GOTO 速率
    
    // 同步
    const val SYNC = "CM"             // 同步到目标
    
    // 系统
    const val VERSION = "V"           // 获取版本
    
    // 扩展命令
    const val EXTENDED = "GX"        // OnStepX 扩展
}
