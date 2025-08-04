package com.lumina.domain.usecase.voice

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.TextToSpeechService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


/**
 * Use case for handling TTS integration with NavigationCues.
 *
 * Extracts TTS processing logic from the ViewModel and provides
 * a clean domain interface for speech synthesis.
 * This class is responsible for converting `NavigationCue` objects into audible speech
 * using the provided `TextToSpeechService`. It also offers methods to control
 * speech playback, such as stopping speech, setting the speech rate, and checking
 * if the TTS engine is currently speaking.
 *
 * @property textToSpeechService The service responsible for the actual text-to-speech conversion.
 */
class HandleTtsUseCase @Inject constructor(
    private val textToSpeechService: TextToSpeechService
) {

    /**
     * Processes a flow of NavigationCues and handles TTS output.
     *
     * @param navigationCueFlow Flow of navigation cues to process
     * @param isTtsEnabled Whether TTS is currently enabled
     * @return Flow of NavigationCues with TTS side effects
     */
    fun processNavigationCues(
        navigationCueFlow: Flow<NavigationCue>,
        isTtsEnabled: Boolean
    ): Flow<NavigationCue> {
        return navigationCueFlow.onEach { navigationCue ->
            if (isTtsEnabled) {
                val message = when (navigationCue) {
                    is NavigationCue.CriticalAlert -> navigationCue.message
                    is NavigationCue.InformationalAlert -> navigationCue.message
                    is NavigationCue.AmbientUpdate -> navigationCue.message
                }

                // Skip empty strings generated while the model is thinking
                if (message.isNotBlank()) {
                    textToSpeechService.speak(navigationCue)
                }
            }
        }
    }

    /**
     * Speaks a simple text message.
     */
    fun speak(text: String, isEnabled: Boolean) {
        if (isEnabled && text.isNotBlank()) {
            textToSpeechService.speak(text)
        }
    }

    /**
     * Stops current speech.
     */
    fun stopSpeech() {
        textToSpeechService.stop()
    }

    /**
     * Sets speech rate.
     */
    fun setSpeechRate(rate: Float) {
        textToSpeechService.setSpeechRate(rate)
    }

    /**
     * Checks if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = textToSpeechService.isSpeaking()
}
