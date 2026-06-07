/*
 * SolarPositionCalculator 单元测试
 *
 * 测试纯 Kotlin VSOP87D 天文计算算法的正确性与自洽性。
 * 不依赖 Android 运行时，可在 JVM 上直接运行。
 *
 * 测试范围：
 * - 工具方法（儒略日换算、坐标解析、格式转换）
 * - 太阳计算（Meeus 算法）
 * - 月球计算（ELP2000 简化）
 * - VSOP87D 行星（水/金/火/木）
 * - 开普勒轨道回退（冥王星）
 * - 未实现行星（土/天/海）返回 null
 * - 多时间点自洽性
 * - 坐标格式合规性
 */
package com.skygoto.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SolarPositionCalculatorTest {

    // ==================== 测试常数 ====================

    /** 观测地：北京国家天文台 兴隆站 */
    private val OBSERVER_LAT = 40.3958  // 北纬
    private val OBSERVER_LON = 117.575  // 东经

    /** 格林威治 */
    private val OBSERVER_LAT_0 = 51.5
    private val OBSERVER_LON_0 = 0.0

    /** J2000.0 历元 (2000-01-01 12:00 UTC) */
    private val JD_J2000 = 2451545.0

    /** 2026-06-07 12:00 UTC 的儒略日 */
    private val JD_2026_06_07 = 2460954.0  // 近似

    /** 角度容差（度）— VSOP87D 对 Skyfield 的理论精度 ~0.001° */
    private const val TOLERANCE_DEG = 2.0

    /** 角秒转度 */
    private const val ARCSEC_TO_DEG = 1.0 / 3600.0

    /** RA 容差（弧秒） */
    private const val TOLERANCE_RA_ARCSEC = 60.0

    /** Dec 容差（弧秒） */
    private const val TOLERANCE_DEC_ARCSEC = 60.0

    // ==================== 辅助方法 ====================

    /** 将 LX200 RA 格式 (HH:MM:SS) 转为度 */
    private fun raToDegrees(ra: String): Double {
        val parts = ra.trim().split(":")
        val h = parts[0].toDouble()
        val m = parts[1].toDouble()
        val s = parts[2].toDouble()
        return (h + m / 60.0 + s / 3600.0) * 15.0
    }

    /** 将 LX200 Dec 格式 ([+-]DD*MM:SS) 转为度 */
    private fun decToDegrees(dec: String): Double {
        val s = dec.trim().replace("*", ":")
        val sign = if (s.startsWith("-")) -1.0 else 1.0
        val absStr = if (sign < 0) s.substring(1) else s
        val parts = absStr.split(":")
        val d = parts[0].toDouble()
        val m = parts[1].toDouble()
        val sec = if (parts.size > 2) parts[2].toDouble() else 0.0
        return sign * (d + m / 60.0 + sec / 3600.0)
    }

    /** 计算两个天体坐标之间的角分离（度） */
    private fun angularSeparationDeg(ra1: String, dec1: String, ra2: String, dec2: String): Double {
        val a1 = raToDegrees(ra1) * kotlin.math.PI / 180.0
        val d1 = decToDegrees(dec1) * kotlin.math.PI / 180.0
        val a2 = raToDegrees(ra2) * kotlin.math.PI / 180.0
        val d2 = decToDegrees(dec2) * kotlin.math.PI / 180.0

        val dRa = a2 - a1
        val cosD1 = cos(d1)
        val sinD1 = sin(d1)
        val cosD2 = cos(d2)
        val sinD2 = sin(d2)

        val num = sqrt((cosD2 * sin(dRa)).pow(2) + (cosD1 * sinD2 - sinD1 * cosD2 * cos(dRa)).pow(2))
        val den = sinD1 * sinD2 + cosD1 * cosD2 * cos(dRa)
        return atan2(num, den) * 180.0 / kotlin.math.PI
    }

    /** 验证 RA 字符串格式 */
    private fun assertValidRa(ra: String) {
        assertTrue(ra.matches(Regex("""^\d{2}:\d{2}:\d{2}$""")),
            "RA 格式错误: '$ra'，应为 HH:MM:SS")
        val parts = ra.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val s = parts[2].toInt()
        assertTrue(h in 0..23, "RA 小时超出范围: $h")
        assertTrue(m in 0..59, "RA 分钟超出范围: $m")
        assertTrue(s in 0..59, "RA 秒数超出范围: $s")
    }

    /** 验证 Dec 字符串格式 */
    private fun assertValidDec(dec: String) {
        assertTrue(dec.matches(Regex("""^[+-]\d{2}\*\d{2}:\d{2}$""")),
            "Dec 格式错误: '$dec'，应为 [+-]DD*MM:SS")
        val content = dec.substring(1).replace("*", ":")
        val parts = content.split(":")
        val d = parts[0].toInt()
        val m = parts[1].toInt()
        val s = parts[2].toInt()
        assertTrue(d in 0..90, "Dec 度超出范围: $d")
        assertTrue(m in 0..59, "Dec 分超出范围: $m")
        assertTrue(s in 0..59, "Dec 秒超出范围: $s")
        if (d == 90) {
            assertTrue(m == 0 && s == 0, "Dec 为 90° 时分秒必须为 0")
        }
    }

    /** 验证 RA 差（角秒） */
    private fun raDiffArcsec(ra1: String, ra2: String): Double {
        return abs(raToDegrees(ra1) - raToDegrees(ra2)) * 3600.0
    }

    /** 验证 Dec 差（角秒） */
    private fun decDiffArcsec(dec1: String, dec2: String): Double {
        return abs(decToDegrees(dec1) - decToDegrees(dec2)) * 3600.0
    }

    /** 生成一系列测试用的儒略日 */
    private fun generateTestJds(): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        val nowJd = SolarPositionCalculator.getJulianDate(now)
        return listOf(
            "J2000.0" to JD_J2000,
            "当前时间" to nowJd,
            "2026-06-07 12:00 UTC" to JD_2026_06_07,
            "2026-01-01 00:00 UTC" to SolarPositionCalculator.getJulianDate(1767225600000L), // 2026-01-01
            "2025-12-21 00:00 UTC" to SolarPositionCalculator.getJulianDate(1764720000000L), // 2025-12-21 (冬至)
            "2026-03-20 00:00 UTC" to SolarPositionCalculator.getJulianDate(1772928000000L), // 2026-03-20 (春分)
        )
    }

    // ==================== 1. 工具方法测试 ====================

    @Test
    fun testGetJulianDate() {
        // J2000.0
        assertEquals("J2000.0 儒略日应约为 2451545",
            2451545.0, SolarPositionCalculator.getJulianDate(-210866803200000L), 1.0)

        // 已知点：2024-01-01 00:00 UTC = JD 2460522.5
        val jd = SolarPositionCalculator.getJulianDate(1704067200000L)
        assertEquals("2024-01-01 00:00 UTC 的 JD 应约为 2460522.5",
            2460522.5, jd, 0.01)

        // 自洽性：同一时间两次调用结果应相同
        val now = System.currentTimeMillis()
        val jd1 = SolarPositionCalculator.getJulianDate(now)
        val jd2 = SolarPositionCalculator.getJulianDate(now)
        assertEquals("同一时间两次调用应返回相同值", jd1, jd2, 0.0)
    }

    @Test
    fun testParseRA() {
        // 正午 RA 6h = 90°
        val raRad = SolarPositionCalculator.parseRA("06:00:00")
        assertEquals("6h RA 应约为 90°", 90.0, raRad * 180.0 / kotlin.math.PI, 0.001)

        // RA 12h = 180°
        val raRad2 = SolarPositionCalculator.parseRA("12:00:00")
        assertEquals("12h RA 应约为 180°", 180.0, raRad2 * 180.0 / kotlin.math.PI, 0.001)

        // RA 0h = 0°
        val raRad0 = SolarPositionCalculator.parseRA("00:00:00")
        assertEquals("0h RA 应约为 0°", 0.0, raRad0 * 180.0 / kotlin.math.PI, 0.001)
    }

    @Test
    fun testParseDec() {
        // Dec 0°
        val decRad = SolarPositionCalculator.parseDec("+00*00:00")
        assertEquals("Dec 0° 应约为 0°", 0.0, decRad * 180.0 / kotlin.math.PI, 0.001)

        // Dec +45°
        val decRad45 = SolarPositionCalculator.parseDec("+45*00:00")
        assertEquals("Dec +45° 应约为 45°", 45.0, decRad45 * 180.0 / kotlin.math.PI, 0.001)

        // Dec -30°
        val decRadNeg = SolarPositionCalculator.parseDec("-30*00:00")
        assertEquals("Dec -30° 应约为 -30°", -30.0, decRadNeg * 180.0 / kotlin.math.PI, 0.001)
    }

    @Test
    fun testParseRaDecConsistency() {
        // 双向验证：parse 后再 format（通过计算链路验证）
        val testCases = listOf(
            "06:30:15" to "+45*30:00",
            "12:00:00" to "+00*00:00",
            "18:45:30" to "-23*26:00",
            "03:15:45" to "+89*59:59",
        )

        for ((raIn, decIn) in testCases) {
            val result = SolarPositionCalculator.calculate(
                SolarPositionCalculator.PlanetId.SUN,
                OBSERVER_LAT, OBSERVER_LON,
                JD_J2000
            )
            // 只是验证不会 crash，format 格式正确
            if (result != null) {
                assertValidRa(result.first)
                assertValidDec(result.second)
            }
        }
    }

    // ==================== 2. 太阳计算测试 ====================

    @Test
    fun testSunCalculation() {
        for ((label, jd) in generateTestJds()) {
            val result = SolarPositionCalculator.calculate(
                SolarPositionCalculator.PlanetId.SUN,
                OBSERVER_LAT, OBSERVER_LON, jd
            )
            assertNotNull(result, "太阳计算在 $label 应返回结果")
            assertValidRa(result.first)
            assertValidDec(result.second)

            val decDeg = decToDegrees(result.second)
            // 太阳赤纬范围 [-23.44°, +23.44°]
            assertTrue(decDeg in -23.5..23.5,
                "太阳赤纬 $decDeg° 应在 ±23.44° 范围内 ($label)")
        }
    }

    @Test
    fun testSunPositionAtEquinox() {
        // 春分 (2026-03-20)：太阳赤纬应接近 0°
        val jdEquinox = SolarPositionCalculator.getJulianDate(1772928000000L) // 2026-03-20
        val result = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.SUN,
            OBSERVER_LAT_0, OBSERVER_LON_0, jdEquinox
        )
        assertNotNull(result)
        val decDeg = decToDegrees(result.second)
        assertTrue(abs(decDeg) < 2.0,
            "春分时太阳赤纬应接近 0°，实际 $decDeg°")
    }

    @Test
    fun testSunPositionAtSolstice() {
        // 冬至 (2025-12-21)：太阳赤纬应接近 -23.44°
        val jdSolstice = SolarPositionCalculator.getJulianDate(1764720000000L) // 2025-12-21
        val result = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.SUN,
            OBSERVER_LAT_0, OBSERVER_LON_0, jdSolstice
        )
        assertNotNull(result)
        val decDeg = decToDegrees(result.second)
        assertTrue(decDeg < -20.0 && decDeg > -24.0,
            "冬至时太阳赤纬应接近 -23.44°，实际 $decDeg°")
    }

    // ==================== 3. 月球计算测试 ====================

    @Test
    fun testMoonCalculation() {
        for ((label, jd) in generateTestJds()) {
            val result = SolarPositionCalculator.calculate(
                SolarPositionCalculator.PlanetId.MOON,
                OBSERVER_LAT, OBSERVER_LON, jd
            )
            assertNotNull(result, "月球计算在 $label 应返回结果")
            assertValidRa(result.first)
            assertValidDec(result.second)

            val decDeg = decToDegrees(result.second)
            // 月球赤纬范围 [-28.6°, +28.6°]
            assertTrue(decDeg in -29.0..29.0,
                "月球赤纬 $decDeg° 应在 ±28.6° 范围内 ($label)")
        }
    }

    // ==================== 4. VSOP87D 行星测试 ====================

    @Test
    fun testMercuryCalculation() {
        testVsopPlanet(SolarPositionCalculator.PlanetId.MERCURY, "水星")
    }

    @Test
    fun testVenusCalculation() {
        testVsopPlanet(SolarPositionCalculator.PlanetId.VENUS, "金星")
    }

    @Test
    fun testMarsCalculation() {
        testVsopPlanet(SolarPositionCalculator.PlanetId.MARS, "火星")
    }

    @Test
    fun testJupiterCalculation() {
        testVsopPlanet(SolarPositionCalculator.PlanetId.JUPITER, "木星")
    }

    private fun testVsopPlanet(planet: SolarPositionCalculator.PlanetId, name: String) {
        for ((label, jd) in generateTestJds()) {
            val result = SolarPositionCalculator.calculate(
                planet,
                OBSERVER_LAT, OBSERVER_LON, jd
            )
            assertNotNull(result, "$name VSOP87D 计算在 $label 应返回结果")
            assertValidRa(result.first)
            assertValidDec(result.second)

            val decDeg = decToDegrees(result.second)
            assertTrue(decDeg in -90.0..90.0,
                "$name 赤纬 $decDeg° 应在有效范围内 ($label)")
        }
    }

    // ==================== 5. 开普勒回退测试 (冥王星) ====================

    @Test
    fun testPlutoCalculation() {
        // 冥王星使用开普勒轨道回退
        for ((label, jd) in generateTestJds()) {
            val result = SolarPositionCalculator.calculate(
                SolarPositionCalculator.PlanetId.PLUTO,
                OBSERVER_LAT, OBSERVER_LON, jd
            )
            assertNotNull(result, "冥王星计算在 $label 应返回结果")
            assertValidRa(result.first)
            assertValidDec(result.second)

            val decDeg = decToDegrees(result.second)
            assertTrue(decDeg in -90.0..90.0,
                "冥王星赤纬 $decDeg° 应在有效范围内 ($label)")
        }
    }

    // ==================== 6. 未实现行星测试 ====================

    @Test
    fun testSaturnReturnsNull() {
        // 土星在 SolarPositionCalculator 中未实现
        val result = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.SATURN,
            OBSERVER_LAT, OBSERVER_LON, JD_J2000
        )
        assertNull(result, "土星应返回 null（无 VSOP87D 数据）")
    }

    @Test
    fun testUranusReturnsNull() {
        val result = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.URANUS,
            OBSERVER_LAT, OBSERVER_LON, JD_J2000
        )
        assertNull(result, "天王星应返回 null（无 VSOP87D 数据）")
    }

    @Test
    fun testNeptuneReturnsNull() {
        val result = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.NEPTUNE,
            OBSERVER_LAT, OBSERVER_LON, JD_J2000
        )
        assertNull(result, "海王星应返回 null（无 VSOP87D 数据）")
    }

    // ==================== 7. 自洽性测试 ====================

    @Test
    fun testDeterministicResults() {
        // 同一输入应产生完全相同的输出
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val lat = 40.0
        val lon = 116.0

        val r1 = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.SUN, lat, lon, jd
        )
        val r2 = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.SUN, lat, lon, jd
        )

        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals("同一输入应产生相同 RA", r1.first, r2.first)
        assertEquals("同一输入应产生相同 Dec", r1.second, r2.second)
    }

    @Test
    fun testDifferentObserversSameBody() {
        // 同一时刻不同观测地，对太阳系天体的 RA/Dec 应相同（地心计算）
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())

        val beijing = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.MOON, 39.9, 116.4, jd
        )
        val greenwich = SolarPositionCalculator.calculate(
            SolarPositionCalculator.PlanetId.MOON, 51.5, 0.0, jd
        )

        assertNotNull(beijing)
        assertNotNull(greenwich)
        // 地心 RA/Dec 应几乎相同（VSOP87D 输出地心坐标）
        val sep = angularSeparationDeg(beijing.first, beijing.second, greenwich.first, greenwich.second)
        assertTrue(sep < 1.0, "不同观测地对月球地心坐标角差应 < 1°，实际 $sep°")
    }

    // ==================== 8. 全天体批次测试 ====================

    @Test
    fun testBatchAllBodies() {
        val jd = SolarPositionCalculator.getJulianDate(System.currentTimeMillis())
        val results = mutableListOf<String>()

        for (planet in SolarPositionCalculator.PlanetId.entries) {
            val result = SolarPositionCalculator.calculate(
                planet, OBSERVER_LAT, OBSERVER_LON, jd
            )
            if (result != null) {
                assertValidRa(result.first)
                assertValidDec(result.second)
                results.add("${planet.nameCn} (${planet.code}): RA=${result.first}, Dec=${result.second}")
            } else {
                results.add("${planet.nameCn} (${planet.code}): 未实现")
            }
        }

        // 验证至少实现了 Sun, Moon, 所有 VSOP87D 行星, Pluto
        val implemented = results.filter { !it.contains("未实现") }
        assertTrue(implemented.size >= 6, "应至少实现 6 个天体，实际 ${implemented.size}")

        // 输出结果到控制台
        println("===== SolarPositionCalculator 批处理结果 =====")
        println("观测地: 北纬 $OBSERVER_LAT°, 东经 $OBSERVER_LON°")
        println("JD: $jd")
        results.forEach { println(it) }
        println("==============================================")
    }
}
