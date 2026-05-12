package com.skygoto.app.domain.model

/**
 * 天体对象
 */
data class CelestialObject(
    val id: String,           // "M31", "NGC7000"
    val name: String,          // "仙女座星系"
    val nameEn: String = "",   // "Andromeda Galaxy"
    val altNames: List<String> = emptyList(), // 别名
    val type: ObjectType,      // 类型
    val ra: String,           // 赤经 "HH:MM:SS"
    val dec: String,          // 赤纬 "sDD*MM:SS"
    val raDeg: Double = 0.0,  // 赤经（度）
    val decDeg: Double = 0.0,  // 赤纬（度）
    val constellation: String, // 星座
    val magnitude: Double,    // 视星等
    val description: String = ""
)

enum class ObjectType(val code: String, val cnName: String) {
    GALAXY("GAL", "星系"),
    NEBULA("DN", "星云"),
    PLANETARY_NEBULA("PN", "行星状星云"),
    OPEN_CLUSTER("OC", "疏散星团"),
    GLOBULAR_CLUSTER("GC", "球状星团"),
    SUPERNOVA_REMNANT("SNR", "超新星遗迹"),
    REFLECTION_NEBULA("RN", "反射星云"),
    EMISSION_NEBULA("EN", "发射星云"),
    UNKNOWN("UNK", "未知");
    
    companion object {
        fun fromCode(code: String): ObjectType {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}
