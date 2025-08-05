package com.lumina.domain.usecase.voice

import com.lumina.domain.service.TextToSpeechService
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
     * Speaks a simple text message.
     */
    fun speak(text: String, isEnabled: Boolean) {
        if (isEnabled) {
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

    /**
     * Stops the TTS engine from speaking.
     *
     * This method is used to stop any ongoing speech synthesis.
     */
    fun stopSpeaking() {
        textToSpeechService.stop()
    }
}
