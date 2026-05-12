package com.skygoto.app.di

import com.skygoto.app.data.repository.CatalogRepositoryImpl
import com.skygoto.app.data.repository.MountRepositoryImpl
import com.skygoto.app.domain.repository.CatalogRepository
import com.skygoto.app.domain.repository.MountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    
    @Binds
    @Singleton
    abstract fun bindMountRepository(
        impl: MountRepositoryImpl
    ): MountRepository
    
    @Binds
    @Singleton
    abstract fun bindCatalogRepository(
        impl: CatalogRepositoryImpl
    ): CatalogRepository
}