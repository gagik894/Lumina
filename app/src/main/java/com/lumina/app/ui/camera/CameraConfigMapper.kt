package com.lumina.app.ui.camera

import com.lumina.domain.service.CameraStateService

/**
 * Utility functions for converting between domain camera modes and UI camera configurations.
 */
object CameraConfigMapper {

    /**
     * Converts domain camera mode to UI camera configuration.
     *
     * @param mode The domain camera mode
     * @return Corresponding camera configuration for UI
     */
    fun mapModeToConfig(mode: CameraStateService.CameraMode): CameraConfig {
        return when (mode) {
            CameraStateService.CameraMode.INACTIVE -> CameraConfig.NAVIGATION // Fallback
            CameraStateService.CameraMode.NAVIGATION -> CameraConfig.NAVIGATION
            CameraStateService.CameraMode.TEXT_READING -> CameraConfig.TEXT_READING
            CameraStateService.CameraMode.PHOTO_CAPTURE -> CameraConfig.PHOTO_CAPTURE
        }
    }

    /**
     * Checks if the camera should be active based on the mode.
     *
     * @param mode The domain camera mode
     * @return true if camera should be active
     */
    fun shouldCameraBeActive(mode: CameraStateService.CameraMode): Boolean {
        return mode != CameraStateService.CameraMode.INACTIVE
    }
}
