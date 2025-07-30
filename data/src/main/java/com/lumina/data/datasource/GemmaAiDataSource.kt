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
    private val maxImageDimension = 512

    var finished = false

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

        val systemPrompt =
            """You are an AI assistant for blind users providing real-time navigation assistance.
            CRITICAL RULES:
            - Be extremely concise and direct
            - Prioritize safety threats and moving objects (e.g., cars, pedestrians, crosswalks, stairs, 
              curbs, etc.)
            - When analyzing multiple frames, identify movement patterns, for example:
              - "Car approaching fast"
              - "Pedestrian moving left",
            no need to describe static objects
            - Maximum 5 words per response for critical alerts (E.g., "Car approaching fast")
            - Maximum 10 words for informational updates (E.g., "New pedestrian entering scene")
            
            Respond with actionable navigation guidance. (E.g., "Turn left now" or "Stop immediately")
            
            
            FINDER MODE:
            If asked to locate a specific object, describe its location (e.g., "keyboard centered near") in no more than 6 words.

            IMPORTANT: State your answer ONCE and then stop. Do not repeat or refine the sentence in subsequent tokens.

            OPEN QUESTION MODE:
            When the user asks a direct question, answer it concisely based on the image content. If the answer isn't in the image, state that clearly.

            MOVEMENT ANALYSIS:
            When given multiple images in sequence, analyze:
            1. Object movement direction and speed
            """

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
        frames: List<TimestampedFrame>
    ): Flow<Pair<String, Boolean>> = callbackFlow {
        generationMutex.lock()
        try {
            val currentSession = getOrCreateNavigationSession()
            finished = false
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
                val scaledBitmap = scaleImageForProcessing(frame.bitmap)
                currentSession.addImage(BitmapImageBuilder(scaledBitmap).build())

                // Clean up if we created a new bitmap
                if (scaledBitmap != frame.bitmap) {
                    scaledBitmap.recycle()
                }
            }

            // Estimate tokens for images (conservative estimate)
            approximateTokenCount += frames.size * 200

            var finished = false

            // Generate response asynchronously with streaming callback
            val startTime = System.currentTimeMillis()
            val uniqueChunks = mutableSetOf<String>()

            currentSession.generateResponseAsync { partialResult, done ->
                Log.d(TAG, "generateResponse: Partial result: $partialResult, Done: $done")

                val chunk = partialResult.trim()
                if (chunk.isNotEmpty()) uniqueChunks.add(chunk)

                // Force-stop conditions: >3 seconds or ≥6 unique chunks
                val forcedDone =
                    (System.currentTimeMillis() - startTime > 3_000) || uniqueChunks.size >= 6

                val finalFlag = done || forcedDone

                if (finalFlag) {
                    finished = true
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
            if (!finished) {
                resetSession()
            }
            generationMutex.unlock()
        }
    }

    /**
     * Legacy method for single image analysis.
     */
    override fun generateResponse(prompt: String, image: Bitmap): Flow<Pair<String, Boolean>> =
        callbackFlow {
            generationMutex.lock()
            try {
                val currentSession = getOrCreateNavigationSession()

                val promptTokens = estimateTokens(prompt)
                val imageTokens = 200 // Conservative estimate for scaled image
                approximateTokenCount += promptTokens + imageTokens

                currentSession.addQueryChunk(prompt)
                val scaledBitmap = scaleImageForProcessing(image)
                currentSession.addImage(BitmapImageBuilder(scaledBitmap).build())

                // Clean up if we created a new bitmap
                if (scaledBitmap != image) {
                    scaledBitmap.recycle()
                }

                val startTime = System.currentTimeMillis()
                finished = false
                val uniqueChunks = mutableSetOf<String>()

                currentSession.generateResponseAsync { partialResult, done ->
                    Log.d(TAG, "generateResponse: Partial result: $partialResult, Done: $done")

                    val chunk = partialResult.trim()
                    if (chunk.isNotEmpty()) uniqueChunks.add(chunk)

                    val forcedDone =
                        (System.currentTimeMillis() - startTime > 3_000) || uniqueChunks.size >= 6

                    val finalFlag = done || forcedDone

                    if (finalFlag) {
                        finished = true
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
                if (!finished) {
                    resetSession()
                }
                // keep navigation session
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
     */
    private fun scaleImageForProcessing(bitmap: Bitmap): Bitmap {
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
        navigationSession?.close()
        navigationSession = null
        approximateTokenCount = 0
        Log.d(TAG, "Session reset")
    }

    override fun close() {
        try {
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