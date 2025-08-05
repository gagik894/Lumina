package com.lumina.domain.usecase.system

import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case for stopping all ongoing operations and releasing associated resources.
 *
 * This use case provides a clean way to stop all running operations,
 * ensuring proper resource cleanup when the user says "stop" or when
 * the application needs to halt all activities.
 *
 * @property repository The LuminaRepository used to stop all operations.
 */
class StopAllOperationsUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    /**
     * Stops all ongoing operations and releases associated resources.
     *
     * This should be called when the user explicitly requests all operations to stop
     * or when the application needs to ensure a clean state.
     */
    operator fun invoke() {
        repository.stopAllOperations()
    }
}