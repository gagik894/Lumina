package com.lumina.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for managing camera activation state and configuration.
 *
 * This service controls when the camera should be active based on user needs,
 * supporting on-demand camera usage for better battery life and privacy.
 * The camera can be activated for specific operations and automatically
 * deactivated when not needed.
 */
@Singleton
class CameraStateService @Inject constructor() {

    companion object {
        private const val TAG = "CameraStateService"
    }

    /**
     * Defines different camera usage modes with specific configurations.
     */
    enum class CameraMode {
        /** Camera is completely off - no camera operations */
        INACTIVE,

        /** Standard navigation mode with balanced performance */
        NAVIGATION,

        /** High-resolution mode for text reading and OCR */
        TEXT_READING,

        /** Single photo capture mode for detailed analysis */
        PHOTO_CAPTURE
    }

    private val _currentMode = MutableStateFlow(CameraMode.INACTIVE)
    val currentMode: StateFlow<CameraMode> = _currentMode.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Activates the camera with the specified mode.
     *
     * @param mode The camera mode to activate
     */
    fun activateCamera(mode: CameraMode) {
        if (mode == CameraMode.INACTIVE) {
            deactivateCamera()
            return
        }

        println(TAG + "🟢 Camera ACTIVATED: $mode")
        _currentMode.value = mode
        _isActive.value = true
    }

    /**
     * Deactivates the camera completely.
     */
    fun deactivateCamera() {
        println(TAG + "🔴 Camera DEACTIVATED (was: ${_currentMode.value})")
        _currentMode.value = CameraMode.INACTIVE
        _isActive.value = false
    }

    /**
     * Checks if the camera is currently active.
     *
     * @return true if camera is active in any mode
     */
    fun isCameraActive(): Boolean {
        return _isActive.value
    }

    /**
     * Gets the current camera mode.
     *
     * @return Current camera mode
     */
    fun getCurrentMode(): CameraMode {
        return _currentMode.value
    }

    /**
     * Temporarily switches to text reading mode for high-resolution capture.
     * This is a transient operation that should be followed by deactivateCamera().
     */
    fun switchToTextReading() {
        println(TAG + "⚡ Switching to TEXT_READING mode")
        activateCamera(CameraMode.TEXT_READING)
    }

    /**
     * Temporarily switches to navigation mode for environmental monitoring.
     * This should be used for on-demand navigation sessions.
     */
    fun switchToNavigation() {
        println(TAG + "⚡ Switching to NAVIGATION mode")
        activateCamera(CameraMode.NAVIGATION)
    }

    /**
     * Temporarily switches to photo capture mode for single high-quality shots.
     * This is a transient operation that should be followed by deactivateCamera().
     */
    fun switchToPhotoCapture() {
        println(TAG + "⚡ Switching to PHOTO_CAPTURE mode")
        activateCamera(CameraMode.PHOTO_CAPTURE)
    }
}
