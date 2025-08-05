package com.lumina.di

import com.lumina.data.service.TextToSpeechServiceImpl
import com.lumina.domain.service.TextToSpeechService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing text-to-speech service dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TextToSpeechModule {

    /**
     * Binds the TextToSpeech implementation to the interface.
     */
    @Binds
    @Singleton
    abstract fun bindTextToSpeechService(
        textToSpeechServiceImpl: TextToSpeechServiceImpl
    ): TextToSpeechService
}
