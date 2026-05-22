package com.skygoto.app.domain.model

import kotlin.math.*

/**
 * 太阳系天体位置计算器
 * 
 * 基于 Jean Meeus《天文算法》中的 VSOP87 简化算法
 * 计算太阳系主要天体（太阳、月亮、行星）的赤经(RA)和赤纬(Dec)
 * 
 * 使用方法:
 *   val calculator = SolarPositionCalculator()
 *   val position = calculator.calculate(PlanetId.JUPITER, observerLat, observerLon, jd)
 *   // position.first = RA in "HH:MM:SS" format
 *   // position.second = Dec in "+DD*MM:SS" format
 */
object SolarPositionCalculator {

    // ========== 天体 ID 枚举 ==========
    enum class PlanetId(val code: String, val nameCn: String) {
        SUN("SOL", "太阳"),
        MOON("LUN", "月亮"),
        MERCURY("MER", "水星"),
        VENUS("VEN", "金星"),
        MARS("MAR", "火星"),
        JUPITER("JUP", "木星"),
        SATURN("SAT", "土星"),
        URANUS("URA", "天王星"),
        NEPTUNE("NEP", "海王星"),
        PLUTO("PLU", "冥王星")
    }

    // ========== 轨道根数 (J2000.0 历元) ==========
    // L0, L1 (rad), M0, M1 (rad), e0, a0, i0 (rad), perihelion, longNode (rad)
    private data class OrbitalElements(
        val L0: Double, val L1: Double, 
        val M0: Double, val M1: Double,
        val e0: Double, val a0: Double, 
        val i0: Double, 
        val perihelion: Double, val longNode: Double
    )

    // 各行星平均轨道根数 (J2000.0, T = 儒略世纪数)
    private val planetaryElements = mapOf(
        PlanetId.MERCURY to OrbitalElements(
            L0 = 4.402507, L1 = 2608.7903, M0 = 6.206240, M1 = 4.093237,
            e0 = 0.205557, a0 = 0.387098, i0 = 0.122259, 
            perihelion = 0.508192, longNode = 0.843509
        ),
        PlanetId.VENUS to OrbitalElements(
            L0 = 3.225058, L1 = 1021.5548, M0 = 0.265463, M1 = 1.277145,
            e0 = 0.006759, a0 = 0.723330, i0 = 0.059305, 
            perihelion = 0.967796, longNode = 1.332170
        ),
        PlanetId.MARS to OrbitalElements(
            L0 = 4.598230, L1 = 532.4418, M0 = 5.400116, M1 = 2.135424,
            e0 = 0.093433, a0 = 1.523679, i0 = 0.032289, 
            perihelion = 4.821960, longNode = 0.495389
        ),
        PlanetId.JUPITER to OrbitalElements(
            L0 = 4.816918, L1 = 90.7292, M0 = 6.204594, M1 = 0.083538,
            e0 = 0.048375, a0 = 5.202561, i0 = 0.022707, 
            perihelion = 0.296474, longNode = 1.751849
        ),
        PlanetId.SATURN to OrbitalElements(
            L0 = 4.533917, L1 = 36.5638, M0 = 5.213496, M1 = 0.033444,
            e0 = 0.052147, a0 = 9.554747, i0 = 0.043778, 
            perihelion = 5.418769, longNode = 1.616069
        ),
        PlanetId.URANUS to OrbitalElements(
            L0 = 4.601980, L1 = 12.2306, M0 = 2.374033, M1 = 0.011712,
            e0 = 0.052012, a0 = 19.218476, i0 = 0.013568, 
            perihelion = 3.396258, longNode = 1.381160
        ),
        PlanetId.NEPTUNE to OrbitalElements(
            L0 = 4.767590, L1 = 6.1027, M0 = 5.567984, M1 = 0.005984,
            e0 = 0.008952, a0 = 30.162177, i0 = 0.030993, 
            perihelion = 4.472792, longNode = 1.899266
        )
    )

