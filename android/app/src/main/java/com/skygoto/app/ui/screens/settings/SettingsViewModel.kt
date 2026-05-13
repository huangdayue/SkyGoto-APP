package com.skygoto.app.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.data.preferences.SettingsDataStore
import com.skygoto.app.data.protocol.LX200Commands as Cmd
import com.skygoto.app.domain.repository.MountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SettingsUiState(
    // 位置设置
    val longitude: String = "120:00:00",
    val latitude: String = "30:00:00",
    val locationFormat: String = "DMS", // "DMS" or "DECIMAL"
    val autoLocation: Boolean = false,
    val isGettingLocation: Boolean = false,
    val locationError: String? = null,
    
    // 时区设置
    val timezone: String = "Asia/Shanghai",
    val autoTimezone: Boolean = true,
    val availableTimezones: List<String> = emptyList(),
    
    // 赤道仪时间
    val mountTime: String = "",
    val mountTimeError: String? = null,
    val isSyncingTime: Boolean = false,
    
    // 权限状态
    val hasLocationPermission: Boolean = false,
    
    // 赤道仪连接状态
    val isMountConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val mountRepository: MountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 常用时区列表
    private val commonTimezones = listOf(
        "Asia/Shanghai",
        "Asia/Tokyo",
        "Asia/Seoul",
        "Asia/Hong_Kong",
        "Asia/Singapore",
        "Asia/Dubai",
        "Asia/Kolkata",
        "Europe/London",
        "Europe/Paris",
        "Europe/Berlin",
        "Europe/Moscow",
        "America/New_York",
        "America/Los_Angeles",
        "America/Chicago",
        "Pacific/Auckland",
        "Australia/Sydney"
    )
    
    init {
        loadSettings()
        _uiState.update { it.copy(availableTimezones = commonTimezones) }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // 加载位置设置
            launch {
                settingsDataStore.longitude.collect { value ->
                    _uiState.update { it.copy(longitude = value) }
                }
            }
            launch {
                settingsDataStore.latitude.collect { value ->
                    _uiState.update { it.copy(latitude = value) }
                }
            }
            launch {
                settingsDataStore.locationFormat.collect { value ->
                    _uiState.update { it.copy(locationFormat = value) }
                }
            }
            launch {
                settingsDataStore.autoLocation.collect { value ->
                    _uiState.update { it.copy(autoLocation = value) }
                }
            }
            
            // 加载时区设置
            launch {
                settingsDataStore.timezone.collect { value ->
                    _uiState.update { it.copy(timezone = value) }
                }
            }
            launch {
                settingsDataStore.autoTimezone.collect { value ->
                    _uiState.update { it.copy(autoTimezone = value) }
                }
            }
            
            // 观察赤道仪连接状态
            launch {
                mountRepository.isConnected.collect { connected ->
                    _uiState.update { it.copy(isMountConnected = connected) }
                }
            }
        }
    }
    
    fun updateLongitude(value: String) {
        _uiState.update { it.copy(longitude = value) }
        viewModelScope.launch {
            settingsDataStore.setLongitude(value)
        }
    }
    
    fun updateLatitude(value: String) {
        _uiState.update { it.copy(latitude = value) }
        viewModelScope.launch {
            settingsDataStore.setLatitude(value)
        }
    }
    
    fun updateLocationFormat(format: String) {
        _uiState.update { it.copy(locationFormat = format) }
        viewModelScope.launch {
            settingsDataStore.setLocationFormat(format)
        }
    }
    
    fun setAutoLocation(enabled: Boolean) {
        _uiState.update { it.copy(autoLocation = enabled) }
        viewModelScope.launch {
            settingsDataStore.setAutoLocation(enabled)
        }
        if (enabled) {
            requestLocation()
        }
    }
    
    fun updateTimezone(timezone: String) {
        _uiState.update { it.copy(timezone = timezone) }
        viewModelScope.launch {
            settingsDataStore.setTimezone(timezone)
        }
    }
    
    fun setAutoTimezone(enabled: Boolean) {
        _uiState.update { it.copy(autoTimezone = enabled) }
        viewModelScope.launch {
            settingsDataStore.setAutoTimezone(enabled)
        }
    }
    
    fun updateHasLocationPermission(hasPermission: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = hasPermission) }
    }
    
    @SuppressLint("MissingPermission")
    fun requestLocation() {
        if (!_uiState.value.hasLocationPermission) return
        
        _uiState.update { it.copy(isGettingLocation = true, locationError = null) }
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 尝试获取最后已知位置
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                
                // 转换为 DMS 格式
                val latDMS = decimalToDMS(lat, true)
                val lonDMS = decimalToDMS(lon, false)
                
                _uiState.update { 
                    it.copy(
                        latitude = latDMS,
                        longitude = lonDMS,
                        isGettingLocation = false
                    ) 
                }
                
                // 保存
                viewModelScope.launch {
                    settingsDataStore.setLatitude(latDMS)
                    settingsDataStore.setLongitude(lonDMS)
                }
                
                // 如果赤道仪已连接，同步位置到赤道仪
                if (_uiState.value.isMountConnected) {
                    syncLocationToMount()
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isGettingLocation = false,
                        locationError = "无法获取位置，请确保 GPS 已开启"
                    ) 
                }
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isGettingLocation = false,
                    locationError = e.message ?: "获取位置失败"
                ) 
            }
        }
    }
    
    fun syncMountTime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTime = true, mountTimeError = null) }
            
            try {
                // 获取赤道仪时间
                // 注意：实际项目中需要通过 mountRepository 或 protocol 获取
                // 这里使用模拟数据
                val mountTimeStr = getMountTimeFromDevice()
                
                _uiState.update { 
                    it.copy(
                        mountTime = mountTimeStr,
                        isSyncingTime = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingTime = false,
                        mountTimeError = e.message ?: "同步失败"
                    ) 
                }
            }
        }
    }
    
    fun syncToAndroidTime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTime = true, mountTimeError = null) }
            
            try {
                val calendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("MMddHHmmss", Locale.US)
                val timeStr = dateFormat.format(calendar.time)
                
                // 发送时间同步命令到赤道仪
                // 这里需要通过 protocol 或 repository 发送
                // :SLMMDDHHmmss# 设置本地时间
                // syncTimeToMount(timeStr)
                
                _uiState.update { 
                    it.copy(
                        mountTime = timeStr,
                        isSyncingTime = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingTime = false,
                        mountTimeError = e.message ?: "同步失败"
                    ) 
                }
            }
        }
    }
    
    fun syncToSiderealTime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTime = true, mountTimeError = null) }
            
            try {
                // 计算当前恒星时
                val lst = calculateLocalSiderealTime()
                
                _uiState.update { 
                    it.copy(
                        mountTime = lst,
                        isSyncingTime = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingTime = false,
                        mountTimeError = e.message ?: "同步失败"
                    ) 
                }
            }
        }
    }
    
    /**
     * 同步位置到赤道仪
     */
    private fun syncLocationToMount() {
        viewModelScope.launch {
            val lon = _uiState.value.longitude
            val lat = _uiState.value.latitude
            
            try {
                val result = mountRepository.setLocation(lon, lat)
                result.fold(
                    onSuccess = {
                        // 位置同步成功
                    },
                    onFailure = { e ->
                        _uiState.update { 
                            it.copy(locationError = "同步位置到赤道仪失败: ${e.message}") 
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(locationError = "同步位置到赤道仪失败: ${e.message}") 
                }
            }
        }
    }
    
    private fun getMountTimeFromDevice(): String {
        // 实际项目中通过 protocol 发送 :GT# 获取赤道仪时间
        // 这里返回当前时间作为示例
        val now = Date()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(now)
    }
    
    private fun syncTimeToMount(timeStr: String) {
        // 实际项目中发送 :SLtimeStr# 到赤道仪
        // mountRepository.sendCommand(":SL${timeStr}#")
    }
    
    private fun calculateLocalSiderealTime(): String {
        // 计算本地恒星时
        val calendar = Calendar.getInstance()
        val jd = getJulianDate(calendar)
        val t = (jd - 2451545.0) / 36525.0
        
        // Greenwich Mean Sidereal Time
        var gmst = 280.46061837 + 360.98564736635 * (jd - 2451545.0) +
                   0.000387933 * t * t - t * t * t / 38710000.0
        
        // 转换为度并归一化到 0-360
        gmst = ((gmst % 360) + 360) % 360
        
        // 获取经度
        val lonStr = _uiState.value.longitude
        val lonDeg = parseDMSToDecimal(lonStr)
        
        // 本地恒星时 = GMST + 经度
        var lst = gmst + lonDeg
        lst = ((lst % 360) + 360) % 360
        
        // 转换为时:分:秒
        val hours = lst / 15.0
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        val s = ((hours - h - m / 60.0) * 3600.0).toInt()
        
        return "%02d:%02d:%02d".format(h, m, s)
    }
    
    private fun getJulianDate(calendar: Calendar): Double {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        
        var jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        val frac = (hour + minute / 60.0 + second / 3600.0) / 24.0
        
        return jdn.toDouble() + frac - 0.5
    }
    
    // 工具函数：十进制转 DMS 格式
    private fun decimalToDMS(decimal: Double, isLatitude: Boolean): String {
        val sign = if (decimal < 0) {
            if (isLatitude) "-" else "-"
        } else {
            if (isLatitude) "+" else ""
        }
        
        val absValue = kotlin.math.abs(decimal)
        val degrees = absValue.toInt()
        val minutesFull = ((absValue - degrees) * 60)
        val minutes = minutesFull.toInt()
        val seconds = ((minutesFull - minutes) * 60)
        
        val direction = if (isLatitude) {
            if (decimal >= 0) "N" else "S"
        } else {
            if (decimal >= 0) "E" else "W"
        }
        
        return "%s%02d°%02d'%02d\"%s".format(sign, degrees, minutes, seconds, direction)
    }
    
    // 工具函数：解析 DMS 格式为十进制
    // 支持格式: "120:30:00", "120°30'00\"", "+30°00'00\"N", "-02°30'00\"S"
    private fun parseDMSToDecimal(dms: String): Double {
        if (dms.isBlank()) return 0.0
        
        var cleaned = dms.trim()
        // 移除最后的方向指示符 (N, S, E, W)
        val lastChar = cleaned.last().uppercaseChar()
        if (lastChar in listOf('N', 'S', 'E', 'W', 'D')) {
            cleaned = cleaned.dropLast(1)
        }
        
        // 替换度分秒符号为冒号
        cleaned = cleaned.replace("°", ":").replace("'", ":").replace("\"", ":")
        
        val parts = cleaned.split(":").filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0.0
        
        // 解析符号
        var sign = 1.0
        val firstPart = parts[0].trim()
        when {
            firstPart.startsWith("-") -> sign = -1.0
            firstPart.startsWith("S") || firstPart.startsWith("W") -> sign = -1.0
        }
        
        // 移除符号和字母，只保留数字
        val degStr = parts[0].replace(Regex("[^0-9.]"), "")
        val deg = degStr.toDoubleOrNull() ?: 0.0
        val min = parts.getOrNull(1)?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
        val sec = parts.getOrNull(2)?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
        
        return sign * (deg + min / 60.0 + sec / 3600.0)
    }
}