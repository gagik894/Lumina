package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for starting street crossing guidance mode.
 */
class StartCrossingModeUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): Flow<NavigationCue> = repository.startCrossingMode()
}
