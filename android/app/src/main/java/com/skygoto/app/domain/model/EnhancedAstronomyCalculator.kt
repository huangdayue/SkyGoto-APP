package com.skygoto.app.domain.model

import com.skygoto.app.util.AppLogger

/**
 * 高精度天文位置计算器 (Skyfield/JPL DE421)
 *
 * 使用 Chaquopy + Skyfield + JPL DE421 星历表进行高精度计算
 * 精度: ±1 arcminute (~0.02°)，与 Stellarium 一致
 * 
 * 依赖:
 * - com.chaquo.python:chaquopy-lib:16.0.0
 * - skyfield (Python package)
 * - de421.bsp ephemeris file (bundled in assets/)
 *
 * 使用方法 (与 SolarPositionCalculator 兼容):
 *   val position = EnhancedAstronomyCalculator.calculate(
 *       SolarPositionCalculator.PlanetId.MOON, observerLat, observerLon, jd
 *   )
 *   // position.first = RA in "HH:MM:SS" format
 *   // position.second = Dec in "+DD*MM:SS" format
 */
object EnhancedAstronomyCalculator {

    private const val TAG = "EnhancedAstronomyCalc"

    /**
     * 计算给定天体的当前位置 (RA/Dec)
     *
     * @param planet   天体 ID
     * @param observerLat 观察者纬度 (度)
     * @param observerLon 观察者经度 (度)
     * @param jd      儒略日 (UTC)
     * @return Pair<RA_String, Dec_String> 或 null
     */
    fun calculate(
        planet: SolarPositionCalculator.PlanetId,
        observerLat: Double,
        observerLon: Double,
        jd: Double
    ): Pair<String, String>? {
        // ========== 调试日志：输入参数 (使用 AppLogger 写入文件) ==========
        AppLogger.d(TAG, "=== EnhancedAstronomyCalculator.calculate() ENTER ===")
        AppLogger.d(TAG, "  planet=$planet (${planet.nameCn})")
        AppLogger.d(TAG, "  observerLat=$observerLat, observerLon=$observerLon")
        AppLogger.d(TAG, "  jd=$jd")

        // ========== 委托给 SkyfieldCalculator ==========
        val result = SkyfieldCalculator.calculate(planet, observerLat, observerLon, jd)

        if (result != null) {
            AppLogger.d(TAG, "  Skyfield result: RA=${result.first}, Dec=${result.second}")
        } else {
            AppLogger.w(TAG, "  SkyfieldCalculator returned null, falling back to SolarPositionCalculator")
        }
        
        AppLogger.d(TAG, "=== EnhancedAstronomyCalculator.calculate() EXIT ===")

        return result
    }

    /**
     * 计算当前时刻的天体位置
     */
    fun calculateNow(
        planet: SolarPositionCalculator.PlanetId,
        observerLat: Double,
        observerLon: Double
    ): Pair<String, String>? {
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        return calculate(planet, observerLat, observerLon, jd)
    }
}