    // ========== 计算 Entry ==========
    /**
     * 计算给定行星的当前 RA/Dec 坐标
     * 
     * @param planet 天体 ID
     * @param observerLat 观察者纬度 (度)
     * @param observerLon 观察者经度 (度)
     * @param jd 儒略日 (UTC)
     * @return Pair<RA_Formatted, Dec_Formatted> 或 null (如果是太阳系特殊天体用其他方法)
     */
    fun calculate(planet: PlanetId, observerLat: Double, observerLon: Double, jd: Double): Pair<String, String>? {
        return when (planet) {
            PlanetId.MOON -> calculateMoon(observerLat, observerLon, jd)
            PlanetId.SUN -> calculateSun(observerLat, observerLon, jd)
            else -> calculatePlanet(planet, observerLat, observerLon, jd)
        }
    }

    /**
     * 计算当前时刻的天体位置
     */
    fun calculateNow(planet: PlanetId, observerLat: Double, observerLon: Double): Pair<String, String>? {
        val jd = getJulianDate(System.currentTimeMillis())
        return calculate(planet, observerLat, observerLon, jd)
    }

    // ========== 内部计算方法 ==========

    private fun calculatePlanet(planet: PlanetId, lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        val el = planetaryElements[planet] ?: return null
        val T = (jd - 2451545.0) / 36525.0  // 儒略世纪数
        
        // 计算平黄经 (度 → 弧度)
        val L = (el.L0 + el.L1 * T) * PI / 180.0
        // 计算平近点角 (度 → 弧度)
        val M = (el.M0 + el.M1 * T) * PI / 180.0
        // 偏近点角 (近似)
        val e = el.e0
        val E0 = M + e * sin(M) * (1.0 + e * cos(M))
        // 解开方程得到精确 E
        var E = E0
        for (i in 0..5) {
            E = E - (E - e * sin(E) - M) / (1.0 - e * cos(E))
        }
        
        // 计算真近点角
        val xv = el.a0 * (cos(E) - e)
        val yv = el.a0 * sqrt(1.0 - e * e) * sin(E)
        val v = atan2(yv, xv)
        
        // 计算到太阳的距离 (AU)
        val r = sqrt(xv * xv + yv * yv)
        
        // 计算当前轨道面内的黄道坐标 (弧度)
        val l = L + (v - M)  // 真黄经
        val b = 0.0  // 黄纬 (近似为0)
        
        // 转换为 J2000 黄道坐标
        val sinL = sin(l)
        val cosL = cos(l)
        val sinB = sin(b)
        val cosB = cos(b)
        
        // 倾角 (J2000, 简化)
        val epsilon0 = 23.4392911 * PI / 180.0  // J2000 黄赤交角
        
        // 赤道坐标 (不含岁差,简化)
        val ra = atan2(cos(epsilon0) * sinL, cosL)
        val dec = asin(sin(epsilon0) * sinL * cosB + cos(epsilon0) * sinB)
        
        // 转换为地平坐标 (含本地时角)
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - ra
        
        val sinAlt = sin(dec) * sin(lat * PI / 180) + cos(dec) * cos(lat * PI / 180) * cos(ha)
        val alt = asin(sinAlt)
        val az = atan2(-cos(dec) * sin(ha), cos(dec) * cos(lat * PI / 180) * sin(dec) - sin(lat * PI / 180) * cos(dec) * cos(ha))
        
        // 最终转换为赤道坐标用于 mount
        val raStr = formatRA(ra)
        val decStr = formatDec(dec)
        
        return Pair(raStr, decStr)
    }

