/*
 * 文件名：CelestialObject.kt
 * 描述：天体对象数据模型 - 定义梅西耶、 NGC 等深空天体的数据结构
 * 作者：SkyGoto Team
 * 日期：2024
 *
 * 功能说明：
 * - 描述单个天体的完整信息
 * - 支持多种天体类型（星系、星云、星团等）
 * - 提供天体坐标（赤经/赤纬）存储
 * - 包含梅西耶和 NGC 目录的枚举定义
 *
 * 天体分类：
 * - 星系 (Galaxy)
 * - 弥漫星云 (Nebula)
 * - 行星状星云 (Planetary Nebula)
 * - 疏散星团 (Open Cluster)
 * - 球状星团 (Globular Cluster)
 * - 超新星遗迹 (Supernova Remnant)
 * - 反射星云 (Reflection Nebula)
 * - 发射星云 (Emission Nebula)
 *
 * 使用方式：
 * - 创建 CelestialObject 实例表示一个天体
 * - 使用 ObjectType.fromCode() 从编码获取类型
 */

package com.skygoto.app.domain.model

/**
 * 天体对象
 *
 * 描述一个天文观测目标，包含其标识符、名称、类型、坐标等信息。
 * 用于天体目录浏览、GOTO 目标选择、观测记录等场景。
 *
 * @param id 天体编号（如 "M31"、"NGC7000"）
 * @param name 天体中文名称（如 "仙女座星系"）
 * @param nameEn 天体英文名称（如 "Andromeda Galaxy"）
 * @param altNames 别名列表（如 ["M31", "NGC 224"]）
 * @param type 天体类型
 * @param ra 赤经坐标（格式："HH:MM:SS"）
 * @param dec 赤纬坐标（格式："+DD*MM:SS" 或 "-DD*MM:SS"）
 * @param raDeg 赤经数值（度，十进制格式，便于计算）
 * @param decDeg 赤纬数值（度，十进制格式，便于计算）
 * @param constellation 所在星座（如 "Andromeda 仙女座"）
 * @param magnitude 视星等（数值越小天体越亮）
 * @param description 天体描述/备注信息
 */
data class CelestialObject(
    val id: String,                    // "M31", "NGC7000"
    val name: String,                  // "仙女座星系"
    val nameEn: String = "",            // "Andromeda Galaxy"
    val altNames: List<String> = emptyList(), // 别名列表
    val type: ObjectType,              // 类型（星系/星云/星团等）
    val ra: String,                    // 赤经 "HH:MM:SS"
    val dec: String,                   // 赤纬 "sDD*MM:SS"
    val raDeg: Double = 0.0,          // 赤经（度）
    val decDeg: Double = 0.0,           // 赤纬（度）
    val constellation: String,         // 星座
    val magnitude: Double,             // 视星等
    val description: String = ""        // 描述信息
)

/**
 * 天体类型枚举
 *
 * 定义常见的天文观测目标类型，
 * 每个类型包含：code（协议编码）、cnName（中文名称）
 *
 * 天体类型说明：
 * - GALAXY（星系）：由数十亿颗恒星组成的巨大天体系统，如银河系
 * - NEBULA（星云）：气体和尘埃组成的云状天体
 * - PLANETARY_NEBULA（行星状星云）：恒星演化末期的外壳抛出
 * - OPEN_CLUSTER（疏散星团）：年轻恒星组成的松散星团
 * - GLOBULAR_CLUSTER（球状星团）：老年恒星组成的球形密集星团
 * - SUPERNOVA_REMNANT（超新星遗迹）：超新星爆发后的残留
 * - REFLECTION_NEBULA（反射星云）：反射邻近恒星光芒的星云
 * - EMISSION_NEBULA（发射星云）：自身发光的气体星云，如 M42 猎户座大星云
 */
enum class ObjectType(val code: String, val cnName: String) {
    GALAXY("GAL", "星系"),              // 星系
    NEBULA("DN", "星云"),               // 弥漫星云（Diffuse Nebula）
    PLANETARY_NEBULA("PN", "行星状星云"), // 行星状星云
    OPEN_CLUSTER("OC", "疏散星团"),      // 疏散星团
    GLOBULAR_CLUSTER("GC", "球状星团"),   // 球状星团
    SUPERNOVA_REMNANT("SNR", "超新星遗迹"), // 超新星遗迹
    REFLECTION_NEBULA("RN", "反射星云"), // 反射星云
    EMISSION_NEBULA("EN", "发射星云"),   // 发射星云
    PLANET("PLT", "行星"),             // 太阳系行星
    MOON("MON", "月亮"),               // 月亮（地球卫星）
    SUN("SUN", "太阳"),               // 太阳
    DWARF_PLANET("DPL", "矮行星"),       // 矮行星（小行星、冥王星等）
    ASTEROID("AST", "小行星"),          // 小行星
    COMET("CMT", "彗星"),             // 彗星
    STAR("STAR", "恒星"),             // 恒星
    UNKNOWN("UNK", "未知");             // 未知类型

    /**
     * 从协议编码获取天体类型
     *
     * @param code LX200 协议中的类型编码
     * @return 对应的 ObjectType，找不到则返回 UNKNOWN
     */
    companion object {
        fun fromCode(code: String): ObjectType {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}