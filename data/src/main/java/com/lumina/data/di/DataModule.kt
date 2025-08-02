package com.lumina.data.di

import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.GemmaAiDataSource
import com.lumina.data.datasource.MediaPipeObjectDetector
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.data.repository.LuminaRepositoryImpl
import com.lumina.domain.repository.LuminaRepository
import com.lumina.domain.service.NavigationModeService
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    /**
     * Binds the ObjectDetectorDataSource interface to its MediaPipe implementation.
     * @Singleton ensures a single instance of our object detector.
     */
    @Binds
    @Singleton
    abstract fun bindObjectDetectorDataSource(
        mediaPipeObjectDetector: MediaPipeObjectDetector
    ): ObjectDetectorDataSource

    companion object {
        /**
         * Provides the NavigationModeService as a singleton.
         * This keeps the domain layer pure while enabling dependency injection.
         */
        @Provides
        @Singleton
        @JvmStatic
        fun provideNavigationModeService(): NavigationModeService {
            return NavigationModeService()
        }
    }
}