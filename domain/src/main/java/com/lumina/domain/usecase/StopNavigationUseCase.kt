package com.lumina.domain.usecase

import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case for stopping all operations and releasing resources.
 *
 * This use case provides explicit control over when all operations should stop,
 * ensuring proper resource cleanup and stopping all active modes.
 *
 * @property repository The LuminaRepository used to stop all operations.
 */
class StopNavigationUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    /**
     * Stops all ongoing operations and releases associated resources.
     *
     * This should be called when the user explicitly requests operations to stop
     * or when all operations need to be halted.
     */
    operator fun invoke() {
        repository.stopAllOperations()
    }
}
