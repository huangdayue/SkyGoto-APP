package com.skygoto.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 应用设置数据管理器
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // 位置设置
        val LONGITUDE = stringPreferencesKey("longitude")
        val LATITUDE = stringPreferencesKey("latitude")
        val LOCATION_FORMAT = stringPreferencesKey("location_format") // "DMS" or "DECIMAL"
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")
        
        // 时区设置
        val TIMEZONE = stringPreferencesKey("timezone")
        val AUTO_TIMEZONE = booleanPreferencesKey("auto_timezone")
        
        // 显示设置
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        
        // 赤道仪设置
        val GUIDE_RATE = doublePreferencesKey("guide_rate")
        val GOTO_RATE = doublePreferencesKey("goto_rate")
        val EAST_LIMIT = doublePreferencesKey("east_limit")
        val WEST_LIMIT = doublePreferencesKey("west_limit")
    }
    
    // 位置设置
    val longitude: Flow<String> = context.dataStore.data.map { it[LONGITUDE] ?: "120:00:00" }
    val latitude: Flow<String> = context.dataStore.data.map { it[LATITUDE] ?: "30:00:00" }
    val locationFormat: Flow<String> = context.dataStore.data.map { it[LOCATION_FORMAT] ?: "DMS" }
    val autoLocation: Flow<Boolean> = context.dataStore.data.map { it[AUTO_LOCATION] ?: false }
    
    // 时区设置
    val timezone: Flow<String> = context.dataStore.data.map { it[TIMEZONE] ?: "Asia/Shanghai" }
    val autoTimezone: Flow<Boolean> = context.dataStore.data.map { it[AUTO_TIMEZONE] ?: true }
    
    // 显示设置
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: true }
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "zh_CN" }
    
    // 赤道仪设置
    val guideRate: Flow<Double> = context.dataStore.data.map { it[GUIDE_RATE] ?: 1.0 }
    val gotoRate: Flow<Double> = context.dataStore.data.map { it[GOTO_RATE] ?: 48.0 }
    val eastLimit: Flow<Double> = context.dataStore.data.map { it[EAST_LIMIT] ?: 180.0 }
    val westLimit: Flow<Double> = context.dataStore.data.map { it[WEST_LIMIT] ?: 180.0 }
    
    suspend fun setLongitude(value: String) {
        context.dataStore.edit { it[LONGITUDE] = value }
    }
    
    suspend fun setLatitude(value: String) {
        context.dataStore.edit { it[LATITUDE] = value }
    }
    
    suspend fun setLocationFormat(value: String) {
        context.dataStore.edit { it[LOCATION_FORMAT] = value }
    }
    
    suspend fun setAutoLocation(value: Boolean) {
        context.dataStore.edit { it[AUTO_LOCATION] = value }
    }
    
    suspend fun setTimezone(value: String) {
        context.dataStore.edit { it[TIMEZONE] = value }
    }
    
    suspend fun setAutoTimezone(value: Boolean) {
        context.dataStore.edit { it[AUTO_TIMEZONE] = value }
    }
    
    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }
    
    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }
    
    suspend fun setGuideRate(value: Double) {
        context.dataStore.edit { it[GUIDE_RATE] = value }
    }
    
    suspend fun setGotoRate(value: Double) {
        context.dataStore.edit { it[GOTO_RATE] = value }
    }
    
    suspend fun setEastLimit(value: Double) {
        context.dataStore.edit { it[EAST_LIMIT] = value }
    }
    
    suspend fun setWestLimit(value: Double) {
        context.dataStore.edit { it[WEST_LIMIT] = value }
    }
}