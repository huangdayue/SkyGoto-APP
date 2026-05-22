/*
 * 文件名：GotoResult.kt
 * 描述：GOTO 操作结果与错误码定义
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 定义 GOTO 操作的成功/失败结果
 * - 定义赤道仪返回的错误码及其含义
 * - 提供错误码到中文描述的转换
 *
 * GOTO 流程概述：
 * 当用户选择一个天体并发起 GOTO 命令后，赤道仪会：
 * 1. 检查目标是否有效（地平线上、非超限等）
 * 2. 如果有效开始跟踪（同步电机驱动）
 * 3. 如果无效返回错误码，解释失败原因
 *
 * 使用方式：
 * - MountRepository.setTargetAndGoto() 返回 GotoResult
 * - 使用 when 表达式处理不同结果
 * - 使用 GotoErrorCodes.getMessage() 获取错误描述
 */

package com.skygoto.app.domain.model

/**
 * GOTO 操作结果密封类
 *
 * 使用密封类定义 GOTO 操作的可能结果：
 * - Success: 成功
 * - Error: 失败（包含错误码和描述）
 *
 * sealed 类确保编译器能检查所有可能的情况，
 * 配合 when 表达式可以实现完整的类型分支处理。
 */
sealed class GotoResult {
    /**
     * GOTO 成功
     *
     * 表示赤道仪已接受目标并开始跟踪。
     * 此时可以观察赤道仪移动到目标位置。
     */
    object Success : GotoResult()

    /**
     * GOTO 错误
     *
     * @param code 错误码（见 GotoErrorCodes）
     * @param message 错误描述
     */
    data class GotoError(val code: Int, val message: String) : GotoResult()
}

/**
 * GOTO 错误码定义
 *
 * 包含所有赤道仪可能返回的 GOTO 错误码，
 * 以及对应的中文描述信息。
 *
 * 错误码来源：
 * - OnStep 固件的 LX200 协议定义
 * - 可能因设备型号不同而略有差异
 */
object GotoErrorCodes {
    /** 目标低于地平线，无法观测 */
    const val BELOW_HORIZON = 1

    /** 目标高于天顶（超过 90°），机械无法到达 */
    const val ABOVE_OVERHEAD = 2

    /** 赤道仪处于待机模式，未启用 */
    const val STANDBY = 3

    /** 赤道仪已停车（Parked），需要先解锁才能移动 */
    const val PARKED = 4

    /** 正在执行另一个 GOTO 命令，请等待完成 */
    const val GOTO_IN_PROGRESS = 5

    /** 目标超出限位范围（东限/西限/高度限制） */
    const val OUTSIDE_LIMITS = 6

    /** 赤道仪硬件故障（如电机驱动错误、传感器故障） */
    const val HARDWARE_ERROR = 7

    /** 赤道仪已经在移动中，不能同时执行两个移动命令 */
    const val ALREADY_SLEWING = 8

    /** 未指定的错误，需要查看设备日志进一步排查 */
    const val UNSPECIFIED = 9

    /**
     * 根据错误码获取中文错误描述
     *
     * @param code 错误码
     * @return 对应的中文错误描述
     */
    fun getMessage(code: Int): String = when (code) {
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