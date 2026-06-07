package com.skygoto.app.ui.screens.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.*
import com.skygoto.app.domain.model.SolarPositionCalculator
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.ui.components.ResultBannerConfig
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlUiState(
    val isConnected: Boolean = false,
    val mountStatus: MountStatus = MountStatus(),
    val selectedRate: MoveRate = MoveRate.GUIDE,
    val isGotoInProgress: Boolean = false,
    val gotoResult: ResultBannerConfig? = null,
    val alignSession: AlignSession = AlignSession(),
    val alignStarList: List<BrightStar> = emptyList(),
    val alignCurrentTargetRa: String = "",
    val alignCurrentTargetDec: String = "",
    val selectedStarName: String = "",
    val starAltAzList: List<Pair<BrightStar, Pair<Double, Double>>> = emptyList(),
    val result: ResultBannerConfig? = null
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val mountRepository: MountRepository
) : ViewModel() {

    private var isPageVisible = false
    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var wasConnected = false
            mountRepository.isConnected.collect { connected ->
                if (wasConnected && !connected) {
                    _uiState.update {
                        it.copy(result = ResultBannerConfig.error("连接已断开，请重新连接", duration = 0L))
                    }
                }
                wasConnected = connected
                _uiState.update { it.copy(isConnected = connected) }
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

    // ===================== 速率选择 =====================
    fun selectRate(rate: MoveRate) {
        AppLogger.i("UserAction", "选择速率: ${rate.name}")
        _uiState.update { it.copy(selectedRate = rate) }
        viewModelScope.launch { mountRepository.setMoveRate(rate) }
    }

    // ===================== 方向键 =====================
    fun move(direction: Direction) {
        viewModelScope.launch {
            if (!_uiState.value.isConnected) { showActionResult("移动失败: 未连接赤道仪"); return@launch }
            mountRepository.move(direction).fold(
                onSuccess = { AppLogger.i("UserAction", "移动成功: ${direction.name}") },
                onFailure = { showActionResult("移动失败: ${it.message}") }
            )
        }
    }

    fun stopMove() {
        viewModelScope.launch {
            if (!_uiState.value.isConnected) { showActionResult("停止失败: 未连接赤道仪"); return@launch }
            mountRepository.stopMove().fold(
                onSuccess = { AppLogger.i("UserAction", "停止移动成功") },
                onFailure = { showActionResult("停止失败: ${it.message}") }
            )
        }
    }

    // ===================== 追踪 =====================
    fun toggleTracking() {
        val current = _uiState.value.mountStatus.tracking
        AppLogger.i("UserAction", if (current) "停止追踪" else "开始追踪")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) { showActionResult("追踪切换失败: 未连接赤道仪"); return@launch }
            if (current) mountRepository.stopTracking() else mountRepository.startTracking()
            delay(500); mountRepository.getStatus()
        }
    }

    // ===================== GOTO =====================
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

    fun clearGotoResult() { _uiState.update { it.copy(gotoResult = null, result = null) } }

    fun home() {
        AppLogger.i("UserAction", "回零位")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) { showActionResult("回零位失败: 未连接赤道仪"); return@launch }
            mountRepository.home().fold(
                onSuccess = { _uiState.update { it.copy(gotoResult = ResultBannerConfig.success("回零位完成")) } },
                onFailure = { e -> showActionResult("回零位失败: ${e.message}") }
            )
        }
    }

    fun setZeroPosition() {
        AppLogger.i("UserAction", "置零位")
        viewModelScope.launch {
            if (!_uiState.value.isConnected) { showActionResult("置零位失败: 未连接赤道仪"); return@launch }
            mountRepository.setZeroPosition().fold(
                onSuccess = { _uiState.update { it.copy(gotoResult = ResultBannerConfig.success("已设为零位")) } },
                onFailure = { e -> showActionResult("置零位失败: ${e.message}") }
            )
        }
    }

    // ===================== 星图 =====================
    fun setAlignStarCount(count: Int) {
        _uiState.update { it.copy(alignSession = it.alignSession.copy(totalStars = count.coerceIn(1, 9))) }
    }




    /** 计算亮星列表的当前 Alt/Az（用于列表显示，不含星图） */
    fun computeStarAltAz() {
        AppLogger.i("AlignCalc", "computeStarAltAz: 开始")
        viewModelScope.launch {
            try {
                AppLogger.i("AlignCalc", "computeStarAltAz: 正在获取位置")
                val locResult = mountRepository.getLocation()
                AppLogger.i("AlignCalc", "computeStarAltAz: getLocation 返回: $locResult")
                locResult.onSuccess { (latStr, lonStr) ->
                    AppLogger.i("AlignCalc", "computeStarAltAz: lat='$latStr' lon='$lonStr'")
                    val lat = parseLatLonSimple(latStr)
                    val lon = -parseLatLonSimple(lonStr)
                    AppLogger.i("AlignCalc", "computeStarAltAz: lat=$lat lon=$lon")
                    if (lat != 0.0 && lon != 0.0) {
                        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
                        AppLogger.i("AlignCalc", "computeStarAltAz: 开始计算67颗星")
                        val items = BrightStarCatalog.stars.map { star ->
                            val (jnowRa, jnowDec) = SolarPositionCalculator.applyPrecession(star.ra, star.dec, jd)
                            val raRad = SolarPositionCalculator.parseRA(jnowRa)
                            val decRad = SolarPositionCalculator.parseDec(jnowDec)
                            val (alt, az) = SolarPositionCalculator.getAltitudeAzimuth(raRad, decRad, lat, lon, jd)
                            star to Pair(alt, az)
                        }
                        val sorted = items.filter { (_, c) -> c.first > 3.0 }.sortedByDescending { (_, c) -> c.first }
                        AppLogger.i("AlignCalc", "computeStarAltAz: 可见星 ${sorted.size}颗")
                        _uiState.update { it.copy(starAltAzList = sorted) }
                    } else {
                        AppLogger.w("AlignCalc", "computeStarAltAz: 位置为0，跳过")
                    }
                }
                locResult.onFailure { e ->
                    AppLogger.e("AlignCalc", "computeStarAltAz: getLocation失败: ${e.message}")
                }
            } catch (e: Exception) {
                AppLogger.e("AlignCalc", "计算星位异常: ${e.message}", e)
            }
        }
    }

    private fun parseLatLonSimple(dms: String): Double {
        val cleaned = dms.trim().replace("*", ":")
        val sign = if (cleaned.startsWith('-')) -1.0 else 1.0
        val parts = cleaned.trimStart('+', '-').split(":")
        if (parts.size < 2) return 0.0
        val d = parts[0].toDoubleOrNull() ?: return 0.0
        val m = parts[1].toDoubleOrNull() ?: 0.0
        val s = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        return sign * (d + m / 60.0 + s / 3600.0)
    }

    fun selectStarFromChart(star: BrightStar) {
        _uiState.update { it.copy(alignCurrentTargetRa = star.ra, alignCurrentTargetDec = star.dec, selectedStarName = star.name) }
    }
    fun selectPrevAlignStar() {
        val stars = _uiState.value.alignStarList
        val current = _uiState.value.selectedStarName
        val idx = stars.indexOfFirst { it.name == current }
        if (idx > 0) selectStarFromChart(stars[idx - 1])
    }

    fun selectNextAlignStar() {
        val stars = _uiState.value.alignStarList
        val current = _uiState.value.selectedStarName
        val idx = stars.indexOfFirst { it.name == current }
        if (idx >= 0 && idx < stars.size - 1) selectStarFromChart(stars[idx + 1])
        else if (idx < 0 && stars.isNotEmpty()) selectStarFromChart(stars[0])
    }


    fun loadAlignStarList() {
        AppLogger.i("AlignCalc", "loadAlignStarList: 加载67颗星")
        _uiState.update { it.copy(alignStarList = BrightStarCatalog.stars) }
    }

    fun selectAlignStar(star: BrightStar) {
        _uiState.update { it.copy(alignCurrentTargetRa = star.ra, alignCurrentTargetDec = star.dec) }
    }

    fun setAlignTargetInput(ra: String, dec: String) {
        _uiState.update { it.copy(alignCurrentTargetRa = ra, alignCurrentTargetDec = dec) }
    }

    fun startAlignment() {
        val cur = _uiState.value
        if (!cur.isConnected) { showActionResult("对齐失败: 未连接赤道仪"); return }
        AppLogger.i("UserAction", "开始对齐流程")
        mountRepository.pausePolling()
        viewModelScope.launch {
            // 阶段1：回零
            _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.HOMING)) }
            val homeResult = mountRepository.homeAndWait()
            homeResult.fold(
                onSuccess = {
                    // 阶段2：启动对齐模式
                    AppLogger.i("UserAction", "回零完成，启动对齐")
                    _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.STARTING)) }
                    try {
                        val alignResult = mountRepository.startAlign(_uiState.value.alignSession.totalStars)
                        alignResult.fold(
                            onSuccess = {
                                AppLogger.i("UserAction", "对齐模式已启动")
                                _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.SELECTING_STAR), alignCurrentTargetRa = "", alignCurrentTargetDec = "") }
                            },
                            onFailure = { err ->
                                AppLogger.e("UserAction", "启动对齐模式失败: ${err.message}")
                                if (isPageVisible) mountRepository.resumePolling()
                                _uiState.update { it.copy(alignSession = AlignSession(), result = ResultBannerConfig.error("启动对齐失败: ${err.message}")) }
                            }
                        )
                    } catch (e: Exception) {
                        AppLogger.e("UserAction", "启动对齐异常: ${e.message}", e)
                        if (isPageVisible) mountRepository.resumePolling()
                        _uiState.update { it.copy(alignSession = AlignSession(), result = ResultBannerConfig.error("启动对齐异常: ${e.message}")) }
                    }
                },
                onFailure = { err ->
                    AppLogger.e("UserAction", "回零失败: ${err.message}")
                    if (isPageVisible) mountRepository.resumePolling()
                    _uiState.update { it.copy(alignSession = AlignSession(), result = ResultBannerConfig.error("回零失败: ${err.message}")) }
                }
            )
        }
    }

    fun alignGoto() {
        val cur = _uiState.value
        val ra = cur.alignCurrentTargetRa; val dec = cur.alignCurrentTargetDec
        if (ra.isBlank() || dec.isBlank()) { showActionResult("请先选择或输入目标星坐标"); return }
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val (jnowRa, jnowDec) = SolarPositionCalculator.applyPrecession(ra, dec, jd)
        AppLogger.i("UserAction", "对齐 GOTO: J2000($ra, $dec) → JNow($jnowRa, $jnowDec)")
        viewModelScope.launch {
            _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.GOTOING)) }
            try {
                val gotoResult = mountRepository.setTargetAndGoto(jnowRa, jnowDec)
                gotoResult.fold(
                    onSuccess = { result ->
                        when (result) {
                            is GotoResult.Success -> {
                                // 等待 GOTO 执行完成（轮询 :D# 直到停止）
                                for (i in 0..<120) {
                                    delay(500)
                                    try {
                                        mountRepository.sendRawCommand(":D")
                                    } catch (_: Exception) { }
                                    delay(50)
                                    val resp = try {
                                        mountRepository.readAvailableBytes().getOrNull() ?: ""
                                    } catch (_: Exception) { "" }
                                    if (resp.isBlank()) continue
                                    if (resp[0].code == 127) continue  // 0x7f = 正在移动
                                    break  // 停止
                                }
                                _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.CENTERING)) }
                                val starName = cur.alignStarList.firstOrNull { s -> s.ra == ra }?.name ?: "目标星"
                                showActionResult("GOTO 到 $starName，请用方向键居中")
                            }
                            is GotoResult.GotoError -> {
                                _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.SELECTING_STAR), result = ResultBannerConfig.error("GOTO 错误: ${result.message}")) }
                            }
                        }
                    },
                    onFailure = { e -> _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.SELECTING_STAR), result = ResultBannerConfig.error("GOTO 失败: ${e.message}")) } }
                )
            } catch (e: Exception) {
                AppLogger.e("UserAction", "对齐GOTO异常: ${e.message}", e)
                _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.SELECTING_STAR), result = ResultBannerConfig.error("GOTO异常: ${e.message}")) }
            }
        }
    }

    fun alignSync() {
        val session = _uiState.value.alignSession
        AppLogger.i("UserAction", "对齐同步: 星 ${session.completedCount + 1}/${session.totalStars}")
        viewModelScope.launch {
            _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.SYNCING)) }
            try {
                val syncResult = if (_uiState.value.alignSession.state > AlignState.IDLE) mountRepository.acceptAlignStar() else mountRepository.syncToCurrentPosition()
                syncResult.fold(
                    onSuccess = {
                        AppLogger.i("UserAction", "同步成功")
                        val star = _uiState.value.alignStarList.firstOrNull { it.ra == _uiState.value.alignCurrentTargetRa && it.dec == _uiState.value.alignCurrentTargetDec }
                        val syncRecord = AlignStarSync(starData = star ?: BrightStar("未知", "", "", 99.0), success = true)
                        val updatedSession = _uiState.value.alignSession.copy(completedStars = _uiState.value.alignSession.completedStars + syncRecord, currentStarIndex = _uiState.value.alignSession.currentStarIndex + 1)
                        if (updatedSession.isAllCompleted) {
                            AppLogger.i("UserAction", "所有 ${updatedSession.totalStars} 星完成，结束对齐")
                            _uiState.update { it.copy(alignSession = updatedSession.copy(state = AlignState.MODEL_BUILDING)) }
                            mountRepository.finishAlign().fold(
                                onSuccess = { _uiState.update { it.copy(alignSession = updatedSession.copy(state = AlignState.COMPLETE), gotoResult = ResultBannerConfig.success("对齐完成！模型已保存")) } },
                                onFailure = { e -> _uiState.update { it.copy(alignSession = updatedSession.copy(state = AlignState.COMPLETE), result = ResultBannerConfig.error("保存模型失败: ${e.message}")) } }
                            )
                            if (isPageVisible) mountRepository.resumePolling()
                        } else {
                            _uiState.update { it.copy(alignSession = updatedSession.copy(state = AlignState.SELECTING_STAR), alignCurrentTargetRa = "", alignCurrentTargetDec = "", gotoResult = ResultBannerConfig.success("星 ${updatedSession.completedCount}/${updatedSession.totalStars} 同步成功，请选择下一颗")) }
                        }
                    },
                    onFailure = { e ->
                        AppLogger.e("UserAction", "同步失败: ${e.message}")
                        _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.CENTERING), result = ResultBannerConfig.error("同步失败: ${e.message}")) }
                    }
                )
            } catch (e: Exception) {
                AppLogger.e("UserAction", "对齐同步异常: ${e.message}", e)
                _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.CENTERING), result = ResultBannerConfig.error("同步异常: ${e.message}")) }
            }
        }
    }

    fun cancelAlignment() {
        AppLogger.i("UserAction", "取消对齐")
        viewModelScope.launch {
            _uiState.update { it.copy(alignSession = it.alignSession.copy(state = AlignState.CANCELLING)) }
            AppLogger.i("UserAction", "取消对齐：发送 :hC# 回零")
            try {
                mountRepository.home()
                for (i in 0..<60) {
                    delay(1000)
                    try {
                        mountRepository.sendRawCommand(":D")
                    } catch (_: Exception) { }
                    delay(50)
                    val resp = try {
                        mountRepository.readAvailableBytes().getOrNull() ?: ""
                    } catch (_: Exception) { "" }
                    if (resp.isBlank()) continue
                    if (resp[0].code == 127) continue
                    break
                }
            } catch (e: Exception) {
                AppLogger.e("UserAction", "取消对齐异常: ${e.message}", e)
            }
            if (isPageVisible) mountRepository.resumePolling()
            _uiState.update { it.copy(alignSession = AlignSession(), alignCurrentTargetRa = "", alignCurrentTargetDec = "", gotoResult = ResultBannerConfig.success("对齐已取消，赤道仪已回零")) }
        }
    }

    fun clearAlignModel() {
        viewModelScope.launch {
            mountRepository.clearAlignModel().fold(
                onSuccess = { showActionResult("对齐数据已清除") },
                onFailure = { e -> showActionResult("清除失败: ${e.message}") }
            )
        }
    }

    // ===================== 轮询控制 =====================
    fun startPollingWhenVisible() {
        isPageVisible = true
        if (_uiState.value.isConnected && _uiState.value.alignSession.state == AlignState.IDLE) {
            AppLogger.i("ControlViewModel", "进入控制页面，恢复轮询")
            mountRepository.resumePolling()
        }
    }

    fun stopPollingWhenHidden() {
        isPageVisible = false
        AppLogger.i("ControlViewModel", "离开控制页面，暂停轮询")
        mountRepository.pausePolling()
    }

    // ===================== 工具方法 =====================
    private fun showActionResult(message: String) {
        AppLogger.w("UserAction", message)
        _uiState.update { it.copy(result = ResultBannerConfig.error(message)) }
    }
}
