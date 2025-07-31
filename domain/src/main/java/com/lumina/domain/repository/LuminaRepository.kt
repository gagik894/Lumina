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
}