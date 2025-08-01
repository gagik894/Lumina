package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting continuous navigation cues from the AI system.
 */
class GetNavigationCuesUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): Flow<NavigationCue> = repository.getNavigationCues()
}
