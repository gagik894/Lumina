package com.lumina.data.datasource

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
 * - Streaming response generation
 * - Resource cleanup
 *
 * The implementation includes intelligent session management to prevent token limit exceeded errors
 * by tracking token usage and automatically resetting sessions when approaching the limit.
 */
@Singleton
class GemmaAiDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AiDataSource {

    private val modelName = "gemma-3n-e2b-it-int4.task"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    /** Current approximate token count to prevent exceeding model limits */
    private var tokenCount = 0
    private val maxTokens = 4000

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
                    .setMaxNumImages(10)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)

                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.8f)
                    .setTopK(40)
                    .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                    .build()
                session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)

                session?.addQueryChunk(
                    "You are 'Lumina', a highly advanced AI assistant for blind and low-vision users. " +
                            "Your primary goal is to provide fast, clear, and concise descriptions of the user's surroundings. " +
                            "Always prioritize safety and key objects. Your responses must be direct and objective. Do not use conversational filler."
                )

                _initializationState.value = InitializationState.Initialized
            } catch (e: Exception) {
                _initializationState.value =
                    InitializationState.Error("Failed to initialize Gemma AI: ${e.message}")
                Log.e(TAG, "Initialization failed", e)
            }
        }
    }

    /**
     * Creates a new inference session with the system prompt.
     *
     * @return A new [LlmInferenceSession] configured for vision tasks
     */
    private fun createNewSession(): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.8f)
            .setTopK(40)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        val newSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)

        newSession.addQueryChunk(
            "You are 'Lumina', a highly advanced AI assistant for blind and low-vision users. " +
                    "Your primary goal is to provide fast, clear, and concise descriptions of the user's surroundings. " +
                    "Always prioritize safety and key objects. Your responses must be direct and objective. Do not use conversational filler."
        )

        tokenCount = 50 // Approximate tokens for system prompt
        return newSession
    }

    /**
     * Gets the current session or creates a new one if approaching token limit.
     *
     * @return An active [LlmInferenceSession] ready for inference
     */
    private fun getOrCreateSession(): LlmInferenceSession {
        if (session == null || tokenCount > maxTokens) {
            session?.close()
            session = createNewSession()
        }
        return session!!
    }

    /**
     * Generates a response for the given prompt and image.
     *
     * This method streams the response in real-time as the AI processes the input.
     * It also handles token management to avoid exceeding the model's limits.
     *
     * @param prompt The text prompt to guide the AI response
     * @param image An optional image to provide context for the response
     * @return A flow of response chunks and completion status
     */
    override fun generateResponse(prompt: String, image: Bitmap): Flow<Pair<String, Boolean>> =
        callbackFlow {
        try {
            val currentSession = getOrCreateSession()

            val promptTokens = prompt.length / 4
            val imageTokens = 50
            tokenCount += promptTokens + imageTokens

            currentSession.addQueryChunk(prompt)
            currentSession.addImage(BitmapImageBuilder(image).build())
            currentSession.generateResponseAsync { partialResult, done ->
                Log.d(TAG, "generateResponse: Partial result: $partialResult, Done: $done")

                if (done) {
                    tokenCount += partialResult.length / 4
                }
                
                trySend(Pair(partialResult, done))
                if (done) {
                    channel.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateResponse", e)
            resetSession()
            close(e)
        }

            awaitClose { }
        }

    /**
     * Resets the current session, releasing any resources.
     *
     * This method should be called when the application no longer needs the AI model
     * or when an error occurs that requires re-initialization.
     */
    override fun resetSession() {
        session?.close()
        session = null
        tokenCount = 0
    }

    /**
     * Closes the AI data source, releasing all resources.
     *
     * This method should be called in the application's onDestroy or equivalent lifecycle
     * method to prevent memory leaks.
     */
    override fun close() {
        try {
            session?.close()
            llmInference?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copies the model from assets to cache directory if not already present.
     *
     * @param modelName The name of the model file in assets
     * @return The absolute path to the cached model file
     */
    private fun getAbsoluteModelPath(modelName: String): String {
        val destinationFile = File(context.cacheDir, modelName)
        if (destinationFile.exists()) return destinationFile.absolutePath
        context.assets.open(modelName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destinationFile.absolutePath
    }
}