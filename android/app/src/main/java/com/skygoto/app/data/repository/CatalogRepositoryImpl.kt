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
    
    private val _messier: List<CelestialObject> by lazy {
        loadCatalog("MessierCatalog.json")
    }
    
    private val _ngc: List<CelestialObject> by lazy {
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
    
    override fun getMessierCatalog(): List<CelestialObject> = _messier
    
    override fun getNGCCatalog(): List<CelestialObject> = _ngc
    
    override fun search(query: String, catalog: CatalogType?): List<CelestialObject> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        
        val source = when (catalog) {
            CatalogType.MESSIER -> _messier
            CatalogType.NGC -> _ngc
            else -> _messier + _ngc
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
            CatalogType.MESSIER -> _messier
            CatalogType.NGC -> _ngc
            else -> _messier + _ngc
        }
        return source.filter { it.type == type }
    }
    
    override fun filterByConstellation(constellation: String, catalog: CatalogType?): List<CelestialObject> {
        val source = when (catalog) {
            CatalogType.MESSIER -> _messier
            CatalogType.NGC -> _ngc
            else -> _messier + _ngc
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
    @com.google.gson.annotations.SerializedName("name_en")
    val nameEn: String = "",
    val altNames: List<String>? = null,
    val type: String,
    @com.google.gson.annotations.SerializedName("type_cn")
    val typeCn: String = "",
    val ra: String,
    val dec: String,
    val constellation: String,
    val magnitude: Double,
    @com.google.gson.annotations.SerializedName("ra_deg")
    val raDeg: Double = 0.0,
    @com.google.gson.annotations.SerializedName("dec_deg")
    val decDeg: Double = 0.0,
    val description: String = ""
) {
    fun toCelestialObject(): CelestialObject = CelestialObject(
        id = id,
        name = name,
        nameEn = nameEn,
        altNames = if (altNames.isNullOrEmpty()) listOf(nameEn) else altNames,
        type = ObjectType.fromCode(type),
        ra = ra,
        dec = dec,
        raDeg = raDeg,
        decDeg = decDeg,
        constellation = constellation,
        magnitude = magnitude,
        description = description
    )
}