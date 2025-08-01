package com.lumina.domain.repository

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for Lumina's AI-powered navigation assistance system.
 *
 * Provides intelligent navigation cues for visually impaired users through
 * a sophisticated decision tree that analyzes camera frames and triggers
 * appropriate AI responses based on threat level and environmental changes.
 */
interface LuminaRepository {

    /** Current initialization state of the AI model */
    val initializationState: StateFlow<InitializationState>

    /**
     * Starts the main navigation pipeline and returns a flow of intelligent navigation cues.
     *
     * This pipeline continuously analyzes the environment and provides alerts based on:
     * 1. Critical threats (e.g., approaching cars)
     * 2. Important new objects (e.g., a person appearing)
     *
     * @return Flow of navigation cues with appropriate urgency levels.
     */
    fun getNavigationCues(): Flow<NavigationCue>

    /**
     * Explicitly starts the navigation director pipeline for on-demand usage.
     * This should be called when the user activates navigation mode.
     */
    fun startNavigationPipeline()

    /**
     * Processes a new camera frame for object detection and potential AI analysis.
     *
     * @param image Camera frame to be analyzed.
     */
    suspend fun processNewFrame(image: ImageInput)

    /**
     * Stops the proactive navigation pipeline and releases all associated resources.
     */
    fun stopNavigation()

    /**
     * Describes a scene based on the provided image and prompt. This is a one-shot operation.
     *
     * @param image The image input to be processed.
     * @param prompt The prompt to guide the description generation.
     * @return A flow of navigation cues containing the scene description.
     */
    fun describeScene(image: ImageInput, prompt: String): Flow<NavigationCue>

    /**
     * Initiates a transient street-crossing guidance mode.
     *
     * This function pauses the main navigation pipeline and returns a flow that provides
     * real-time crossing instructions (e.g., "WAIT", "CROSS"). The flow will automatically
     * complete and close once the AI determines the user has safely crossed the street,
     * at which point normal navigation will resume.
     *
     * @return A self-terminating [Flow] of [NavigationCue] containing crossing guidance.
     */
    fun startCrossingMode(): Flow<NavigationCue>

    /**
     * Asks a specific question about the current camera view. This is a one-shot operation.
     *
     * @param question The user's question.
     * @return A flow of navigation cues containing the answer.
     */
    fun askQuestion(question: String): Flow<NavigationCue>

    /**
     * Searches the live camera stream for a specific object. This is a transient operation.
     * The returned flow will emit cues and complete once the object is found and described.
     *
     * @param target Category label to search for (case-insensitive).
     * @return A flow of [NavigationCue] that emits updates on the search and location.
     */
    fun findObject(target: String): Flow<NavigationCue>

    /**
     * Identifies currency (bills/coins) from the provided image. This is a one-shot operation.
     *
     * @param image The image containing currency to identify.
     * @return A flow of navigation cues containing currency identification details.
     */
    fun identifyCurrency(image: ImageInput): Flow<NavigationCue>

    /**
     * Reads receipt or document text from the provided image. This is a one-shot operation.
     *
     * @param image The image containing the receipt or document to read.
     * @return A flow of navigation cues containing the organized text content.
     */
    fun readReceipt(image: ImageInput): Flow<NavigationCue>

    /**
     * Reads any visible text from the provided image. This is a one-shot operation.
     *
     * @param image The image containing text to read.
     * @return A flow of navigation cues containing the extracted text.
     */
    fun readText(image: ImageInput): Flow<NavigationCue>

    /**
     * Identifies currency from multiple frames for better accuracy. This is a one-shot operation.
     * Multiple frames help improve recognition accuracy and handle motion blur.
     *
     * @param images List of images containing the same currency from different angles/moments.
     * @return A flow of navigation cues containing currency identification details.
     */
    fun identifyCurrencyMultiFrame(images: List<ImageInput>): Flow<NavigationCue>

    /**
     * Reads receipt or document text from multiple frames for better accuracy. This is a one-shot operation.
     * Multiple frames help improve text recognition and handle motion blur or partial visibility.
     *
     * @param images List of images containing the same document from different angles/moments.
     * @return A flow of navigation cues containing the organized text content.
     */
    fun readReceiptMultiFrame(images: List<ImageInput>): Flow<NavigationCue>

    /**
     * Reads general text from multiple frames for better accuracy. This is a one-shot operation.
     * Multiple frames help improve text recognition and handle motion blur or partial visibility.
     *
     * @param images List of images containing the same text from different angles/moments.
     * @return A flow of navigation cues containing the text content.
     */
    fun readTextMultiFrame(images: List<ImageInput>): Flow<NavigationCue>
}