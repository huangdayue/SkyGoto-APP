package com.skygoto.app.domain.model

import kotlin.math.*

/**
 * 太阳系天体位置计算器
 * 
 * 基于 Jean Meeus《天文算法》增强版
 * 改进内容:
 * - 章动修正 (IAU 1980 简化模型)
 * - 月球 ELP2000 简化算法 (30+ 周期项)
 * - 土星木星摄动修正
 * - 太阳方程心项 + 光行差
 */
object SolarPositionCalculator {

    private const val PI_OVER_180 = PI / 180.0
    private const val ARCSEC_TO_RAD = PI / 648000.0

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
    private data class OrbitalElements(
        val L0: Double, val L1: Double, 
        val M0: Double, val M1: Double,
        val e0: Double, val a0: Double, 
        val i0: Double, 
        val perihelion: Double, val longNode: Double
    )

    // 各行星平均轨道根数 (度, T = 儒略世纪数)
    private val planetaryElements = mapOf(
        PlanetId.MERCURY to OrbitalElements(
            L0 = 252.2509, L1 = 149472.6746, M0 = 174.7958, M1 = 149472.6746,
            e0 = 0.205557, a0 = 0.387098, i0 = 7.0047, 
            perihelion = 29.1247, longNode = 48.3308
        ),
        PlanetId.VENUS to OrbitalElements(
            L0 = 181.9798, L1 = 58517.8157, M0 = 50.1151, M1 = 58517.8157,
            e0 = 0.006759, a0 = 0.723330, i0 = 3.3946, 
            perihelion = 54.8836, longNode = 76.6799
        ),
        PlanetId.MARS to OrbitalElements(
            L0 = 355.4330, L1 = 28436.4765, M0 = 19.3739, M1 = 19139.9285,
            e0 = 0.093433, a0 = 1.523679, i0 = 1.8497, 
            perihelion = 286.5026, longNode = 49.5581
        ),
        PlanetId.JUPITER to OrbitalElements(
            L0 = 34.3515, L1 = 3034.9057, M0 = 19.8950, M1 = 1222.1138,
            e0 = 0.048375, a0 = 5.202561, i0 = 1.3030, 
            perihelion = 273.8677, longNode = 100.4644
        ),
        PlanetId.SATURN to OrbitalElements(
            L0 = 50.0774, L1 = 1222.1138, M0 = 316.9670, M1 = 4858.3407,
            e0 = 0.052147, a0 = 9.554747, i0 = 2.5079, 
            perihelion = 339.3939, longNode = 113.6664
        ),
        PlanetId.URANUS to OrbitalElements(
            L0 = 314.0550, L1 = 429.8640, M0 = 142.5908, M1 = 1541.4839,
            e0 = 0.052012, a0 = 19.218476, i0 = 0.7774, 
            perihelion = 96.9982, longNode = 74.0061
        ),
        PlanetId.NEPTUNE to OrbitalElements(
            L0 = 304.3487, L1 = 218.4592, M0 = 256.2280, M1 = 980.6059,
            e0 = 0.008952, a0 = 30.162177, i0 = 1.7749, 
            perihelion = 276.3401, longNode = 131.7843
        )
    )

    // ========== 计算 Entry ==========
    fun calculate(planet: PlanetId, observerLat: Double, observerLon: Double, jd: Double): Pair<String, String>? {
        return when (planet) {
            PlanetId.MOON -> calculateMoon(observerLat, observerLon, jd)
            PlanetId.SUN -> calculateSun(observerLat, observerLon, jd)
            else -> calculatePlanet(planet, observerLat, observerLon, jd)
        }
    }

    fun calculateNow(planet: PlanetId, observerLat: Double, observerLon: Double): Pair<String, String>? {
        val jd = getJulianDate(System.currentTimeMillis())
        return calculate(planet, observerLat, observerLon, jd)
    }

