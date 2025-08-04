package com.lumina.domain.usecase.system

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import javax.inject.Inject

/**
 * Use case responsible for transforming a flow of [com.lumina.domain.model.NavigationCue] objects into a flow of UI state pairs.
 * This class encapsulates the logic for processing navigation cues and determining the appropriate
 * text and type to be displayed in the UI.
 *
 * It takes a [kotlinx.coroutines.flow.Flow] of [com.lumina.domain.model.NavigationCue] as input and uses the `scan` operator to accumulate
 * the messages from the cues. The `scan` operator maintains an internal state (the accumulator)
 * and updates it based on each emitted [com.lumina.domain.model.NavigationCue].
 *
 * The output is a [kotlinx.coroutines.flow.Flow] of [Pair]<String, [com.lumina.domain.model.NavigationCueType]>, where the String represents
 * the accumulated message to be displayed, and [com.lumina.domain.model.NavigationCueType] indicates the type of
 * navigation cue (e.g., CRITICAL, INFORMATIONAL, AMBIENT).
 *
 * If a [com.lumina.domain.model.NavigationCue] has its `isDone` flag set to true, the accumulated message is cleared.
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
        ) { (accumulator, previousType), navigationCue ->
            when (navigationCue) {
                is NavigationCue.CriticalAlert -> {
                    if (navigationCue.isDone) {
                        // When done, show the final complete message then clear for next
                        if (navigationCue.message.isNotBlank()) {
                            // Final message piece - add it and mark as complete
                            val finalText = accumulator + navigationCue.message
                            Pair(finalText, NavigationCueType.CRITICAL)
                        } else {
                            // Just a completion signal - clear the screen
                            Pair("", NavigationCueType.NONE)
                        }
                    } else {
                        // Still building the message
                        val text = accumulator + navigationCue.message
                        Pair(text, NavigationCueType.CRITICAL)
                    }
                }

                is NavigationCue.InformationalAlert -> {
                    if (navigationCue.isDone) {
                        // When done, show the final complete message then clear for next
                        if (navigationCue.message.isNotBlank()) {
                            // Final message piece - add it and mark as complete
                            val finalText = accumulator + navigationCue.message
                            Pair(finalText, NavigationCueType.INFORMATIONAL)
                        } else {
                            // Just a completion signal - clear the screen
                            Pair("", NavigationCueType.NONE)
                        }
                    } else {
                        // Still building the message
                        val text = accumulator + navigationCue.message
                        Pair(text, NavigationCueType.INFORMATIONAL)
                    }
                }

                is NavigationCue.AmbientUpdate -> {
                    if (navigationCue.isDone) {
                        // When done, show the final complete message then clear for next
                        if (navigationCue.message.isNotBlank()) {
                            // Final message piece - add it and mark as complete
                            val finalText = accumulator + navigationCue.message
                            Pair(finalText, NavigationCueType.AMBIENT)
                        } else {
                            // Just a completion signal - clear the screen
                            Pair("", NavigationCueType.NONE)
                        }
                    } else {
                        // Still building the message
                        val text = accumulator + navigationCue.message
                        Pair(text, NavigationCueType.AMBIENT)
                    }
                }
            }
        }
    }
}