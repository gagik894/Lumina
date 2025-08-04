package com.lumina.data.datasource

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.lumina.domain.model.InitializationState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaAiDataSource"

/**
 * Implementation of [AiDataSource] using Google's Gemma AI model for generating scene descriptions.
 *
 * This data source manages the lifecycle of the Gemma model, including:
 * - Model initialization and caching
 * - Session management with token limit handling
 * - Streaming response generation with motion analysis
 * - Resource cleanup
 *
 * The implementation includes:
 * - Intelligent session management to prevent token limit exceeded errors
 * - Multi-frame motion analysis for better navigation assistance
 * - Optimized image processing to prevent out-of-memory errors
 */
@Singleton
class GemmaAiDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AiDataSource {

    private val modelName = "gemma-3n-e2b-it-int4.task"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var llmInference: LlmInference? = null

    /** Long-lived session for navigation cues */
    private var navigationSession: LlmInferenceSession? = null
    private var approximateTokenCount = 0

    /** ensures only one generateResponse runs at a time */
    private val generationMutex = Mutex()

    /** Conservative token limit to prevent OUT_OF_RANGE errors */
    private val maxTokens = 3500

    /** Maximum image dimension for processing to prevent OOM */
    private val maxImageDimension = 1024

    private val _initializationState =
        MutableStateFlow<InitializationState>(InitializationState.NotInitialized)
    override val initializationState: StateFlow<InitializationState> =
        _initializationState.asStateFlow()

    init {
        initialize()
    }

    /**
     * Initializes the Gemma AI model asynchronously.
     *
     * This method:
     * 1. Copies the model from assets to cache if needed
     * 2. Creates the LLM inference engine with GPU acceleration
     * 3. Establishes an initial session with the system prompt
     * 4. Updates the initialization state for UI feedback
     */
    private fun initialize() {
        coroutineScope.launch {
            try {
                _initializationState.value = InitializationState.Initializing

                val modelPath = getAbsoluteModelPath(modelName)
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .setMaxTokens(4096)
                    .setMaxNumImages(5)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                createNewSession()

                _initializationState.value = InitializationState.Initialized
                Log.d(TAG, "Gemma AI initialized successfully")
            } catch (e: Exception) {
                _initializationState.value = InitializationState.Error(
                    "Failed to initialize Gemma AI: ${e.message}"
                )
                Log.e(TAG, "Initialization failed", e)
            }
        }
    }