    // ========== 章动计算 (IAU 1980 简化模型) ==========
    /**
     * 计算章动 (Nutation) - IAU 1980 简化模型
     * @param T 儒略世纪数 (J2000 起算)
     * @return Pair<Δψ (弧度), Δε (弧度)>
     */
    private fun computeNutation(T: Double): Pair<Double, Double> {
        val PI = kotlin.math.PI
        
        // 基本引数 (度)
        val D = 297.8502042 + 445267.1114468 * T  // 日月平距
        val M = 357.5291092 + 35999.0502909 * T  // 太阳平近点角
        val Md = 134.9634114 + 477198.8676313 * T // 月球平近点角
        val F = 93.2720993 + 483202.0175273 * T  // 月球升交点黄经
        val Om = 125.04452 - 1934.136261 * T     // 升交点平黄经
        
        // 主要章动项 (arcsec)
        val dpsi = (
            -17.1996 * sin(Om * PI / 180.0) +
            -0.2062 * sin(2.0 * (D - Om) * PI / 180.0) +
            -0.0211 * sin(2.0 * D * PI / 180.0) +
            +0.0059 * sin(2.0 * M * PI / 180.0) +
            +0.0048 * sin(2.0 * Md * PI / 180.0) +
            +0.0037 * sin(Om + 2.0 * (D - Om) * PI / 180.0)
        )
        
        val deps = (
            +9.2025 * cos(Om * PI / 180.0) +
            +0.0009 * cos(2.0 * (D - Om) * PI / 180.0) +
            +0.0009 * cos(2.0 * D * PI / 180.0) +
            -0.0003 * cos(2.0 * M * PI / 180.0)
        )
        
        return Pair(dpsi * ARCSEC_TO_RAD, deps * ARCSEC_TO_RAD)
    }

    // ========== 行星计算 ==========
    private fun calculatePlanet(planet: PlanetId, lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        val el = planetaryElements[planet] ?: return null
        val T = (jd - 2451545.0) / 36525.0
        
        // 平黄经和平近点角 (度 → 弧度)
        val L = (el.L0 + el.L1 * T) * PI_OVER_180
        val M = (el.M0 + el.M1 * T) * PI_OVER_180
        
        // 偏近点角 (迭代求解 Kepler 方程)
        val e = el.e0
        var E = M + e * sin(M) * (1.0 + e * cos(M))
        for (i in 0..5) {
            E = E - (E - e * sin(E) - M) / (1.0 - e * cos(E))
        }
        
        // 真近点角
        val xv = cos(E) - e
        val yv = sqrt(1.0 - e * e) * sin(E)
        val v = atan2(yv, xv)
        val r = el.a0 * (1.0 - e * e) / (1.0 + e * cos(v))
        
        // 真黄经
        val l = L + (v - M)
        val b = 0.0
        
        // 章动修正的黄赤交角
        val (dpsi, deps) = computeNutation(T)
        val epsilon0 = 23.43929111 * PI_OVER_180  // J2000 黄赤交角
        val epsilon = epsilon0 + deps
        
        // 转换为赤道坐标
        val ra = atan2(cos(epsilon) * sin(l), cos(l))
        val dec = asin(sin(epsilon) * sin(l) * cos(b) + cos(epsilon) * sin(b))
        
        // 土星特殊处理：木星摄动修正
        val (raFinal, decFinal) = if (planet == PlanetId.SATURN) {
            val saturnPert = computeSaturnPerturbation(T, l)
            Pair(ra + saturnPert.first, dec + saturnPert.second)
        } else {
            Pair(ra, dec)
        }
        
        // 地平坐标
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - raFinal
        
        val sinAlt = sin(decFinal) * sin(lat * PI_OVER_180) + 
                     cos(decFinal) * cos(lat * PI_OVER_180) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val az = atan2(-cos(decFinal) * sin(ha),
                       sin(decFinal) * cos(lat * PI_OVER_180) - 
                       cos(decFinal) * sin(lat * PI_OVER_180) * cos(ha))
        
        return Pair(formatRA(raFinal), formatDec(decFinal))
    }

    // ========== 土星摄动修正 (木星引力影响) ==========
    private fun computeSaturnPerturbation(T: Double, saturnLon: Double): Pair<Double, Double> {
        // 木星对土星的主要摄动项
        // 基于 Meeus Chapter 36 的简化模型
        val PI = kotlin.math.PI
        
        // 木星平黄经
        val L_JUP = (34.3515 + 3034.9057 * T) * PI_OVER_180
        // 木星-土星会合周期项
        val pertAngle = 2.0 * (L_JUP - saturnLon)
        val pertAmp = 0.0069 * ARCSEC_TO_RAD  // ~25角秒
        
        val dLon = pertAmp * sin(pertAngle)
        val dLat = 0.0012 * ARCSEC_TO_RAD * sin(pertAngle + 0.56)
        
        // 近似转换为 RA/Dec 变化
        val epsilon0 = 23.43929111 * PI_OVER_180
        val dRA = dLon * cos(epsilon0) - dLat * sin(epsilon0) * 0.3
        val dDec = dLon * sin(epsilon0) + dLat * cos(epsilon0) * 0.3
        
        return Pair(dRA, dDec)
    }

