package com.lumina.domain.usecase.navigation

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


/**
 * Use case for starting street crossing guidance mode.
 *
 * This use case interacts with the [LuminaRepository] to initiate the street crossing guidance mode.
 * When invoked, it returns a [Flow] of [NavigationCue] objects, which represent the guidance instructions
 * provided to the user during the crossing process.
 */
class StartCrossingModeUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): Flow<NavigationCue> = repository.startCrossingMode()
}
