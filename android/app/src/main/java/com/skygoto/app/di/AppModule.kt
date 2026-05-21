/*
 * 文件名：AppModule.kt
 * 描述：Hilt 依赖注入模块 - 提供蓝牙连接管理器和仓储绑定
 *
 * 作者：SkyGoto Team
 * 日期：2024
 */
package com.skygoto.app.di

import android.content.Context
import com.skygoto.app.data.datasource.BluetoothConnectionManager
import com.skygoto.app.data.repository.CatalogRepositoryImpl
import com.skygoto.app.data.repository.MountRepositoryImpl
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.MountRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块 - 提供蓝牙连接管理器和仓储绑定
 *
 * BluetoothConnectionManager 需要 Context，而 Android 没有构造器可注入的 Context。
 * 因此通过 @Provides + @Singleton 在 Hilt module 中统一创建并管理这个单例，
 * 确保 ConnectViewModel 和 ConnectionManager 注入的是同一个实例，
 * 避免各自 new 出实例导致广播接收器/Runnable 泄漏。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * 绑定赤道仪仓储接口到实现类
     */
    @Binds
    @Singleton
    abstract fun bindMountRepository(
        impl: MountRepositoryImpl
    ): MountRepository

    /**
     * 绑定天体目录仓储接口到实现类
     */
    @Binds
    @Singleton
    abstract fun bindCatalogRepository(
        impl: CatalogRepositoryImpl
    ): CatalogRepository

    companion object {
        /**
         * 提供蓝牙连接管理器单例
         */
        @Provides
        @Singleton
        fun provideBluetoothConnectionManager(
            @dagger.hilt.android.qualifiers.ApplicationContext context: Context
        ): BluetoothConnectionManager {
            return BluetoothConnectionManager(context)
        }
    }
}