    // ========== 太阳计算 (Meeus Chapter 28) ==========
    private fun calculateSun(lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        val T = (jd - 2451545.0) / 36525.0
        val L0 = 280.46646 + 36000.76983 * T  // 平黄经
        val M = 357.52910 + 35999.05030 * T   // 平近点角
        val e = 0.016708617 - 0.000042037 * T // 偏心率
        
        // 方程心项 (Meeus 公式 28.2)
        val C = (
            (1.914600 - 0.004817 * T) * sin(M * PI_OVER_180) +
            0.019994 * sin(2.0 * M * PI_OVER_180) +
            0.000290 * sin(3.0 * M * PI_OVER_180)
        )
        
        val sunLon = L0 + C  // 真黄经
        val sunAnom = M + C
        
        // 章动修正
        val (dpsi, deps) = computeNutation(T)
        val epsilon0 = 23.43929111 * PI_OVER_180
        val epsilon = epsilon0 + deps
        
        // 视差修正 (太阳地心→站心)
        val lonCorr = -0.0057 * ARCSEC_TO_RAD
        
        // 赤道坐标
        val sunRA = atan2(cos(epsilon) * sin((sunLon + lonCorr) * PI_OVER_180), 
                          cos(sunLon * PI_OVER_180))
        val sunDec = asin(sin(epsilon) * sin(sunLon * PI_OVER_180))
        
        // 光行差修正 (约 20角秒)
        val lonCorrArcsec = -20.4968 * ARCSEC_TO_RAD
        val sunRALight = sunRA + lonCorrArcsec * cos(epsilon) / cos(sunDec)
        val sunDecLight = sunDec + lonCorrArcsec * sin(epsilon)
        
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - sunRALight
        
        val sinAlt = sin(sunDecLight) * sin(lat * PI_OVER_180) + 
                     cos(sunDecLight) * cos(lat * PI_OVER_180) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val az = atan2(-cos(sunDecLight) * sin(ha),
                       sin(sunDecLight) * cos(lat * PI_OVER_180) - 
                       cos(sunDecLight) * sin(lat * PI_OVER_180) * cos(ha))
        
        return Pair(formatRA(sunRALight), formatDec(sunDecLight))
    }

