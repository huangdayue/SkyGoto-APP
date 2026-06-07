package com.skygoto.app.ui.screens.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.PecInfo
import com.skygoto.app.domain.model.PecState
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PecUiState(
    val pecInfo: PecInfo = PecInfo(),
    val curveData: List<Int> = emptyList(),
    val selectedIndex: Int = -1,
    val editValue: Int = 0,
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String = "",
    val messageIsError: Boolean = false
)

@HiltViewModel
class PecViewModel @Inject constructor(
    private val mountRepository: MountRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PecViewModel"
        private const val POLL_INTERVAL_MS = 1000L
        private const val POLL_INTERVAL_RECORDING_MS = 500L
    }

    private val _uiState = MutableStateFlow(PecUiState())
    val uiState: StateFlow<PecUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            mountRepository.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) startPolling() else stopPolling()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    // ===================== 轮询 =====================

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value.pecInfo.state
                val interval = if (state == PecState.RECORDING || state == PecState.READY_RECORD)
                    POLL_INTERVAL_RECORDING_MS else POLL_INTERVAL_MS
                delay(interval)
                refreshPecState()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ===================== 状态刷新 =====================

    fun refreshPecState() {
        viewModelScope.launch {
            val result = mountRepository.getPecState()
            result.fold(
                onSuccess = { info ->
                    _uiState.update { it.copy(pecInfo = info) }
                    // 录制完成时自动加载曲线
                    if (info.state != PecState.RECORDING && info.state != PecState.READY_RECORD) {
                        val curve = _uiState.value.curveData
                        if (curve.isEmpty() && info.isRecorded && info.bufferSizeSeconds > 0) {
                            loadCurve()
                        }
                    }
                },
                onFailure = { /* 静默失败，下次轮询再试 */ }
            )
        }
    }

    // ===================== PEC 控制 =====================

    fun pecPlay() = doAction("启用回放") {
        mountRepository.pecPlay()
        delay(200)
        refreshPecState()
    }

    fun pecStop() = doAction("禁用") {
        mountRepository.pecStop()
        delay(200)
        refreshPecState()
    }

    fun pecRecord() = doAction("录制") {
        mountRepository.pecRecord()
        delay(200)
        refreshPecState()
    }

    fun pecClear() = doAction("清空") {
        mountRepository.pecClear()
        _uiState.update { it.copy(curveData = emptyList(), selectedIndex = -1) }
        delay(200)
        refreshPecState()
    }

    fun pecSave() = doAction("保存") {
        _uiState.update { it.copy(isSaving = true, message = "") }
        mountRepository.pecSave()
        _uiState.update { it.copy(isSaving = false) }
    }

    fun saveCurveToMount() {
        viewModelScope.launch {
            val values = _uiState.value.curveData
            if (values.isEmpty()) return@launch
            _uiState.update { it.copy(isSaving = true, message = "") }
            val result = mountRepository.savePecCurve(values)
            result.fold(
                onSuccess = {
                    mountRepository.pecSave()
                    showMessage("曲线已保存到 NV")
                },
                onFailure = { e -> showError("保存失败: ${e.message}") }
            )
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    // ===================== 曲线数据 =====================

    fun loadCurve() {
        val bufferSize = _uiState.value.pecInfo.bufferSizeSeconds
        if (bufferSize <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = mountRepository.loadPecCurve(bufferSize)
            result.fold(
                onSuccess = { curve ->
                    _uiState.update { it.copy(curveData = curve, isLoading = false) }
                    AppLogger.i(TAG, "PEC curve loaded: ${curve.size} entries")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false) }
                    AppLogger.e(TAG, "Failed to load PEC curve: ${e.message}")
                }
            )
        }
    }

    // ===================== 编辑 =====================

    fun selectIndex(index: Int) {
        val data = _uiState.value.curveData
        if (index < 0 || index >= data.size) return
        _uiState.update {
            it.copy(selectedIndex = index, editValue = data[index])
        }
    }

    fun updateEditValue(value: Int) {
        val index = _uiState.value.selectedIndex
        if (index < 0) return
        val clamped = value.coerceIn(-127, 127)
        _uiState.update { it.copy(editValue = clamped) }
    }

    fun applyEdit() {
        val s = _uiState.value
        val index = s.selectedIndex
        if (index < 0) return
        val newValue = s.editValue.coerceIn(-127, 127)
        val updated = s.curveData.toMutableList()
        updated[index] = newValue
        _uiState.update {
            it.copy(curveData = updated, selectedIndex = -1)
        }
        // 写入赤道仪
        viewModelScope.launch {
            mountRepository.writePecEntry(index, newValue)
        }
    }

    fun resetAtSelected() {
        updateEditValue(0)
        applyEdit()
    }

    fun cycleShiftForward() {
        val d = _uiState.value.curveData.toMutableList()
        if (d.isEmpty()) return
        val last = d.removeLast()
        d.add(0, last)
        _uiState.update { it.copy(curveData = d) }
    }

    fun cycleShiftBackward() {
        val d = _uiState.value.curveData.toMutableList()
        if (d.isEmpty()) return
        val first = d.removeFirst()
        d.add(first)
        _uiState.update { it.copy(curveData = d) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = "", messageIsError = false) }
    }

    // ===================== 内部方法 =====================

    private fun doAction(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                showMessage("$name 成功")
            } catch (e: Exception) {
                showError("$name 失败: ${e.message}")
            }
        }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg, messageIsError = false) }
    }

    private fun showError(msg: String) {
        _uiState.update { it.copy(message = msg, messageIsError = true) }
    }
}
