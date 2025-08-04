package com.lumina.data.di

import com.lumina.data.service.TrafficLightTimerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger module for providing crossing enhancement related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object CrossingEnhancementModule {

    @Provides
    @Singleton
    fun provideTrafficLightTimerService(): TrafficLightTimerService {
        return TrafficLightTimerService()
    }
}
