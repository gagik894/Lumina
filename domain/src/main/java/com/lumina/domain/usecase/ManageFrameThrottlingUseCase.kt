package com.lumina.domain.usecase

import javax.inject.Inject

/**
 * Use case for managing frame processing throttling logic.
 *
 * Contains the business rules for frame throttling without Android dependencies.
 * This keeps the domain layer pure while providing throttling logic.
 */
class ManageFrameThrottlingUseCase @Inject constructor() {

    private var lastFrameProcessedTime = 0L
    private val frameThrottleInterval = 100L // Process max 10 frames per second
    private var isProcessingFrame = false

    /**
     * Checks if a frame should be processed based on throttling rules.
     *
     * @return true if frame should be processed, false if it should be dropped
     */
    fun shouldProcessFrame(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Throttle frame processing to prevent MediaPipe timestamp issues
        if (isProcessingFrame ||
            (currentTime - lastFrameProcessedTime) < frameThrottleInterval
        ) {
            return false
        }

        return true
    }

    /**
     * Marks the start of frame processing.
     */
    fun startFrameProcessing() {
        isProcessingFrame = true
        lastFrameProcessedTime = System.currentTimeMillis()
    }

    /**
     * Marks the end of frame processing.
     */
    fun endFrameProcessing() {
        isProcessingFrame = false
    }

    /**
     * Resets the throttling state. Useful when switching camera modes.
     */
    fun resetThrottling() {
        isProcessingFrame = false
        lastFrameProcessedTime = 0L
    }
}
