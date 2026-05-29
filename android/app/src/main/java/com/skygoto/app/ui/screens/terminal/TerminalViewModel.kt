package com.skygoto.app.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val mountRepository: MountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalVM"
        private const val POLL_INTERVAL_MS = 100L
        private const val SILENCE_TIMEOUT_MS = 500L
        private const val MAX_HISTORY_SIZE = 500
        private const val TRIM_SIZE = 400
        private const val HISTORY_FILE = "terminal_history.json"
    }

    // ========== 持久化文件 ==========

    private val historyFile by lazy { File(context.filesDir, HISTORY_FILE) }

    // ========== UI 状态 ==========

    private val _messages = MutableStateFlow<List<TerminalMessage>>(emptyList())
    val messages: StateFlow<List<TerminalMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _hexModeEnabled = MutableStateFlow(false)
    val hexModeEnabled: StateFlow<Boolean> = _hexModeEnabled.asStateFlow()

    private val _showNotConnectedHint = MutableStateFlow(false)
    val showNotConnectedHint: StateFlow<Boolean> = _showNotConnectedHint.asStateFlow()

    // ========== 内部状态 ==========

    private var pollingJob: Job? = null
    private var isPageVisible = false
    private val partialBuffer = StringBuilder()
    private var lastByteTime = 0L

    init {
        loadHistory()
        viewModelScope.launch {
            mountRepository.isConnected.collect { connected ->
                _isConnected.value = connected
                if (!connected) {
                    stopPolling()
                    flushPartialBuffer()
                }
            }
        }
    }

    // ========== 输入 ==========

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setCommandFromReference(cmd: String) {
        _inputText.value = cmd
    }

    // ========== 发送 ==========

    fun sendCommand() {
        val command = _inputText.value.trim()
        if (command.isEmpty()) return

        if (!_isConnected.value) {
            _showNotConnectedHint.value = true
            return
        }

        flushPartialBuffer()

        val userMsg = TerminalMessage(
            content = command,
            isFromUser = true
        )
        addMessage(userMsg)

        viewModelScope.launch {
            mountRepository.sendRawCommand(command)
        }

        _inputText.value = ""
    }

    fun dismissNotConnectedHint() {
        _showNotConnectedHint.value = false
    }

    // ========== Hex 模式 ==========

    fun toggleHexMode() {
        _hexModeEnabled.update { !it }
    }

    // ========== 轮询 ==========

    fun startPolling() {
        isPageVisible = true
        if (!_isConnected.value) return

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && isPageVisible) {
                val result = mountRepository.readAvailableBytes()
                result.onSuccess { data ->
                    if (data.isNotEmpty()) {
                        lastByteTime = System.currentTimeMillis()
                        for (ch in data) {
                            if (ch == '#') {
                                flushPartialBuffer()
                            } else {
                                partialBuffer.append(ch)
                            }
                        }
                    }
                }

                if (partialBuffer.isNotEmpty() &&
                    System.currentTimeMillis() - lastByteTime > SILENCE_TIMEOUT_MS
                ) {
                    flushPartialBuffer()
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        isPageVisible = false
        pollingJob?.cancel()
        pollingJob = null
    }

    // ========== 清屏 ==========

    fun clearMessages() {
        partialBuffer.clear()
        _messages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { deleteHistoryFile() }
    }

    // ========== 内部方法 ==========

    private fun flushPartialBuffer() {
        if (partialBuffer.isEmpty()) return
        val mountMsg = TerminalMessage(
            content = partialBuffer.toString(),
            isFromUser = false,
            hexMode = _hexModeEnabled.value
        )
        addMessage(mountMsg)
        partialBuffer.clear()
    }

    private fun addMessage(msg: TerminalMessage) {
        _messages.update { current ->
            val newList = current + msg
            if (newList.size > MAX_HISTORY_SIZE) {
                newList.drop(newList.size - TRIM_SIZE)
            } else {
                newList
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistMessage(msg)
        }
    }

    // ========== 持久化 ==========

    private fun persistMessage(msg: TerminalMessage) {
        try {
            val json = JSONObject().apply {
                put("content", msg.content)
                put("isFromUser", msg.isFromUser)
                put("timestamp", msg.timestamp)
                put("hexMode", msg.hexMode)
            }
            historyFile.appendText(json.toString() + "\n")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to persist message", e)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!historyFile.exists()) return@launch
                val messages = historyFile.readLines()
                    .mapNotNull { line ->
                        try {
                            val obj = JSONObject(line)
                            TerminalMessage(
                                content = obj.getString("content"),
                                isFromUser = obj.getBoolean("isFromUser"),
                                timestamp = obj.getLong("timestamp"),
                                hexMode = obj.optBoolean("hexMode", false)
                            )
                        } catch (_: Exception) { null }
                    }
                    .takeLast(MAX_HISTORY_SIZE)
                _messages.value = messages
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load history", e)
            }
        }
    }

    private fun deleteHistoryFile() {
        try { historyFile.delete() } catch (_: Exception) {}
    }
}