    /**
     * Creates a new inference session with the optimized system prompt.
     *
     * @return A new [LlmInferenceSession] configured for vision tasks
     */
    private fun createNewSession(): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.8f) // Slightly more focused responses
            .setTopK(40) // Reduced for faster inference
            .setTopP(0.9f)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()

        val newSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)

        val systemPrompt = getSystemPrompt()
        newSession.addQueryChunk(systemPrompt)
        approximateTokenCount = estimateTokens(systemPrompt)

        navigationSession = newSession
        return newSession
    }

    /**
     * Gets the current session or creates a new one if approaching token limit.
     *
     * @return An active [LlmInferenceSession] ready for inference
     */
    /** Returns the navigation session, creating or refreshing it if needed */
    private fun getOrCreateNavigationSession(): LlmInferenceSession {
        return if (navigationSession == null || approximateTokenCount > maxTokens) {
            navigationSession?.close()
            navigationSession = createNewSession()
            navigationSession!!
        } else {
            navigationSession!!
        }
    }

    /**
     * Generates a response for multiple timestamped frames with motion analysis.
     * This is the preferred method for navigation assistance.
     */
    override fun generateResponse(
        prompt: String,
        frames: List<TimestampedFrame>,
        useHighResolution: Boolean
    ): Flow<Pair<String, Boolean>> = callbackFlow {
        if (!generationMutex.tryLock()) {
            Log.w(TAG, "Concurrent call to generateResponse detected, ignoring.")
            channel.close()
            return@callbackFlow
        }
        try {
            val currentSession = getOrCreateNavigationSession()
            // Calculate timing information for motion context
            val timeSpanMs = if (frames.size > 1) {
                frames.last().timestampMs - frames.first().timestampMs
            } else 0L

            val motionContextPrompt = buildMotionContextPrompt(prompt, frames.size, timeSpanMs)

            // Add prompt and track tokens
            currentSession.addQueryChunk(motionContextPrompt)
            approximateTokenCount += estimateTokens(motionContextPrompt)

            // Add frames in chronological order
            frames.forEach { frame ->
                val scaledBitmap = scaleImageForProcessing(frame.bitmap, useHighResolution)
                currentSession.addImage(BitmapImageBuilder(scaledBitmap).build())

                // Clean up if we created a new bitmap
                if (scaledBitmap != frame.bitmap) {
                    scaledBitmap.recycle()
                }
            }

            // Estimate tokens for images (conservative estimate)
            approximateTokenCount += frames.size * 200

            val uniqueChunks = mutableSetOf<String>()

            currentSession.generateResponseAsync { partialResult, done ->
                Log.d(TAG, "generateResponse: Partial result: $partialResult, Done: $done")

                val chunk = partialResult.trim()
                if (chunk.isNotEmpty()) uniqueChunks.add(chunk)


                val finalFlag = done

                if (finalFlag) {
                    approximateTokenCount += estimateTokens(partialResult)
                }

                trySend(Pair(partialResult, finalFlag))
                if (finalFlag) {
                    channel.close()
                }
            }

        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            generationMutex.unlock()
        }
    }

    /**
     * Builds a motion-aware prompt that helps the AI understand timing and movement.
     */
    private fun buildMotionContextPrompt(
        userPrompt: String,
        frameCount: Int,
        timeSpanMs: Long
    ): String {
        val motionContext = when {
            frameCount == 1 -> "Analyze this single frame."
            timeSpanMs > 0 -> "Analyze $frameCount frames captured over ${timeSpanMs}ms. " +
                    "Identify any movement, direction changes, or objects entering/leaving the scene."

            else -> "Analyze these $frameCount sequential frames for movement patterns."
        }

        return "$motionContext $userPrompt"
    }

    /**
     * Scales image to optimal size for processing, preventing OOM while maintaining quality.
     * For high-resolution tasks, the image is not scaled down.
     */
    private fun scaleImageForProcessing(bitmap: Bitmap, isHighResolution: Boolean): Bitmap {
        if (isHighResolution) {
            return bitmap // Use original resolution for OCR tasks
        }

        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxImageDimension && height <= maxImageDimension) {
            return bitmap
        }

        val scaleFactor = maxImageDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * Estimates token count for text (rough approximation).
     */
    private fun estimateTokens(text: String): Int {
        return text.length / 3 // Conservative estimate
    }

    override fun resetSession() {
        if (generationMutex.tryLock()) {
            try {
                // If we haven't used many tokens yet, just reset the counter
                // This avoids unnecessary session recreation for quick resets
                if (approximateTokenCount < 1000) { // fresh session
                    approximateTokenCount = estimateTokens(getSystemPrompt())
                    Log.d(TAG, "Session context reset (lightweight) - keeping fresh session")
                    return
                }

                // For sessions with more context, recreate to truly clear memory
                if (navigationSession != null) {
                    navigationSession?.close()
                    createNewSession() // Creates a fresh session with system prompt
                } else {
                    createNewSession() // Create if doesn't exist
                }
                Log.d(TAG, "Session context cleared, fresh session ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset session context", e)
                // Fallback: set to null so it gets recreated on next use
                navigationSession = null
                approximateTokenCount = 0
            } finally {
                generationMutex.unlock()
            }
        } else {
            Log.w(
                TAG,
                "Reset session called while generation in progress. Will reset on next call."
            )
            // Schedule reset for next generation call
            navigationSession = null
            approximateTokenCount = maxTokens + 1 // Force recreation
        }
    }

    /**
     * Returns the system prompt for token estimation.
     */
    private fun getSystemPrompt(): String {
        return """You are an AI assistant for blind users providing real-time navigation assistance.
            CRITICAL RULES:
            - Be extremely concise and direct
            """
    }

    override fun close() {
        try {
            if (generationMutex.isLocked) {
                generationMutex.unlock()
            }
            navigationSession?.close()
            llmInference?.close()
            Log.d(TAG, "Resources closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }

    /**
     * Copies the model from assets to cache directory if not already present.
     */
    private fun getAbsoluteModelPath(modelName: String): String {
        val destinationFile = File(context.cacheDir, modelName)
        if (destinationFile.exists()) {
            Log.d(TAG, "Model already cached at: ${destinationFile.absolutePath}")
            return destinationFile.absolutePath
        }

        Log.d(TAG, "Copying model to cache...")
        context.assets.open(modelName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Log.d(TAG, "Model cached at: ${destinationFile.absolutePath}")
        return destinationFile.absolutePath
    }
}