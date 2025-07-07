package com.lumina.data.datasource

import android.graphics.Bitmap
import com.lumina.domain.model.InitializationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a timestamped frame for motion analysis.
 *
 * @param bitmap The image frame
 * @param timestampMs The timestamp when this frame was captured (in milliseconds)
 */
data class TimestampedFrame(
    val bitmap: Bitmap,
    val timestampMs: Long
)

/**
 * AiDataSource defines the contract for interacting with the AI model to generate scene descriptions.
 * Supports both single image analysis and multi-frame motion analysis for enhanced understanding.
 */
interface AiDataSource {

    val initializationState: StateFlow<InitializationState>

    /**
     * Generates a scene description based on multiple timestamped frames for motion analysis.
     * This is the preferred method for navigation assistance as it provides context about movement.
     *
     * @param prompt The prompt to guide the description generation
     * @param frames List of timestamped frames (typically 3-5 frames over 150-300ms)
     * @return A flow of pairs containing the scene description and completion status
     */
    fun generateResponse(
        prompt: String,
        frames: List<TimestampedFrame>
    ): Flow<Pair<String, Boolean>>

    /**
     * Legacy method for single image analysis. Prefer the multi-frame version for better results.
     *
     * @param prompt The prompt to guide the description generation
     * @param image Single image input to be processed
     * @return A flow of pairs containing the scene description and completion status
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