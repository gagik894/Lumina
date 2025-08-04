package com.lumina.domain.usecase.system

import com.lumina.domain.model.InitializationState
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Use case to retrieve the current initialization state of the Lumina system.
 *
 * This use case provides a [kotlinx.coroutines.flow.StateFlow] that emits updates to the [com.lumina.domain.model.InitializationState].
 * Observers can subscribe to this flow to react to changes in the system's initialization status.
 *
 * @property repository The [com.lumina.domain.repository.LuminaRepository] responsible for providing the initialization state.
 */
class GetInitializationStateUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): StateFlow<InitializationState> = repository.initializationState
}