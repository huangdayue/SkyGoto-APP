package com.skygoto.app.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.data.preferences.SettingsDataStore
import com.skygoto.app.data.protocol.LX200Commands as Cmd
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

data class SettingsUiState(
    // 位置设置
    val longitude: String = "120:00:00",
    val latitude: String = "30:00:00",
    val locationFormat: String = "DMS", // "DMS" or "DECIMAL"
    val isSyncingLocation: Boolean = false,
    val locationError: String? = null,
    
    // 时区设置
    val timezone: String = "Asia/Shanghai",
    val availableTimezones: List<String> = emptyList(),
    
    // 赤道仪时间
    val mountTime: String = "",
    val mountTimeError: String? = null,
    val isSyncingTime: Boolean = false,
    
    // 权限状态
    val hasLocationPermission: Boolean = false,
    
    // 赤道仪连接状态
    val isMountConnected: Boolean = false,
    
    // 日志
    val logFilePath: String = "",
    val isExportingLogs: Boolean = false,
    val exportLogSuccess: String? = null,
    val exportLogError: String? = null
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
            
            // 加载时区设置
            launch {
                settingsDataStore.timezone.collect { value ->
                    _uiState.update { it.copy(timezone = value) }
                }
            }
            
            // 观察赤道仪连接状态（不再自动获取位置，改由页面可见性控制）
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
    
    fun updateTimezone(timezone: String) {
        _uiState.update { it.copy(timezone = timezone) }
        viewModelScope.launch {
            settingsDataStore.setTimezone(timezone)
        }
    }
    
    fun updateHasLocationPermission(hasPermission: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = hasPermission) }
    }
    
    /**
     * 从赤道仪加载位置
     */
    fun loadLocationFromMount() {
        if (!_uiState.value.isMountConnected) {
            _uiState.update { it.copy(locationError = "赤道仪未连接") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingLocation = true, locationError = null) }
            
            try {
                val result = mountRepository.getLocation()
                result.fold(
                    onSuccess = { (lat, lon) ->
                        _uiState.update { 
                            it.copy(
                                longitude = lon,
                                latitude = lat,
                                isSyncingLocation = false
                            ) 
                        }
                        // 保存到 DataStore
                        launch {
                            settingsDataStore.setLongitude(lon)
                            settingsDataStore.setLatitude(lat)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { 
                            it.copy(
                                isSyncingLocation = false,
                                locationError = "获取赤道仪位置失败: ${e.message}"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingLocation = false,
                        locationError = "获取赤道仪位置失败: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 从赤道仪加载所有信息（位置 + 日期时间）
     * 进入设置页面时自动调用
     */
    fun loadAllFromMount() {
        if (!_uiState.value.isMountConnected) {
            _uiState.update { 
                it.copy(
                    locationError = "赤道仪未连接",
                    mountTime = "赤道仪未连接"
                ) 
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSyncingLocation = true,
                    locationError = null
                ) 
            }
            
            try {
                // 并行获取所有信息
                val locationDeferred = async { mountRepository.getLocation() }
                val dateDeferred = async { mountRepository.getDate() }
                val timeDeferred = async { mountRepository.getLocalTime() }
                val timezoneDeferred = async { mountRepository.getTimezone() }
                
                val location = locationDeferred.await()
                val date = dateDeferred.await()
                val time = timeDeferred.await()
                val timezone = timezoneDeferred.await()
                
                // 更新位置
                location.getOrNull()?.let { (lat, lon) ->
                    _uiState.update { 
                        it.copy(
                            latitude = lat,
                            longitude = lon
                        ) 
                    }
                    // 保存到 DataStore
                    launch {
                        settingsDataStore.setLatitude(lat)
                        settingsDataStore.setLongitude(lon)
                    }
                }
                
                // 更新日期时间显示格式: YYYY-MM-DD HH:MM:SS +ZZ:00
                val dateStr = date.getOrNull() ?: ""
                val timeStr = time.getOrNull() ?: ""
                val timezoneStr = timezone.getOrNull() ?: ""
                val dateTimeDisplay = if (dateStr.isNotEmpty() && timeStr.isNotEmpty()) {
                    "$dateStr $timeStr $timezoneStr"
                } else {
                    "读取失败"
                }
                _uiState.update { it.copy(mountTime = dateTimeDisplay) }
                
                // 处理错误
                if (location.isFailure) {
                    _uiState.update { it.copy(locationError = "位置读取失败") }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingLocation = false,
                        locationError = "读取失败: ${e.message}",
                        mountTime = "读取失败"
                    ) 
                }
            } finally {
                _uiState.update { it.copy(isSyncingLocation = false) }
            }
        }
    }
    
    /**
     * 同步手机 GPS 位置到赤道仪
     */
    @SuppressLint("MissingPermission")
    fun syncPhoneLocationToMount() {
        if (!_uiState.value.hasLocationPermission) {
            _uiState.update { it.copy(locationError = "需要位置权限") }
            return
        }
        
        if (!_uiState.value.isMountConnected) {
            _uiState.update { it.copy(locationError = "赤道仪未连接") }
            return
        }
        
        _uiState.update { it.copy(isSyncingLocation = true, locationError = null) }
        
        viewModelScope.launch {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                
                // 检查 GPS 是否开启
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    _uiState.update { 
                        it.copy(
                            isSyncingLocation = false,
                            locationError = "GPS 未开启，请在系统设置中开启定位服务"
                        )
                    }
                    return@launch
                }
                
                // 尝试获取最后已知位置（快速方法）
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                
                if (location != null) {
                    // 有最后已知位置，直接使用
                    processLocationResult(location)
                } else {
                    // 没有最后已知位置，请求实时更新
                    requestSingleLocation(locationManager)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSyncingLocation = false,
                        locationError = e.message ?: "获取位置失败"
                    )
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun processLocationResult(location: android.location.Location) {
        val lat = location.latitude
        val lon = location.longitude
        
        // 转换为 DMS 格式
        val latDMS = decimalToDMS(lat, true)
        val lonDMS = decimalToDMS(lon, false)
        
        // 发送到赤道仪
        val result = mountRepository.setLocation(lonDMS, latDMS)
        result.fold(
            onSuccess = {
                _uiState.update { 
                    it.copy(
                        longitude = lonDMS,
                        latitude = latDMS,
                        isSyncingLocation = false
                    )
                }
                // 保存到 DataStore
                viewModelScope.launch {
                    settingsDataStore.setLongitude(lonDMS)
                    settingsDataStore.setLatitude(latDMS)
                    settingsDataStore.setLatitudeDec(lat)
                    settingsDataStore.setLongitudeDec(lon)
                }
            },
            onFailure = { e ->
                _uiState.update { 
                    it.copy(
                        isSyncingLocation = false,
                        locationError = "同步位置到赤道仪失败: ${e.message}"
                    )
                }
            }
        )
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(locationManager: LocationManager) {
        try {
            val countDownLatch = CountDownLatch(1)
            var capturedLocation: android.location.Location? = null
            var hasResult = false
            
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    capturedLocation = location
                    hasResult = true
                    countDownLatch.countDown()
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in API level 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            
            // 请求位置更新
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener
                )
            } catch (e: Exception) {
                // GPS provider 不可用，尝试网络 provider
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener
                    )
                } catch (e2: Exception) {
                    // 两个都不可用
                    _uiState.update { 
                        it.copy(
                            isSyncingLocation = false,
                            locationError = "GPS 和网络定位都不可用"
                        )
                    }
                    return
                }
            }
            
            // 等待最多 10 秒
            try {
                countDownLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                // 被中断
            }
            
            // 停止更新
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                // 忽略停止更新时的异常
            }
            
            if (hasResult && capturedLocation != null) {
                processLocationResult(capturedLocation!!)
            } else {
                _uiState.update { 
                    it.copy(
                        isSyncingLocation = false,
                        locationError = "无法获取 GPS 位置，请到户外或检查 GPS 设置"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isSyncingLocation = false,
                    locationError = "GPS 获取超时: ${e.message}"
                )
            }
        }
    }
    
    fun syncMountTime() {
        if (!_uiState.value.isMountConnected) {
            AppLogger.w(TAG, "syncMountTime: 赤道仪未连接，跳过")
            _uiState.update { it.copy(mountTimeError = "赤道仪未连接", isSyncingTime = false) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTime = true, mountTimeError = null) }
            AppLogger.i(TAG, "syncMountTime: 正在从赤道仪获取本地时间...")
            
            try {
                // 从赤道仪获取本地时间 :GL#
                val result = mountRepository.getLocalTime()
                result.fold(
                    onSuccess = { time ->
                        AppLogger.i(TAG, "syncMountTime: 获取成功，赤道仪时间=$time")
                        _uiState.update { 
                            it.copy(
                                mountTime = time,  // 显示赤道仪本地时间（如 08:51:42）
                                isSyncingTime = false
                            ) 
                        }
                    },
                    onFailure = { e ->
                        AppLogger.e(TAG, "syncMountTime: 获取失败 - ${e.message}", e)
                        _uiState.update { 
                            it.copy(
                                isSyncingTime = false,
                                mountTimeError = "获取赤道仪时间失败: ${e.message}"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "syncMountTime: 异常 - ${e.message}", e)
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
     * 同步手机日期、时间和时区到赤道仪
     * 发送 :SC 日期、:SL 时间和 :SG 时区命令
     */
    fun syncToAndroidTime() {
        if (!_uiState.value.isMountConnected) {
            AppLogger.w(TAG, "syncToAndroidTime: 赤道仪未连接，跳过")
            _uiState.update { it.copy(mountTimeError = "赤道仪未连接", isSyncingTime = false) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingTime = true, mountTimeError = null) }
            
            try {
                val calendar = Calendar.getInstance()
                
                // 1. 获取并格式化日期 (MM/DD/YY)
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val year = calendar.get(Calendar.YEAR) % 100
                val dateStr = "%02d/%02d/%02d".format(month, day, year)
                
                // 2. 获取并格式化时间 (HH:MM:SS)
                val hours = calendar.get(Calendar.HOUR_OF_DAY)
                val minutes = calendar.get(Calendar.MINUTE)
                val seconds = calendar.get(Calendar.SECOND)
                val timeStr = "%02d:%02d:%02d".format(hours, minutes, seconds)
                
                // 3. 获取手机时区偏移
                // 注意：东半球时区发送负值到赤道仪（如北京发送 "-08:00"）
                val timezone = TimeZone.getDefault()
                val offsetMillis = timezone.rawOffset
                val offsetHours = offsetMillis / 3600000.0
                val offsetMinutes = (Math.abs(offsetMillis) % 3600000) / 60000
                val sign = if (offsetHours >= 0) "-" else "+"  // 东半球为负，西半球为正
                val timezoneStr = "%s%02d:%02d".format(sign, Math.abs(offsetHours).toInt(), offsetMinutes)
                
                AppLogger.i(TAG, "syncToAndroidTime: 日期=$dateStr, 时间=$timeStr, 时区=$timezoneStr")
                
                // 并行发送到赤道仪
                val dateDeferred = async { mountRepository.setDate(dateStr) }
                val timeDeferred = async { mountRepository.setTime(timeStr) }
                val timezoneDeferred = async { mountRepository.setTimezone(timezoneStr) }
                
                val dateResult = dateDeferred.await()
                val timeResult = timeDeferred.await()
                val timezoneResult = timezoneDeferred.await()
                
                // 检查是否全部成功
                if (dateResult.isSuccess && timeResult.isSuccess && timezoneResult.isSuccess) {
                    AppLogger.i(TAG, "syncToAndroidTime: 同步成功")
                    _uiState.update { it.copy(isSyncingTime = false) }
                    loadAllFromMount()
                } else {
                    val errors = listOfNotNull(
                        if (dateResult.isFailure) "日期" else null,
                        if (timeResult.isFailure) "时间" else null,
                        if (timezoneResult.isFailure) "时区" else null
                    ).joinToString(", ")
                    _uiState.update { 
                        it.copy(
                            isSyncingTime = false,
                            mountTimeError = "同步失败: $errors"
                        ) 
                    }
                }
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "syncToAndroidTime: 异常 - ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isSyncingTime = false,
                        mountTimeError = e.message ?: "同步失败"
                    ) 
                }
            }
        }
    }
    
    // 工具函数：十进制转 DMS 格式（OnStepX 格式：+22*23:49）
    private fun decimalToDMS(decimal: Double, isLatitude: Boolean): String {
        // 纬度: North-positive (decimal<0 → South → "-", decimal>=0 → North → "+")
        // 经度: West-positive (decimal>=0 → East → "-", decimal<0 → West → "+")
        //        因为 OnStep 使用 West-positive 约定: 东经=负, 西经=正
        val sign = if (isLatitude) {
            if (decimal < 0) "-" else "+"
        } else {
            if (decimal >= 0) "-" else "+"
        }
        
        val absValue = kotlin.math.abs(decimal)
        val degrees = absValue.toInt()
        val minutesFull = (absValue - degrees) * 60
        val minutes = minutesFull.toInt()
        val seconds = ((minutesFull - minutes) * 60 + 0.5).toInt()  // 四舍五入
        
        return "%s%02d*%02d:%02d".format(sign, degrees, minutes, seconds)
    }
    
    /**
     * 清空日志文件
     */
    fun clearLogs() {
        viewModelScope.launch {
            AppLogger.clearLogs()
            _uiState.update { it.copy(exportLogSuccess = "日志已清空", exportLogError = null) }
        }
    }

    /**
     * 导出日志文件
     * 将日志文件通过 Android 分享功能发送到其他应用
     */
    fun exportLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingLogs = true, exportLogSuccess = null, exportLogError = null) }
            
            try {
                val logPath = AppLogger.getLogFilePath()
                if (logPath.isNullOrEmpty()) {
                    _uiState.update { it.copy(isExportingLogs = false, exportLogError = "日志文件不存在") }
                    return@launch
                }
                
                val logFile = File(logPath)
                if (!logFile.exists()) {
                    _uiState.update { it.copy(isExportingLogs = false, exportLogError = "日志文件不存在") }
                    return@launch
                }
                
                // 使用 FileProvider 获取文件 URI
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile
                )
                
                // 更新日志路径显示
                _uiState.update { it.copy(logFilePath = logPath) }
                
                // 创建分享 Intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "OnStepX APP 日志")
                    putExtra(Intent.EXTRA_TEXT, "OnStepX APP 调试日志，请查看附件。")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // 启动分享Activity
                val chooserIntent = Intent.createChooser(shareIntent, "分享日志到")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                
                _uiState.update { it.copy(isExportingLogs = false, exportLogSuccess = "日志分享已启动") }
                AppLogger.i(TAG, "日志导出成功: $logPath")
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "日志导出失败: ${e.message}", e)
                _uiState.update { it.copy(isExportingLogs = false, exportLogError = "导出失败: ${e.message}") }
            }
        }
    }
}