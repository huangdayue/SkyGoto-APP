package com.skygoto.app.domain.model

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.skygoto.app.util.AppLogger

/**
 * Skyfield-based astronomical position calculator
 * 
 * Uses JPL DE421 ephemeris via Skyfield library for high-precision
 * planetary position calculations.
 */
object SkyfieldCalculator {

    private const val TAG = "SkyfieldCalc"
    
    // RA 格式: HH:MM:SS (如 "04:25:12")
    private val RA_PATTERN = Regex("""^\d{2}:\d{2}:\d{2}$""")
    
    // Dec 格式: +DD*MM:SS 或 -DD*MM:SS (如 "+12*34:56" 或 "-05*12:34")
    private val DEC_PATTERN = Regex("""^[+-]\d{2}\*\d{2}:\d{2}$""")
    
    private var _pythonModule: PyObject? = null
    private var pythonFailed = false

    /**
     * Ensure Python module is loaded (lazy, fails gracefully)
     * @return module or null if failed
     */
    private fun ensureModule(): PyObject? {
        if (pythonFailed) return null
        if (_pythonModule == null) {
            try {
                val python = Python.getInstance()
                _pythonModule = python.getModule("skyfield_position")
                AppLogger.d(TAG, "Python module loaded: $_pythonModule")
            } catch (e: Exception) {
                pythonFailed = true
                AppLogger.e(TAG, "Python module init failed, falling back to Kotlin calculator", e)
                return null
            }
        }
        return _pythonModule
    }

    /**
     * Calculate RA/Dec for a planet
     *
     * @param planet Planet ID (SUN, MOON, MERCURY, etc.)
     * @param observerLat Observer latitude (degrees)
     * @param observerLon Observer longitude (degrees)
     * @param jd Julian Date (UTC)
     * @return Pair<RA_String, Dec_String> or null on error
     */
    fun calculate(
        planet: SolarPositionCalculator.PlanetId,
        observerLat: Double,
        observerLon: Double,
        jd: Double
    ): Pair<String, String>? {
        AppLogger.d(TAG, "=== SkyfieldCalculator.calculate() ENTER ===")
        AppLogger.d(TAG, "  planet=$planet, lat=$observerLat, lon=$observerLon, jd=$jd")


        try {
            val module = ensureModule() ?: run {
                AppLogger.w(TAG, "Python module unavailable, falling back to Kotlin SolarPositionCalculator")
                return SolarPositionCalculator.calculate(planet, observerLat, observerLon, jd)
            }

            val planetName: String = planet.toSkyfieldName()
            AppLogger.d(TAG, "  planetName=$planetName")

            // Call Python function: calculate_planet_position(planetName, lat, lon, jd)
            // Returns tuple (ra_string, dec_string)
            val resultTuple = module.callAttr("calculate_planet_position", planetName, observerLat, observerLon, jd)
            
            // Convert Python tuple to Java List for easier access
            val resultList = resultTuple.asList()
            
            // ========== 数据验证 ==========
            
            // 1. 检查返回数据数量
            if (resultList.size < 2) {
                AppLogger.e(TAG, "Python returned insufficient data: ${resultList.size} elements (expected 2)")
                return null
            }
            
            // 2. 检查元素是否为 null
            val raElement = resultList[0]
            val decElement = resultList[1]
            
            if (raElement == null || decElement == null) {
                AppLogger.e(TAG, "Python returned null values: ra=${raElement}, dec=${decElement}")
                return null
            }
            
            // 3. 转换为字符串并去除空白
            val ra: String = raElement.toString().trim()
            val dec: String = decElement.toString().trim()
            
            AppLogger.d(TAG, "  Raw result: ra='$ra', dec='$dec'")
            
            // 4. 检查是否为 None 字符串
            if (ra.equals("None", ignoreCase = true) || dec.equals("None", ignoreCase = true)) {
                AppLogger.e(TAG, "Python returned 'None' string: ra='$ra', dec='$dec'")
                return null
            }
            
            // 5. 检查是否为空字符串
            if (ra.isEmpty() || dec.isEmpty()) {
                AppLogger.e(TAG, "Python returned empty strings: ra='$ra', dec='$dec'")
                return null
            }
            
            // 6. 验证 RA 格式 (HH:MM:SS)
            if (!RA_PATTERN.matches(ra)) {
                AppLogger.e(TAG, "RA format invalid: '$ra' (expected HH:MM:SS)")
                return null
            }
            
            // 7. 验证 Dec 格式 (+DD*MM:SS 或 -DD*MM:SS)
            if (!DEC_PATTERN.matches(dec)) {
                AppLogger.e(TAG, "Dec format invalid: '$dec' (expected +DD*MM:SS or -DD*MM:SS)")
                return null
            }
            
            // 8. 验证数值范围
            if (!validateRange(ra, dec)) {
                AppLogger.e(TAG, "RA/Dec values out of valid range")
                return null
            }

            AppLogger.d(TAG, "  result: RA=$ra, Dec=$dec")
            AppLogger.d(TAG, "=== SkyfieldCalculator.calculate() EXIT ===")

            return Pair(ra, dec)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error calculating position: ${e.message}", e)
            return null
        }
    }

