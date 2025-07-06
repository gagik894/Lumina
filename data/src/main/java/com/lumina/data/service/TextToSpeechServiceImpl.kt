package com.lumina.data.service

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.TextToSpeechService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TextToSpeechServiceImpl"

/**
 * Implementation of [TextToSpeechService] using Android's TextToSpeech API.
 *
 * Provides intelligent speech output optimized for visually impaired users with
 * different urgency levels and speech characteristics based on navigation cue types.
 * Features smart buffering to handle AI text generation that's faster than speech.
 */
@Singleton
class TextToSpeechServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechService {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var utteranceId = 0

    // Smart buffering for handling fast AI text generation
    private val textBuffer = StringBuilder()
    private var lastBufferUpdateTime = 0L
    private val bufferDelayMs = 500L // Wait 500ms before speaking buffered text
    private var bufferHandler: android.os.Handler? = null
    private var pendingBufferRunnable: Runnable? = null

    init {
        bufferHandler = android.os.Handler(android.os.Looper.getMainLooper())
    }

    override fun initialize(
        onInitialized: () -> Unit,
        onError: (String) -> Unit
    ) {
        textToSpeech = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    textToSpeech?.let { tts ->
                        val result = tts.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            // Fallback to English if default language is not supported
                            val englishResult = tts.setLanguage(Locale.US)
                            if (englishResult == TextToSpeech.LANG_MISSING_DATA ||
                                englishResult == TextToSpeech.LANG_NOT_SUPPORTED
                            ) {
                                onError("Language not supported")
                                return@let
                            }
                        }

                        // Configure TTS for accessibility
                        tts.setSpeechRate(1.0f) // Normal speech rate
                        tts.setPitch(1.0f) // Normal pitch

                        // Set up utterance listener for monitoring speech progress
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "Speech started: $utteranceId")
                            }

                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "Speech completed: $utteranceId")
                            }

                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "Speech error: $utteranceId")
                            }
                        })

                        isInitialized = true
                        onInitialized()
                    }
                }

                else -> {
                    onError("Failed to initialize TextToSpeech")
                }
            }
        }
    }

    override fun speak(navigationCue: NavigationCue) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak navigation cue")
            return
        }

        val message = when (navigationCue) {
            is NavigationCue.CriticalAlert -> navigationCue.message
            is NavigationCue.InformationalAlert -> navigationCue.message
            is NavigationCue.AmbientUpdate -> navigationCue.message
        }

        val isDone = when (navigationCue) {
            is NavigationCue.CriticalAlert -> navigationCue.isDone
            is NavigationCue.InformationalAlert -> navigationCue.isDone
            is NavigationCue.AmbientUpdate -> navigationCue.isDone
        }

        // Handle critical alerts specially with clear announcement
        if (navigationCue is NavigationCue.CriticalAlert) {
            if (isDone && message.isNotBlank()) {
                // Only speak complete critical alert messages
                speakCriticalAlert(message)
            }
        } else {
            // For non-critical alerts, use smart buffering
            addToBuffer(message, navigationCue, isDone)
        }
    }

    /**
     * Speaks critical alerts with clear announcement and proper pacing.
     */
    private fun speakCriticalAlert(message: String) {
        // Stop any current speech first
        textToSpeech?.stop()

        // Clear announcement to get attention
        speakWithParameters("ALERT", TextToSpeech.QUEUE_FLUSH, 1.0f, 0.8f)

        // Brief pause, then speak the complete message clearly
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            speakWithParameters(message, TextToSpeech.QUEUE_ADD, 1.0f, 0.9f)
        }, 1000) // Slightly longer pause for clarity
    }

    /**
     * Adds text to buffer and schedules speaking based on completion or natural breaks.
     */
    private fun addToBuffer(text: String, navigationCue: NavigationCue, isDone: Boolean) {
        synchronized(textBuffer) {
            // Only add non-empty text
            if (text.isNotBlank()) {
                textBuffer.append(text)
            }

            lastBufferUpdateTime = System.currentTimeMillis()

            // Cancel previous pending speech
            pendingBufferRunnable?.let { bufferHandler?.removeCallbacks(it) }

            // Speak immediately if:
            // 1. Generation is complete (isDone)
            // 2. We have a complete sentence
            // 3. Buffer is getting too long
            val hasCompleteSentence = textBuffer.toString().let { buffer ->
                buffer.contains('.') || buffer.contains('!') || buffer.contains('?')
            }

            val shouldSpeakNow = isDone ||
                    hasCompleteSentence ||
                    textBuffer.length > 150

            if (shouldSpeakNow) {
                speakBufferedText(navigationCue)
            } else {
                // Schedule speaking after delay only if generation is still ongoing
                pendingBufferRunnable = Runnable {
                    if (System.currentTimeMillis() - lastBufferUpdateTime >= bufferDelayMs) {
                        speakBufferedText(navigationCue)
                    }
                }
                bufferHandler?.postDelayed(pendingBufferRunnable!!, bufferDelayMs)
            }
        }
    }

    /**
     * Speaks the buffered text and clears the buffer.
     */
    private fun speakBufferedText(navigationCue: NavigationCue) {
        synchronized(textBuffer) {
            if (textBuffer.isNotEmpty()) {
                val textToSpeak = textBuffer.toString().trim()
                textBuffer.clear()

                if (textToSpeak.isNotBlank()) {
                    speakImmediately(textToSpeak, TextToSpeech.QUEUE_ADD, navigationCue)
                }
            }
        }
    }

    /**
     * Speaks text immediately without buffering.
     */
    private fun speakImmediately(text: String, queueMode: Int, navigationCue: NavigationCue) {
        val (pitch, rate) = when (navigationCue) {
            is NavigationCue.CriticalAlert -> Pair(1.0f, 0.9f) // Normal pitch, slightly slower
            is NavigationCue.InformationalAlert -> Pair(1.0f, 1.0f)
            is NavigationCue.AmbientUpdate -> Pair(0.9f, 0.9f)
        }

        speakWithParameters(text, queueMode, pitch, rate)
    }

    override fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak text")
            return
        }

        speakWithParameters(text, TextToSpeech.QUEUE_ADD, 1.0f, 1.0f)
    }

    private fun speakWithParameters(
        text: String,
        queueMode: Int,
        pitch: Float,
        rate: Float
    ) {
        textToSpeech?.let { tts ->
            // Set speech characteristics
            tts.setPitch(pitch)
            tts.setSpeechRate(rate)

            // Create unique utterance ID for tracking
            val currentUtteranceId = "utterance_${++utteranceId}"

            // Create speech parameters
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, currentUtteranceId)
            }

            // Speak the text
            val result = tts.speak(text, queueMode, params, currentUtteranceId)

            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Failed to speak text: $text")
            } else {
                Log.d(TAG, "Speaking: $text (ID: $currentUtteranceId)")
            }
        }
    }

    override fun stop() {
        // Clear buffer and stop speech
        synchronized(textBuffer) {
            textBuffer.clear()
            pendingBufferRunnable?.let { bufferHandler?.removeCallbacks(it) }
        }
        textToSpeech?.stop()
    }

    override fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    override fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.1f, 3.0f))
    }

    override fun shutdown() {
        synchronized(textBuffer) {
            textBuffer.clear()
            pendingBufferRunnable?.let { bufferHandler?.removeCallbacks(it) }
        }
        textToSpeech?.apply {
            stop()
            shutdown()
        }
        textToSpeech = null
        isInitialized = false
    }
}

/**
 * Helper data class for tuple with 4 elements.
 */
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
