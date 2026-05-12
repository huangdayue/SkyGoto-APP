package com.skygoto.app.domain.repository

import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import kotlinx.coroutines.flow.StateFlow

/**
 * 天体目录仓储接口
 */
interface CatalogRepository {
    /**
     * 获取所有梅西耶天体
     */
    fun getMessierCatalog(): List<CelestialObject>
    
    /**
     * 获取所有 NGC 天体
     */
    fun getNGCCatalog(): List<CelestialObject>
    
    /**
     * 搜索天体
     */
    fun search(query: String, catalog: CatalogType? = null): List<CelestialObject>
    
    /**
     * 按类型筛选
     */
    fun filterByType(type: ObjectType, catalog: CatalogType? = null): List<CelestialObject>
    
    /**
     * 按星座筛选
     */
    fun filterByConstellation(constellation: String, catalog: CatalogType? = null): List<CelestialObject>
}

enum class CatalogType {
    MESSIER,
    NGC,
    IC,
    ALL
}