    private fun calculateSun(lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        val T = (jd - 2451545.0) / 36525.0
        val L0 = 280.46592 + 36000.76991 * T
        val M = 357.52910 + 35999.05030 * T
        val e = 0.016708617 - 0.000042037 * T
        
        val C = (1.914600 - 0.004817 * T) * sin(M * PI / 180) + 0.0200 * sin(2 * M * PI / 180)
        val sunLon = L0 + C
        val sunAnom = M + C
        val epsilon0 = 23.4393 - 0.0130 * T
        
        val sunRA = atan2(cos(epsilon0 * PI / 180) * sin(sunLon * PI / 180), cos(sunLon * PI / 180))
        val sunDec = asin(sin(epsilon0 * PI / 180) * sin(sunLon * PI / 180))
        
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - sunRA
        
        val sinAlt = sin(sunDec) * sin(lat * PI / 180) + cos(sunDec) * cos(lat * PI / 180) * cos(ha)
        val alt = asin(sinAlt)
        val az = atan2(-cos(sunDec) * sin(ha), cos(sunDec) * cos(lat * PI / 180) * sin(sunDec) - sin(lat * PI / 180) * cos(sunDec) * cos(ha))
        
        val raStr = formatRA(sunRA)
        val decStr = formatDec(sunDec)
        
        return Pair(raStr, decStr)
    }

    private fun calculateMoon(lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        // 简化月球算法 (基于平均要素)
        val T = (jd - 2451545.0) / 36525.0
        
        val L0 = 218.3164591 + 481267.88134236 * T  // 月球平黄经
        val l = 134.9634114 + 477198.8676313 * T     // 太阳平均近点角
        val lp = 357.5291092 + 35999.0502909 * T   // 月球平近点角
        val F = 93.2720993 + 483202.0175273 * T     // 月球升交点平黄经
        val D = 297.8502042 + 445267.1114468 * T   // 日月平距
        
        // 月球坐标 (简化)
        val lambda = L0 + 6.289 * sin(l * PI / 180)
        val beta = 5.128 * sin(F * PI / 180)
        val epsilon0 = 23.4393 * PI / 180
        
        val moonRA = atan2(sin(epsilon0) * sin(lambda * PI / 180) - tan(beta * PI / 180) * cos(epsilon0), cos(lambda * PI / 180))
        val moonDec = asin(sin(epsilon0) * sin(lambda * PI / 180) * cos(beta * PI / 180) + cos(epsilon0) * sin(beta * PI / 180))
        
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - moonRA
        
        val sinAlt = sin(moonDec) * sin(lat * PI / 180) + cos(moonDec) * cos(lat * PI / 180) * cos(ha)
        val alt = asin(sinAlt)
        val az = atan2(-cos(moonDec) * sin(ha), cos(moonDec) * cos(lat * PI / 180) * sin(moonDec) - sin(lat * PI / 180) * cos(moonDec) * cos(ha))
        
        val raStr = formatRA(moonRA)
        val decStr = formatDec(moonDec)
        
        return Pair(raStr, decStr)
    }

    // ========== 工具方法 ==========

    /**
     * 获取儒略日 (UTC 毫秒)
     */
    fun getJulianDate(timeMillis: Long): Double {
        val a = ((timeMillis / 86400000.0) + 2440587.5) / 1.0
        return a
    }

    /**
     * 计算本地平均恒星时 (rad)
     */
    private fun localMeanSiderealTime(jd: Double, lon: Double): Double {
        val T = (jd - 2451545.0) / 36525.0
        val GMST = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * T * T - T * T * T / 38710000.0
        val LMST = (GMST + lon) * PI / 180.0
        return ((LMST % (2 * PI)) + 2 * PI) % (2 * PI)
    }

    /**
     * 格式化赤经为 HH:MM:SS
     */
    private fun formatRA(ra: Double): String {
        var raDeg = (ra * 180.0 / PI)
        if (raDeg < 0) raDeg += 360.0
        val hours = raDeg / 15.0
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        val s = (((hours - h) * 60 - m) * 60)
        return "%02d:%02d:%02d".format(h, m, (s * 100).toInt() / 100)
    }

