package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import javax.inject.Inject


/**
 * Use case responsible for transforming a flow of [NavigationCue] objects into a flow of UI state pairs.
 * This class encapsulates the logic for processing navigation cues and determining the appropriate
 * text and type to be displayed in the UI.
 *
 * It takes a [Flow] of [NavigationCue] as input and uses the `scan` operator to accumulate
 * the messages from the cues. The `scan` operator maintains an internal state (the accumulator)
 * and updates it based on each emitted [NavigationCue].
 *
 * The output is a [Flow] of [Pair]<String, [NavigationCueType]>, where the String represents
 * the accumulated message to be displayed, and [NavigationCueType] indicates the type of
 * navigation cue (e.g., CRITICAL, INFORMATIONAL, AMBIENT).
 *
 * If a [NavigationCue] has its `isDone` flag set to true, the accumulated message is cleared.
 * Otherwise, the new message from the cue is appended to the existing accumulator.
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
