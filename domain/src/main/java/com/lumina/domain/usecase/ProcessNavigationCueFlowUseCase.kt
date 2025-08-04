package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import javax.inject.Inject

/**
 * Use case for processing NavigationCue flows into UI state.
 *
 * Extracts the complex flow transformation logic from the ViewModel
 * and encapsulates it in the domain layer.
 */
class ProcessNavigationCueFlowUseCase @Inject constructor() {

    /**
     * Processes a flow of NavigationCues into UI state pairs.
     *
     * @param navigationCueFlow Flow of navigation cues to process
     * @return Flow of Pair<String, NavigationCueType> for UI state
     */
    fun processFlow(navigationCueFlow: Flow<NavigationCue>): Flow<Pair<String, NavigationCueType>> {
        return navigationCueFlow.scan(
            Pair(
                "",
                NavigationCueType.NONE
            )
        ) { (accumulator, _), navigationCue ->
            when (navigationCue) {
                is NavigationCue.CriticalAlert -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.CRITICAL)
                }

                is NavigationCue.InformationalAlert -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.INFORMATIONAL)
                }

                is NavigationCue.AmbientUpdate -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.AMBIENT)
                }
            }
        }
    }
}
