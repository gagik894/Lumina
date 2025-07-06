package com.lumina.data.di

import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.GemmaAiDataSource
import com.lumina.data.repository.LuminaRepositoryImpl
import com.lumina.domain.repository.LuminaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /**
     * Binds the LuminaRepository interface to its implementation.
     * @Singleton ensures that there is only one instance of the repository throughout the app's lifecycle.
     */
    @Binds
    @Singleton
    abstract fun bindLuminaRepository(
        luminaRepositoryImpl: LuminaRepositoryImpl
    ): LuminaRepository

    /**
     * Binds the AiDataSource interface to its Gemma implementation.
     * @Singleton ensures a single instance of our AI data source.
     */
    @Binds
    @Singleton
    abstract fun bindAiDataSource(
        gemmaAiDataSource: GemmaAiDataSource
    ): AiDataSource
}