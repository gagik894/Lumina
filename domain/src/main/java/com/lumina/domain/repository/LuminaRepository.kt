package com.lumina.domain.repository

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.SceneDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * LuminaRepository defines the contract for interacting with the AI model to describe scenes.
 * It provides a method to describe a scene based on an image input and a prompt.
 */
interface LuminaRepository {
    val initializationState: StateFlow<InitializationState>
    fun getProactiveNavigationCues(): Flow<SceneDescription>
    suspend fun processNewFrame(image: ImageInput)
    fun stopProactiveNavigation()
    /**
     * Describes a scene based on the provided image and prompt.
     * This method returns a flow of SceneDescription, allowing for streaming responses.
     *
     * @param image The image input to be processed.
     * @param prompt The prompt to guide the description generation.
     * @return A flow of SceneDescription containing partial responses and completion status.
     */
    fun describeScene(image: ImageInput, prompt: String): Flow<SceneDescription>
}