    /**
     * 验证 RA/Dec 数值范围
     * 
     * RA: 00:00:00 ~ 23:59:59
     * Dec: +90*00:00 ~ -90*00:00
     */
    private fun validateRange(ra: String, dec: String): Boolean {
        try {
            // 验证 RA 范围
            val raParts = ra.split(":")
            val raHours = raParts[0].toInt()
            val raMinutes = raParts[1].toInt()
            val raSeconds = raParts[2].toDouble()
            
            if (raHours < 0 || raHours > 23) {
                AppLogger.e(TAG, "RA hours out of range: $raHours")
                return false
            }
            if (raMinutes < 0 || raMinutes > 59) {
                AppLogger.e(TAG, "RA minutes out of range: $raMinutes")
                return false
            }
            if (raSeconds < 0 || raSeconds >= 60) {
                AppLogger.e(TAG, "RA seconds out of range: $raSeconds")
                return false
            }
            
            // 验证 Dec 范围
            val decContent = dec.substring(1).replace("*", ":")
            val decParts = decContent.split(":")
            val decDegrees = decParts[0].toInt()
            val decMinutes = decParts[1].toInt()
            val decSeconds = decParts[2].toDouble()
            
            if (decDegrees < 0 || decDegrees > 90) {
                AppLogger.e(TAG, "Dec degrees out of range: $decDegrees")
                return false
            }
            if (decMinutes < 0 || decMinutes > 59) {
                AppLogger.e(TAG, "Dec minutes out of range: $decMinutes")
                return false
            }
            if (decSeconds < 0 || decSeconds >= 60) {
                AppLogger.e(TAG, "Dec seconds out of range: $decSeconds")
                return false
            }
            // Dec 不能超过 90 度
            if (decDegrees == 90 && (decMinutes > 0 || decSeconds > 0)) {
                AppLogger.e(TAG, "Dec exceeds 90 degrees")
                return false
            }
            
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during range validation: ${e.message}")
            return false
        }
    }

    /**
     * Calculate for current time
     */
    fun calculateNow(
        planet: SolarPositionCalculator.PlanetId,
        observerLat: Double,
        observerLon: Double
    ): Pair<String, String>? {
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        return calculate(planet, observerLat, observerLon, jd)
    }

    /**
     * Get Skyfield version for debugging
     */
    fun getVersion(): String? {
        return try {
            ensureModule()
            _pythonModule!!.callAttr("get_version").toString()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting version: ${e.message}", e)
            null
        }
    }

    /**
     * Convert PlanetId to Skyfield body name
     */
    private fun SolarPositionCalculator.PlanetId.toSkyfieldName(): String = when (this) {
        SolarPositionCalculator.PlanetId.SUN -> "sun"
        SolarPositionCalculator.PlanetId.MOON -> "moon"
        SolarPositionCalculator.PlanetId.MERCURY -> "mercury"
        SolarPositionCalculator.PlanetId.VENUS -> "venus"
        SolarPositionCalculator.PlanetId.MARS -> "mars"
        SolarPositionCalculator.PlanetId.JUPITER -> "jupiter"
        SolarPositionCalculator.PlanetId.SATURN -> "saturn"
        SolarPositionCalculator.PlanetId.URANUS -> "uranus"
        SolarPositionCalculator.PlanetId.NEPTUNE -> "neptune"
        SolarPositionCalculator.PlanetId.PLUTO -> "pluto"
    }
}