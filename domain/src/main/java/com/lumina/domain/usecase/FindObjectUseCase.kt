package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for finding specific objects in the camera view.
 */
class FindObjectUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(target: String): Flow<NavigationCue> = repository.findObject(target)
}
