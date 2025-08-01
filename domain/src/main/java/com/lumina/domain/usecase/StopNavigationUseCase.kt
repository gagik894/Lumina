package com.lumina.domain.usecase

import com.lumina.domain.repository.LuminaRepository
import com.lumina.domain.service.CameraStateService
import javax.inject.Inject

/**
 * Use case for stopping navigation and deactivating the camera.
 *
 * This use case provides explicit control over when navigation should stop,
 * allowing users to conserve battery and maintain privacy by turning off
 * the camera when navigation is not needed.
 *
 * @property repository The LuminaRepository used to stop navigation pipeline.
 * @property cameraStateService The CameraStateService used to deactivate the camera.
 */
class StopNavigationUseCase @Inject constructor(
    private val repository: LuminaRepository,
    private val cameraStateService: CameraStateService
) {
    /**
     * Stops navigation pipeline and deactivates the camera completely.
     *
     * This should be called when the user explicitly requests navigation to stop
     * or when navigation is no longer needed. The camera will be turned off
     * to conserve battery and maintain privacy.
     */
    operator fun invoke() {
        repository.stopNavigation()
        cameraStateService.deactivateCamera()
    }
}
