package com.lumina.di

import com.lumina.data.service.HapticFeedbackServiceImpl
import com.lumina.domain.service.HapticFeedbackService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing haptic feedback service dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HapticModule {

    /**
     * Binds the HapticFeedbackService implementation to the interface.
     */
    @Binds
    @Singleton
    abstract fun bindHapticFeedbackService(
        hapticFeedbackServiceImpl: HapticFeedbackServiceImpl
    ): HapticFeedbackService
}