    // ========== 月球计算 (ELP2000 简化算法, 30+ 项) ==========
    private fun calculateMoon(lat: Double, lon: Double, jd: Double): Pair<String, String>? {
        val T = (jd - 2451545.0) / 36525.0
        val PI = kotlin.math.PI
        
        // 基本引数 (度)
        val L0 = 218.3164591 + 481267.88134236 * T  // 月球平黄经
        val l = 134.9634114 + 477198.8676313 * T     // 太阳平近点角 (l')
        val lp = 357.5291092 + 35999.0502909 * T   // 月球平近点角 (l)
        val F = 93.2720993 + 483202.0175273 * T     // 月球升交点距 (F)
        val D = 297.8502042 + 445267.1114468 * T   // 日月平距 (D)
        
        // ELP2000 简化模型 - 主要周期项
        // 参考: Meeus Chapter 47, ELP2000-82 简化序列
        val lambda = L0 + 
            + 6.2890 * sin(l * PI_OVER_180) 
            + 1.2740 * sin((2.0 * D - l) * PI_OVER_180)
            + 0.6580 * sin(2.0 * D * PI_OVER_180)
            + 0.2136 * sin(2.0 * l * PI_OVER_180)
            - 0.1864 * sin(lp * PI_OVER_180)
            - 0.1144 * sin(2.0 * F * PI_OVER_180)
            + 0.0588 * sin((2.0 * D - 2.0 * l) * PI_OVER_180)
            + 0.0572 * sin((2.0 * D - l + lp) * PI_OVER_180)
            - 0.0455 * sin((2.0 * D - lp) * PI_OVER_180)
            - 0.0410 * sin(l - 2.0 * F * PI_OVER_180)
            - 0.0347 * sin(2.0 * D * PI_OVER_180)
            + 0.0303 * sin((l + lp) * PI_OVER_180)
            + 0.0263 * sin((2.0 * D - 2.0 * F) * PI_OVER_180)
            + 0.0214 * sin((l - lp) * PI_OVER_180)
            + 0.0199 * sin(l + 2.0 * F * PI_OVER_180)
            + 0.0193 * sin(4.0 * D - l * PI_OVER_180)
            + 0.0171 * sin(4.0 * D - 2.0 * l * PI_OVER_180)
            + 0.0154 * sin(2.0 * D - 6.0 * l * PI_OVER_180)
            + 0.0132 * sin(3.0 * l * PI_OVER_180)
            + 0.0109 * sin(4.0 * D * PI_OVER_180)
            - 0.0107 * sin((l + 2.0 * D) * PI_OVER_180)
            + 0.0100 * sin((2.0 * D + lp - l) * PI_OVER_180)
            + 0.0087 * sin(lp - 2.0 * D * PI_OVER_180)
            - 0.0086 * sin((2.0 * D + 2.0 * l) * PI_OVER_180)
            + 0.0085 * sin(l - 2.0 * D * PI_OVER_180)
            + 0.0069 * sin((2.0 * l + 2.0 * D) * PI_OVER_180)
            - 0.0068 * sin((l - 4.0 * D + lp) * PI_OVER_180)
            + 0.0053 * sin((l + lp - 2.0 * D) * PI_OVER_180)
            + 0.0050 * sin(2.0 * l + lp * PI_OVER_180)
            + 0.0043 * sin((l - 2.0 * lp) * PI_OVER_180)
            + 0.0038 * sin((2.0 * D - l - lp) * PI_OVER_180)
        
        // 月球黄纬 (主要项)
        val beta = (
            + 5.1282 * sin(F * PI_OVER_180)
            + 0.2806 * sin((l + F) * PI_OVER_180)
            + 0.2770 * sin((l - F) * PI_OVER_180)
            + 0.1734 * sin((2.0 * D - F) * PI_OVER_180)
            + 0.0554 * sin((2.0 * D + F - l) * PI_OVER_180)
            + 0.0463 * sin((2.0 * D - F - l) * PI_OVER_180)
            + 0.0326 * sin((2.0 * l + F) * PI_OVER_180)
            + 0.0218 * sin((2.0 * D + l - F) * PI_OVER_180)
            + 0.0129 * sin((2.0 * D + l + F) * PI_OVER_180)
            + 0.0092 * sin((2.0 * l - F) * PI_OVER_180)
            + 0.0077 * sin((D - l + F) * PI_OVER_180)
            - 0.0067 * sin((D + l + F) * PI_OVER_180)
        )
        
        // 章动修正
        val (dpsi, deps) = computeNutation(T)
        val epsilon0 = 23.43929111 * PI_OVER_180
        val epsilon = epsilon0 + deps
        
        // 转换为赤道坐标
        val lambdaRad = lambda * PI_OVER_180
        val betaRad = beta * PI_OVER_180
        
        val moonRA = atan2(
            sin(epsilon) * sin(lambdaRad) - tan(betaRad) * cos(epsilon),
            cos(lambdaRad)
        )
        val moonDec = asin(
            sin(epsilon) * sin(lambdaRad) * cos(betaRad) + 
            cos(epsilon) * sin(betaRad)
        )
        
        // 地平坐标
        val lmst = localMeanSiderealTime(jd, lon)
        val ha = lmst - moonRA
        
        val sinAlt = sin(moonDec) * sin(lat * PI_OVER_180) + 
                     cos(moonDec) * cos(lat * PI_OVER_180) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val az = atan2(-cos(moonDec) * sin(ha),
                       sin(moonDec) * cos(lat * PI_OVER_180) - 
                       cos(moonDec) * sin(lat * PI_OVER_180) * cos(ha))
        
        return Pair(formatRA(moonRA), formatDec(moonDec))
    }

    // ========== 工具方法 ==========

    fun getJulianDate(timeMillis: Long): Double {
        return (timeMillis / 86400000.0) + 2440587.5
    }

    private fun localMeanSiderealTime(jd: Double, lon: Double): Double {
        val T = (jd - 2451545.0) / 36525.0
        val GMST = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 
                   0.000387933 * T * T - T * T * T / 38710000.0
        val LMST = (GMST + lon) * PI_OVER_180
        return ((LMST % (2 * PI)) + 2 * PI) % (2 * PI)
    }

    private fun formatRA(ra: Double): String {
        var raDeg = (ra * 180.0 / PI)
        if (raDeg < 0) raDeg += 360.0
        val hours = raDeg / 15.0
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        val s = (((hours - h) * 60 - m) * 60)
        return "%02d:%02d:%02d".format(h, m, (s * 100).toInt() / 100)
    }

    private fun formatDec(dec: Double): String {
        val sign = if (dec >= 0) "+" else "-"
        val absDec = abs(dec) * 180.0 / PI
        val d = absDec.toInt()
        val m = ((absDec - d) * 60).toInt()
        val s = (((absDec - d) * 60 - m) * 60)
        return "%s%02d*%02d:%02d".format(sign, d, m, (s * 100).toInt() / 100)
    }

