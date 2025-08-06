package com.lumina.data.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.TextToSpeechService
import com.lumina.domain.service.TtsPreprocessingService
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
    @ApplicationContext private val context: Context,
    private val ttsPreprocessingService: TtsPreprocessingService
) : TextToSpeechService {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var utteranceId = 0

    // Smart buffering for handling fast AI text generation
    private val textBuffer = StringBuilder()
    private var bufferHandler: Handler? = Handler(Looper.getMainLooper())
    private var pendingBufferRunnable: Runnable? = null

    override fun initialize(
        onInitialized: () -> Unit,
        onError: (String) -> Unit
    ) {
        textToSpeech = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    textToSpeech?.let { tts ->
                        val result = tts.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            val englishResult = tts.setLanguage(Locale.US)
                            if (englishResult == TextToSpeech.LANG_MISSING_DATA || englishResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                                onError("Language not supported")
                                return@let
                            }
                        }
                        tts.setSpeechRate(1.0f)
                        tts.setPitch(1.0f)
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

                else -> onError("Failed to initialize TextToSpeech")
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

        addToBuffer(message, navigationCue, isDone)
    }

    private fun addToBuffer(text: String, navigationCue: NavigationCue, isDone: Boolean) {
        synchronized(textBuffer) {
            textBuffer.append(text)
            Log.d(TAG, "Added to buffer: '$text', buffer now: '${textBuffer}'")

            pendingBufferRunnable?.let { bufferHandler?.removeCallbacks(it) }
            val bufferContent = textBuffer.toString()

            val hasNaturalBreak = bufferContent.contains(". ") ||
                    bufferContent.contains(".\n") ||
                    bufferContent.contains("?") ||
                    bufferContent.contains("!") ||
                    bufferContent.contains("\n") ||
                    bufferContent.contains("...") ||
                    bufferContent.contains("  ") ||
                    bufferContent.contains("|")

            val isCritical = navigationCue is NavigationCue.CriticalAlert
            val bufferThreshold = if (isCritical) 80 else 300

            Log.d(
                TAG,
                "Natural break detected: $hasNaturalBreak, isDone: $isDone, buffer length: ${bufferContent.length}, threshold: $bufferThreshold"
            )

            if (hasNaturalBreak || isDone || bufferContent.length > bufferThreshold) {
                speakBufferedText(navigationCue, isDone)
            }
        }
    }

    private fun speakBufferedText(navigationCue: NavigationCue, isDone: Boolean) {
        synchronized(textBuffer) {
            if (textBuffer.isEmpty()) return

            val bufferContent = textBuffer.toString()
            val textToSpeak: String

            if (isDone) {
                // If it's the final chunk, speak everything that's left.
                textToSpeak = bufferContent
                textBuffer.clear()
            } else {
                // Define sentence break markers. Note ". " to avoid breaking on "example.com".
                val breakMarkers =
                    listOf(". ", "?", "!", ".\n", "?\n", "!\n", "\n", "|", "...", "? ", "!  ")
                var lastBreakIndex = -1
                var breakMarkerLength = 0

                // Find the last occurrence of any break marker.
                for (marker in breakMarkers) {
                    val index = bufferContent.lastIndexOf(marker)
                    if (index > lastBreakIndex) {
                        lastBreakIndex = index
                        breakMarkerLength = marker.length
                    }
                }

                if (lastBreakIndex != -1) {
                    // A valid sentence break was found. Extract everything up to and including it.
                    val endIndex = lastBreakIndex + breakMarkerLength
                    textToSpeak = bufferContent.substring(0, endIndex)
                    // Update the buffer to only contain the remaining, unspoken part.
                    textBuffer.delete(0, endIndex)
                } else {
                    // No complete sentence found in the buffer yet, so don't speak anything.
                    // We'll wait for the next chunk or the "isDone" signal.
                    return
                }
            }
            Log.d(TAG, "Speaking buffered text: '$textToSpeak'")
            val queueMode =
                if (navigationCue is NavigationCue.CriticalAlert) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            speakWithParameters(textToSpeak, queueMode, 1.0f, 1.0f)

        }
    }


    override fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak text")
            return
        }
        speakWithParameters(text, TextToSpeech.QUEUE_ADD, 1.0f, 1.0f)
    }

    private fun speakWithParameters(text: String, queueMode: Int, pitch: Float, rate: Float) {
        textToSpeech?.let { tts ->
            val processedText = ttsPreprocessingService.preprocessForTts(text)
            tts.setPitch(pitch)
            tts.setSpeechRate(rate)
            val currentUtteranceId = "utterance_${++utteranceId}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, currentUtteranceId)
            }
            val result = tts.speak(processedText, queueMode, params, currentUtteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Failed to speak text: $processedText")
            } else {
                Log.d(TAG, "Speaking: $processedText (ID: $currentUtteranceId)")
            }
        }
    }

    override fun stop() {
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