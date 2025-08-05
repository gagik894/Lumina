package com.lumina.domain.usecase.camera

import com.lumina.domain.service.CameraStateService
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use case for managing camera operations and frame capture.
 *
 * This class encapsulates the logic related to controlling the camera,
 * such as activating it for different modes (text reading, navigation),
 * deactivating it, and capturing frames. It abstracts the underlying
 * `CameraStateService` and provides higher-level operations that can be
 * used by ViewModels or other domain components.
 *
 * The primary responsibilities include:
 * - Switching camera modes (text reading, navigation).
 * - Activating/deactivating the camera.
 * - Providing mechanisms to wait for and capture single or multiple camera frames.
 *
 * This use case helps in separating concerns by moving camera-specific logic
 * out of the presentation layer and into the domain layer, making the codebase
 * more modular and testable.
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
        frameCount: Int = 5,
        intervalMs: Long = 100,
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