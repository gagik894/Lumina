package com.lumina.data.datasource

import android.graphics.Bitmap
import com.lumina.domain.model.InitializationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * AiDataSource defines the contract for interacting with the AI model to generate scene descriptions.
 * It provides a method to generate a scene description based on an image and a prompt.
 */
interface AiDataSource {

    val initializationState: StateFlow<InitializationState>

    /**
     * Generates a scene description based on the provided prompt and image.
     * This method returns a flow of pairs containing the scene description and a boolean indicating if the response is complete.
     *
     * @param prompt The prompt to guide the description generation.
     * @param image The image input to be processed.
     * @return A flow of pairs containing the scene description and completion status.
     */
    fun generateResponse(prompt: String, image: Bitmap): Flow<Pair<String, Boolean>>

    /**
     * Resets the current session, clearing any state or cached data.
     * This is useful for starting a new session without residual data from previous interactions.
     */
    fun resetSession()
    /**
     * Closes any resources used by the data source.
     * This method should be called when the data source is no longer needed to free up resources.
     */
    fun close()
}