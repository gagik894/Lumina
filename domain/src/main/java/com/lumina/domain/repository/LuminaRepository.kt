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
     * Provides intelligent navigation cues based on object detection and AI analysis.
     *
     * Uses a sophisticated decision tree with four rules:
     * 1. Critical threats (immediate obstacles)
     * 2. Important new objects appearing
     * 3. Periodic ambient updates
     * 4. Stable state (power saving mode)
     *
     * @return Flow of navigation cues with appropriate urgency levels
     */
    fun getNavigationCues(): Flow<NavigationCue>

    /**
     * Processes a new camera frame for object detection and potential AI analysis.
     *
     * @param image Camera frame to be analyzed
     */
    suspend fun processNewFrame(image: ImageInput)

    /**
     * Stops the proactive navigation pipeline and releases resources.
     */
    fun stopNavigation()

    /**
     * Describes a scene based on the provided image and prompt.
     *
     * @param image The image input to be processed
     * @param prompt The prompt to guide the description generation
     * @return A flow of navigation cues containing the scene description
     */
    fun describeScene(image: ImageInput, prompt: String): Flow<NavigationCue>

    /** Starts street-crossing guidance; pauses other navigation cues. */
    fun startCrossingMode()

    /** Stops street-crossing guidance and resumes normal navigation. */
    fun stopCrossingMode()

    /**
     * Asks a specific question about the current scene.
     *
     * @param question The user's question.
     * @return A flow of navigation cues containing the answer.
     */
    fun askQuestion(question: String): Flow<NavigationCue>

    /**
     * Searches the live camera stream for a specific object.
     * The flow emits a single [NavigationCue] when the object is detected.
     *
     * @param target Category label to search for (case-insensitive).
     */
    fun findObject(target: String): Flow<NavigationCue>
}