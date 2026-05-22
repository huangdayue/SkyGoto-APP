package com.skygoto.app.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.EnhancedAstronomyCalculator
import com.skygoto.app.domain.model.GotoResult
import com.skygoto.app.domain.model.ObjectType
import com.skygoto.app.domain.model.SolarPositionCalculator
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.CatalogType
import com.skygoto.app.domain.repository.MountRepository
import com.skygoto.app.util.AppLogger
import com.skygoto.app.ui.components.ResultBannerConfig
import com.skygoto.app.ui.components.ResultLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val objects: List<CelestialObject> = emptyList(),
    val filteredObjects: List<CelestialObject> = emptyList(),
    val searchQuery: String = "",
    val selectedCatalog: CatalogType = CatalogType.ALL,
    val selectedType: ObjectType? = null,
    val selectedObject: CelestialObject? = null,
    // 详情页：天体当前地平坐标
    val selectedObjectAlt: Double? = null,
    val selectedObjectAz: Double? = null,
    // 详情页：修正后的目标坐标（J2000→当前历元，供 GOTO 使用）
    val selectedObjectTargetRa: String? = null,
    val selectedObjectTargetDec: String? = null,
    val isLoading: Boolean = false,
    val isGotoInProgress: Boolean = false,
    val gotoResult: ResultBannerConfig? = null,
    val lastGotoObject: CelestialObject? = null,
    val gotoProgressInfo: GotoProgressInfo? = null,
    val cachedMountLat: Double = 0.0,
    val cachedMountLon: Double = 0.0,
    val isLocationCached: Boolean = false,
    // 地平线下确认弹窗
    val showBelowHorizonDialog: Boolean = false
)

