package com.lumina.domain.usecase.system

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCueType
import com.lumina.domain.service.TextToSpeechService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for navigation cue processing and text-to-speech coordination.
 *
 * Responsibilities:
 * - Combines manual and automatic navigation cues into a unified flow
 * - Handles TTS processing reactively based on user preferences
 * - Manages navigation cue state accumulation for UI display
 * - Provides clean separation between domain logic and presentation layer
 *
 * This component consolidates navigation flow management that was previously
 * scattered across multiple layers, improving maintainability and testability.
 */
@Singleton
class NavigationOrchestrator @Inject constructor(
    private val ttsService: TextToSpeechService
) {
    // Input flow for manual navigation cues (user actions, voice commands)
    private val manualCueFlow = MutableSharedFlow<NavigationCue>()

    init {
        // Initialize TTS service when NavigationOrchestrator is created
        ttsService.initialize(
            onInitialized = {
            },
            onError = { error ->
            }
        )
    }

    /**
     * Emits a manual navigation cue into the system.
     */
    suspend fun emitNavigationCue(cue: NavigationCue) {
        manualCueFlow.emit(cue)
    }

    /**
     * Creates the complete navigation flow with TTS processing.
     *
     * @param automaticCueFlow Flow of automatic cues from camera/AI
     * @param isTtsEnabled Flow of TTS enabled state
     * @return Combined flow of processed navigation cues with TTS side effects
     */
    fun createNavigationFlow(
        automaticCueFlow: Flow<NavigationCue>,
        isTtsEnabled: Flow<Boolean>
    ): Flow<Pair<String, NavigationCueType>> {

        // Combine automatic and manual cues
        val combinedCueFlow = merge(automaticCueFlow, manualCueFlow)

        // Apply TTS processing as side effect
        val cueFlowWithTts = combine(combinedCueFlow, isTtsEnabled) { cue, ttsEnabled ->
            if (ttsEnabled) {
                ttsService.speak(cue)
            }
            cue
        }

        // Convert to UI state format
        return cueFlowWithTts.scan(
            Pair("", NavigationCueType.NONE)
        ) { (accumulator, _), navigationCue ->
            when (navigationCue) {
                is NavigationCue.CriticalAlert -> {
                    val text = if (navigationCue.isDone) {
                        accumulator + navigationCue.message
                    } else {
                        accumulator + navigationCue.message
                    }
                    Pair(text, NavigationCueType.CRITICAL)
                }

                is NavigationCue.InformationalAlert -> {
                    val text = if (navigationCue.isDone) {
                        accumulator + navigationCue.message
                    } else {
                        accumulator + navigationCue.message
                    }
                    Pair(text, NavigationCueType.INFORMATIONAL)
                }

                is NavigationCue.AmbientUpdate -> {
                    val text = if (navigationCue.isDone) {
                        accumulator + navigationCue.message
                    } else {
                        accumulator + navigationCue.message
                    }
                    Pair(text, NavigationCueType.AMBIENT)
                }
            }
        }
    }
}
