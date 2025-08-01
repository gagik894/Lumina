package com.lumina.domain.usecase

import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case for stopping navigation services.
 */
class StopNavigationUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke() {
        repository.stopNavigation()
    }
}
