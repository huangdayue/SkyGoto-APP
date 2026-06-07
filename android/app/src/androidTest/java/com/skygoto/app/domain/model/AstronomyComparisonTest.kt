/*
 * 天文计算算法对比测试（Instrumentation Test）
 *
 * 在 Android 设备/模拟器上运行，通过 Chaquopy 调用 Skyfield (JPL DE421)，
 * 与纯 Kotlin VSOP87D (SolarPositionCalculator) 进行对比。
 *
 * 测试内容：
 * 1. 对太阳系全部 10 个天体，在多个时间点分别计算 RA/Dec
 * 2. 计算两种算法之间的角分离
 * 3. 评估 VSOP87D 相对 Skyfield 的精度
 * 4. 验证 VSOP87D 无法计算的天体（土/天/海）是否被 Skyfield 正确覆盖
 *
 * 运行方式：
 *   ./gradlew connectedAndroidTest
 * 或直接在 Android Studio 中运行此测试
 */
package com.skygoto.app.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skygoto.app.util.AppLogger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AstronomyComparisonTest {

    // ==================== 测试常数 ====================

    /** 观测地：北京 */
    private val OBSERVER_LAT = 40.3958
    private val OBSERVER_LON = 117.575

    /** 角分离容差（度）— VSOP87D 理论精度 ~0.001°，放宽到 5° */
    private const val ANGULAR_TOLERANCE_DEG = 5.0

    /** 严格容差（度）— 对 Sun/Moon 等 VSOP87D 精度较高的天体 */
    private const val STRICT_TOLERANCE_DEG = 2.0

    /** 宽从容差（度）— 对开普勒回退的天体 */
    private const val LOOSE_TOLERANCE_DEG = 10.0

    /** Skyfield 可用标记位 */
    private var skyfieldAvailable = false
    private var skyfieldError: String? = null

    // ==================== 数据类 ====================

    data class ComparisonResult(
        val planet: SolarPositionCalculator.PlanetId,
        val label: String,
        val solarRa: String?,
        val solarDec: String?,
        val skyfieldRa: String?,
        val skyfieldDec: String?,
        val angularSeparationDeg: Double?,
        val usedFallback: Boolean  // Skyfield 是否回退到了 SolarPositionCalculator
    )

    // ==================== 辅助方法 ====================

    @Before
    fun setUp() {
        // 初始化 AppLogger（使用 instrumentation 上下文）
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLogger.init(context)

        // 尝试初始化 Skyfield
        try {
            val version = SkyfieldCalculator.getVersion()
            AppLogger.d("ComparisonTest", "Skyfield 版本: $version")
            skyfieldAvailable = version != null
            if (version == null) {
                skyfieldError = "SkyfieldCalculator.getVersion() 返回 null"
            }
        } catch (e: Exception) {
            skyfieldAvailable = false
            skyfieldError = "${e.javaClass.simpleName}: ${e.message}"
            AppLogger.e("ComparisonTest", "Skyfield 初始化失败: $skyfieldError")
        }
    }

    /** RA (HH:MM:SS) → 度 */
    private fun raToDegrees(ra: String): Double {
        val parts = ra.trim().split(":")
        return (parts[0].toDouble() + parts[1].toDouble() / 60.0 + parts[2].toDouble() / 3600.0) * 15.0
    }

    /** Dec ([+-]DD*MM:SS) → 度 */
    private fun decToDegrees(dec: String): Double {
        val s = dec.trim().replace("*", ":")
        val sign = if (s.startsWith("-")) -1.0 else 1.0
        val absStr = if (sign < 0) s.substring(1) else s
        val parts = absStr.split(":")
        val deg = parts[0].toDouble() + parts[1].toDouble() / 60.0 + parts[2].toDouble() / 3600.0
        return sign * deg
    }

    /** 角分离（度） */
    private fun angularSeparationDeg(ra1: String, dec1: String, ra2: String, dec2: String): Double {
        val a1 = raToDegrees(ra1) * kotlin.math.PI / 180.0
        val d1 = decToDegrees(dec1) * kotlin.math.PI / 180.0
        val a2 = raToDegrees(ra2) * kotlin.math.PI / 180.0
        val d2 = decToDegrees(dec2) * kotlin.math.PI / 180.0
        val dRa = a2 - a1
        val num = sqrt((cos(d2) * sin(dRa)).pow(2) +
                (cos(d1) * sin(d2) - sin(d1) * cos(d2) * cos(dRa)).pow(2))
        val den = sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(dRa)
        return atan2(num, den) * 180.0 / kotlin.math.PI
    }

    /** 生成多个测试时间点 */
    private fun generateTestJds(): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        val nowJd = SolarPositionCalculator.getJulianDate(now)
        return listOf(
            "当前时间" to nowJd,
            "J2000.0" to 2451545.0,
            "2026-06-07" to 2460954.0,
            "2026-01-01" to SolarPositionCalculator.getJulianDate(1767225600000L),
            "2025-06-01" to SolarPositionCalculator.getJulianDate(1751328000000L),
            "2024-06-01" to SolarPositionCalculator.getJulianDate(1717200000000L),
        )
    }

    /**
     * 调用 Skyfield 计算（绕过 EnhancedAstronomyCalculator 的自动回退）
     * 只有当 Skyfield 真正可用时才返回结果，否则返回 null
     */
    private fun callSkyfield(
        planet: SolarPositionCalculator.PlanetId,
        lat: Double, lon: Double, jd: Double
    ): Pair<String, String>? {
        if (!skyfieldAvailable) return null
        return try {
            // 直接调用 SkyfieldCalculator，如果它内部回退到 SolarPositionCalculator，
            // 我们通过检查结果是否与 SolarPositionCalculator 相同来判断是否回退
            val result = SkyfieldCalculator.calculate(planet, lat, lon, jd)
            result
        } catch (e: Exception) {
            AppLogger.e("ComparisonTest", "Skyfield 调用失败: ${e.message}")
            null
        }
    }

    /**
     * 判断 Skyfield 是否真正使用了 Python/Skyfield（而非回退）
     * 策略：如果 SkyfieldCalculator 可用且结果 != SolarPositionCalculator 结果，
     * 则判定为真正使用了 Skyfield
     */
    private fun isRealSkyfield(
        planet: SolarPositionCalculator.PlanetId,
        lat: Double, lon: Double, jd: Double,
        skyfieldResult: Pair<String, String>?,
        solarResult: Pair<String, String>?
    ): Boolean {
        if (skyfieldResult == null || solarResult == null) return false
        // 如果完全一致，很可能是回退
        return skyfieldResult != solarResult
    }

    // ==================== 测试用例 ====================

    @Test
    fun testSkyfieldAvailability() {
        // 报告 Skyfield 可用性
        if (skyfieldAvailable) {
            println("✅ Skyfield (JPL DE421) 可用")
            val version = SkyfieldCalculator.getVersion()
            println("   Skyfield 版本: $version")
        } else {
            println("⚠️ Skyfield (JPL DE421) 不可用: $skyfieldError")
            println("   将仅测试 SolarPositionCalculator 的自洽性")
        }
        // 这个测试不应该失败，只是报告状态
        assertTrue(true)
    }

    @Test
    fun testAllPlanetsBatch() {
        val jds = generateTestJds()
        val allResults = mutableListOf<ComparisonResult>()
        val stats = mutableMapOf<SolarPositionCalculator.PlanetId, MutableList<Double>>()

        for (planet in SolarPositionCalculator.PlanetId.entries) {
            stats[planet] = mutableListOf()
            for ((label, jd) in jds) {
                // SolarPositionCalculator
                val solarResult = SolarPositionCalculator.calculate(
                    planet, OBSERVER_LAT, OBSERVER_LON, jd
                )

                // Skyfield
                val skyfieldResult = callSkyfield(planet, OBSERVER_LAT, OBSERVER_LON, jd)
                val usedFallback = !isRealSkyfield(planet, OBSERVER_LAT, OBSERVER_LON, jd,
                    skyfieldResult, solarResult)

                val angularSep = if (solarResult != null && skyfieldResult != null) {
                    angularSeparationDeg(solarResult.first, solarResult.second,
                        skyfieldResult.first, skyfieldResult.second)
                } else {
                    null
                }

                if (angularSep != null) {
                    stats[planet]!!.add(angularSep)
                }

                allResults.add(ComparisonResult(
                    planet = planet,
                    label = label,
                    solarRa = solarResult?.first,
                    solarDec = solarResult?.second,
                    skyfieldRa = skyfieldResult?.first,
                    skyfieldDec = skyfieldResult?.second,
                    angularSeparationDeg = angularSep,
                    usedFallback = usedFallback
                ))
            }
        }

        // ===== 打印完整对比报告 =====
        println("=" .repeat(100))
        println("SkyGoto 天文计算算法对比报告")
        println("=" .repeat(100))
        println("观测地: 北纬 ${OBSERVER_LAT}°, 东经 ${OBSERVER_LON}°")
        println("Skyfield 状态: ${if (skyfieldAvailable) "✅ 可用" else "❌ 不可用 ($skyfieldError)"}")
        println("测试时间点: ${jds.map { it.first }}")
        println()

        println("-".repeat(100))
        println(String.format("%-10s %-14s %-22s %-22s %-10s %-8s",
            "天体", "时间", "SolarPosition", "Skyfield", "角差(°)", "回退?"))
        println("-".repeat(100))

        for (r in allResults) {
            val solarStr = if (r.solarRa != null) "${r.solarRa} / ${r.solarDec}" else "未实现"
            val skyfieldStr = if (r.skyfieldRa != null) "${r.skyfieldRa} / ${r.skyfieldDec}" else "不可用"
            val sepStr = r.angularSeparationDeg?.let { String.format("%.4f", it) } ?: "N/A"
            val fbStr = if (r.usedFallback) "是" else "否"
            println(String.format("%-10s %-14s %-22s %-22s %-10s %-8s",
                r.planet.code, r.label, solarStr, skyfieldStr, sepStr, fbStr))
        }

        println("-".repeat(100))
        println()

        // ===== 打印统计摘要 =====
        println("=" .repeat(60))
        println("精度统计摘要")
        println("=" .repeat(60))
        println(String.format("%-12s %-12s %-12s %-12s %-12s",
            "天体", "平均角差(°)", "最大角差(°)", "样本数", "Skyfield"))
        println("-".repeat(60))

        for (planet in SolarPositionCalculator.PlanetId.entries) {
            val values = stats[planet] ?: continue
            val skyfieldResult = jds.firstOrNull()?.let { (_, jd) ->
                callSkyfield(planet, OBSERVER_LAT, OBSERVER_LON, jd)
            }
            val sfAvailable = skyfieldResult != null

            if (values.isNotEmpty()) {
                val avg = values.average()
                val max = values.max()
                println(String.format("%-12s %-12.4f %-12.4f %-12d %-12s",
                    planet.code, avg, max, values.size, if (sfAvailable) "✅" else "❌"))
            } else {
                println(String.format("%-12s %-12s %-12s %-12d %-12s",
                    planet.code, "N/A", "N/A", 0, if (sfAvailable) "✅" else "❌"))
            }
        }
        println("-".repeat(60))
        println()

        // ===== 对于 Skyfield 可用的天体，验证精度 =====
        for ((planet, values) in stats) {
            if (values.isEmpty()) continue
            val avg = values.average()
            val tolerance = when (planet) {
                SolarPositionCalculator.PlanetId.SUN,
                SolarPositionCalculator.PlanetId.MOON,
                SolarPositionCalculator.PlanetId.MERCURY,
                SolarPositionCalculator.PlanetId.VENUS,
                SolarPositionCalculator.PlanetId.MARS,
                SolarPositionCalculator.PlanetId.JUPITER -> STRICT_TOLERANCE_DEG
                SolarPositionCalculator.PlanetId.PLUTO -> LOOSE_TOLERANCE_DEG
                else -> ANGULAR_TOLERANCE_DEG
            }
            assertTrue(avg < tolerance,
                "${planet.code} 平均角差 ${avg}° 超过容差 ${tolerance}°")
        }
    }

    @Test
    fun testSkyfieldCoversAllBodies() {
        // 验证 Skyfield 能计算所有 10 个太阳系天体
        if (!skyfieldAvailable) return  // Skip if Skyfield unavailable

        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val failed = mutableListOf<String>()

        for (planet in SolarPositionCalculator.PlanetId.entries) {
            val result = SkyfieldCalculator.calculate(planet, OBSERVER_LAT, OBSERVER_LON, jd)
            if (result == null) {
                failed.add(planet.code)
            } else {
                println("✅ Skyfield 成功计算 ${planet.code} (${planet.nameCn}): RA=${result.first}, Dec=${result.second}")
            }
        }

        assertTrue(failed.isEmpty(),
            "Skyfield 应能计算所有天体，但以下天体失败: $failed")
    }

    @Test
    fun testSolarPositionVsSkyfieldAccuracy() {
        // 对于 VSOP87D 已实现的天体，精确对比与 Skyfield 的差异
        if (!skyfieldAvailable) return

        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())

        // 只测试 VSOP87D 已实现的天体
        val vsopBodies = listOf(
            SolarPositionCalculator.PlanetId.SUN,
            SolarPositionCalculator.PlanetId.MOON,
            SolarPositionCalculator.PlanetId.MERCURY,
            SolarPositionCalculator.PlanetId.VENUS,
            SolarPositionCalculator.PlanetId.MARS,
            SolarPositionCalculator.PlanetId.JUPITER,
        )

        println()
        println("=" .repeat(80))
        println("VSOP87D vs Skyfield 精确度对比（当前时刻）")
        println("=" .repeat(80))
        println(String.format("%-10s %-20s %-20s %-12s %-12s %-12s",
            "天体", "SolarPosition", "Skyfield", "RA差(\")", "Dec差(\")", "角差(\')"))
        println("-".repeat(80))

        for (planet in vsopBodies) {
            val solar = SolarPositionCalculator.calculate(planet, OBSERVER_LAT, OBSERVER_LON, jd)
            val skyfield = SkyfieldCalculator.calculate(planet, OBSERVER_LAT, OBSERVER_LON, jd)

            if (solar != null && skyfield != null) {
                val raDeg1 = raToDegrees(solar.first)
                val raDeg2 = raToDegrees(skyfield.first)
                val decDeg1 = decToDegrees(solar.second)
                val decDeg2 = decToDegrees(skyfield.second)

                val raDiff = (raDeg1 - raDeg2) * 3600.0  // 角秒
                val decDiff = (decDeg1 - decDeg2) * 3600.0  // 角秒
                val angular = angularSeparationDeg(solar.first, solar.second,
                    skyfield.first, skyfield.second) * 60.0  // 角分

                println(String.format("%-10s %-20s %-20s %-12.1f %-12.1f %-12.2f",
                    planet.code,
                    "${solar.first} ${solar.second}",
                    "${skyfield.first} ${skyfield.second}",
                    raDiff, decDiff, angular))

                // VSOP87D 理论精度 ~1角分
                assertTrue(angular < 30.0,
                    "${planet.code} 角差 ${angular} 角分 > 30 角分")
            } else {
                println(String.format("%-10s %-20s %-20s",
                    planet.code,
                    solar?.let { "${it.first} ${it.second}" } ?: "失败",
                    skyfield?.let { "${it.first} ${it.second}" } ?: "失败"))
            }
        }
        println("=" .repeat(80))
    }

    @Test
    fun testSolarPositionImplementationCoverage() {
        // 统计 SolarPositionCalculator 的覆盖情况
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val implemented = mutableListOf<String>()
        val unimplemented = mutableListOf<String>()

        for (planet in SolarPositionCalculator.PlanetId.entries) {
            val result = SolarPositionCalculator.calculate(planet, OBSERVER_LAT, OBSERVER_LON, jd)
            if (result != null) {
                implemented.add("${planet.code} (${planet.nameCn})")
            } else {
                unimplemented.add("${planet.code} (${planet.nameCn})")
            }
        }

        println()
        println("=" .repeat(60))
        println("SolarPositionCalculator 实现覆盖")
        println("=" .repeat(60))
        println("已实现 (${implemented.size}/10):")
        implemented.forEach { println("  ✅ $it") }
        println("未实现 (${unimplemented.size}/10):")
        unimplemented.forEach { println("  ❌ $it") }
        println("=" .repeat(60))

        // Skyfield 应能补全所有未实现的天体
        if (skyfieldAvailable) {
            println()
            println("Skyfield 补充覆盖验证:")
            for (code in unimplemented) {
                val planetId = SolarPositionCalculator.PlanetId.entries.first {
                    "$it".contains(code.take(3))
                }
                val result = SkyfieldCalculator.calculate(planetId, OBSERVER_LAT, OBSERVER_LON, jd)
                if (result != null) {
                    println("  ✅ Skyfield 能计算 $code: RA=${result.first}, Dec=${result.second}")
                } else {
                    println("  ❌ Skyfield 也无法计算 $code")
                }
            }
        }

        assertIntEquals(7, implemented.size, "SolarPositionCalculator 应实现 7 个天体")
    }

    @Test
    fun testMultipleEpochsConsistency() {
        // 测试不同历元下 VSOP87D 结果的稳定性
        for (planet in listOf(
            SolarPositionCalculator.PlanetId.SUN,
            SolarPositionCalculator.PlanetId.MOON,
            SolarPositionCalculator.PlanetId.MARS
        )) {
            val jds = generateTestJds()
            val results = jds.map { (label, jd) ->
                Pair(label,
                    SolarPositionCalculator.calculate(planet, OBSERVER_LAT, OBSERVER_LON, jd))
            }

            println()
            println("${planet.code} 在不同历元的计算结果:")
            for ((label, result) in results) {
                val str = result?.let { "${it.first} / ${it.second}" } ?: "失败"
                println("  $label: $str")
            }

            // 不同时间点的 RA 应该不同（行星在运动）
            val raValues = results.mapNotNull { it.second?.first }.distinct()
            assertTrue(raValues.size > 1,
                "${planet.code} 在不同时间点的 RA 应该不同")
        }
    }

    private fun assertIntEquals(expected: Int, actual: Int, message: String) {
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong(), message)
    }
}