    /**
     * 格式化赤纬为 +DD*MM:SS 或 -DD*MM:SS
     */
    private fun formatDec(dec: Double): String {
        val sign = if (dec >= 0) "+" else "-"
        val absDec = abs(dec) * 180.0 / PI
        val d = absDec.toInt()
        val m = ((absDec - d) * 60).toInt()
        val s = (((absDec - d) * 60 - m) * 60)
        return "%s%02d*%02d:%02d".format(sign, d, m, (s * 100).toInt() / 100)
    }

    // ========== 岁差修正 (IAU 2000A) ==========
    /**
     * 将 J2000 坐标修正为当前历元坐标（含岁差）
     * @param raJ2000Str 赤经 J2000 字符串 ("HH:MM:SS")
     * @param decJ2000Str 赤纬 J2000 字符串 ("+DD*MM:SS" 或 "-DD*MM:SS")
     * @param jd 目标儒略日
     * @return Pair<当前RA字符串, 当前Dec字符串>
     */
    fun applyPrecession(raJ2000Str: String, decJ2000Str: String, jd: Double): Pair<String, String> {
        val raJ2000 = parseRA(raJ2000Str)
        val decJ2000 = parseDec(decJ2000Str)
        val (ra, dec) = applyPrecessionRad(raJ2000, decJ2000, jd)
        return Pair(formatRA(ra), formatDec(dec))
    }

    /**
     * 将 J2000 弧度坐标修正为当前历元坐标（含岁差）
     * 使用 IAU 2000A (Capitaine et al. 2003) 三角函数公式
     * 与 Skyfield precessionlib.py 一致
     *
     * @param raJ2000Rad 赤经 J2000 (弧度)
     * @param decJ2000Rad 赤纬 J2000 (弧度)
     * @param jd 目标儒略日
     * @return Pair<当前RA弧度, 当前Dec弧度>
     */
    fun applyPrecessionRad(raJ2000Rad: Double, decJ2000Rad: Double, jd: Double): Pair<Double, Double> {
        val T = (jd - 2451545.0) / 36525.0  // 儒略世纪数 (J2000 = 2451545)

        // IAU 2000A Capitaine angles (arcsec)
        val psia   = ((((-0.0000000951  * T
                      + 0.000132851)  * T
                      - 0.00114045)   * T
                      - 1.0790069)    * T
                      + 5038.481507)  * T

        val omegaa = ((((+0.0000003337  * T
                      - 0.000000467)  * T
                      - 0.00772503)   * T
                      + 0.0512623)    * T
                      - 0.025754)     * T + 84381.406

        val chia   = ((((-0.0000000560  * T
                      + 0.000170663)  * T
                      - 0.00121197)   * T
                      - 2.3814292)    * T
                      + 10.556403)    * T

        // 转换为弧度
        val eps0 = 84381.406 * PI / 648000.0
        val psiaRad = psia * PI / 648000.0
        val omegaaRad = omegaa * PI / 648000.0
        val chiaRad = chia * PI / 648000.0

        // 预计算三角函数
        val sa = sin(eps0);         val ca = cos(eps0)
        val sb = sin(-psiaRad);     val cb = cos(-psiaRad)
        val sc = sin(-omegaaRad);   val cc = cos(-omegaaRad)
        val sd = sin(chiaRad);      val cd = cos(chiaRad)

        // 旋转矩阵: R3(chi_a) * R1(-omega_a) * R3(-psi_a) * R1(epsilon_0)
        // 对应 Skyfield precessionlib.py rot3 变量
        val e11 =  cd * cb - sb * sd * cc
        val e12 =  cd * sb * ca + sd * cc * cb * ca - sa * sd * sc
        val e13 =  cd * sb * sa + sd * cc * cb * sa + ca * sd * sc

        val e21 = -sd * cb - sb * cd * cc
        val e22 = -sd * sb * ca + cd * cc * cb * ca - sa * cd * sc
        val e23 = -sd * sb * sa + cd * cc * cb * sa + ca * cd * sc

        val e31 =  sb * sc
        val e32 = -sc * cb * ca - sa * cc
        val e33 = -sc * cb * sa + cc * ca

        // 转换 RA/Dec 到方向余弦
        val cosDec = cos(decJ2000Rad)
        val x = cosDec * cos(raJ2000Rad)
        val y = cosDec * sin(raJ2000Rad)
        val z = sin(decJ2000Rad)

        // 应用旋转矩阵
        val x2 = e11 * x + e12 * y + e13 * z
        val y2 = e21 * x + e22 * y + e23 * z
        val z2 = e31 * x + e32 * y + e33 * z

        // 转回 RA/Dec
        val ra  = atan2(y2, x2)
        val dec = asin(z2.coerceIn(-1.0, 1.0))

        return Pair(normalizeRA(ra), dec)
    }

