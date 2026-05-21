/*
 * 文件名:SettingsDataStore.kt
 * 描述:应用设置数据管理器 - 使用 Jetpack DataStore 持久化用户偏好设置
 * 作者:SkyGoto Team
 * 日期:2024
 *
 * 功能说明:
 * - 存储和管理用户的天文观测设置
 * - 位置设置(经纬度、格式)
 * - 时区设置
 * - 显示设置(深色模式、语言)
 * - 赤道仪参数(导星率、GOTO速率、限位)
 *
 * 使用方式:
 * - 通过 Hilt 依赖注入获取实例
 * - Flow 属性用于实时观察设置变化
 * - setXxx() 方法用于更新设置
 */

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

/**
 * DataStore 委托扩展
 *
 * 为 Context 添加 dataStore 属性,
 * 用于创建和管理 Preferences DataStore 实例。
 * 使用 by preferencesDataStore 委托自动处理 DataStore 的创建和访问。
 *
 * @param name DataStore 的名称,会作为文件名存储(如 "settings.preferences_pb")
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 应用设置数据管理器
 *
 * 使用 Jetpack DataStore(Preferences DataStore)存储用户偏好设置。
 * DataStore 相比 SharedPreferences 具有以下优势:
 * - 完全异步操作,避免主线程阻塞
 * - 类型安全,支持 Flow
 * - 支持并发安全写入
 *
 * 设置分类:
 * 1. 位置设置 - 经纬度、格式、自动定位
 * 2. 时区设置 - 时区选择、自动设置
 * 3. 显示设置 - 深色模式、语言
 * 4. 赤道仪设置 - 导星率、GOTO速率、限位
 *
 * @param context Android 上下文,用于访问 DataStore
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ========== companion object: 定义所有偏好设置键 ==========

    companion object {
        // ----- 位置设置 -----
        /** 经度设置,格式如 "120:30:00" 或 "-120.5" */
        val LONGITUDE = stringPreferencesKey("longitude")

        /** 纬度设置,格式如 "30:15:00" 或 "30.25" */
        val LATITUDE = stringPreferencesKey("latitude")

        /**
         * 经纬度格式设置
         * - "DMS": 度分秒格式(如 120°30'30")
         * - "DECIMAL": 十进制格式(如 120.5083°)
         */
        val LOCATION_FORMAT = stringPreferencesKey("location_format")

        /** 是否自动获取位置(使用 GPS) */
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")

        // ----- 时区设置 -----
        /** 时区 ID,如 "Asia/Shanghai"、"America/New_York" */
        val TIMEZONE = stringPreferencesKey("timezone")

        // ----- 显示设置 -----
        /** 深色模式开关 */
        val DARK_MODE = booleanPreferencesKey("dark_mode")

        /** 语言设置,格式如 "zh_CN"、"en_US" */
        val LANGUAGE = stringPreferencesKey("language")

        // ----- 赤道仪设置 -----
        /** 导星速率,单位:恒星时速(sidereal rate)的倍数
         *  默认 1.0x 表示正常导星速率
         */
        val GUIDE_RATE = doublePreferencesKey("guide_rate")

        /** GOTO 速率,单位:恒星时速的倍数
         *  默认 48.0x 表示快速移动
         */
        val GOTO_RATE = doublePreferencesKey("goto_rate")

        /** 东侧限位角度(从正南向东方),单位:度
         *  超出此范围赤道仪将停止运动
         */
        val EAST_LIMIT = doublePreferencesKey("east_limit")

        /** 西侧限位角度(从正南向西方),单位:度
         *  超出此范围赤道仪将停止运动
         */
        val WEST_LIMIT = doublePreferencesKey("west_limit")

        /** 当前纬度(十进制,GPS获取),用于星历表计算 */
        val LATITUDE_DEC = doublePreferencesKey("latitude_dec")

        /** 当前经度(十进制,GPS获取),用于星历表计算 */
        val LONGITUDE_DEC = doublePreferencesKey("longitude_dec")
    }

    // ========== 位置设置(只读 Flow 属性)==========

    /**
     * 经度值 Flow
     * 默认值:"120:00:00"(东经 120 度)
     */
    val longitude: Flow<String> = context.dataStore.data.map { it[LONGITUDE] ?: "120:00:00" }

    /**
     * 纬度值 Flow
     * 默认值:"30:00:00"(北纬 30 度)
     */
    val latitude: Flow<String> = context.dataStore.data.map { it[LATITUDE] ?: "30:00:00" }

    /**
     * 经纬度格式 Flow
     * 默认值:"DMS"(度分秒格式)
     */
    val locationFormat: Flow<String> = context.dataStore.data.map { it[LOCATION_FORMAT] ?: "DMS" }

    /**
     * 自动定位开关 Flow
     * 默认值:false(手动输入位置)
     */
    val autoLocation: Flow<Boolean> = context.dataStore.data.map { it[AUTO_LOCATION] ?: false }

    // ========== 时区设置(只读 Flow 属性)==========

    /**
     * 时区 ID Flow
     * 默认值:"Asia/Shanghai"
     */
    val timezone: Flow<String> = context.dataStore.data.map { it[TIMEZONE] ?: "Asia/Shanghai" }

    // ========== 显示设置(只读 Flow 属性)==========

    /**
     * 深色模式开关 Flow
     * 默认值:true(深色主题)
     */
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: true }

    /**
     * 语言设置 Flow
     * 默认值:"zh_CN"(简体中文)
     */
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "zh_CN" }

    // ========== 赤道仪设置(只读 Flow 属性)==========

    /**
     * 导星速率 Flow
     * 默认值:1.0(正常导星速率)
     */
    val guideRate: Flow<Double> = context.dataStore.data.map { it[GUIDE_RATE] ?: 1.0 }

    /**
     * GOTO 速率 Flow
     * 默认值:48.0(快速 GOTO)
     */
    val gotoRate: Flow<Double> = context.dataStore.data.map { it[GOTO_RATE] ?: 48.0 }

    /**
     * 东限位角度 Flow
     * 默认值:180.0(允许向东转动 180 度)
     */
    val eastLimit: Flow<Double> = context.dataStore.data.map { it[EAST_LIMIT] ?: 180.0 }

    /**
     * 西限位角度 Flow
     * 默认值:180.0(允许向西转动 180 度)
     */
    val westLimit: Flow<Double> = context.dataStore.data.map { it[WEST_LIMIT] ?: 180.0 }

    /**
     * 当前纬度(十进制)Flow
     * 用于星历表计算
     */
    val latitudeDec: Flow<Double> = context.dataStore.data.map { it[LATITUDE_DEC] ?: 30.0 }

    /**
     * 当前经度(十进制)Flow
     * 用于星历表计算
     */
    val longitudeDec: Flow<Double> = context.dataStore.data.map { it[LONGITUDE_DEC] ?: 120.0 }

    // ========== 设置写入方法 ==========

    /**
     * 设置经度值
     * @param value 经度字符串,格式由 locationFormat 决定
     */
    suspend fun setLongitude(value: String) {
        context.dataStore.edit { it[LONGITUDE] = value }
    }

    /**
     * 设置纬度值
     * @param value 纬度字符串,格式由 locationFormat 决定
     */
    suspend fun setLatitude(value: String) {
        context.dataStore.edit { it[LATITUDE] = value }
    }

    /**
     * 设置经纬度格式
     * @param value "DMS" 或 "DECIMAL"
     */
    suspend fun setLocationFormat(value: String) {
        context.dataStore.edit { it[LOCATION_FORMAT] = value }
    }

    /**
     * 设置是否自动获取位置
     * @param value true 启用 GPS 自动定位
     */
    suspend fun setAutoLocation(value: Boolean) {
        context.dataStore.edit { it[AUTO_LOCATION] = value }
    }

    /**
     * 设置时区
     * @param value 时区 ID(如 "Asia/Shanghai")
     */
    suspend fun setTimezone(value: String) {
        context.dataStore.edit { it[TIMEZONE] = value }
    }
    
    /**
     * 设置深色模式
     * @param value true 启用深色主题
     */
    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }

    /**
     * 设置语言
     * @param value 语言标签(如 "zh_CN"、"en_US")
     */
    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }

    /**
     * 设置导星速率
     * @param value 恒星时速倍数(如 0.5 半速,2.0 双速)
     */
    suspend fun setGuideRate(value: Double) {
        context.dataStore.edit { it[GUIDE_RATE] = value }
    }

    /**
     * 设置 GOTO 速率
     * @param value 恒星时速倍数(如 48.0 快速)
     */
    suspend fun setGotoRate(value: Double) {
        context.dataStore.edit { it[GOTO_RATE] = value }
    }

    /**
     * 设置东侧限位
     * @param value 限位角度(度)
     */
    suspend fun setEastLimit(value: Double) {
        context.dataStore.edit { it[EAST_LIMIT] = value }
    }

    /**
     * 设置西侧限位
     * @param value 限位角度(度)
     */
    suspend fun setWestLimit(value: Double) {
        context.dataStore.edit { it[WEST_LIMIT] = value }
    }

    /**
     * 设置当前纬度(十进制)
     * @param value 纬度(度)
     */
    suspend fun setLatitudeDec(value: Double) {
        context.dataStore.edit { it[LATITUDE_DEC] = value }
    }

    /**
     * 设置当前经度(十进制)
     * @param value 经度(度)
     */
    suspend fun setLongitudeDec(value: Double) {
        context.dataStore.edit { it[LONGITUDE_DEC] = value }
    }
}