/*
 * 文件名:CatalogRepository.kt
 * 描述:天体目录仓储接口 - 定义梅西耶、 NGC 等天体目录的数据访问方法
 * 作者:SkyGoto Team
 * 日期:2024
 *
 * 功能说明:
 * - 定义天体目录的数据查询接口
 * - 支持梅西耶(Messier)目录
 * - 支持 NGC 目录
 * - 提供搜索和筛选功能
 *
 * 设计理念:
 * - Repository 模式隔离数据源
 * - 调用者无需关心数据来自内存/数据库/网络
 * - 支持多种筛选维度组合查询
 *
 * 使用方式:
 * - 实现此接口提供具体的数据源
 * - 调用 getMessierCatalog()/getNGCCatalog() 获取完整目录
 * - 使用 search() 进行关键词搜索
 * - 使用 filterByType()/filterByConstellation() 筛选结果
 */

package com.skygoto.app.domain.repository

import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import kotlinx.coroutines.flow.StateFlow

/**
 * 天体目录仓储接口
 *
 * 定义天体目录数据访问的抽象接口。
 * 实现类可以是从内存、数据库或网络加载数据。
 *
 * 主要功能:
 * 1. 获取完整目录(梅西耶、NGC)
 * 2. 关键词搜索
 * 3. 按类型筛选
 * 4. 按星座筛选
 */
interface CatalogRepository {

    /**
     * 获取所有梅西耶天体
     *
     * 梅西耶目录包含 110 个最亮的深空天体,
     * 由法国天文学家 Charles Messier 于 18 世纪编制。
     * 包括星系、星云、星团等。
     *
     * @return 梅西耶天体列表(按编号排序)
     */
    fun getMessierCatalog(): List<CelestialObject>

    /**
     * 获取所有 NGC 天体
     *
     * NGC(New General Catalog)目录包含上万 个深空天体,
     * 是最全面的深空天体目录之一。
     * 包括星系、星云、星团、超新星遗迹等。
     *
     * @return NGC 天体列表
     */
    fun getNGCCatalog(): List<CelestialObject>

    /**
     * 获取太阳系天体
     *
     * 包括太阳、月亮、八大行星、矮行星、主要小行星等。
     * 太阳系天体坐标会实时计算,此处提供标称坐标或占位符。
     *
     * @return 太阳系天体列表
     */
    fun getSolarSystemCatalog(): List<CelestialObject>
    
    /**
     * 获取亮星目录
     * 
     * 包括织女星、牛郎星、天津四、天狼星等著名恒星。
     * 恒星坐标固定不变，直接使用星表数据。
     *
     * @return 亮星列表
     */
    fun getStarCatalog(): List<CelestialObject>
    
    /**
     * 搜索天体
     *
     * 支持按名称、编号、别名等关键词搜索。
     * 搜索结果可限定在特定目录范围内。
     *
     * @param query 搜索关键词(如 "M31"、"仙女座")
     * @param catalog 可选,限定搜索的目录范围(默认搜索所有目录)
     * @return 匹配的天体列表(按相关性排序)
     */
    fun search(query: String, catalog: CatalogType? = null): List<CelestialObject>

    /**
     * 按天体类型筛选
     *
     * 从指定目录中筛选特定类型的天体。
     *
     * @param type 天体类型(如 ObjectType.GALAXY)
     * @param catalog 可选,限定筛选的目录范围
     * @return 符合类型条件的天体列表
     */
    fun filterByType(type: ObjectType, catalog: CatalogType? = null): List<CelestialObject>

    /**
     * 按星座筛选
     *
     * 从指定目录中筛选位于特定星座的天体。
     *
     * @param constellation 星座名称(如 "Andromeda"、"猎户座")
     * @param catalog 可选,限定筛选的目录范围
     * @return 位于该星座的天体列表
     */
    fun filterByConstellation(constellation: String, catalog: CatalogType? = null): List<CelestialObject>
}

/**
 * 天体目录类型枚举
 *
 * 定义支持的目录类型:
 * - MESSIER: 梅西耶目录(110 个天体)
 * - NGC: 新总目录(数千天体)
 * - IC: 索引目录(NGC 的补充)
 * - ALL: 所有目录
 */
enum class CatalogType {
    MESSIER,  // 梅西耶目录
    NGC,       // NGC 目录
    IC,        // IC(索引)目录
    SOLAR_SYSTEM,  // 太阳系天体
    STAR,      // 亮星目录
    ALL        // 所有目录
}