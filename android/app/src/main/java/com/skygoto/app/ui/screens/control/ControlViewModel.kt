package com.skygoto.app.ui.screens.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.*
import com.skygoto.app.domain.repository.MountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlUiState(
    val isConnected: Boolean = false,
    val mountStatus: MountStatus = MountStatus(),
    val selectedRate: MoveRate = MoveRate.GUIDE,
    val isGotoInProgress: Boolean = false,
    val gotoResult: String? = null
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val mountRepository: MountRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            mountRepository.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }
        viewModelScope.launch {
            mountRepository.mountStatus.collect { status ->
                _uiState.update { it.copy(mountStatus = status) }
            }
        }
    }
    
    fun selectRate(rate: MoveRate) {
        _uiState.update { it.copy(selectedRate = rate) }
    }
    
    fun move(direction: Direction) {
        viewModelScope.launch {
            mountRepository.move(direction, _uiState.value.selectedRate)
        }
    }
    
    fun stopMove() {
        viewModelScope.launch {
            mountRepository.stopMove()
        }
    }
    
    fun toggleTracking() {
        viewModelScope.launch {
            val current = _uiState.value.mountStatus.tracking
            if (current) {
                mountRepository.stopTracking()
            } else {
                mountRepository.startTracking()
            }
        }
    }
    
    fun goto(ra: String, dec: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGotoInProgress = true, gotoResult = null) }
            
            val result = mountRepository.setTargetAndGoto(ra, dec)
            
            result.fold(
                onSuccess = { gotoResult ->
                    when (gotoResult) {
                        is GotoResult.Success -> {
                            _uiState.update { it.copy(isGotoInProgress = false, gotoResult = "GOTO 已开始") }
                        }
                        is GotoResult.Error -> {
                            _uiState.update { it.copy(isGotoInProgress = false, gotoResult = gotoResult.message) }
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isGotoInProgress = false, gotoResult = "错误: ${e.message}") }
                }
            )
        }
    }
    
    fun cancelGoto() {
        viewModelScope.launch {
            mountRepository.cancelGoto()
            _uiState.update { it.copy(isGotoInProgress = false, gotoResult = "GOTO 已取消") }
        }
    }
    
    fun clearGotoResult() {
        _uiState.update { it.copy(gotoResult = null) }
    }
}
