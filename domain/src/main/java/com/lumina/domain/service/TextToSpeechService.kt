package com.lumina.domain.service

import com.lumina.domain.model.NavigationCue

/**
 * Service interface for text-to-speech functionality.
 *
 * Provides audio feedback for navigation cues with different urgency levels
 * and speech characteristics appropriate for visually impaired users.
 */
interface TextToSpeechService {

    /**
     * Initializes the text-to-speech engine.
     *
     * @param onInitialized Callback invoked when TTS is ready to use
     * @param onError Callback invoked if initialization fails
     */
    fun initialize(
        onInitialized: () -> Unit,
        onError: (String) -> Unit
    )

    /**
     * Speaks a navigation cue with appropriate urgency and speech characteristics.
     *
     * Critical alerts are spoken immediately and interrupt current speech.
     * Informational alerts queue normally.
     * Ambient updates are spoken at lower priority.
     *
     * @param navigationCue The navigation cue to speak
     */
    fun speak(navigationCue: NavigationCue)

    /**
     * Speaks plain text with normal priority.
     *
     * @param text The text to speak
     */
    fun speak(text: String)

    /**
     * Stops all current speech output.
     */
    fun stop()

    /**
     * Checks if the TTS engine is currently speaking.
     *
     * @return true if speech is in progress
     */
    fun isSpeaking(): Boolean

    /**
     * Sets the speech rate.
     *
     * @param rate Speech rate (0.1 to 3.0, where 1.0 is normal)
     */
    fun setSpeechRate(rate: Float)

    /**
     * Releases TTS resources.
     * Should be called when the service is no longer needed.
     */
    fun shutdown()
}
