package com.lumina.domain.usecase

import com.lumina.domain.model.InitializationState
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Use case to retrieve the current initialization state of the Lumina system.
 *
 * This use case provides a [StateFlow] that emits updates to the [InitializationState].
 * Observers can subscribe to this flow to react to changes in the system's initialization status.
 *
 * @property repository The [LuminaRepository] responsible for providing the initialization state.
 */
class GetInitializationStateUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): StateFlow<InitializationState> = repository.initializationState
}