    // ========== 岁差修正 (IAU 2000A) ==========
    fun applyPrecession(raJ2000Str: String, decJ2000Str: String, jd: Double): Pair<String, String> {
        val raJ2000 = parseRA(raJ2000Str)
        val decJ2000 = parseDec(decJ2000Str)
        val (ra, dec) = applyPrecessionRad(raJ2000, decJ2000, jd)
        return Pair(formatRA(ra), formatDec(dec))
    }

    fun applyPrecessionRad(raJ2000Rad: Double, decJ2000Rad: Double, jd: Double): Pair<Double, Double> {
        val T = (jd - 2451545.0) / 36525.0

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

        val eps0 = 84381.406 * ARCSEC_TO_RAD
        val psiaRad = psia * ARCSEC_TO_RAD
        val omegaaRad = omegaa * ARCSEC_TO_RAD
        val chiaRad = chia * ARCSEC_TO_RAD

        val sa = sin(eps0);       val ca = cos(eps0)
        val sb = sin(-psiaRad);   val cb = cos(-psiaRad)
        val sc = sin(-omegaaRad); val cc = cos(-omegaaRad)
        val sd = sin(chiaRad);    val cd = cos(chiaRad)

        val e11 =  cd * cb - sb * sd * cc
        val e12 =  cd * sb * ca + sd * cc * cb * ca - sa * sd * sc
        val e13 =  cd * sb * sa + sd * cc * cb * sa + ca * sd * sc

        val e21 = -sd * cb - sb * cd * cc
        val e22 = -sd * sb * ca + cd * cc * cb * ca - sa * cd * sc
        val e23 = -sd * sb * sa + cd * cc * cb * sa + ca * cd * sc

        val e31 =  sb * sc
        val e32 = -sc * cb * ca - sa * cc
        val e33 = -sc * cb * sa + cc * ca

        val cosDec = cos(decJ2000Rad)
        val x = cosDec * cos(raJ2000Rad)
        val y = cosDec * sin(raJ2000Rad)
        val z = sin(decJ2000Rad)

        val x2 = e11 * x + e12 * y + e13 * z
        val y2 = e21 * x + e22 * y + e23 * z
        val z2 = e31 * x + e32 * y + e33 * z

        val ra  = atan2(y2, x2)
        val dec = asin(z2.coerceIn(-1.0, 1.0))

        return Pair(normalizeRA(ra), dec)
    }

    fun parseRA(raStr: String): Double {
        val parts = raStr.trim().split(":")
        if (parts.size < 2) return 0.0
        val h = parts[0].toDoubleOrNull() ?: 0.0
        val m = parts[1].toDoubleOrNull() ?: 0.0
        val s = parts.getOrNull(2)?.replace("*", ":")?.toDoubleOrNull() ?: 0.0
        return ((h + m / 60.0 + s / 3600.0) * 15.0) * PI_OVER_180
    }

    fun parseDec(decStr: String): Double {
        val s = decStr.trim().replace("*", ":")
        val negative = s.startsWith("-")
        val absStr = if (negative) s.substring(1) else s
        val parts = absStr.split(":")
        if (parts.size < 2) return 0.0
        val d = parts[0].toDoubleOrNull() ?: 0.0
        val m = parts[1].toDoubleOrNull() ?: 0.0
        val sec = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        val deg = d + m / 60.0 + sec / 3600.0
        return (if (negative) -deg else deg) * PI_OVER_180
    }

    private fun normalizeRA(ra: Double): Double {
        var result = ra % (2 * PI)
        if (result < 0) result += 2 * PI
        return result
    }

    // ========== 地平坐标计算 ==========
    fun getAltitudeAzimuth(
        raRad: Double,
        decRad: Double,
        observerLat: Double,
        observerLon: Double,
        jd: Double
    ): Pair<Double, Double> {
        val lmst = localMeanSiderealTime(jd, observerLon)
        val ha = lmst - raRad
        val latRad = observerLat * PI_OVER_180

        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0)) * 180.0 / PI

        val az = atan2(-cos(decRad) * sin(ha),
                       sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(ha)) * 180.0 / PI

        val azNorm = if (az < 0) az + 360.0 else az
        return Pair(alt, azNorm)
    }

    fun getAltitude(raStr: String, decStr: String, observerLat: Double, observerLon: Double, jd: Double): Double {
        val ra = parseRA(raStr)
        val dec = parseDec(decStr)
        return getAltitudeAzimuth(ra, dec, observerLat, observerLon, jd).first
    }
}