package com.lumina.domain.usecase

import com.lumina.domain.repository.LuminaRepository
import com.lumina.domain.service.CameraStateService
import javax.inject.Inject

/**
 * Use case for starting the navigation pipeline and activating the camera.
 *
 * This use case provides on-demand navigation activation, allowing users
 * to control when the camera and navigation system should be active for
 * better battery life and privacy.
 *
 * @property repository The LuminaRepository instance used to start navigation.
 * @property cameraStateService The CameraStateService used to activate the camera.
 */
class StartNavigationUseCase @Inject constructor(
    private val repository: LuminaRepository,
    private val cameraStateService: CameraStateService
) {

    /**
     * Starts the navigation director pipeline and activates the camera for continuous environmental monitoring.
     *
     * This should be called when the user explicitly requests navigation to begin.
     * The camera will be activated in navigation mode and the navigation system will
     * continuously analyze camera frames and provide intelligent alerts based on the environment.
     */
    operator fun invoke() {
        cameraStateService.switchToNavigation()
        repository.startNavigationPipeline()
    }
}
