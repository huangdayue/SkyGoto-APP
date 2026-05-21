/*
 * 文件名：MountStatus.kt
 * 描述：赤道仪状态模型 - 包含连接状态、位置、追踪模式等数据结构
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义赤道仪连接状态的密封类
 * - 描述赤道仪实时状态数据（RA/Dec/Alt/Az）
 * - 定义追踪模式和移动方向
 * - 提供移动速率等级枚举
 *
 * 数据来源：
 * - 位置数据来自赤道仪的闭环位置反馈
 * - 高度/方位由坐标转换计算得出
 * - 追踪状态由赤道仪控制板直接提供
 *
 * 使用方式：
 * - 通过 MountRepository.mountStatus StateFlow 观察实时状态
 * - 使用 ConnectionState 判断连接状态
 * - 使用 TrackingMode/MoveRate 控制赤道仪行为
 */

package com.skygoto.app.domain.model

/**
 * 连接状态密封类
 *
 * 定义应用与赤道仪之间的连接状态：
 * - Disconnected: 未连接
 * - Connecting: 正在连接
 * - Connected: 已连接（包含连接详情）
 * - Error: 连接错误
 *
 * sealed 类确保完整的类型覆盖，
 * when 表达式可以穷举所有可能的状态。
 */
sealed class ConnectionState {
    /** 未连接状态 */
    object Disconnected : ConnectionState()

    /** 正在尝试连接 */
    object Connecting : ConnectionState()

    /**
     * 已成功连接到赤道仪
     *
     * @param type 连接类型（蓝牙/WiFi）
     * @param deviceName 设备名称
     * @param deviceAddress 设备地址（MAC/IP）
     * @param extraInfo 额外信息（如固件版本）
     */
    data class Connected(
        val type: ConnectionType,
        val deviceName: String,
        val deviceAddress: String = "",
        val extraInfo: String = ""
    ) : ConnectionState()

    /**
     * 连接错误
     *
     * @param message 错误描述
     */
    data class Error(val message: String) : ConnectionState()
}

/**
 * 连接类型枚举
 *
 * 定义支持的赤道仪连接方式：
 * - BLUETOOTH: 蓝牙 RFCOMM 串口连接
 * - WIFI: WiFi TCP 网络连接
 */
enum class ConnectionType {
    BLUETOOTH,  // 蓝牙串口连接
    WIFI        // WiFi 网络连接
}

/**
 * 赤道仪状态数据类
 *
 * 包含赤道仪的完整实时状态信息。
 * 这些数据由应用定期从赤道仪查询获取。
 *
 * @param ra 当前赤经位置（格式："HH:MM:SS"）
 * @param dec 当前赤纬位置（格式："+DD*MM:SS"）
 * @param alt 当前高度（格式："DD*MM"）
 * @param az 当前方位角（格式："DDD*MM"）
 * @param tracking 是否正在追踪（恒星时追踪）
 * @param slewing 是否正在移动（GOTO 或手动移动中）
 * @param targetRa 当前目标赤经（如果正在进行 GOTO）
 * @param targetDec 当前目标赤纬
 * @param trackingRate 追踪速率（默认 60.0 恒星时速 Hz）
 */
data class MountStatus(
    val ra: String = "--:--:--",         // 当前赤经
    val dec: String = "--:--:--",          // 当前赤纬
    val alt: String = "--:--",             // 高度（Altitude）
    val az: String = "---:--",             // 方位角（Azimuth）
    val tracking: Boolean = false,        // 是否追踪中
    val slewing: Boolean = false,         // 是否在移动中
    val targetRa: String? = null,         // 目标赤经
    val targetDec: String? = null,         // 目标赤纬
    val trackingRate: Double = 60.0       // 跟踪速率 Hz
)

/**
 * 追踪模式枚举
 *
 * 定义赤道仪的追踪速率模式：
 * - SIDEREAL: 恒星时追踪（60.0 Hz），补偿地球自转
 * - SOLAR: 太阳追踪（60.0 Hz），用于日食观测
 * - LUNAR: 月球追踪（57.9 Hz），补偿月球公转
 * - STOPPED: 停止追踪
 *
 * @param displayName 显示名称（中文）
 * @param rateHz 追踪速率（赫兹）
 */
enum class TrackingMode(val displayName: String, val rateHz: Double) {
    SIDEREAL("恒星时", 60.0),  // 恒星时追踪，默认模式
    SOLAR("太阳", 60.0),       // 太阳追踪速率
    LUNAR("月球", 57.9),       // 月球追踪速率（约 4% 慢于恒星时）
    STOPPED("停止", 0.0)       // 停止追踪
}

/**
 * 移动方向枚举
 *
 * 定义赤道仪的四个基本移动方向：
 * 用于手动控制赤道仪微调位置。
 *
 * 注意：这是赤道仪坐标系中的方向，
 * 通常与地理方向有所区别。
 */
enum class Direction {
    NORTH,  // 北方向（赤纬增加）
    SOUTH,  // 南方向（赤纬减少）
    EAST,   // 东方向（赤经增加）
    WEST    // 西方向（赤经减少）
}

/**
 * 移动速率等级枚举
 *
 * 定义手动控制的移动速度等级。
 * 从慢到快分为 5 个等级：
 * - GUIDE: 导星速率，用于精确校准
 * - CENTERING: 居中速率，用于目标居中
 * - FIND: 寻找速率，用于快速找到目标
 * - FAST: 快速速率，用于大范围移动
 * - SLEW: GOTO 速率，用于 GOTO 移动
 *
 * @param displayName 显示名称
 * @param level 速率等级（数值越大越快）
 */
enum class MoveRate(val displayName: String, val level: Int) {
    GUIDE("导星 1x", 2),       // 导星速率，约 1 倍恒星时
    CENTERING("居中 8x", 5),   // 居中速率，约 8 倍恒星时
    FIND("寻找 20x", 6),       // 寻找速率，约 20 倍恒星时
    FAST("快速 48x", 7),       // 快速速率，约 48 倍恒星时
    SLEW("GOTO速率", 8)        // GOTO 速率，用于高速移动
}