    /**
     * 解析 RA 字符串 ("HH:MM:SS") → 弧度
     */
    fun parseRA(raStr: String): Double {
        val parts = raStr.trim().split(":")
        if (parts.size < 3) return 0.0
        val h = parts[0].toDoubleOrNull() ?: 0.0
        val m = parts[1].toDoubleOrNull() ?: 0.0
        val s = parts[2].replace("*", ":").toDoubleOrNull() ?: 0.0
        return ((h + m / 60.0 + s / 3600.0) * 15.0) * PI / 180.0
    }

    /**
     * 解析 Dec 字符串 ("+DD*MM:SS" 或 "-DD*MM:SS") → 弧度
     */
    fun parseDec(decStr: String): Double {
        val s = decStr.trim().replace("*", ":")
        val negative = s.startsWith("-")
        val absStr = if (negative) s.substring(1) else s
        val parts = absStr.split(":")
        if (parts.size < 3) return 0.0
        val d = parts[0].toDoubleOrNull() ?: 0.0
        val m = parts[1].toDoubleOrNull() ?: 0.0
        val sec = parts[2].toDoubleOrNull() ?: 0.0
        val deg = d + m / 60.0 + sec / 3600.0
        return (if (negative) -deg else deg) * PI / 180.0
    }

    /**
     * 将 RA 弧度规范化到 [0, 2π)
     */
    private fun normalizeRA(ra: Double): Double {
        var result = ra % (2 * PI)
        if (result < 0) result += 2 * PI
        return result
    }

    // ========== 地平坐标计算 ==========
    /**
     * 计算任意天体的地平高度和方位角
     * @param raRad 赤经（弧度）
     * @param decRad 赤纬（弧度）
     * @param observerLat 观察者纬度（度）
     * @param observerLon 观察者经度（度）
     * @param jd 儒略日
     * @return Pair<高度角度, 方位角度>
     */
    fun getAltitudeAzimuth(
        raRad: Double,
        decRad: Double,
        observerLat: Double,
        observerLon: Double,
        jd: Double
    ): Pair<Double, Double> {
        val lmst = localMeanSiderealTime(jd, observerLon)
        val ha = lmst - raRad
        val latRad = observerLat * PI / 180.0

        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0)) * 180.0 / PI

        // 方位角公式（标准天文公式）：从正北起算，顺时针为正
        val az = atan2(-cos(decRad) * sin(ha),
                       sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(ha)) * 180.0 / PI

        val azNorm = if (az < 0) az + 360.0 else az
        return Pair(alt, azNorm)
    }

    /**
     * 计算任意天体的地平高度（重载版本，接受 RA/Dec 字符串）
     */
    fun getAltitude(raStr: String, decStr: String, observerLat: Double, observerLon: Double, jd: Double): Double {
        val ra = parseRA(raStr)
        val dec = parseDec(decStr)
        return getAltitudeAzimuth(ra, dec, observerLat, observerLon, jd).first
    }
}