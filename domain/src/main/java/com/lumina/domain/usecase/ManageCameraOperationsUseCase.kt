package com.lumina.domain.usecase

import com.lumina.domain.service.CameraStateService
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use case for managing camera operations and frame capture.
 *
 * Extracts camera management logic from the ViewModel and provides
 * domain-level operations for camera handling.
 */
class ManageCameraOperationsUseCase @Inject constructor(
    private val cameraStateService: CameraStateService
) {

    /**
     * Activates camera for text reading operations.
     */
    fun activateTextReadingMode() {
        cameraStateService.switchToTextReading()
    }

    /**
     * Activates camera for navigation operations.
     */
    fun activateNavigationMode() {
        cameraStateService.switchToNavigation()
    }

    /**
     * Deactivates camera to conserve battery and privacy.
     */
    fun deactivateCamera() {
        cameraStateService.deactivateCamera()
    }

    /**
     * Optimistically activates camera for voice commands.
     */
    fun optimisticallyActivateCamera() {
        cameraStateService.switchToTextReading()
    }

    /**
     * Waits for a camera frame to become available after camera activation.
     *
     * @param timeoutMs Maximum time to wait for a frame
     * @param frameProvider Function that provides the current frame
     * @return Frame bytes if available, null if timeout
     */
    suspend fun waitForFrame(
        timeoutMs: Long = 2000,
        frameProvider: () -> ByteArray?
    ): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            frameProvider()?.let { return it }
            delay(100) // Check every 100ms
        }
        return null
    }

    /**
     * Captures multiple frames for better accuracy.
     *
     * @param frameCount Number of frames to capture
     * @param intervalMs Interval between frame captures
     * @param frameProvider Function that provides the current frame
     * @return List of captured frames
     */
    suspend fun captureMultipleFrames(
        frameCount: Int = 3,
        intervalMs: Long = 200,
        frameProvider: () -> ByteArray?
    ): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()

        repeat(frameCount) { frameIndex ->
            val frame = waitForFrame(1000, frameProvider) // Shorter timeout per frame
            if (frame != null) {
                frames.add(frame)
                if (frameIndex < frameCount - 1) {
                    delay(intervalMs) // Wait between frames
                }
            }
        }

        return frames
    }
}
