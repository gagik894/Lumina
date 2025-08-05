package com.lumina.domain.usecase.navigation

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for starting the navigation mode as a transient operation.
 *
 * This use case starts navigation as a self-contained transient operation
 * that returns a Flow of navigation cues. When the flow is cancelled or completed,
 * all resources are automatically cleaned up.
 *
 * @property repository The LuminaRepository instance used to start navigation.
 */
class StartNavigationUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    /**
     * Starts navigation as a transient operation and returns a flow of navigation cues.
     *
     * The returned flow will emit navigation cues continuously until cancelled.
     * When the flow is cancelled or completed, all associated resources are automatically cleaned up.
     *
     * @return Flow of navigation cues with appropriate urgency levels.
     */
    operator fun invoke(): Flow<NavigationCue> {
        return repository.startNavigation()
    }
}
