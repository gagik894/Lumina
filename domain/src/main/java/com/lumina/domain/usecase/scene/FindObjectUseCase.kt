package com.lumina.domain.usecase.scene

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


/**
 * Use case for finding specific objects in the camera view.
 *
 * This use case interacts with the [LuminaRepository] to initiate the object finding process.
 * When invoked with a target object name, it returns a [Flow] of [NavigationCue] objects.
 * These cues provide guidance to the user on how to locate the target object within their camera's field of view.
 */
class FindObjectUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(target: String): Flow<NavigationCue> = repository.findObject(target)
}
