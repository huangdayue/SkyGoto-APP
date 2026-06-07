package com.skygoto.app.domain.model

/**
 * PEC 状态枚举
 *
 * @property code OnStepX 返回的字符代码
 * @property displayName 中文显示名
 */
enum class PecState(val code: Char, val displayName: String) {
    IDLE('I', "空闲"),
    READY_PLAY('p', "待回放"),
    PLAYING('P', "回放中"),
    READY_RECORD('r', "待录制"),
    RECORDING('R', "录制中"),
    UNKNOWN('?', "未知");

    companion object {
        fun fromCode(code: Char): PecState =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

/**
 * PEC 完整信息
 */
data class PecInfo(
    val state: PecState = PecState.UNKNOWN,
    val hasIndexDetect: Boolean = false,
    val isRecorded: Boolean = false,
    val bufferSizeSeconds: Int = 0,
    val wormRotationSteps: Long = 0,
    val currentIndexSecond: Int = 0,
    val currentCorrection: Int = 0,
    val progressPercent: Int = 0
) {
    val isActive: Boolean get() = state == PecState.PLAYING || state == PecState.RECORDING
    val isRecording: Boolean get() = state == PecState.RECORDING || state == PecState.READY_RECORD
    val isPlaying: Boolean get() = state == PecState.PLAYING || state == PecState.READY_PLAY
    val totalSeconds: Int get() = bufferSizeSeconds
}
