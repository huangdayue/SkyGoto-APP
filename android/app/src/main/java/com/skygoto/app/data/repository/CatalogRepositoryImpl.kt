package com.skygoto.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.CatalogType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CatalogRepository {
    
    private val gson = Gson()
    
    private val messierCatalog: List<CelestialObject> by lazy {
        loadCatalog("MessierCatalog.json")
    }
    
    private val ngcCatalog: List<CelestialObject> by lazy {
        loadCatalog("NGCCatalog.json")
    }
    
    private fun loadCatalog(filename: String): List<CelestialObject> {
        return try {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            val type = object : TypeToken<CatalogData>() {}.type
            val data: CatalogData = gson.fromJson(json, type)
            data.objects.map { it.toCelestialObject() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun getMessierCatalog(): List<CelestialObject> = messierCatalog
    
    override fun getNGCCatalog(): List<CelestialObject> = ngcCatalog
    
    override fun search(query: String, catalog: CatalogType?): List<CelestialObject> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        
        val source = when (catalog) {
            CatalogType.MESSIER -> messierCatalog
            CatalogType.NGC -> ngcCatalog
            else -> messierCatalog + ngcCatalog
        }
        
        return source.filter { obj ->
            obj.id.lowercase().contains(q) ||
            obj.name.lowercase().contains(q) ||
            obj.constellation.lowercase().contains(q) ||
            obj.altNames.any { it.lowercase().contains(q) }
        }
    }
    
    override fun filterByType(type: ObjectType, catalog: CatalogType?): List<CelestialObject> {
        val source = when (catalog) {
            CatalogType.MESSIER -> messierCatalog
            CatalogType.NGC -> ngcCatalog
            else -> messierCatalog + ngcCatalog
        }
        return source.filter { it.type == type }
    }
    
    override fun filterByConstellation(constellation: String, catalog: CatalogType?): List<CelestialObject> {
        val source = when (catalog) {
            CatalogType.MESSIER -> messierCatalog
            CatalogType.NGC -> ngcCatalog
            else -> messierCatalog + ngcCatalog
        }
        return source.filter { it.constellation.contains(constellation, ignoreCase = true) }
    }
}

// JSON 数据结构
private data class CatalogData(
    val catalog: String,
    val description: String,
    val version: String,
    val totalCount: Int,
    val objects: List<CatalogObject>
)

private data class CatalogObject(
    val id: String,
    val name: String,
    val nameEn: String = "",
    val altNames: List<String> = emptyList(),
    val type: String,
    val type_cn: String,
    val ra: String,
    val dec: String,
    val constellation: String,
    val magnitude: Double,
    val ra_deg: Double,
    val dec_deg: Double,
    val description: String = ""
) {
    fun toCelestialObject(): CelestialObject = CelestialObject(
        id = id,
        name = name,
        nameEn = nameEn,
        altNames = altNames,
        type = ObjectType.fromCode(type),
        ra = ra,
        dec = dec,
        raDeg = ra_deg,
        decDeg = dec_deg,
        constellation = constellation,
        magnitude = magnitude,
        description = description
    )
}