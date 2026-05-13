package com.skygoto.app.domain.model

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val type: ConnectionType,
        val deviceName: String,
        val deviceAddress: String = "",
        val extraInfo: String = ""
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class ConnectionType {
    BLUETOOTH,
    WIFI
}

/**
 * 赤道仪状态
 */
data class MountStatus(
    val ra: String = "--:--:--",      // 当前赤经
    val dec: String = "--:--:--",      // 当前赤纬
    val alt: String = "--:--",         // 高度
    val az: String = "---:--",         // 方位
    val tracking: Boolean = false,       // 是否追踪中
    val slewing: Boolean = false,      // 是否在移动中
    val targetRa: String? = null,     // 目标赤经
    val targetDec: String? = null,     // 目标赤纬
    val trackingRate: Double = 60.0    // 跟踪速率 Hz
)

/**
 * 追踪模式
 */
enum class TrackingMode(val displayName: String, val rateHz: Double) {
    SIDEREAL("恒星时", 60.0),
    SOLAR("太阳", 60.0),
    LUNAR("月球", 57.9),
    STOPPED("停止", 0.0)
}

/**
 * 移动方向
 */
enum class Direction {
    NORTH, SOUTH, EAST, WEST
}

/**
 * 移动速率等级
 */
enum class MoveRate(val displayName: String, val level: Int) {
    GUIDE("导星 1x", 2),
    CENTERING("居中 8x", 5),
    FIND("寻找 20x", 6),
    FAST("快速 48x", 7),
    SLEW("GOTO速率", 8)
}
