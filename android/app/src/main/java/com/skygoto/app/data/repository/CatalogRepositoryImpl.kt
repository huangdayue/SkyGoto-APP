package com.skygoto.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.CatalogType
import com.skygoto.app.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CatalogRepository {
    
    companion object {
        private const val TAG = "CatalogRepositoryImpl"
    }
    
    private val gson = Gson()
    
    private val messierList: List<CelestialObject> by lazy {
        AppLogger.d(TAG, "Loading Messier catalog...")
        loadCatalog("MessierCatalog.json").also {
            AppLogger.d(TAG, "Messier catalog loaded: ${it.size} objects")
        }
    }
    
    private val ngcList: List<CelestialObject> by lazy {
        AppLogger.d(TAG, "Loading NGC catalog...")
        loadCatalog("NGCCatalog.json").also {
            AppLogger.d(TAG, "NGC catalog loaded: ${it.size} objects")
        }
    }
    
    private val solarSystemList: List<CelestialObject> by lazy {
        AppLogger.d(TAG, "Loading Solar System catalog...")
        loadCatalog("SolarSystemCatalog.json").also {
            AppLogger.d(TAG, "Solar System catalog loaded: ${it.size} objects")
        }
    }
    
    private val starList: List<CelestialObject> by lazy {
        AppLogger.d(TAG, "Loading Star catalog...")
        loadCatalog("StarCatalog.json").also {
            AppLogger.d(TAG, "Star catalog loaded: ${it.size} objects")
        }
    }
    
    private fun loadCatalog(filename: String): List<CelestialObject> {
        return try {
            AppLogger.d(TAG, "Opening asset: $filename")
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            AppLogger.d(TAG, "JSON loaded, length: ${json.length}")
            
            val type = object : TypeToken<CatalogData>() {}.type
            val data: CatalogData = gson.fromJson(json, type)
            AppLogger.d(TAG, "Parsed CatalogData: catalog=${data.catalog}, objects=${data.objects.size}")
            
            data.objects.mapNotNull { obj ->
                try {
                    obj.toCelestialObject()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error converting object ${obj.id}: ${e.message}")
                    null
                }
            }.also {
                AppLogger.d(TAG, "Converted $filename: ${it.size} objects successfully")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load $filename", e)
            emptyList()
        }
    }
    
    override fun getMessierCatalog(): List<CelestialObject> = messierList
    
    override fun getNGCCatalog(): List<CelestialObject> = ngcList
    
    override fun getSolarSystemCatalog(): List<CelestialObject> = solarSystemList
    
    override fun getStarCatalog(): List<CelestialObject> = starList
    
    override fun search(query: String, catalog: CatalogType?): List<CelestialObject> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        
        val source = when (catalog) {
            CatalogType.MESSIER -> messierList
            CatalogType.NGC -> ngcList
            CatalogType.SOLAR_SYSTEM -> solarSystemList
            CatalogType.STAR -> starList
            else -> messierList + ngcList + solarSystemList + starList
        }
        
        return source.filter { obj ->
            obj.id.lowercase().contains(q) ||
            obj.name.lowercase().contains(q) ||
            obj.constellation.lowercase().contains(q) ||
            obj.altNames.any { alt -> alt.lowercase().contains(q) }
        }
    }
    
    override fun filterByType(type: ObjectType, catalog: CatalogType?): List<CelestialObject> {
        val source = when (catalog) {
            CatalogType.MESSIER -> messierList
            CatalogType.NGC -> ngcList
            CatalogType.SOLAR_SYSTEM -> solarSystemList
            CatalogType.STAR -> starList
            else -> messierList + ngcList + solarSystemList + starList
        }
        return source.filter { it.type == type }
    }
    
    override fun filterByConstellation(constellation: String, catalog: CatalogType?): List<CelestialObject> {
        val source = when (catalog) {
            CatalogType.MESSIER -> messierList
            CatalogType.NGC -> ngcList
            CatalogType.SOLAR_SYSTEM -> solarSystemList
            else -> messierList + ngcList + solarSystemList
        }
        return source.filter { it.constellation.contains(constellation, ignoreCase = true) }
    }
}

private data class CatalogData(
    val catalog: String,
    val description: String,
    val version: String,
    val totalCount: Int? = null,
    val objects: List<CatalogObject>
)

private data class CatalogObject(
    val id: String,
    val name: String,
    val name_en: String? = null,
    val altNames: List<String>? = null,
    val type: String,
    val type_cn: String? = null,
    val ra: String,
    val dec: String,
    val constellation: String,
    val magnitude: Double,
    val ra_deg: Double? = null,
    val dec_deg: Double? = null,
    val description: String? = null
) {
    fun toCelestialObject(): CelestialObject {
        val altNamesList = when {
            !altNames.isNullOrEmpty() -> altNames!!
            !name_en.isNullOrBlank() -> listOf(name_en!!)
            else -> emptyList()
        }
        
        return CelestialObject(
            id = id,
            name = name,
            nameEn = name_en ?: "",
            altNames = altNamesList,
            type = ObjectType.fromCode(type),
            ra = ra,
            dec = dec,
            raDeg = ra_deg ?: 0.0,
            decDeg = dec_deg ?: 0.0,
            constellation = constellation,
            magnitude = magnitude,
            description = description ?: ""
        )
    }
}