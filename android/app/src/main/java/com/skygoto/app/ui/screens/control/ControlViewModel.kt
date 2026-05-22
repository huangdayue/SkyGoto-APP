package com.skygoto.app.ui.screens.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.Direction
import com.skygoto.app.domain.model.GotoResult
import com.skygoto.app.domain.model.MountStatus
import com.skygoto.app.domain.model.MoveRate
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.ui.components.ResultBannerConfig
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlUiState(
    val isConnected: Boolean = false,
    val mountStatus: MountStatus = MountStatus(),
    val selectedRate: MoveRate = MoveRate.GUIDE,
    val isGotoInProgress: Boolean = false,
    val gotoResult: ResultBannerConfig? = null,  // GOTO 成功提示
    val result: ResultBannerConfig? = null        // 错误/警告统一提示
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val mountRepository: MountRepository
) : ViewModel() {
    
    // 标记控制页面是否可见（用于页面可见性控制的轮询）
    private var isPageVisible = false
    
    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            var wasConnected = false
            mountRepository.isConnected.collect { connected ->
                // 检测连接断开（从 true 变为 false）
                if (wasConnected && !connected) {
                    _uiState.update { it.copy(result = ResultBannerConfig.error("连接已断开，请重新连接", duration = 0L)) }
                }
                wasConnected = connected
                _uiState.update { it.copy(isConnected = connected) }
                // 连接断开时停止轮询（连接时的启动由页面可见性控制）
                if (!connected) {
                    AppLogger.i("ControlViewModel", "赤道仪已断开，停止状态轮询")
                    mountRepository.stopPolling()
                }
            }
        }
        viewModelScope.launch {
            mountRepository.mountStatus.collect { status ->
                _uiState.update { it.copy(mountStatus = status) }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        mountRepository.stopPolling()
    }
    
    fun selectRate(rate: MoveRate) {
        AppLogger.i("UserAction", "选择速率: ${rate.name}")
        _uiState.update { it.copy(selectedRate = rate) }
        // 立即发送速率到赤道仪
        viewModelScope.launch {
            mountRepository.setMoveRate(rate)
        }
    }
    
    fun move(direction: Direction) {
        AppLogger.i("UserAction", "移动方向: ${direction.name}, 速率: ${_uiState.value.selectedRate.name}")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                showActionResult("移动失败: 未连接赤道仪")
                return@launch
            }
            val result = mountRepository.move(direction)
            result.fold(
                onSuccess = { AppLogger.i("UserAction", "移动成功: ${direction.name}") },
                onFailure = { showActionResult("移动失败: ${it.message}") }
            )
        }
    }
    
    fun stopMove() {
        AppLogger.i("UserAction", "停止移动")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                showActionResult("停止失败: 未连接赤道仪")
                return@launch
            }
            val result = mountRepository.stopMove()
            result.fold(
                onSuccess = { AppLogger.i("UserAction", "停止移动成功") },
                onFailure = { showActionResult("停止失败: ${it.message}") }
            )
        }
    }
    
    fun toggleTracking() {
        val current = _uiState.value.mountStatus.tracking
        AppLogger.i("UserAction", if (current) "停止追踪" else "开始追踪")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                showActionResult("追踪切换失败: 未连接赤道仪")
                return@launch
            }
            if (current) {
                val result = mountRepository.stopTracking()
                result.fold(
                    onSuccess = { AppLogger.i("UserAction", "停止追踪成功") },
                    onFailure = { showActionResult("停止追踪失败: ${it.message}") }
                )
            } else {
                val result = mountRepository.startTracking()
                result.fold(
                    onSuccess = { AppLogger.i("UserAction", "开始追踪成功") },
                    onFailure = { showActionResult("开始追踪失败: ${it.message}") }
                )
            }
            // 等待500ms后重新获取状态确认
            kotlinx.coroutines.delay(500)
            mountRepository.getStatus()
        }
    }
    
    fun goto(ra: String, dec: String) {
        AppLogger.i("UserAction", "GOTO: RA=$ra, Dec=$dec")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                _uiState.update { it.copy(isGotoInProgress = false, result = ResultBannerConfig.error("GOTO失败: 未连接赤道仪")) }
                return@launch
            }
            
            val result = mountRepository.setTargetAndGoto(ra, dec)
            
            result.fold(
                onSuccess = { gotoResult ->
                    when (gotoResult) {
                        is GotoResult.Success -> {
                            AppLogger.i("UserAction", "GOTO 开始: RA=$ra, Dec=$dec")
                            _uiState.update { it.copy(isGotoInProgress = false, gotoResult = ResultBannerConfig.success("GOTO 已开始"), result = null) }
                        }
                        is GotoResult.GotoError -> {
                            AppLogger.w("UserAction", "GOTO 错误: ${gotoResult.message}")
                            _uiState.update { it.copy(isGotoInProgress = false, result = ResultBannerConfig.error(gotoResult.message)) }
                        }
                    }
                },
                onFailure = { e ->
                    AppLogger.e("UserAction", "GOTO 失败: ${e.message}", e)
                    _uiState.update { it.copy(isGotoInProgress = false, result = ResultBannerConfig.error("GOTO失败: ${e.message}")) }
                }
            )
        }
    }
    
    fun cancelGoto() {
        AppLogger.i("UserAction", "取消 GOTO")
        viewModelScope.launch {
            mountRepository.cancelGoto()
            _uiState.update { it.copy(isGotoInProgress = false, gotoResult = ResultBannerConfig.success("GOTO 已取消")) }
        }
    }
    
    fun clearGotoResult() {
        AppLogger.i("UserAction", "清除 GOTO 结果")
        _uiState.update { it.copy(gotoResult = null, result = null) }
    }
    
    /**
     * 当用户进入控制页面时调用（ON_RESUME）
     * 如果已连接，则恢复轮询
     */
    fun startPollingWhenVisible() {
        isPageVisible = true
        if (_uiState.value.isConnected) {
            AppLogger.i("ControlViewModel", "进入控制页面，恢复轮询")
            mountRepository.resumePolling()
        }
    }
    
    /**
     * 当用户离开控制页面时调用（ON_PAUSE）
     * 停止轮询
     */
    fun stopPollingWhenHidden() {
        isPageVisible = false
        AppLogger.i("ControlViewModel", "离开控制页面，暂停轮询")
        mountRepository.pausePolling()
    }
    
    private fun showActionResult(message: String) {
        AppLogger.w("UserAction", message)
        _uiState.update { it.copy(result = ResultBannerConfig.error(message)) }
    }
    
    fun home() {
        AppLogger.i("UserAction", "回零位")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                showActionResult("回零位失败: 未连接赤道仪")
                return@launch
            }
            val result = mountRepository.home()
            result.fold(
                onSuccess = { _uiState.update { it.copy(gotoResult = ResultBannerConfig.success("回零位完成")) } },
                onFailure = { e -> showActionResult("回零位失败: ${e.message}") }
            )
        }
    }
    
    fun setZeroPosition() {
        AppLogger.i("UserAction", "置零位")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) {
                showActionResult("置零位失败: 未连接赤道仪")
                return@launch
            }
            val result = mountRepository.setZeroPosition()
            result.fold(
                onSuccess = { _uiState.update { it.copy(gotoResult = ResultBannerConfig.success("已设为零位")) } },
                onFailure = { e -> showActionResult("置零位失败: ${e.message}") }
            )
        }
    }
}