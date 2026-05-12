package com.skygoto.app.domain.model

/**
 * GOTO 结果
 */
sealed class GotoResult {
    object Success : GotoResult()
    data class Error(val code: Int, val message: String) : GotoResult()
}

/**
 * GOTO 错误码
 */
object GotoErrorCodes {
    const val BELOW_HORIZON = 1
    const val ABOVE_OVERHEAD = 2
    const val STANDBY = 3
    const val PARKED = 4
    const val GOTO_IN_PROGRESS = 5
    const val OUTSIDE_LIMITS = 6
    const val HARDWARE_ERROR = 7
    const val ALREADY_SLEWING = 8
    const val UNSPECIFIED = 9
    
    fun getMessage(code: Int): String = when(code) {
        BELOW_HORIZON -> "低于地平线"
        ABOVE_OVERHEAD -> "高于天顶"
        STANDBY -> "赤道仪待机中"
        PARKED -> "赤道仪已停车"
        GOTO_IN_PROGRESS -> "GOTO 进行中"
        OUTSIDE_LIMITS -> "超出限位"
        HARDWARE_ERROR -> "硬件故障"
        ALREADY_SLEWING -> "已经在移动"
        else -> "未知错误"
    }
}