data class GotoProgressInfo(
    val targetRa: String,
    val targetDec: String,
    val currentRa: String = "--:--:--",
    val currentDec: String = "--:--:--",
    val isFinished: Boolean = false,
    val objectId: String = ""
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val mountRepository: MountRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CatalogViewModel"
    }
    
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()
    
    private var gotoProgressJob: Job? = null
    
    init {
        loadCatalogs()
        cacheMountLocation()
    }
    
    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val messier = catalogRepository.getMessierCatalog()
            val ngc = catalogRepository.getNGCCatalog()
            val solarSystem = catalogRepository.getSolarSystemCatalog()
            val stars = catalogRepository.getStarCatalog()
            val all = messier + ngc + solarSystem + stars
            
            _uiState.update { state ->
                state.copy(
                    objects = all,
                    filteredObjects = all,
                    isLoading = false
                )
            }
        }
    }
    
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }
    
    fun selectCatalog(catalog: CatalogType) {
        _uiState.update { it.copy(selectedCatalog = catalog) }
        applyFilters()
    }
    
    fun filterByType(type: ObjectType?) {
        _uiState.update { it.copy(selectedType = type) }
        applyFilters()
    }
    
    fun selectObject(obj: CelestialObject?) {
        if (obj == null) {
            _uiState.update {
                it.copy(
                    selectedObject = null,
                    selectedObjectAlt = null,
                    selectedObjectAz = null,
                    selectedObjectTargetRa = null,
                    selectedObjectTargetDec = null
                )
            }
            return
        }

        // 计算目标坐标（当前历元，供 GOTO 使用）
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val observerLat = _uiState.value.cachedMountLat
        val observerLon = _uiState.value.cachedMountLon

        val hasValidLocation = isLocationCached() && observerLat != 0.0

        val (targetRa, targetDec) = if (hasValidLocation) {
            // 有 mount 位置时，计算实时坐标
            if (isSolarSystemBody(obj)) {
                computeTargetCoordinates(obj, observerLat, observerLon, jd)
            } else {
                // 恒星/深空天体：J2000 → 当前历元（岁差修正）
                SolarPositionCalculator.applyPrecession(obj.ra, obj.dec, jd)
            }
        } else {
            // 未获取到 mount 位置时，不计算坐标，alt/az 显示 "—"
            Pair(null, null)
        }

        // 计算地平高度和方位角（用于详情页显示）
        val altAz = if (hasValidLocation && targetRa != null && targetDec != null) {
            val raRad = SolarPositionCalculator.parseRA(targetRa)
            val decRad = SolarPositionCalculator.parseDec(targetDec)
            SolarPositionCalculator.getAltitudeAzimuth(raRad, decRad, observerLat, observerLon, jd)
        } else {
            Pair(null, null)
        }

        _uiState.update {
            it.copy(
                selectedObject = obj,
                selectedObjectAlt = altAz.first,
                selectedObjectAz = altAz.second,
                selectedObjectTargetRa = targetRa,
                selectedObjectTargetDec = targetDec
            )
        }
    }

    /**
     * 计算 GOTO 目标坐标（当前历元）
     * - 太阳系天体：EnhancedAstronomyCalculator (Python/Skyfield)
     * - 恒星/深空天体：J2000 + 岁差修正
     */
    private fun computeTargetCoordinates(
        obj: CelestialObject,
        observerLat: Double,
        observerLon: Double,
        jd: Double
    ): Pair<String, String> {
        return if (isSolarSystemBody(obj)) {
            val planetId = getPlanetId(obj.id)
            if (planetId != null) {
                // 太阳系天体：Skyfield 计算当前历元坐标
                val position = EnhancedAstronomyCalculator.calculate(planetId, observerLat, observerLon, jd)
                if (position != null) {
                    Pair(position.first, position.second)
                } else {
                    // 回退到 Kotlin 计算
                    val fallback = SolarPositionCalculator.calculate(planetId, observerLat, observerLon, jd)
                    if (fallback != null) Pair(fallback.first, fallback.second)
                    else Pair(obj.ra, obj.dec)
                }
            } else {
                Pair(obj.ra, obj.dec)
            }
        } else {
            // 恒星/深空天体：J2000 → 当前历元（岁差修正）
            SolarPositionCalculator.applyPrecession(obj.ra, obj.dec, jd)
        }
    }

    private fun isLocationCached(): Boolean {
        return _uiState.value.isLocationCached && _uiState.value.cachedMountLat != 0.0
    }

    fun dismissBelowHorizonDialog() {
        _uiState.update { it.copy(showBelowHorizonDialog = false) }
    }

    fun confirmGotoBelowHorizon() {
        val obj = _uiState.value.selectedObject ?: return
        val targetRa = _uiState.value.selectedObjectTargetRa ?: return
        val targetDec = _uiState.value.selectedObjectTargetDec ?: return
        _uiState.update { it.copy(showBelowHorizonDialog = false) }
        executeGoto(obj, targetRa, targetDec)
    }
    
    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.objects
        
        // 按目录筛选
        filtered = when (state.selectedCatalog) {
            CatalogType.MESSIER -> filtered.filter { it.id.startsWith("M") }
            CatalogType.NGC -> filtered.filter { it.id.startsWith("NGC") }
            CatalogType.IC -> filtered.filter { it.id.startsWith("IC") }
            CatalogType.SOLAR_SYSTEM -> filtered.filter { isSolarSystemBody(it) }
            CatalogType.STAR -> filtered.filter { it.type == ObjectType.STAR }
            CatalogType.ALL -> filtered
        }
        
        // 按类型筛选
        state.selectedType?.let { type ->
            filtered = filtered.filter { it.type == type }
        }
        
        // 按搜索词筛选
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter { obj ->
                obj.id.lowercase().contains(q) ||
                obj.name.lowercase().contains(q) ||
                obj.constellation.lowercase().contains(q) ||
                obj.altNames.any { it.lowercase().contains(q) }
            }
        }
        
        _uiState.update { it.copy(filteredObjects = filtered) }
    }
    
    // getObjectForGoto 已废弃，GOTO 坐标直接使用 selectedObjectTargetRa/Dec

    /**
     * 触发 GOTO（从详情页按钮调用）
     * 会先检查地平高度，低于地平线则弹出确认对话框
     */
    fun gotoObject(obj: CelestialObject) {
        val state = _uiState.value
        val targetRa = state.selectedObjectTargetRa ?: return
        val targetDec = state.selectedObjectTargetDec ?: return
        val alt = state.selectedObjectAlt

        if (alt != null && alt < 0) {
            // 低于地平线，显示确认弹窗
            _uiState.update { it.copy(showBelowHorizonDialog = true) }
        } else {
            executeGoto(obj, targetRa, targetDec)
        }
    }

    /**
     * 执行 GOTO（跳过地平线检查）
     */
    private fun executeGoto(obj: CelestialObject, targetRa: String, targetDec: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGotoInProgress = true, gotoResult = null) }

            val isConnected = mountRepository.isConnected.value
            if (!isConnected) {
                _uiState.update { it.copy(isGotoInProgress = false, gotoResult = ResultBannerConfig.error("赤道仪未连接")) }
                return@launch
            }

            AppLogger.w(TAG, "[GOTO] 目标 RA=$targetRa Dec=$targetDec")
            val result = mountRepository.setTargetAndGoto(targetRa, targetDec)
            
            result.fold(
                onSuccess = { gotoResult ->
                    when (gotoResult) {
                        is GotoResult.Success -> {
                            AppLogger.w(TAG, "[GOTO进度] GOTO 执行成功, 目标 RA=${targetRa} Dec=${targetDec}")
                            _uiState.update { 
                                it.copy(
                                    isGotoInProgress = false,
                                    lastGotoObject = obj,
                                    // GOTO 已开始，关闭弹窗后进入 GOTO 进度弹窗，无需再显示成功提示
                                    gotoResult = null,
                                    gotoProgressInfo = GotoProgressInfo(
                                        targetRa = targetRa,
                                        targetDec = targetDec,
                                        currentRa = "--:--:--",
                                        currentDec = "--:--:--",
                                        isFinished = false,
                                        objectId = obj.id
                                    )
                                ) 
                            }
                            startGotoProgressPolling()
                        }
                        is GotoResult.GotoError -> {
                            AppLogger.w(TAG, "[GOTO进度] GotoError: ${gotoResult.message}")
                            dismissGotoProgress()
                            _uiState.update { it.copy(isGotoInProgress = false, lastGotoObject = obj, gotoResult = ResultBannerConfig.error("GOTO 失败: ${gotoResult.message}")) }
                        }
                    }
                },
                onFailure = { e ->
                    AppLogger.e(TAG, "GOTO exception: ${e.message}", e)
                    AppLogger.w(TAG, "[GOTO进度] GOTO 异常: ${e.message}")
                    dismissGotoProgress()
                    _uiState.update { it.copy(isGotoInProgress = false, gotoResult = ResultBannerConfig.error("GOTO 异常: ${e.message}")) }
                }
            )
        }
    }
    
    private fun isSolarSystemBody(obj: CelestialObject): Boolean {
        val id = obj.id.uppercase()
        return id in listOf("SOL", "LUN", "MER", "VEN", "MAR", "JUP", "SAT", "URA", "NEP", "PLU", "CER", "VES", "PAL")
    }
    
    private fun getPlanetId(id: String): SolarPositionCalculator.PlanetId? {
        return when (id) {
            "SOL" -> SolarPositionCalculator.PlanetId.SUN
            "LUN" -> SolarPositionCalculator.PlanetId.MOON
            "MER" -> SolarPositionCalculator.PlanetId.MERCURY
            "VEN" -> SolarPositionCalculator.PlanetId.VENUS
            "MAR" -> SolarPositionCalculator.PlanetId.MARS
            "JUP" -> SolarPositionCalculator.PlanetId.JUPITER
            "SAT" -> SolarPositionCalculator.PlanetId.SATURN
            "URA" -> SolarPositionCalculator.PlanetId.URANUS
            "NEP" -> SolarPositionCalculator.PlanetId.NEPTUNE
            "PLU" -> SolarPositionCalculator.PlanetId.PLUTO
            else -> null
        }
    }
    
    fun cacheMountLocation() {
        if (!mountRepository.isConnected.value) {
            return
        }
        
        viewModelScope.launch {
            try {
                val result = mountRepository.getLocation()
                result.fold(
                    onSuccess = { (latDms, lonDms) ->
                        val lat = dmsToDecimal(latDms, isFromOnstep = false)
                        val lon = dmsToDecimal(lonDms, isFromOnstep = true)
                        
                        _uiState.update { 
                            it.copy(
                                cachedMountLat = lat,
                                cachedMountLon = lon,
                                isLocationCached = true
                            ) 
                        }
                    },
                    onFailure = { e ->
                        AppLogger.e(TAG, "cacheMountLocation exception: ${e.message}")
                        _uiState.update { it.copy(isLocationCached = false) }
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "cacheMountLocation exception: ${e.message}")
                _uiState.update { it.copy(isLocationCached = false) }
            }
        }
    }
    
    private fun dmsToDecimal(dms: String, isFromOnstep: Boolean = false): Double {
        if (dms.isEmpty()) return 0.0
        
        val sign = if (dms.startsWith("-")) -1.0 else 1.0
        val value = dms.replace("+", "").replace("-", "")
        
        val parts = value.split("*")
        if (parts.size != 2) return 0.0
        
        val degrees = parts[0].toDoubleOrNull() ?: 0.0
        val dmsParts = parts[1].split(":")
        val minutes = dmsParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val seconds = dmsParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        
        val result = sign * (degrees + minutes / 60.0 + seconds / 3600.0)
        val finalResult = if (isFromOnstep && dms.startsWith("-")) -result else result
        
        return finalResult
    }
    
    fun clearGotoResult() {
        _uiState.update { it.copy(gotoResult = null) }
    }
    
    fun dismissGotoProgress() {
        AppLogger.w(TAG, "[GOTO进度] 关闭弹窗")
        gotoProgressJob?.cancel()
        gotoProgressJob = null
        _uiState.update { it.copy(gotoProgressInfo = null) }
    }
    
    /**
     * 判断赤道仪是否已到达目标坐标（容差：RA ±60角秒，Dec ±60角秒）
     * 支持目标坐标为 sDD:MM:SS 格式（目录），当前坐标为 sDD*MM:SS 格式（赤道仪）
     */
    private fun isCoordinateReached(currentRa: String, currentDec: String, targetRa: String, targetDec: String): Boolean {
        val targetDecNorm = targetDec.trim().replace(":", "*")
        
        val raArcsec = parseDMSArcsec(currentRa.clean())
        val targetRaArcsec = parseDMSArcsec(targetRa.trim().clean())
        val decArcsec = parseDecArcsec(currentDec.clean())
        val targetDecArcsec = parseDecArcsec(targetDecNorm.clean())
        

        
        if (raArcsec != null && targetRaArcsec != null && decArcsec != null && targetDecArcsec != null) {
            val raDiff = kotlin.math.abs(raArcsec - targetRaArcsec)
            val decDiff = kotlin.math.abs(decArcsec - targetDecArcsec)
            AppLogger.d(TAG, "[GOTO进度] 精确比较: RA差=${raDiff.toInt()}角秒, Dec差=${decDiff.toInt()}角秒, 阈值=60角秒")
            return raDiff <= 60.0 && decDiff <= 60.0
        }
        
        val fallbackOk = coordsMatchSecondLevel(currentRa, currentDec, targetRa, targetDecNorm)
        AppLogger.w(TAG, "[GOTO进度] 兜底比较(秒级): ${if (fallbackOk) "匹配" else "不匹配"}")
        return fallbackOk
    }
    
    /**
     * 秒级坐标比较：分别解析 RA 和 Dec 的时/分/秒，误差容忍 RA ±4秒，Dec ±10秒
     * 彻底脱离字符串下标依赖，解决 \r\n 和字符长度不一致导致的比较错误
     */
    private fun coordsMatchSecondLevel(currentRa: String, currentDec: String,
                                       targetRa: String, targetDec: String): Boolean {
        fun parseRAHMS(ra: String): Triple<Int, Int, Int>? {
            val parts = ra.clean().replace("*", ":").split(":").filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val s = parts[2].take(2).toIntOrNull() ?: return null
            return Triple(h, m, s)
        }
        
        fun parseDecHMS(dec: String): Triple<Int, Int, Int>? {
            val cleaned = dec.clean()
            val sign = if (cleaned.startsWith("-")) -1 else 1
            val rest = cleaned.replace("+", "").replace("-", "")
            
            val match = Regex("[:*]").find(rest) ?: return null
            val dStr = rest.substring(0, match.range.first)
            val msStr = rest.substring(match.range.last + 1)
            
            val msMatch = Regex("[:*]").find(msStr) ?: return null
            val mStr = msStr.substring(0, msMatch.range.first)
            val sStr = msStr.substring(msMatch.range.last + 1)
            
            val d = dStr.toIntOrNull() ?: return null
            val m = mStr.toIntOrNull() ?: return null
            val s = sStr.toIntOrNull() ?: return null
            
            return Triple(sign * d, m, s)
        }
        
        val cRa = parseRAHMS(currentRa)
        val tRa = parseRAHMS(targetRa)
        val cDec = parseDecHMS(currentDec)
        val tDec = parseDecHMS(targetDec)
        
        AppLogger.d(TAG, "[GOTO进度] 兜底解析: currentRA=$cRa targetRA=$tRa currentDec=$cDec targetDec=$tDec")
        
        if (cRa == null || tRa == null || cDec == null || tDec == null) {
            AppLogger.d(TAG, "[GOTO进度] 兜底解析失败，跳过比较")
            return false
        }
        
        val raTotalSec = (cRa.first * 3600 + cRa.second * 60 + cRa.third)
        val tRaTotalSec = (tRa.first * 3600 + tRa.second * 60 + tRa.third)
        val raDiffSec = kotlin.math.abs(raTotalSec - tRaTotalSec)
        
        // 解析后 cDec.first 已经包含符号（parseDecHMS 中 sign * d）
        // 直接使用无需 abs，保留符号信息
        val cDecTotalSec = cDec.first * 3600 + cDec.second * 60 + cDec.third
        val tDecTotalSec = tDec.first * 3600 + tDec.second * 60 + tDec.third
        val decDiffSec = kotlin.math.abs(cDecTotalSec - tDecTotalSec)
        
        AppLogger.d(TAG, "[GOTO进度] 兜底秒数: RA差=${raDiffSec}s, Dec差=${decDiffSec}s, 容忍 RA±4s Dec±10s")
        
        return raDiffSec <= 4 && decDiffSec <= 10
    }
    
    private fun String.clean(): String {
        return this.replace("\r", "").replace("\n", "").replace(" ", "")
            .filter { it.isDigit() || it == ':' || it == '*' || it == '+' || it == '-' }
    }
    
    private fun parseDMSArcsec(ra: String): Double? {
        val cleaned = ra.clean()
        val parts = cleaned.replace("*", ":").split(":")
        if (parts.size != 3) return null
        val h = parts[0].toDoubleOrNull() ?: return null
        val m = parts[1].toDoubleOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return (h * 15.0 + m * 0.25 + s * 0.004166667) * 3600.0
    }
    
    private fun parseDecArcsec(dec: String): Double? {
        val cleaned = dec.clean()
        if (cleaned.length < 7) return null
        val sign = if (cleaned.startsWith("-")) -1.0 else 1.0
        val rest = cleaned.replace("+", "").replace("-", "")
        
        val match = Regex("[:*]").find(rest) ?: return null
        val dStr = rest.substring(0, match.range.first)
        val msStr = rest.substring(match.range.last + 1)
        
        val msMatch = Regex("[:*]").find(msStr) ?: return null
        val mStr = msStr.substring(0, msMatch.range.first)
        val sStr = msStr.substring(msMatch.range.last + 1)
        
        val d = dStr.toDoubleOrNull() ?: return null
        val m = mStr.toDoubleOrNull() ?: return null
        val s = sStr.toDoubleOrNull() ?: return null
        
        return sign * (d * 3600.0 + m * 60.0 + s)
    }
    
    private fun startGotoProgressPolling() {
        gotoProgressJob?.cancel()
        val progressInfo = _uiState.value.gotoProgressInfo
        AppLogger.w(TAG, "[GOTO进度] 开始轮询监控: 目标 RA=${progressInfo?.targetRa} Dec=${progressInfo?.targetDec}")
        gotoProgressJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val info = _uiState.value.gotoProgressInfo
                if (info == null) {
                    AppLogger.w(TAG, "[GOTO进度] 轮询结束: gotoProgressInfo 为 null")
                    break
                }
                
                try {
                    val statusResult = mountRepository.getStatus()
                    
                    val currentRa = statusResult.getOrNull()?.ra ?: info.currentRa
                    val currentDec = statusResult.getOrNull()?.dec ?: info.currentDec
                    
                    val isFinished = isCoordinateReached(currentRa, currentDec, info.targetRa, info.targetDec)
                    
                    _uiState.update { state ->
                        state.copy(
                            gotoProgressInfo = info.copy(
                                currentRa = currentRa,
                                currentDec = currentDec,
                                isFinished = isFinished
                            )
                        )
                    }
                    
                    if (isFinished) {
                        AppLogger.w(TAG, "GOTO 完成检测: RA=$currentRa, Dec=$currentDec")
                        break
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "轮询获取 RA/Dec 失败: ${e.message}")
                }
            }
        }
    }
    
    fun cancelGoto() {
        AppLogger.w(TAG, "[GOTO进度] 用户取消 GOTO")
        viewModelScope.launch {
            mountRepository.cancelGoto()
            dismissGotoProgress()
        }
    }
    
    fun refresh() {
        loadCatalogs()
    }
}